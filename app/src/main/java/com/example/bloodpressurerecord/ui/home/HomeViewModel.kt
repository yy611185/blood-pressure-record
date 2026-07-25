package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class HomeUiState(
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
    val formMessage: String = "",
    val showHighRiskDialog: Boolean = false,
    val showAbnormalConfirmDialog: Boolean = false,
    val abnormalConfirmMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val saveDisabledReason: String = "至少填写两组有效读数后才能保存。",
    val isDirty: Boolean = false
)

class HomeViewModel(
    private val repository: BloodPressureRepository,
    highRiskAlertEnabled: Flow<Boolean> = flowOf(true),
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val draftStore = SessionDraftStore(savedStateHandle, "add_session")
    private val restoredDraft = draftStore.restore()
    private val localState = MutableStateFlow(
        restoredDraft?.toHomeUiState() ?: HomeUiState()
    )
    private var pendingSaveInput: SaveSessionInput? = null
    private var pendingSaveContainsHighRisk: Boolean = false

    val uiState: StateFlow<HomeUiState> = localState.asStateFlow()
    val measurementCount: StateFlow<Int> = repository.observeSessionCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )
    private val highRiskAlertsEnabled = highRiskAlertEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    fun updateMeasuredAtText(value: String) = updateForm { it.copy(measuredAtText = value) }
    fun updateScene(value: String) = updateForm { it.copy(scene = value) }

    fun toggleThirdReading(show: Boolean) {
        localState.update { state ->
            val nextExtras = if (show) {
                if (state.extraReadings.isEmpty()) listOf(SessionReadingInputUi()) else state.extraReadings
            } else {
                emptyList()
            }
            recomputeDerived(state.copy(showExtraReadings = show, extraReadings = nextExtras))
        }
        persistDraft()
    }

    fun addNextReadingGroup() = updateReading { state ->
        if (allReadings(state).size >= SessionFormLogic.UI_MAX_READING_COUNT) {
            return@updateReading state.copy(
                formMessage = "每次测量最多 ${SessionFormLogic.UI_MAX_READING_COUNT} 组读数。"
            )
        }
        state.copy(
            showExtraReadings = true,
            extraReadings = state.extraReadings + SessionReadingInputUi()
        )
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
        localState.update { state ->
            val next = state.selectedSymptoms.toMutableSet()
            if (symptom == "无症状") {
                if (symptom in next) next.clear() else {
                    next.clear()
                    next += symptom
                }
            } else {
                next.remove("无症状")
                if (!next.add(symptom)) next.remove(symptom)
            }
            state.copy(selectedSymptoms = next, isDirty = true)
        }
        persistDraft()
    }

    fun onSaveClicked() {
        val state = localState.value
        if (state.isSaving) return
        val measuredAt = DateTimeInputFormatter.parse(state.measuredAtText)
        if (measuredAt == null) {
            localState.update { it.copy(formMessage = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm") }
            return
        }
        val validate = SessionFormLogic.validateAndBuildReadings(
            readings = allReadings(state),
            requiredCount = 2
        )
        if (validate.error != null) {
            localState.update { it.copy(formMessage = validate.error) }
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
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
        if (pendingSaveInput == null) return
        if (highRiskAlertsEnabled.value && pendingSaveContainsHighRisk) {
            localState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePendingInput()
    }

    fun dismissAbnormalDialog() {
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
    }

    fun confirmHighRiskAndSave() {
        localState.update { it.copy(showHighRiskDialog = false) }
        savePendingInput()
    }

    fun dismissHighRiskDialog() {
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
        localState.update { it.copy(showHighRiskDialog = false) }
    }

    fun clearForm() {
        draftStore.clear()
        localState.value = HomeUiState(
            measuredAtText = DateTimeInputFormatter.nowText(),
            scene = localState.value.scene,
            formMessage = "表单已清空。"
        )
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
    }

    fun discardDraft() {
        draftStore.clear()
        pendingSaveInput = null
        pendingSaveContainsHighRisk = false
    }

    private fun savePendingInput() {
        val input = pendingSaveInput ?: return
        if (localState.value.isSaving) return
        localState.update {
            it.copy(
                isSaving = true,
                formMessage = "正在保存..."
            )
        }
        viewModelScope.launch {
            repository.saveSession(input)
                .onSuccess {
                    val keptScene = localState.value.scene
                    localState.value = HomeUiState(
                        measuredAtText = DateTimeInputFormatter.nowText(),
                        scene = keptScene,
                        formMessage = "保存成功。"
                    )
                    draftStore.clear()
                    pendingSaveInput = null
                    pendingSaveContainsHighRisk = false
                }
                .onFailure { throwable ->
                    localState.update {
                        it.copy(
                            formMessage = "保存失败：${throwable.message ?: "请稍后重试"}",
                            isSaving = false,
                            showHighRiskDialog = false,
                            showAbnormalConfirmDialog = false
                        )
                    }
                }
        }
    }

    private fun updateReading(transform: (HomeUiState) -> HomeUiState) {
        localState.update { state -> recomputeDerived(transform(state).copy(isDirty = true)) }
        persistDraft()
    }

    private fun updateForm(transform: (HomeUiState) -> HomeUiState) {
        localState.update { transform(it).copy(isDirty = true) }
        persistDraft()
    }

    private fun persistDraft() {
        val state = localState.value
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

    private fun recomputeDerived(state: HomeUiState): HomeUiState {
        val derived = SessionFormLogic.recomputeDerived(
            readings = allReadings(state),
            requiredCount = 2
        )
        return state.copy(
            avgSystolic = derived.avgSystolic,
            avgDiastolic = derived.avgDiastolic,
            avgPulse = derived.avgPulse,
            categoryLabel = derived.categoryLabel,
            canSave = SessionFormLogic.saveDisabledReason(allReadings(state)) == null,
            saveDisabledReason = SessionFormLogic.saveDisabledReason(allReadings(state)).orEmpty()
        )
    }

    private fun allReadings(state: HomeUiState): List<SessionReadingInputUi> {
        return listOf(state.reading1, state.reading2) + state.extraReadings
    }

    private fun List<SessionReadingInputUi>.updateAt(
        index: Int,
        transform: (SessionReadingInputUi) -> SessionReadingInputUi
    ): List<SessionReadingInputUi> {
        if (index !in indices) return this
        return mapIndexed { i, item -> if (i == index) transform(item) else item }
    }

    private fun SessionFormDraft.toHomeUiState(): HomeUiState {
        val first = readings.getOrNull(0) ?: SessionReadingInputUi()
        val second = readings.getOrNull(1) ?: SessionReadingInputUi()
        val extras = readings.drop(2)
        return recomputeDerived(
            HomeUiState(
                measuredAtText = measuredAtText,
                scene = scene,
                reading1 = first,
                reading2 = second,
                extraReadings = extras,
                showExtraReadings = extras.isNotEmpty(),
                note = note,
                selectedSymptoms = symptoms,
                formMessage = "已恢复未保存的测量草稿。",
                isDirty = true
            )
        )
    }
}
