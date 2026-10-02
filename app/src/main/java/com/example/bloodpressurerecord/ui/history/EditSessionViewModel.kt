package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.domain.time.MeasurementPeriod
import com.example.bloodpressurerecord.ui.common.SessionDerivedResult
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionDraftStore
import com.example.bloodpressurerecord.ui.common.SessionDraftRepository
import com.example.bloodpressurerecord.ui.common.SessionFormDraft
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.time.MeasurementTimestampValidator
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EditSessionUiState(
    val measuredAtText: String = DateTimeInputFormatter.nowText(),
    val scene: String = MeasurementPeriod.labelFor(System.currentTimeMillis()),
    val reading1: SessionReadingInputUi = SessionReadingInputUi(),
    val reading2: SessionReadingInputUi = SessionReadingInputUi(),
    val extraReadings: List<SessionReadingInputUi> = emptyList(),
    val note: String = "",
    val legacySymptoms: Set<String> = emptySet(),
    val symptomNote: String = "",
    val factorNote: String = "",
    val averagedGroupCount: Int = 0,
    val completionDeadlineMillis: Long? = null,
    val avgSystolic: Int? = null,
    val avgDiastolic: Int? = null,
    val avgPulse: Int? = null,
    val categoryLabel: String = "待计算",
    val message: String = "",
    val loading: Boolean = true,
    val showHighRiskDialog: Boolean = false,
    val showAbnormalConfirmDialog: Boolean = false,
    val abnormalConfirmMessage: String = "",
    val saved: Boolean = false,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val saveDisabledReason: String = "把两组的高压和低压都填好，就可以保存啦",
    val isDirty: Boolean = false
)

