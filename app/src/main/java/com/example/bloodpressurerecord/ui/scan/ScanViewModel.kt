package com.example.bloodpressurerecord.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.scan.PhotoSaver
import com.example.bloodpressurerecord.data.scan.ScanPhoto
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.ocr.BpReadingParser
import com.example.bloodpressurerecord.domain.ocr.ReadingFlag
import com.example.bloodpressurerecord.domain.ocr.OcrResult
import com.example.bloodpressurerecord.mlkit.OcrEngine
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.SessionFormLogic
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanPhase { Camera, Processing, Review, Saving, Saved }

enum class ScanField { SYSTOLIC, DIASTOLIC, PULSE }

data class ScanGroup(
    /** 1-based，与确认页“第 N 组”一致。 */
    val groupNumber: Int,
    val thumbnail: Bitmap? = null,
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    val flags: Set<ReadingFlag> = emptySet()
) {
    val isSuspicious: Boolean get() = flags.isNotEmpty()
    val isRecognized: Boolean get() = systolic.isNotBlank() && diastolic.isNotBlank()
}

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Camera,
    val groups: List<ScanGroup> = emptyList(),
    val currentGroupNumber: Int = 1,
    val retakeGroupNumber: Int? = null,
    val message: String = "",
    val messageIsError: Boolean = false,
    val measuredAtText: String = "",
    val scene: String = "",
    val canSave: Boolean = false,
    val saveDisabledReason: String = "",
    val isSaving: Boolean = false,
    val isPhotoSavingEnabled: Boolean = false,
    val showHighRiskDialog: Boolean = false,
    val showAbnormalConfirmDialog: Boolean = false,
    val abnormalConfirmMessage: String = "",
    val saved: Boolean = false
)

/**
 * 拍照识别流程状态机：Idle → 拍照 → Processing → 组结果暂存 → Review → Saving → Saved。
 * 识别失败不占用组位；最多 3 组；保存必须经用户确认（minReadings = 1）。
 */
