package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.time.MeasurementPeriod
import com.example.bloodpressurerecord.domain.time.MeasurementTimestampValidator
import com.example.bloodpressurerecord.ui.common.SessionDraftRepository
import com.example.bloodpressurerecord.ui.common.SessionDraftStore
import com.example.bloodpressurerecord.ui.common.RemovedExtraReading
import com.example.bloodpressurerecord.ui.common.RestoreExtraReadingResult
import com.example.bloodpressurerecord.ui.common.SessionFormDraft
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.update.UpdatePromptGate
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompletedSessionSave(
    val sessionId: String,
    val avgSystolic: Int?,
    val avgDiastolic: Int?,
    val categoryLabel: String
)

data class HomeUiState(
    val measuredAtText: String = DateTimeInputFormatter.nowText(),
    val scene: String = MeasurementPeriod.labelFor(System.currentTimeMillis()),
    val reading1: SessionReadingInputUi = SessionReadingInputUi(),
    val reading2: SessionReadingInputUi = SessionReadingInputUi(),
    val extraReadings: List<SessionReadingInputUi> = emptyList(),
    val note: String = "",
    val legacySymptoms: Set<String> = emptySet(),
    val symptomNote: String = "",
    val factorNote: String = "",
    val avgSystolic: Int? = null,
    val avgDiastolic: Int? = null,
    val avgPulse: Int? = null,
    val averagedGroupCount: Int = 0,
    val containsHighRiskReading: Boolean = false,
    val categoryLabel: String = "待计算",
    val formMessage: String = "",
    val formMessageIsError: Boolean = false,
    val saved: Boolean = false,
    val savedSessionId: String? = null,
    val completionDeadlineMillis: Long? = null,
    val showHighRiskDialog: Boolean = false,
    val showAbnormalConfirmDialog: Boolean = false,
    val abnormalConfirmMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val saveDisabledReason: String = "把两组的高压和低压都填好，就可以保存啦",
    val isDirty: Boolean = false,
    val restoredFromDraft: Boolean = false
)

