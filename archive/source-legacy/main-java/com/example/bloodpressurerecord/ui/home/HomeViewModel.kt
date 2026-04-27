package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val measuredAtText: String = DateTimeInputFormatter.nowText(),
    val scene: String = "晨起",
    val reading1: SessionReadingInputUi = SessionReadingInputUi(),
    val reading2: SessionReadingInputUi = SessionReadingInputUi(),
    val reading3: SessionReadingInputUi = SessionReadingInputUi(),
    val showThirdReading: Boolean = false,
    val note: String = "",
    val selectedSymptoms: Set<String> = emptySet(),
    val avgSystolic: Int? = null,
    val avgDiastolic: Int? = null,
    val avgPulse: Int? = null,
    val categoryLabel: String = "待计算",
    val formMessage: String = "",
    val showHighRiskDialog: Boolean = false,
    val showAbnormalConfirmDialog: Boolean = false,
    val abnormalConfirmMessage: String = ""
)

class HomeViewModel(
    private val repository: BloodPressureRepository
) : ViewModel() {
    private val localState = MutableStateFlow(HomeUiState())
    private var pendingSaveInput: SaveSessionInput? = null

    val uiState: StateFlow<HomeUiState> = localState.asStateFlow()

    val measurementCount: StateFlow<Int> =
        repository.observeSessionCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0
            )

    fun updateMeasuredAtText(value: String) {
        localState.update { it.copy(measuredAtText = value) }
    }

    fun updateScene(value: String) {
        localState.update { it.copy(scene = value) }
    }

    fun toggleThirdReading(show: Boolean) {
        localState.update { state ->
            recomputeDerived(
                state.copy(
                    showThirdReading = show,
                    reading3 = if (show) state.reading3 else SessionReadingInputUi()
                )
            )
        }
    }

    fun updateReading1Systolic(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(systolic = value)) }
    fun updateReading1Diastolic(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(diastolic = value)) }
    fun updateReading1Pulse(value: String) = updateReading { it.copy(reading1 = it.reading1.copy(pulse = value)) }
    fun updateReading2Systolic(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(systolic = value)) }
    fun updateReading2Diastolic(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(diastolic = value)) }
    fun updateReading2Pulse(value: String) = updateReading { it.copy(reading2 = it.reading2.copy(pulse = value)) }
    fun updateReading3Systolic(value: String) = updateReading { it.copy(reading3 = it.reading3.copy(systolic = value)) }
    fun updateReading3Diastolic(value: String) = updateReading { it.copy(reading3 = it.reading3.copy(diastolic = value)) }
    fun updateReading3Pulse(value: String) = updateReading { it.copy(reading3 = it.reading3.copy(pulse = value)) }

    fun updateNote(value: String) {
        localState.update { it.copy(note = value) }
    }

    fun toggleSymptom(symptom: String) {
        localState.update { state ->
            val next = state.selectedSymptoms.toMutableSet()
            if (!next.add(symptom)) next.remove(symptom)
            state.copy(selectedSymptoms = next)
        }
    }

    fun onSaveClicked() {
        val state = localState.value
        val measuredAt = DateTimeInputFormatter.parse(state.measuredAtText)
        if (measuredAt == null) {
            localState.update { it.copy(formMessage = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm") }
            return
        }

        val parseResult = validateAndBuildReadings(state)
        if (parseResult.error != null) {
            localState.update { it.copy(formMessage = parseResult.error) }
            return
        }

        val readings = parseResult.readings
        val saveInput = SaveSessionInput(
            measuredAt = measuredAt,
            scene = state.scene,
            note = state.note,
            symptoms = state.selectedSymptoms.toList(),
            readings = readings
        )

        val abnormalMessage = buildAbnormalMessage(readings)
        pendingSaveInput = saveInput
        if (abnormalMessage != null) {
            localState.update {
                it.copy(
                    showAbnormalConfirmDialog = true,
                    abnormalConfirmMessage = abnormalMessage
                )
            }
            return
        }

        if (containsHighRisk(readings)) {
            localState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePendingInput()
    }

    fun confirmAbnormalAndContinue() {
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
        val pending = pendingSaveInput ?: return
        if (containsHighRisk(pending.readings)) {
            localState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePendingInput()
    }

    fun dismissAbnormalDialog() {
        pendingSaveInput = null
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
    }

    fun confirmHighRiskAndSave() {
        localState.update { it.copy(showHighRiskDialog = false) }
        savePendingInput()
    }

    fun dismissHighRiskDialog() {
        pendingSaveInput = null
        localState.update { it.copy(showHighRiskDialog = false) }
    }

    fun clearForm() {
        localState.value = HomeUiState(
            measuredAtText = DateTimeInputFormatter.nowText(),
            scene = localState.value.scene,
            formMessage = "表单已清空。"
        )
        pendingSaveInput = null
    }

    private fun savePendingInput() {
        val input = pendingSaveInput ?: return
        viewModelScope.launch {
            repository.saveSession(input)
                .onSuccess {
                    val keptScene = localState.value.scene
                    localState.value = HomeUiState(
                        measuredAtText = DateTimeInputFormatter.nowText(),
                        scene = keptScene,
                        formMessage = "保存成功。"
                    )
                    pendingSaveInput = null
                }
                .onFailure { throwable ->
                    localState.update {
                        it.copy(
                            formMessage = "保存失败：${throwable.message ?: "请稍后重试"}",
                            showHighRiskDialog = false,
                            showAbnormalConfirmDialog = false
                        )
                    }
                )
        }
    }

    private fun updateReading(transform: (HomeUiState) -> HomeUiState) {
        localState.update { state ->
            recomputeDerived(transform(state))
        }
    }

    private fun recomputeDerived(state: HomeUiState): HomeUiState {
        val derived = SessionFormLogic.recomputeDerived(
            reading1 = state.reading1,
            reading2 = state.reading2,
            reading3 = state.reading3,
            showThird = state.showThirdReading
        )
        return state.copy(
            avgSystolic = derived.avgSystolic,
            avgDiastolic = derived.avgDiastolic,
            avgPulse = derived.avgPulse,
            categoryLabel = derived.categoryLabel
        )
    }

    private fun validateAndBuildReadings(state: HomeUiState): ReadingBuildResult {
        val result = SessionFormLogic.validateAndBuildReadings(
            reading1 = state.reading1,
            reading2 = state.reading2,
            reading3 = state.reading3,
            showThird = state.showThirdReading
        )
        return ReadingBuildResult(
            readings = result.readings,
            error = result.error
        )
    }

    private fun containsHighRisk(readings: List<SessionReadingInput>): Boolean {
        return SessionFormLogic.containsHighRisk(readings)
    }

    private fun buildAbnormalMessage(readings: List<SessionReadingInput>): String? {
        return SessionFormLogic.buildAbnormalMessage(readings)
    }

    private data class ReadingBuildResult(
        val readings: List<SessionReadingInput> = emptyList(),
        val error: String? = null
    )
}
