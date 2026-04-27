package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
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
    val reading3: SessionReadingInputUi = SessionReadingInputUi(),
    val showThirdReading: Boolean = false,
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
    val saved: Boolean = false
)

class EditSessionViewModel(
    private val sessionId: String,
    private val repository: BloodPressureRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditSessionUiState())
    val uiState: StateFlow<EditSessionUiState> = _uiState.asStateFlow()
    private var hasInitFromData = false
    private var pendingSaveInput: SaveSessionInput? = null

    init {
        viewModelScope.launch {
            repository.observeSession(sessionId).collectLatest { session ->
                if (!hasInitFromData && session != null) {
                    hasInitFromData = true
                    val reading1 = session.readings.find { it.orderIndex == 1 }?.toInputUi() ?: SessionReadingInputUi()
                    val reading2 = session.readings.find { it.orderIndex == 2 }?.toInputUi() ?: SessionReadingInputUi()
                    val reading3 = session.readings.find { it.orderIndex == 3 }?.toInputUi() ?: SessionReadingInputUi()
                    val showThird = session.readings.any { it.orderIndex == 3 }
                    val derived = SessionFormLogic.recomputeDerived(reading1, reading2, reading3, showThird)
                    _uiState.value = EditSessionUiState(
                        measuredAtText = DateTimeInputFormatter.format(session.measuredAt),
                        scene = session.scene,
                        reading1 = reading1,
                        reading2 = reading2,
                        reading3 = reading3,
                        showThirdReading = showThird,
                        note = session.note.orEmpty(),
                        selectedSymptoms = session.symptoms.toSet(),
                        avgSystolic = derived.avgSystolic,
                        avgDiastolic = derived.avgDiastolic,
                        avgPulse = derived.avgPulse,
                        categoryLabel = derived.categoryLabel,
                        loading = false
                    )
                } else if (!hasInitFromData && session == null) {
                    _uiState.update { it.copy(loading = false, message = "未找到可编辑记录。") }
                }
            }
        }
    }

    fun updateMeasuredAtText(value: String) = _uiState.update { it.copy(measuredAtText = value) }
    fun updateScene(value: String) = _uiState.update { it.copy(scene = value) }
    fun toggleThirdReading(show: Boolean) = updateReading {
        it.copy(showThirdReading = show, reading3 = if (show) it.reading3 else SessionReadingInputUi())
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
    fun updateNote(value: String) = _uiState.update { it.copy(note = value) }

    fun toggleSymptom(symptom: String) {
        _uiState.update { state ->
            val set = state.selectedSymptoms.toMutableSet()
            if (!set.add(symptom)) set.remove(symptom)
            state.copy(selectedSymptoms = set)
        }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        val measuredAt = DateTimeInputFormatter.parse(state.measuredAtText)
        if (measuredAt == null) {
            _uiState.update { it.copy(message = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm") }
            return
        }
        val validate = SessionFormLogic.validateAndBuildReadings(
            reading1 = state.reading1,
            reading2 = state.reading2,
            reading3 = state.reading3,
            showThird = state.showThirdReading
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

        val abnormal = SessionFormLogic.buildAbnormalMessage(validate.readings)
        if (abnormal != null) {
            _uiState.update { it.copy(showAbnormalConfirmDialog = true, abnormalConfirmMessage = abnormal) }
            return
        }
        if (SessionFormLogic.containsHighRisk(validate.readings)) {
            _uiState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePending()
    }

    fun confirmAbnormalAndContinue() {
        _uiState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
        val pending = pendingSaveInput ?: return
        if (SessionFormLogic.containsHighRisk(pending.readings)) {
            _uiState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        savePending()
    }

    fun dismissAbnormalDialog() {
        pendingSaveInput = null
        _uiState.update { it.copy(showAbnormalConfirmDialog = false, abnormalConfirmMessage = "") }
    }

    fun confirmHighRiskAndSave() {
        _uiState.update { it.copy(showHighRiskDialog = false) }
        savePending()
    }

    fun dismissHighRiskDialog() {
        pendingSaveInput = null
        _uiState.update { it.copy(showHighRiskDialog = false) }
    }

    private fun savePending() {
        val input = pendingSaveInput ?: return
        viewModelScope.launch {
            repository.updateSession(sessionId, input)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            saved = true,
                            message = "编辑已保存。",
                            showAbnormalConfirmDialog = false,
                            showHighRiskDialog = false
                        )
                    }
                    pendingSaveInput = null
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            message = "保存失败：${throwable.message ?: "请稍后重试"}",
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
                reading1 = next.reading1,
                reading2 = next.reading2,
                reading3 = next.reading3,
                showThird = next.showThirdReading
            )
            next.copy(
                avgSystolic = derived.avgSystolic,
                avgDiastolic = derived.avgDiastolic,
                avgPulse = derived.avgPulse,
                categoryLabel = derived.categoryLabel
            )
        }
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
            }
        }
    }
}
