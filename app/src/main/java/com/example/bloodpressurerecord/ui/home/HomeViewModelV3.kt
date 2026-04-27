package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
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
    val abnormalConfirmMessage: String = ""
)

class HomeViewModel(
    private val repository: BloodPressureRepository
) : ViewModel() {
    private val localState = MutableStateFlow(HomeUiState())
    private var pendingSaveInput: SaveSessionInput? = null

    val uiState: StateFlow<HomeUiState> = localState.asStateFlow()
    val measurementCount: StateFlow<Int> = repository.observeSessionCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    fun updateMeasuredAtText(value: String) = localState.update { it.copy(measuredAtText = value) }
    fun updateScene(value: String) = localState.update { it.copy(scene = value) }

    fun toggleThirdReading(show: Boolean) {
        localState.update { state ->
            val nextExtras = if (show) {
                if (state.extraReadings.isEmpty()) listOf(SessionReadingInputUi()) else state.extraReadings
            } else {
                emptyList()
            }
            recomputeDerived(state.copy(showExtraReadings = show, extraReadings = nextExtras))
        }
    }

    fun addNextReadingGroup() = updateReading { state ->
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

    fun updateNote(value: String) = localState.update { it.copy(note = value) }

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
        SessionFormLogic.buildAbnormalMessage(validate.readings)?.let { abnormal ->
            localState.update { it.copy(showAbnormalConfirmDialog = true, abnormalConfirmMessage = abnormal) }
            return
        }
        if (SessionFormLogic.containsHighRisk(validate.readings)) {
            localState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePendingInput()
    }

    fun confirmAbnormalAndContinue() {
        localState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
        val pending = pendingSaveInput ?: return
        if (SessionFormLogic.containsHighRisk(pending.readings)) {
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
                }
        }
    }

    private fun updateReading(transform: (HomeUiState) -> HomeUiState) {
        localState.update { state -> recomputeDerived(transform(state)) }
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
            categoryLabel = derived.categoryLabel
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
}
