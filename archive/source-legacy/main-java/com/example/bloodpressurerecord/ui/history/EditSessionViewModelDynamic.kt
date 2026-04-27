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
        val nextExtras = if (show) {
            if (it.extraReadings.isEmpty()) listOf(SessionReadingInputUi()) else it.extraReadings
        } else {
            emptyList()
        }
        it.copy(showExtraReadings = show, extraReadings = nextExtras)
    }
    fun addNextReadingGroup() = updateReading {
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
                readings = allReadings(next),
                requiredCount = 2
            )
            next.copy(
                avgSystolic = derived.avgSystolic,
                avgDiastolic = derived.avgDiastolic,
                avgPulse = derived.avgPulse,
                categoryLabel = derived.categoryLabel
            )
        }
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

