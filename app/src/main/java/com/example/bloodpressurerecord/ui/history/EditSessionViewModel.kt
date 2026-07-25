package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionDraftStore
import com.example.bloodpressurerecord.ui.common.SessionFormDraft
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditSessionUiState(
    val measuredAtText: String = DateTimeInputFormatter.nowText(),
    val scene: String = "晨起",
    val reading1: SessionReadingInputUi = SessionReadingInputUi(),
    val reading2: SessionReadingInputUi = SessionReadingInputUi(),
    val extraReadings: List<SessionReadingInputUi> = emptyList(),
    val showExtraReadings: Boolean = false,
    val note: String = "",
    val selectedSymptoms: Set<String> = emptySet(),
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
    val saveDisabledReason: String = "至少填写两组有效读数后才能保存。",
    val isDirty: Boolean = false
)

class EditSessionViewModel(
    private val sessionId: String,
    private val repository: BloodPressureRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditSessionUiState())
    val uiState: StateFlow<EditSessionUiState> = _uiState.asStateFlow()
    private val draftStore = SessionDraftStore(savedStateHandle, "edit_session.$sessionId")
    private val restoredDraft = draftStore.restore()
    private var hasInitFromData = false
    private var pendingSaveInput: SaveSessionInput? = null
    private var pendingSaveContainsHighRisk: Boolean = false

    init {
        viewModelScope.launch {
            repository.observeSession(sessionId).collectLatest { session ->
                if (!hasInitFromData && session != null) {
                    hasInitFromData = true
                    if (restoredDraft != null) {
                        _uiState.value = restoredDraft.toEditUiState()
                        return@collectLatest
                    }
                    val sortedReadings = session.readings.sortedBy { it.orderIndex }
                    val reading1 = sortedReadings.getOrNull(0)?.toInputUi() ?: SessionReadingInputUi()
                    val reading2 = sortedReadings.getOrNull(1)?.toInputUi() ?: SessionReadingInputUi()
                    val extras = sortedReadings.drop(2).map { it.toInputUi() }
                    val derived = SessionFormLogic.recomputeDerived(
                        readings = listOf(reading1, reading2) + extras,
                        requiredCount = 2
                    )
                    _uiState.value = EditSessionUiState(
                        measuredAtText = DateTimeInputFormatter.format(session.measuredAt),
                        scene = session.scene,
                        reading1 = reading1,
                        reading2 = reading2,
                        extraReadings = extras,
                        showExtraReadings = extras.isNotEmpty(),
                        note = session.note.orEmpty(),
                        selectedSymptoms = session.symptoms.toSet(),
                        avgSystolic = derived.avgSystolic,
                        avgDiastolic = derived.avgDiastolic,
                        avgPulse = derived.avgPulse,
                        categoryLabel = derived.categoryLabel,
                        canSave = SessionFormLogic.saveDisabledReason(
                            listOf(reading1, reading2) + extras
                        ) == null,
                        saveDisabledReason = SessionFormLogic.saveDisabledReason(
                            listOf(reading1, reading2) + extras
                        ).orEmpty(),
                        loading = false
                    )
                } else if (!hasInitFromData && session == null) {
                    _uiState.update { it.copy(loading = false, message = "未找到可编辑记录。") }
                }
            }
        }
    }

    fun updateMeasuredAtText(value: String) = updateForm { it.copy(measuredAtText = value) }
    fun updateScene(value: String) = updateForm { it.copy(scene = value) }
    fun toggleThirdReading(show: Boolean) = updateReading {
        val nextExtras = if (show) {
            if (it.extraReadings.isEmpty()) listOf(SessionReadingInputUi()) else it.extraReadings
        } else {
            emptyList()
        }
        it.copy(showExtraReadings = show, extraReadings = nextExtras)
    }

    fun addNextReadingGroup() = updateReading {
        if (allReadings(it).size >= SessionFormLogic.UI_MAX_READING_COUNT) {
            return@updateReading it.copy(
                message = "每次测量最多 ${SessionFormLogic.UI_MAX_READING_COUNT} 组读数。"
            )
        }
        it.copy(showExtraReadings = true, extraReadings = it.extraReadings + SessionReadingInputUi())
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
            extraReadings = state.extraReadings.filterIndexed { itemIndex, _ -> itemIndex != index },
            showExtraReadings = state.extraReadings.size > 1
        )
    }

    fun updateNote(value: String) = updateForm { it.copy(note = value) }

    fun toggleSymptom(symptom: String) {
        _uiState.update { state ->
            val set = state.selectedSymptoms.toMutableSet()
            if (symptom == "无症状") {
                if (symptom in set) set.clear() else {
                    set.clear()
                    set += symptom
                }
            } else {
                set.remove("无症状")
                if (!set.add(symptom)) set.remove(symptom)
            }
            state.copy(selectedSymptoms = set, isDirty = true)
        }
        persistDraft()
    }

    fun onSaveClicked() {
        val state = _uiState.value
        val measuredAt = DateTimeInputFormatter.parse(state.measuredAtText)
        if (measuredAt == null) {
            _uiState.update { it.copy(message = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm") }
            return
        }
        val validate = SessionFormLogic.validateAndBuildReadings(
            readings = allReadings(state),
            requiredCount = 2
        )
        if (validate.error != null) {
            _uiState.update { it.copy(message = validate.error) }
            return
        }
        val input = SaveSessionInput(
            measuredAt = measuredAt,
            scene = state.scene,
            note = state.note,
            symptoms = state.selectedSymptoms.toList(),
            readings = validate.readings
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
                    _uiState.update {
                        it.copy(
                            saved = true,
                            message = "编辑已保存。",
                            isSaving = false,
                            showAbnormalConfirmDialog = false,
                            showHighRiskDialog = false
                        )
                    }
                    draftStore.clear()
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
            val derived = SessionFormLogic.recomputeDerived(
                readings = allReadings(next),
                requiredCount = 2
            )
            next.copy(
                avgSystolic = derived.avgSystolic,
                avgDiastolic = derived.avgDiastolic,
                avgPulse = derived.avgPulse,
                categoryLabel = derived.categoryLabel,
                canSave = SessionFormLogic.saveDisabledReason(allReadings(next)) == null,
                saveDisabledReason = SessionFormLogic.saveDisabledReason(allReadings(next)).orEmpty(),
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
        val state = _uiState.value
        draftStore.save(
            SessionFormDraft(
                measuredAtText = state.measuredAtText,
                scene = state.scene,
                readings = allReadings(state),
                note = state.note,
                symptoms = state.selectedSymptoms
            )
        )
    }

    fun discardDraft() {
        draftStore.clear()
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
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
            scene = scene,
            reading1 = first,
            reading2 = second,
            extraReadings = extras,
            showExtraReadings = extras.isNotEmpty(),
            note = note,
            selectedSymptoms = symptoms,
            message = "已恢复未保存的编辑草稿。",
            loading = false,
            isDirty = true
        )
        val derived = SessionFormLogic.recomputeDerived(allReadings(base))
        return base.copy(
            avgSystolic = derived.avgSystolic,
            avgDiastolic = derived.avgDiastolic,
            avgPulse = derived.avgPulse,
            categoryLabel = derived.categoryLabel,
            canSave = SessionFormLogic.saveDisabledReason(allReadings(base)) == null,
            saveDisabledReason = SessionFormLogic.saveDisabledReason(allReadings(base)).orEmpty()
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
        fun provideFactory(sessionId: String, repository: BloodPressureRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EditSessionViewModel(sessionId, repository) as T
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras
                ): T {
                    return EditSessionViewModel(
                        sessionId,
                        repository,
                        extras.createSavedStateHandle()
                    ) as T
                }
            }
        }
    }
}