class HomeViewModel(
    private val repository: BloodPressureRepository,
    highRiskAlertEnabled: Flow<Boolean> = flowOf(true),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    draftRepository: SessionDraftRepository? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val updatePromptGate: UpdatePromptGate? = null
) : ViewModel() {
    private val highRiskAlertsEnabled = highRiskAlertEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )
    private val draftStore = SessionDraftStore(savedStateHandle, "add_session", draftRepository)
    private var nextEditorId = 1L
    private val restoredDraft = draftStore.restore()
    private val restoredSavedId = (savedStateHandle.get<String>("add_session.saved_id") ?: restoredDraft?.sessionId)
        ?.takeIf { it.isNotBlank() }
    private val localState = MutableStateFlow(
        (restoredDraft?.toHomeUiState() ?: HomeUiState()).copy(
            savedSessionId = restoredSavedId,
            saved = (savedStateHandle["add_session.completed"] ?: false) && restoredSavedId != null,
            completionDeadlineMillis = savedStateHandle["add_session.deadline"]
        )
    )
    private var completedSave: CompletedSessionSave? = restoredSavedId?.takeIf {
        savedStateHandle.get<Boolean>("add_session.completed") == true
    }?.let { id ->
        CompletedSessionSave(
            sessionId = id,
            avgSystolic = savedStateHandle.get<Int>("add_session.saved_sys") ?: localState.value.avgSystolic,
            avgDiastolic = savedStateHandle.get<Int>("add_session.saved_dia") ?: localState.value.avgDiastolic,
            categoryLabel = savedStateHandle.get<String>("add_session.saved_cat") ?: localState.value.categoryLabel
        )
    }
    private var pendingSaveInput: SaveSessionInput? = null
    private var pendingSaveContainsHighRisk: Boolean = false

    val uiState: StateFlow<HomeUiState> = localState.asStateFlow()

    fun updateMeasuredAtText(value: String) = updateForm { state ->
        val period = DateTimeInputFormatter.parse(value)?.let { MeasurementPeriod.labelFor(it) }
        state.copy(measuredAtText = value, scene = period ?: state.scene, restoredFromDraft = false)
    }

    fun refreshMeasuredAtToNow() {
        if (localState.value.isSaving) return
        val now = nowMillis()
        updateForm { state ->
            state.copy(
                measuredAtText = DateTimeInputFormatter.format(now),
                scene = MeasurementPeriod.labelFor(now),
                restoredFromDraft = false,
                formMessage = ""
            )
        }
    }

    fun dismissRestoredDraftBanner() {
        if (localState.value.isSaving) return
        localState.update { it.copy(restoredFromDraft = false, formMessage = "") }
    }

    fun updateSymptomNote(value: String) = updateForm { it.copy(symptomNote = value) }
    fun updateFactorNote(value: String) = updateForm { it.copy(factorNote = value) }

    fun editSavedSession() {
        if (localState.value.isSaving || localState.value.savedSessionId.isNullOrBlank()) return
        completedSave = null
        savedStateHandle["add_session.completed"] = false
        savedStateHandle.remove<Long>("add_session.deadline")
        localState.update { it.copy(saved = false, completionDeadlineMillis = null, formMessage = "") }
    }

    /** 原子消费一次保存完成结果，防止自动返回与手动返回重复消费或读取保存后被清空的字段。 */
    fun consumeSavedSession(): CompletedSessionSave? {
        val state = localState.value
        if (!state.saved || state.isSaving) return null
        val snapshot = completedSave ?: CompletedSessionSave(
            sessionId = state.savedSessionId.orEmpty(),
            avgSystolic = state.avgSystolic,
            avgDiastolic = state.avgDiastolic,
            categoryLabel = state.categoryLabel
        )
        closeSavedSession()
        return snapshot
    }

    fun closeSavedSession() {
        if (localState.value.isSaving) return
        completedSave = null
        draftStore.clear()
        listOf("saved_id", "completed", "deadline", "saved_sys", "saved_dia", "saved_cat").forEach {
            savedStateHandle.remove<Any>("add_session.$it")
        }
        localState.value = HomeUiState()
    }

    fun addNextReadingGroup() = updateReading { state ->
        if (allReadings(state).size >= SessionFormLogic.UI_MAX_READING_COUNT) {
            return@updateReading state.copy(
                formMessage = "每次测量最多 ${SessionFormLogic.UI_MAX_READING_COUNT} 组读数。",
                formMessageIsError = true
            )
        }
        state.copy(
            extraReadings = state.extraReadings + SessionReadingInputUi(editorId = nextEditorId++),
            formMessage = ""
        )
    }

    fun updateReading1Systolic(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(systolic = value)) }
    fun updateReading1Diastolic(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(diastolic = value)) }
    fun updateReading1Pulse(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(pulse = value)) }
    fun updateReading2Systolic(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(systolic = value)) }
    fun updateReading2Diastolic(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(diastolic = value)) }
    fun updateReading2Pulse(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(pulse = value)) }
    fun updateExtraReadingSystolic(editorId: Long, value: String) = updateReading { state ->
        state.copy(extraReadings = state.extraReadings.updateById(editorId) { it.copy(systolic = value) })
    }

    fun updateExtraReadingDiastolic(editorId: Long, value: String) = updateReading { state ->
        state.copy(extraReadings = state.extraReadings.updateById(editorId) { it.copy(diastolic = value) })
    }

    fun updateExtraReadingPulse(editorId: Long, value: String) = updateReading { state ->
        state.copy(extraReadings = state.extraReadings.updateById(editorId) { it.copy(pulse = value) })
    }

    fun removeExtraReading(editorId: Long): RemovedExtraReading? {
        val state = localState.value
        if (state.isSaving || state.saved) return null
        val index = state.extraReadings.indexOfFirst { it.editorId == editorId }
        if (index < 0) return null
        val removed = state.extraReadings[index]
        updateReading { it.copy(extraReadings = it.extraReadings.filterNot { row -> row.editorId == editorId }) }
        return RemovedExtraReading(index, removed)
    }

    fun restoreExtraReading(removed: RemovedExtraReading): RestoreExtraReadingResult {
        val current = localState.value
        if (current.isSaving || current.saved) return RestoreExtraReadingResult.BUSY
        if (current.extraReadings.any { it.editorId == removed.reading.editorId }) return RestoreExtraReadingResult.RESTORED
        if (allReadings(current).size >= SessionFormLogic.UI_MAX_READING_COUNT) {
            val reminder = "已达 ${SessionFormLogic.UI_MAX_READING_COUNT} 组读数上限，请先删除一组后再撤销恢复。"
            localState.update { it.copy(formMessage = reminder, formMessageIsError = true) }
            return RestoreExtraReadingResult.AT_LIMIT
        }
        updateReading { state ->
            val restored = state.extraReadings.toMutableList()
            restored.add(removed.index.coerceIn(0, restored.size), removed.reading)
            state.copy(extraReadings = restored, formMessage = "", formMessageIsError = false)
        }
        return RestoreExtraReadingResult.RESTORED
    }

    fun onSaveClicked() {
        val state = localState.value
        if (state.isSaving || state.saved || state.showAbnormalConfirmDialog || state.showHighRiskDialog) return
        val measuredAt = DateTimeInputFormatter.parse(state.measuredAtText)
        if (measuredAt == null) {
            localState.update {
                it.copy(
                    formMessage = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm",
                    formMessageIsError = true
                )
            }
            return
        }
        MeasurementTimestampValidator.validate(measuredAt, nowMillis())?.let { message ->
            localState.update { it.copy(formMessage = message, formMessageIsError = true) }
            return
        }
        val validate = SessionFormLogic.validateAndBuildReadings(
            readings = allReadings(state),
            requiredCount = 2,
            strategy = AverageStrategy.ALL
        )
        if (validate.error != null) {
            localState.update { it.copy(formMessage = validate.error, formMessageIsError = true) }
            return
        }
        val input = SaveSessionInput(
            measuredAt = measuredAt,
            scene = MeasurementPeriod.labelFor(measuredAt),
            note = state.note,
            symptoms = state.legacySymptoms.toList(),
            timePeriod = MeasurementPeriod.labelFor(measuredAt),
            symptomNote = state.symptomNote,
            factorNote = state.factorNote,
            readings = validate.readings,
            averageStrategy = AverageStrategy.ALL
        )
        pendingSaveInput = input
        pendingSaveContainsHighRisk = validate.containsHighRiskReading
        SessionFormLogic.buildAbnormalMessage(validate.readings)?.let { abnormal ->
            localState.update { it.copy(showAbnormalConfirmDialog = true, abnormalConfirmMessage = abnormal) }
            return
        }
        if (highRiskAlertsEnabled.value && pendingSaveContainsHighRisk) {
            localState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePendingInput()
    }

    fun confirmAbnormalAndContinue() {
        if (localState.value.isSaving) return
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
        if (pendingSaveInput == null) return
        if (highRiskAlertsEnabled.value && pendingSaveContainsHighRisk) {
            localState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePendingInput()
    }

    fun dismissAbnormalDialog() {
        if (localState.value.isSaving) return
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
    }

    fun confirmHighRiskAndSave() {
        if (localState.value.isSaving) return
        localState.update { it.copy(showHighRiskDialog = false) }
        savePendingInput()
    }

    fun dismissHighRiskDialog() {
        if (localState.value.isSaving) return
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
        localState.update { it.copy(showHighRiskDialog = false) }
    }

    fun discardDraft() {
        if (localState.value.isSaving) return
        closeSavedSession()
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
    }

    fun saveDraft(onSaved: () -> Unit) {
        if (localState.value.isSaving || localState.value.saved) return
        val draft = currentDraft()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            localState.update { it.copy(isSaving = true) }
            val release = updatePromptGate?.hold()
            try {
                draftStore.persist(draft, Dispatchers.IO)
                    .onSuccess { onSaved() }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        localState.update { it.copy(formMessage = "草稿保存失败：${throwable.message ?: "请稍后重试"}", formMessageIsError = true) }
                    }
            } finally {
                localState.update { it.copy(isSaving = false) }
                release?.invoke()
            }
        }
    }

    private fun savePendingInput() {
        val input = pendingSaveInput ?: return
        if (localState.value.isSaving) return
        val submittedDerived = SessionFormLogic.recomputeDerived(
            readings = input.readings.map {
                SessionReadingInputUi(
                    systolic = it.systolic.toString(),
                    diastolic = it.diastolic.toString(),
                    pulse = it.pulse?.toString().orEmpty()
                )
            },
            requiredCount = 2,
            strategy = input.averageStrategy
        )
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            localState.update {
                it.copy(
                    isSaving = true,
                    formMessage = "正在保存...",
                    formMessageIsError = false
                )
            }
            val release = updatePromptGate?.hold()
            try {
                val existingId = localState.value.savedSessionId?.takeIf { it.isNotBlank() }
                val result = if (existingId == null) {
                    repository.saveSession(input)
                } else {
                    repository.updateSession(existingId, input).map { existingId }
                }
                result.onSuccess { sessionId ->
                    val deadline = nowMillis() + 5_000
                    val validId = sessionId.trim()
                    val summary = CompletedSessionSave(
                        sessionId = validId,
                        avgSystolic = submittedDerived.avgSystolic,
                        avgDiastolic = submittedDerived.avgDiastolic,
                        categoryLabel = submittedDerived.categoryLabel
                    )
                    completedSave = summary
                    savedStateHandle["add_session.saved_id"] = validId
                    savedStateHandle["add_session.completed"] = true
                    savedStateHandle["add_session.deadline"] = deadline
                    savedStateHandle["add_session.saved_sys"] = submittedDerived.avgSystolic
                    savedStateHandle["add_session.saved_dia"] = submittedDerived.avgDiastolic
                    savedStateHandle["add_session.saved_cat"] = submittedDerived.categoryLabel
                    localState.update {
                        it.copy(
                            saved = true,
                            savedSessionId = validId.takeIf { id -> id.isNotEmpty() },
                            avgSystolic = submittedDerived.avgSystolic,
                            avgDiastolic = submittedDerived.avgDiastolic,
                            avgPulse = submittedDerived.avgPulse,
                            averagedGroupCount = submittedDerived.averagedGroupCount,
                            containsHighRiskReading = submittedDerived.containsHighRiskReading,
                            categoryLabel = submittedDerived.categoryLabel,
                            completionDeadlineMillis = deadline,
                            isSaving = false,
                            isDirty = false,
                            formMessage = "",
                            showHighRiskDialog = false,
                            showAbnormalConfirmDialog = false
                        )
                    }
                    draftStore.clear()
                    persistDraft()
                    pendingSaveInput = null
                    pendingSaveContainsHighRisk = false
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    localState.update {
                        it.copy(
                            formMessage = "保存失败：${throwable.message ?: "请稍后重试"}",
                            formMessageIsError = true,
                            isSaving = false,
                            showHighRiskDialog = false,
                            showAbnormalConfirmDialog = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                localState.update { it.copy(isSaving = false) }
                throw cancelled
            } finally {
                release?.invoke()
            }
        }
    }

    private fun updateReading(transform: (HomeUiState) -> HomeUiState) {
        if (localState.value.isSaving) return
        if (localState.value.saved) return
        localState.update { state -> recomputeDerived(transform(state).copy(isDirty = true)) }
        persistDraft()
    }

    private fun updateForm(transform: (HomeUiState) -> HomeUiState) {
        if (localState.value.isSaving) return
        if (localState.value.saved) return
        localState.update { transform(it).copy(isDirty = true) }
        persistDraft()
    }

    private fun persistDraft() {
        draftStore.save(currentDraft())
    }

    private fun currentDraft(): SessionFormDraft {
        val state = localState.value
        return SessionFormDraft(
            measuredAtText = state.measuredAtText,
            scene = state.scene,
            readings = allReadings(state),
            note = state.note,
            symptoms = state.legacySymptoms,
            timePeriod = state.scene,
            symptomNote = state.symptomNote,
            factorNote = state.factorNote,
            sessionId = state.savedSessionId
        )
    }

    private fun recomputeDerived(state: HomeUiState): HomeUiState {
        val derived = SessionFormLogic.recomputeDerived(
            readings = allReadings(state),
            requiredCount = 2,
            strategy = AverageStrategy.ALL
        )
        val readingError = SessionFormLogic.saveDisabledReason(allReadings(state))
        return state.copy(
            avgSystolic = derived.avgSystolic,
            avgDiastolic = derived.avgDiastolic,
            avgPulse = derived.avgPulse,
            averagedGroupCount = derived.averagedGroupCount,
            containsHighRiskReading = derived.containsHighRiskReading,
            categoryLabel = derived.categoryLabel,
            canSave = readingError == null,
            saveDisabledReason = readingError.orEmpty()
        )
    }

    private fun allReadings(state: HomeUiState): List<SessionReadingInputUi> {
        return listOf(state.reading1, state.reading2) + state.extraReadings
    }

    private fun List<SessionReadingInputUi>.updateById(
        editorId: Long,
        transform: (SessionReadingInputUi) -> SessionReadingInputUi
    ): List<SessionReadingInputUi> {
        return map { if (it.editorId == editorId) transform(it) else it }
    }

    private fun SessionFormDraft.toHomeUiState(): HomeUiState {
        val first = readings.getOrNull(0) ?: SessionReadingInputUi()
        val second = readings.getOrNull(1) ?: SessionReadingInputUi()
        val extras = readings.drop(2).map { it.copy(editorId = nextEditorId++) }
        return recomputeDerived(
            HomeUiState(
                measuredAtText = measuredAtText,
                scene = DateTimeInputFormatter.parse(measuredAtText)?.let { MeasurementPeriod.labelFor(it) } ?: scene,
                reading1 = first,
                reading2 = second,
                extraReadings = extras,
                note = note,
                legacySymptoms = symptoms,
                symptomNote = symptomNote.orEmpty(),
                factorNote = factorNote.orEmpty(),
                formMessage = "",
                isDirty = true,
                restoredFromDraft = true
            )
        )
    }
}