class ScanViewModel(
    private val repository: BloodPressureRepository,
    private val ocrEngine: OcrEngine,
    private val photoSaver: PhotoSaver,
    saveScanPhotosEnabled: Flow<Boolean> = flowOf(false),
    discardFirstReading: Flow<Boolean> = flowOf(false),
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScanUiState(
            measuredAtText = DateTimeInputFormatter.nowText(),
            scene = MeasurementTags.defaultSceneFor(LocalTime.now().hour)
        )
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val savePhotosEnabled = saveScanPhotosEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    private val discardFirstEnabled = discardFirstReading.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    private var pendingSaveInput: SaveSessionInput? = null

    init {
        viewModelScope.launch {
            savePhotosEnabled.collect { enabled ->
                _uiState.update { it.copy(isPhotoSavingEnabled = enabled) }
            }
        }
    }

    override fun onCleared() {
        // 退出拍照流程（返回/放弃）时释放所有已拍组缩略图。
        _uiState.value.groups.forEach { group ->
            group.thumbnail?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        super.onCleared()
    }

    fun updateMeasuredAtText(value: String) {
        _uiState.update { it.copy(measuredAtText = value) }
    }

    fun updateScene(value: String) {
        _uiState.update { it.copy(scene = value) }
    }

    fun updateGroup(groupNumber: Int, field: ScanField, value: String) {
        _uiState.update { state ->
            val groups = state.groups.map { group ->
                if (group.groupNumber != groupNumber) {
                    group
                } else {
                    when (field) {
                        ScanField.SYSTOLIC -> group.copy(systolic = value)
                        ScanField.DIASTOLIC -> group.copy(diastolic = value)
                        ScanField.PULSE -> group.copy(pulse = value)
                    }
                }
            }
            recomputeCanSave(state.copy(groups = groups))
        }
    }

    fun retakeGroup(groupNumber: Int) {
        _uiState.update {
            it.copy(
                phase = ScanPhase.Camera,
                retakeGroupNumber = groupNumber,
                message = "请重拍第 $groupNumber 组",
                messageIsError = false
            )
        }
    }

    fun continueCapturing() {
        val state = _uiState.value
        val next = state.groups.size + 1
        if (next <= 3) {
            _uiState.update {
                it.copy(
                    phase = ScanPhase.Camera,
                    currentGroupNumber = next,
                    retakeGroupNumber = null,
                    message = "",
                    messageIsError = false
                )
            }
        } else {
            enterReview()
        }
    }

    fun enterReview() {
        _uiState.update {
            recomputeCanSave(
                it.copy(phase = ScanPhase.Review, message = "", messageIsError = false)
            )
        }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        if (_uiState.value.phase == ScanPhase.Processing || _uiState.value.phase == ScanPhase.Saving) {
            return
        }
        val targetGroup = _uiState.value.retakeGroupNumber ?: (_uiState.value.groups.size + 1)
        _uiState.update {
            it.copy(phase = ScanPhase.Processing, message = "正在识别...", messageIsError = false)
        }
        viewModelScope.launch {
            val normalized = bitmap.normalize()
            if (normalized !== bitmap) bitmap.recycle()
            val result = ocrEngine.recognize(normalized)
            // “保存识别照片”开启时才保留全尺寸图供落盘；默认只留 480px 缩略图，
            // 避免每组 ~10MB 的全尺寸 Bitmap 长期占用内存。
            val keepFull = savePhotosEnabled.value
            val thumbnail = if (keepFull) {
                normalized
            } else {
                val thumb = normalized.thumbnail()
                if (thumb !== normalized) normalized.recycle()
                thumb
            }
            onOcrReady(targetGroup, result, thumbnail)
        }
    }

    /** OCR 结果就绪：解析并更新组数据（JVM 单测直接注入 OcrResult）。 */
    internal fun onOcrReady(
        targetGroup: Int,
        result: OcrResult,
        thumbnail: Bitmap? = null
    ) {
        val candidate = BpReadingParser.parse(result)
        if (candidate == null && thumbnail != null && !thumbnail.isRecycled) {
            thumbnail.recycle()
        }
        _uiState.update { state ->
            if (candidate == null) {
                state.copy(
                    phase = ScanPhase.Camera,
                    message = if (BpReadingParser.looksLikeStandby(result)) {
                        "请拍摄测量完成后的屏幕，当前画面像是待机界面。"
                    } else {
                        "未识别到血压读数，请重拍或改为手动输入。"
                    },
                    messageIsError = true
                )
            } else {
                val group = ScanGroup(
                    groupNumber = targetGroup,
                    thumbnail = thumbnail,
                    systolic = candidate.systolic.toString(),
                    diastolic = candidate.diastolic.toString(),
                    pulse = candidate.pulse?.toString().orEmpty(),
                    flags = candidate.flags
                )
                val groups = if (state.groups.any { it.groupNumber == targetGroup }) {
                    state.groups.map { existing ->
                        if (existing.groupNumber == targetGroup) {
                            // 重拍替换：释放被替换组的旧缩略图，避免内存泄漏。
                            existing.thumbnail?.let { old ->
                                if (!old.isRecycled && old !== group.thumbnail) old.recycle()
                            }
                            group
                        } else {
                            existing
                        }
                    }
                } else {
                    state.groups + group
                }
                val pulseText = candidate.pulse?.let { "，脉搏 $it" }.orEmpty()
                state.copy(
                    groups = groups,
                    phase = ScanPhase.Camera,
                    message = "已识别：${candidate.systolic}/${candidate.diastolic}$pulseText。",
                    messageIsError = false
                )
            }
        }
    }

    fun onCaptureError(message: String) {
        _uiState.update {
            it.copy(phase = ScanPhase.Camera, message = "拍照失败：$message", messageIsError = true)
        }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        val validation = SessionFormLogic.validateAndBuildReadings(
            readings = state.groups.map { SessionReadingInputUi(it.systolic, it.diastolic, it.pulse) },
            requiredCount = 1,
            strategy = averageStrategy()
        )
        if (validation.error != null) {
            _uiState.update { it.copy(message = validation.error, messageIsError = true) }
            return
        }
        val input = SaveSessionInput(
            measuredAt = DateTimeInputFormatter.parse(state.measuredAtText) ?: nowMillis(),
            scene = state.scene,
            note = null,
            symptoms = emptyList(),
            readings = validation.readings,
            averageStrategy = averageStrategy(),
            minReadings = 1
        )
        if (validation.containsHighRiskReading) {
            pendingSaveInput = input
            _uiState.update { it.copy(showHighRiskDialog = true) }
            return
        }
        val abnormalMessage = SessionFormLogic.buildAbnormalMessage(validation.readings)
        if (abnormalMessage != null) {
            pendingSaveInput = input
            _uiState.update {
                it.copy(showAbnormalConfirmDialog = true, abnormalConfirmMessage = abnormalMessage)
            }
            return
        }
        save(input)
    }

    fun confirmHighRiskAndContinue() {
        val input = pendingSaveInput
        pendingSaveInput = null
        _uiState.update { it.copy(showHighRiskDialog = false) }
        if (input != null) save(input)
    }

    fun dismissHighRiskDialog() {
        pendingSaveInput = null
        _uiState.update { it.copy(showHighRiskDialog = false) }
    }

    fun confirmAbnormalAndContinue() {
        val input = pendingSaveInput
        pendingSaveInput = null
        _uiState.update { it.copy(showAbnormalConfirmDialog = false) }
        if (input != null) save(input)
    }

    fun dismissAbnormalDialog() {
        pendingSaveInput = null
        _uiState.update { it.copy(showAbnormalConfirmDialog = false) }
    }

    fun discard() {
        pendingSaveInput = null
    }

    private fun save(input: SaveSessionInput) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                isSaving = true,
                message = "正在保存...",
                messageIsError = false,
                showHighRiskDialog = false,
                showAbnormalConfirmDialog = false
            )
        }
        viewModelScope.launch {
            repository.saveSession(input)
                .onSuccess { sessionId ->
                    val state = _uiState.value
                    val photoWarning = if (state.isPhotoSavingEnabled) {
                        val photos = state.groups.mapNotNull { group ->
                            group.thumbnail?.let { bitmap -> ScanPhoto(group.groupNumber, bitmap) }
                        }
                        runCatching { photoSaver.save(sessionId, photos) }.exceptionOrNull()?.message
                    } else {
                        null
                    }
                    _uiState.value = ScanUiState(
                        phase = ScanPhase.Saved,
                        measuredAtText = DateTimeInputFormatter.nowText(),
                        scene = MeasurementTags.defaultSceneFor(LocalTime.now().hour),
                        message = if (photoWarning == null) "保存成功。" else "保存成功，但识别照片保存失败。",
                        saved = true
                    )
                    state.groups.forEach { group ->
                        group.thumbnail?.let { bitmap ->
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = "保存失败：${throwable.message ?: "请稍后重试"}",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    private fun averageStrategy(): AverageStrategy =
        if (discardFirstEnabled.value) AverageStrategy.DISCARD_FIRST else AverageStrategy.ALL

    private fun recomputeCanSave(state: ScanUiState): ScanUiState {
        val readings = state.groups.map { SessionReadingInputUi(it.systolic, it.diastolic, it.pulse) }
        val reason = SessionFormLogic.saveDisabledReason(readings, requiredCount = 1)
        return state.copy(canSave = reason == null, saveDisabledReason = reason.orEmpty())
    }
}
