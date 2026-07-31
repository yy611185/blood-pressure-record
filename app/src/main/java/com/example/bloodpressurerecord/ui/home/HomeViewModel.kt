package com.example.bloodpressurerecord.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionDraftStore
import com.example.bloodpressurerecord.ui.common.SessionFormDraft
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.model.AverageStrategy
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
    val scene: String = MeasurementTags.defaultSceneFor(java.time.LocalTime.now().hour),
    val reading1: SessionReadingInputUi = SessionReadingInputUi(),
    val reading2: SessionReadingInputUi = SessionReadingInputUi(),
    val extraReadings: List<SessionReadingInputUi> = emptyList(),
    val showExtraReadings: Boolean = false,
    val note: String = "",
    val selectedSymptoms: Set<String> = emptySet(),
    val selectedFactors: Set<String> = emptySet(),
    val avgSystolic: Int? = null,
    val avgDiastolic: Int? = null,
    val avgPulse: Int? = null,
    val categoryLabel: String = "待计算",
    val formMessage: String = "",
    val formMessageIsError: Boolean = false,
    val saved: Boolean = false,
    val showHighRiskDialog: Boolean = false,
    val showAbnormalConfirmDialog: Boolean = false,
    val abnormalConfirmMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val saveDisabledReason: String = "把两组的高压和低压都填好，就可以保存啦",
    val isDirty: Boolean = false
)

class HomeViewModel(
    private val repository: BloodPressureRepository,
    highRiskAlertEnabled: Flow<Boolean> = flowOf(true),
    discardFirstReading: Flow<Boolean> = flowOf(false),
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    // 注意：discardFirstEnabled 必须先于 localState 初始化，
    // 因为恢复草稿时 recomputeDerived 会读取当前平均策略。
    private val highRiskAlertsEnabled = highRiskAlertEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )
    private val discardFirstEnabled = discardFirstReading.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    private val draftStore = SessionDraftStore(savedStateHandle, "add_session")
    private val restoredDraft = draftStore.restore()
    private val localState = MutableStateFlow(
        restoredDraft?.toHomeUiState() ?: HomeUiState()
    )
    private var pendingSaveInput: SaveSessionInput? = null
    private var pendingSaveContainsHighRisk: Boolean = false

    val uiState: StateFlow<HomeUiState> = localState.asStateFlow()

    init {
        // 策略切换时同步刷新预览的平均值和分级。
        viewModelScope.launch {
            discardFirstEnabled.collect {
                localState.update { state -> recomputeDerived(state) }
            }
        }
    }

    private fun averageStrategy(): AverageStrategy =
        if (discardFirstEnabled.value) AverageStrategy.DISCARD_FIRST else AverageStrategy.ALL

    // 用户手动选过场景后，改测量时间不再自动跟随时段。
    private var sceneManuallyChosen: Boolean = restoredDraft != null

    fun updateMeasuredAtText(value: String) = updateForm { state ->
        val next = state.copy(measuredAtText = value)
        if (sceneManuallyChosen) {
            next
        } else {
            val hour = DateTimeInputFormatter.parse(value)
                ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).hour }
            if (hour != null) next.copy(scene = MeasurementTags.defaultSceneFor(hour)) else next
        }
    }

    fun updateScene(value: String) {
        sceneManuallyChosen = true
        updateForm { it.copy(scene = value) }
    }

    fun toggleFactor(factor: String) {
        localState.update { state ->
            val next = state.selectedFactors.toMutableSet()
            if (!next.add(factor)) next.remove(factor)
            state.copy(selectedFactors = next, isDirty = true)
        }
        persistDraft()
    }

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
                formMessage = "每次测量最多 ${SessionFormLogic.UI_MAX_READING_COUNT} 组读数。",
                formMessageIsError = true
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
            localState.update {
                it.copy(
                    formMessage = "测量时间格式不正确，请使用 yyyy-MM-dd HH:mm",
                    formMessageIsError = true
                )
            }
            return
        }
        val validate = SessionFormLogic.validateAndBuildReadings(
            readings = allReadings(state),
            requiredCount = 2,
            strategy = averageStrategy()
        )
        if (validate.error != null) {
            localState.update { it.copy(formMessage = validate.error, formMessageIsError = true) }
            return
        }
        val input = SaveSessionInput(
            measuredAt = measuredAt,
            scene = state.scene,
            note = state.note,
            // 症状与影响因素合并存入同一标签列表，展示时按已知因素表拆分。
            symptoms = (state.selectedSymptoms + state.selectedFactors).toList(),
            readings = validate.readings,
            averageStrategy = averageStrategy()
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
                formMessage = "正在保存...",
                formMessageIsError = false
            )
        }
        viewModelScope.launch {
            repository.saveSession(input)
                .onSuccess {
                    // 用 saved 标志驱动导航，避免界面依赖文案字符串做控制流。
                    // 表单重置为当前时间，场景恢复按时段自动预选。
                    sceneManuallyChosen = false
                    localState.value = HomeUiState(
                        measuredAtText = DateTimeInputFormatter.nowText(),
                        scene = MeasurementTags.defaultSceneFor(java.time.LocalTime.now().hour),
                        formMessage = "保存成功。",
                        saved = true
                    )
                    draftStore.clear()
                    pendingSaveInput = null
                    pendingSaveContainsHighRisk = false
                }
                .onFailure { throwable ->
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
                symptoms = state.selectedSymptoms + state.selectedFactors
            )
        )
    }

    private fun recomputeDerived(state: HomeUiState): HomeUiState {
        val derived = SessionFormLogic.recomputeDerived(
            readings = allReadings(state),
            requiredCount = 2,
            strategy = averageStrategy()
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
        val (symptomTags, factorTags) = MeasurementTags.splitSymptomsAndFactors(symptoms)
        return recomputeDerived(
            HomeUiState(
                measuredAtText = measuredAtText,
                scene = scene,
                reading1 = first,
                reading2 = second,
                extraReadings = extras,
                showExtraReadings = extras.isNotEmpty(),
                note = note,
                selectedSymptoms = symptomTags,
                selectedFactors = factorTags,
                formMessage = "已恢复未保存的测量草稿。",
                isDirty = true
            )
        )
    }
}