class EditSessionViewModel(
    private val sessionId: String,
    private val repository: BloodPressureRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    draftRepository: SessionDraftRepository? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditSessionUiState())
    val uiState: StateFlow<EditSessionUiState> = _uiState.asStateFlow()
    private val draftStore = SessionDraftStore(
        savedStateHandle, "edit_session.$sessionId", draftRepository
    )
    private val restoredDraft = draftStore.restore()
    private var hasInitFromData = false
    private var persistedAverageStrategy: AverageStrategy? = null
    private var originalReadings: List<SessionReadingInputUi> = emptyList()
    private var originalDerived: SessionDerivedResult? = null
    private var minimumReadingCount = MeasurementInputRules.MIN_READING_COUNT
    private var pendingSaveInput: SaveSessionInput? = null
    private var pendingSaveContainsHighRisk: Boolean = false

    private fun averageStrategy(): AverageStrategy = persistedAverageStrategy ?: AverageStrategy.ALL

    fun editSavedSession() {
        savedStateHandle["edit_session.$sessionId.completed"] = false
        savedStateHandle.remove<Long>("edit_session.$sessionId.deadline")
        _uiState.update { it.copy(saved = false, completionDeadlineMillis = null, message = "") }
    }

    fun closeSavedSession() {
        draftStore.clear()
        savedStateHandle.remove<Boolean>("edit_session.$sessionId.completed")
        savedStateHandle.remove<Long>("edit_session.$sessionId.deadline")
    }

    init {
        viewModelScope.launch {
            repository.observeSession(sessionId).collectLatest { session ->
                if (!hasInitFromData && session != null) {
                    hasInitFromData = true
                    // 编辑旧记录必须沿用它保存时的策略，不能受当前全局设置变化影响。
                    persistedAverageStrategy = session.averageStrategy
                    val sortedReadings = session.readings.sortedBy { it.orderIndex }
                    minimumReadingCount = minOf(
                        MeasurementInputRules.MIN_READING_COUNT,
                        sortedReadings.size.coerceAtLeast(1)
                    )
                    val reading1 = sortedReadings.getOrNull(0)?.toInputUi() ?: SessionReadingInputUi()
                    val reading2 = sortedReadings.getOrNull(1)?.toInputUi() ?: SessionReadingInputUi()
                    val extras = sortedReadings.drop(2).map { it.toInputUi() }
                    originalReadings = listOf(reading1, reading2) + extras
                    originalDerived = SessionDerivedResult(
                        session.avgSystolic, session.avgDiastolic, session.avgPulse,
                        CategoryPresentation.label(session.category), session.containsHighRiskReading,
                        sortedReadings.size
                    )
                    if (restoredDraft != null) {
                        _uiState.value = restoredDraft.toEditUiState()
                        return@collectLatest
                    }
                    val derived = originalDerived!!
                    _uiState.value = EditSessionUiState(
                        measuredAtText = DateTimeInputFormatter.format(session.measuredAt),
                        scene = MeasurementPeriod.labelFor(session.measuredAt),
                        reading1 = reading1,
                        reading2 = reading2,
                        extraReadings = extras,
                        note = session.note.orEmpty(),
                        legacySymptoms = session.symptoms.toSet(),
                        symptomNote = session.symptomNote.orEmpty(),
                        factorNote = session.factorNote.orEmpty(),
                        averagedGroupCount = derived.averagedGroupCount,
                        saved = savedStateHandle["edit_session.$sessionId.completed"] ?: false,
                        completionDeadlineMillis = savedStateHandle["edit_session.$sessionId.deadline"],
                        avgSystolic = derived.avgSystolic,
                        avgDiastolic = derived.avgDiastolic,
                        avgPulse = derived.avgPulse,
                        categoryLabel = derived.categoryLabel,
                        canSave = SessionFormLogic.saveDisabledReason(
                            listOf(reading1, reading2) + extras,
                            requiredCount = minimumReadingCount,
                            maximumCount = MeasurementInputRules.MAX_READING_COUNT
                        ) == null,
                        saveDisabledReason = SessionFormLogic.saveDisabledReason(
                            listOf(reading1, reading2) + extras,
                            requiredCount = minimumReadingCount,
                            maximumCount = MeasurementInputRules.MAX_READING_COUNT
                        ).orEmpty(),
                        loading = false
                    )
                } else if (!hasInitFromData && session == null) {
                    _uiState.update { it.copy(loading = false, message = "未找到可编辑记录。") }
                }
            }
        }
    }

    fun updateMeasuredAtText(value: String) = updateForm { state ->
        state.copy(measuredAtText = value,
            scene = DateTimeInputFormatter.parse(value)?.let { MeasurementPeriod.labelFor(it) } ?: state.scene)
    }
    fun updateSymptomNote(value: String) = updateForm { it.copy(symptomNote = value) }
    fun updateFactorNote(value: String) = updateForm { it.copy(factorNote = value) }

    fun addNextReadingGroup() = updateReading {
        if (allReadings(it).size >= SessionFormLogic.UI_MAX_READING_COUNT) {
            return@updateReading it.copy(
                message = "每次测量最多 ${SessionFormLogic.UI_MAX_READING_COUNT} 组读数。"
            )
        }
        it.copy(extraReadings = it.extraReadings + SessionReadingInputUi())
    }

    fun updateReading1Systolic(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(systolic = value)) }
    fun updateReading1Diastolic(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(diastolic = value)) }
    fun updateReading1Pulse(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(pulse = value)) }
    fun updateReading2Systolic(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(systolic = value)) }
    fun updateReading2Diastolic(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(diastolic = value)) }
    fun updateReading2Pulse(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(pulse = value)) }
    fun updateExtraReadingSystolic(index: Int, value: String) = updateReading { state ->
        state.copy(extraReadings = state.extraReadings.updateAt(index) { it.copy(systolic = value) })
    }

    fun updateExtraReadingDiastolic(index: Int, value: String) = updateReading { state ->
        state.copy(extraReadings = state.extraReadings.updateAt(index) { it.copy(diastolic = value) })
    }

    fun updateExtraReadingPulse(index: Int, value: String) = updateReading { state ->
        state.copy(extraReadings = state.extraReadings.updateAt(index) { it.copy(pulse = value) })
    }

    fun removeExtraReading(index: Int) = updateReading { state ->
        state.copy(
            extraReadings = state.extraReadings.filterIndexed { itemIndex, _ -> itemIndex != index }
        )
    }

    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSaving || state.saved || state.showHighRiskDialog || state.showAbnormalConfirmDialog) return
        val measuredAt = DateTimeInputFormatter.parse(state.measuredAtText)
        if (measuredAt == null) {
            _uiState.update { it.copy(message = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm") }
            return
        }
        MeasurementTimestampValidator.validate(measuredAt, nowMillis())?.let { message ->
            _uiState.update { it.copy(message = message) }
            return
        }
        val validate = SessionFormLogic.validateAndBuildReadings(
            readings = allReadings(state),
            requiredCount = minimumReadingCount,
            strategy = averageStrategy(),
            maximumCount = MeasurementInputRules.MAX_READING_COUNT
        )
        if (validate.error != null) {
            _uiState.update { it.copy(message = validate.error) }
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
            averageStrategy = averageStrategy()
        )
        pendingSaveInput = input
        pendingSaveContainsHighRisk = validate.containsHighRiskReading
        val abnormal = SessionFormLogic.buildAbnormalMessage(validate.readings)
        if (abnormal != null) {
            _uiState.update { it.copy(showAbnormalConfirmDialog = true, abnormalConfirmMessage = abnormal) }
            return
        }
        if (pendingSaveContainsHighRisk) {
            _uiState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePending()
    }

    fun confirmAbnormalAndContinue() {
        _uiState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
        if (pendingSaveInput == null) return
        if (pendingSaveContainsHighRisk) {
            _uiState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePending()
    }

    fun dismissAbnormalDialog() {
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
        _uiState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
    }

    fun confirmHighRiskAndSave() {
        _uiState.update { it.copy(showHighRiskDialog = false) }
        savePending()
    }

    fun dismissHighRiskDialog() {
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
        _uiState.update { it.copy(showHighRiskDialog = false) }
    }

    private fun savePending() {
        val input = pendingSaveInput ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = "正在保存…") }
        viewModelScope.launch {
            repository.updateSession(sessionId, input)
                .onSuccess {
                    // 后续“修改”以刚保存的原始读数和代表值为基准。
                    val savedReadings = input.readings.map {
                        SessionReadingInputUi(it.systolic.toString(), it.diastolic.toString(), it.pulse?.toString().orEmpty())
                    }
                    val savedDerived = deriveFor(savedReadings)
                    originalReadings = savedReadings
                    originalDerived = savedDerived
                    val deadline = nowMillis() + 5_000
                    savedStateHandle["edit_session.$sessionId.completed"] = true
                    savedStateHandle["edit_session.$sessionId.deadline"] = deadline
                    _uiState.update {
                        it.copy(
                            saved = true,
                            isDirty = false,
                            completionDeadlineMillis = deadline,
                            message = "编辑已保存。",
                            isSaving = false,
                            showAbnormalConfirmDialog = false,
                            showHighRiskDialog = false
                        )
                    }
                    draftStore.clear()
                    persistDraft()
                    pendingSaveInput = null
                    pendingSaveContainsHighRisk = false
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            message = "保存失败：${throwable.message ?: "请稍后重试"}",
                            isSaving = false,
                            showAbnormalConfirmDialog = false,
                            showHighRiskDialog = false
                        )
                    }
                }
        }
    }

    private fun updateReading(transform: (EditSessionUiState) -> EditSessionUiState) {
        _uiState.update { state ->
            val next = transform(state)
            val derived = deriveFor(allReadings(next))
            next.copy(
                avgSystolic = derived.avgSystolic,
                avgDiastolic = derived.avgDiastolic,
                avgPulse = derived.avgPulse,
                averagedGroupCount = derived.averagedGroupCount,
                categoryLabel = derived.categoryLabel,
                canSave = SessionFormLogic.saveDisabledReason(
                    allReadings(next), minimumReadingCount, MeasurementInputRules.MAX_READING_COUNT
                ) == null,
                saveDisabledReason = SessionFormLogic.saveDisabledReason(
                    allReadings(next), minimumReadingCount, MeasurementInputRules.MAX_READING_COUNT
                ).orEmpty(),
                isDirty = true
            )
        }
        persistDraft()
    }

    private fun updateForm(transform: (EditSessionUiState) -> EditSessionUiState) {
        _uiState.update { transform(it).copy(isDirty = true) }
        persistDraft()
    }

    private fun persistDraft() {
        draftStore.save(currentDraft())
    }

    fun saveDraft(onSaved: () -> Unit) {
        val draft = currentDraft()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { draftStore.persist(draft) }
            result.onSuccess { onSaved() }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = "草稿保存失败：${throwable.message ?: "请稍后重试"}")
                }
            }
        }
    }

    private fun currentDraft(): SessionFormDraft {
        val state = _uiState.value
        return SessionFormDraft(
            measuredAtText = state.measuredAtText,
            scene = state.scene,
            readings = allReadings(state),
            note = state.note,
            symptoms = state.legacySymptoms,
            timePeriod = state.scene,
            symptomNote = state.symptomNote,
            factorNote = state.factorNote
        )
    }

    fun discardDraft() {
        draftStore.clear()
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
    }

    private fun deriveFor(readings: List<SessionReadingInputUi>): SessionDerivedResult {
        fun values(items: List<SessionReadingInputUi>) = items
            .filter { it.systolic.isNotBlank() || it.diastolic.isNotBlank() || it.pulse.isNotBlank() }
            .map { Triple(it.systolic.toIntOrNull(), it.diastolic.toIntOrNull(), it.pulse.toIntOrNull()) }
        if (values(readings) == values(originalReadings)) originalDerived?.let { return it }
        return SessionFormLogic.recomputeDerived(readings, minimumReadingCount, averageStrategy())
    }

    private fun allReadings(state: EditSessionUiState): List<SessionReadingInputUi> {
        return listOf(state.reading1, state.reading2) + state.extraReadings
    }

    private fun List<SessionReadingInputUi>.updateAt(
        index: Int,
        transform: (SessionReadingInputUi) -> SessionReadingInputUi
    ): List<SessionReadingInputUi> {
        if (index !in indices) return this
        return mapIndexed { i, item -> if (i == index) transform(item) else item }
    }

    private fun SessionFormDraft.toEditUiState(): EditSessionUiState {
        val first = readings.getOrNull(0) ?: SessionReadingInputUi()
        val second = readings.getOrNull(1) ?: SessionReadingInputUi()
        val extras = readings.drop(2)
        val base = EditSessionUiState(
            measuredAtText = measuredAtText,
            scene = DateTimeInputFormatter.parse(measuredAtText)?.let { MeasurementPeriod.labelFor(it) } ?: scene,
            reading1 = first,
            reading2 = second,
            extraReadings = extras,
            note = note,
            legacySymptoms = symptoms,
            symptomNote = symptomNote.orEmpty(),
            factorNote = factorNote.orEmpty(),
            saved = savedStateHandle["edit_session.$sessionId.completed"] ?: false,
            completionDeadlineMillis = savedStateHandle["edit_session.$sessionId.deadline"],
            message = "已恢复未保存的编辑草稿。",
            loading = false,
            isDirty = true
        )
        val derived = deriveFor(allReadings(base))
        return base.copy(
            avgSystolic = derived.avgSystolic,
            avgDiastolic = derived.avgDiastolic,
            avgPulse = derived.avgPulse,
            averagedGroupCount = derived.averagedGroupCount,
            categoryLabel = derived.categoryLabel,
            canSave = SessionFormLogic.saveDisabledReason(
                allReadings(base), minimumReadingCount, MeasurementInputRules.MAX_READING_COUNT
            ) == null,
            saveDisabledReason = SessionFormLogic.saveDisabledReason(
                allReadings(base), minimumReadingCount, MeasurementInputRules.MAX_READING_COUNT
            ).orEmpty()
        )
    }

    private fun com.example.bloodpressurerecord.data.repository.SessionReading.toInputUi(): SessionReadingInputUi {
        return SessionReadingInputUi(
            systolic = systolic.toString(),
            diastolic = diastolic.toString(),
            pulse = pulse?.toString().orEmpty()
        )
    }

    companion object {
        fun provideFactory(
            sessionId: String,
            repository: BloodPressureRepository,
            draftRepository: SessionDraftRepository? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EditSessionViewModel(
                        sessionId, repository,
                        draftRepository = draftRepository
                    ) as T
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras
                ): T {
                    return EditSessionViewModel(
                        sessionId,
                        repository,
                        extras.createSavedStateHandle(),
                        draftRepository
                    ) as T
                }
            }
        }
    }
}
