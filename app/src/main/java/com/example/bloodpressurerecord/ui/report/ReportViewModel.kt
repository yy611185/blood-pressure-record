package com.example.bloodpressurerecord.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.domain.report.ReportBuilder
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import com.example.bloodpressurerecord.domain.report.ReportPolicy
import com.example.bloodpressurerecord.domain.report.ReportTemplate
import com.example.bloodpressurerecord.update.UpdatePromptGate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportOutputKind { PDF, IMAGE }

data class ReportUiState(
    val period: ReportPeriod,
    val periodPresetDays: Int? = 30,
    val today: LocalDate = period.endDate,
    val hasProfileIdentity: Boolean = true,
    val options: ReportOptions = ReportOptions(),
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val artifact: ReportArtifact? = null,
    val previewOptions: ReportOptions? = null,
    val message: String = "",
    val selectingOutput: ReportOutputKind? = null
) {
    val hasCurrentPreview: Boolean get() = artifact != null && previewOptions == options &&
        artifact.plan.report.period == period
    val canExport: Boolean get() = hasCurrentPreview && !isGenerating && !isSaving && selectingOutput == null
}

class ReportViewModel(
    private val repository: ReportRepository,
    private val generator: ReportGenerator,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneProvider: () -> ZoneId = { zoneId },
    todayTicks: Flow<LocalDate> = emptyFlow(),
    timeChangeSignal: Flow<Unit> = emptyFlow(),
    private val updatePromptGate: UpdatePromptGate? = null
) : ViewModel() {
    private fun currentZone(): ZoneId = zoneProvider()
    private fun currentToday(zone: ZoneId = currentZone()): LocalDate =
        Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()

    private val _uiState = MutableStateFlow(
        run {
            val zone = currentZone()
            val now = nowMillis()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            ReportUiState(
                period = ReportPeriod.recent(30, now, zone),
                today = today
            )
        }
    )
    val uiState = _uiState.asStateFlow()
    private var generationJob: Job? = null
    private var generationId = 0L
    private var activeSaveCount = 0
    private var clearedWhileSaving = false
    private var selectedOutputArtifact: ReportArtifact? = null
    private var releaseOutputSelection: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                // 清理失败不阻止记录或报告流程，也不暴露文件名及异常原文。
                runCatching { generator.cleanup() }
            }
        }
        refreshProfileIdentityAvailability()
        viewModelScope.launch {
            merge(todayTicks, timeChangeSignal).collect {
                syncDateAndZoneIfIdle()
            }
        }
    }

    /** 每次进入“导出医生报告”页面时重置身份隐私开关（默认关闭且不记忆）并刷新日期与资料状态。 */
    fun onScreenEntered() {
        refreshProfileIdentityAvailability()
        _uiState.update { state ->
            if (state.isGenerating || state.isSaving || state.artifact != null) {
                state
            } else {
                val zone = currentZone()
                val now = nowMillis()
                val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                state.copy(
                    today = today,
                    period = refreshPeriod(state, today, now, zone),
                    options = state.options.copy(includeIdentity = false),
                    message = ""
                )
            }
        }
    }

    private fun refreshProfileIdentityAvailability() {
        viewModelScope.launch {
            val hasIdentity = runCatching {
                withContext(ioDispatcher) { repository.hasProfileIdentity() }
            }.getOrDefault(true)
            _uiState.update { state ->
                val adjustedOptions = if (!hasIdentity && state.options.includeIdentity) {
                    state.options.copy(includeIdentity = false)
                } else {
                    state.options
                }
                state.copy(hasProfileIdentity = hasIdentity, options = adjustedOptions)
            }
        }
    }

    private fun syncDateAndZoneIfIdle() {
        _uiState.update { state ->
            val zone = currentZone()
            val now = nowMillis()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            if (state.isGenerating || state.isSaving || state.artifact != null) {
                state.copy(today = today)
            } else {
                state.copy(today = today, period = refreshPeriod(state, today, now, zone))
            }
        }
    }

    private fun refreshPeriod(state: ReportUiState, today: LocalDate, now: Long, zone: ZoneId): ReportPeriod {
        state.periodPresetDays?.let { return ReportPeriod.recent(it, now, zone) }
        val end = state.period.endDate.coerceAtMost(today)
        val start = if (state.options.template == ReportTemplate.HOME_SEVEN_DAY) {
            end.minusDays(6)
        } else {
            state.period.startDate.coerceAtMost(end)
        }
        return ReportPeriod(start, end, zone)
    }

    fun setIncludeIdentity(include: Boolean) {
        if (include && !_uiState.value.hasProfileIdentity) return
        changeOptions(_uiState.value.options.copy(includeIdentity = include))
    }

    fun setIncludeNotes(include: Boolean) = changeOptions(_uiState.value.options.copy(includeNotes = include))
    fun setIncludeMedications(include: Boolean) = changeOptions(_uiState.value.options.copy(includeMedications = include))

    private fun changeOptions(options: ReportOptions) {
        val state = _uiState.value
        if (state.isSaving || state.selectingOutput != null || state.options == options) return
        _uiState.update { it.copy(options = options, message = "") }
        if (state.artifact != null) beginGeneration()
    }

    fun selectRecentPeriod(days: Int) {
        if (days !in listOf(7, 30, 90) || _uiState.value.isGenerating || _uiState.value.isSaving) return
        if (_uiState.value.options.template == ReportTemplate.HOME_SEVEN_DAY && days != 7) return
        val zone = currentZone()
        val now = nowMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        selectPeriod(ReportPeriod.recent(days, now, zone), days, today)
    }

    fun selectTemplate(template: ReportTemplate) {
        val state = _uiState.value
        if (state.isGenerating || state.isSaving || state.options.template == template) return
        state.artifact?.let(::discardSafely)
        val zone = currentZone()
        val today = currentToday(zone)
        val endDate = state.period.endDate.coerceAtMost(today)
        val period = if (template == ReportTemplate.HOME_SEVEN_DAY) {
            ReportPeriod(endDate.minusDays(6), endDate, zone)
        } else {
            runCatching { ReportPeriod(state.period.startDate, endDate, zone) }
                .getOrElse { ReportPeriod.recent(state.periodPresetDays ?: 30, nowMillis(), zone) }
        }
        _uiState.update {
            it.copy(
                options = it.options.copy(template = template, includeIdentity = false),
                period = period,
                today = today,
                periodPresetDays = if (template == ReportTemplate.HOME_SEVEN_DAY && it.periodPresetDays != null) 7 else it.periodPresetDays,
                artifact = null,
                previewOptions = null,
                message = ""
            )
        }
    }

    fun selectCustomPeriod(start: LocalDate, end: LocalDate): Boolean {
        if (_uiState.value.isGenerating || _uiState.value.isSaving) return false
        val zone = currentZone()
        val today = currentToday(zone)
        val period = runCatching { ReportPeriod(start, end, zone) }.getOrNull()
        if (_uiState.value.options.template == ReportTemplate.HOME_SEVEN_DAY && period?.dayCount != 7) {
            showMessage("7 日监测的起止日期须恰好包含 7 个自然日。")
            return false
        }
        if (period == null || end.isAfter(today)) {
            showMessage("请选择不晚于今天的有效日期范围，最多 ${ReportPolicy.MAX_PERIOD_DAYS} 天。")
            return false
        }
        selectPeriod(period, null, today)
        return true
    }

    private fun selectPeriod(period: ReportPeriod, presetDays: Int?, today: LocalDate) {
        _uiState.value.artifact?.let(::discardSafely)
        _uiState.update {
            it.copy(
                period = period,
                periodPresetDays = presetDays,
                today = today,
                artifact = null,
                previewOptions = null,
                message = ""
            )
        }
    }

    fun confirmAndGenerate() {
        if (_uiState.value.isGenerating || _uiState.value.isSaving) return
        beginGeneration()
    }

    fun regeneratePreview() {
        val state = _uiState.value
        if (state.artifact == null || state.isSaving || state.selectingOutput != null) return
        beginGeneration()
    }

    private fun beginGeneration() {
        val selected = _uiState.value
        val id = ++generationId
        generationJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = true,
                previewOptions = null,
                message = ""
            )
        }
        generationJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val release = updatePromptGate?.hold()
            var pending: ReportArtifact? = null
            try {
                val snapshot = repository.loadSnapshot(selected.period, selected.options)
                val report = withContext(calculationDispatcher) {
                    ReportBuilder.build(snapshot, selected.period, selected.options, nowMillis())
                }
                if (report == null) {
                    if (id == generationId) {
                        _uiState.update {
                            it.copy(isGenerating = false, message = ReportPolicy.NO_DATA)
                        }
                    }
                    return@launch
                }
                pending = generator.generate(report)
                if (id != generationId) {
                    discardSafely(pending)
                    pending = null
                    return@launch
                }
                _uiState.value.artifact?.let(::discardSafely)
                _uiState.update {
                    it.copy(isGenerating = false, artifact = pending, previewOptions = selected.options)
                }
                pending = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (id == generationId) {
                    _uiState.update { it.copy(isGenerating = false, message = "报告生成失败，请重试。") }
                }
            } finally {
                pending?.let(::discardSafely)
                release?.invoke()
            }
        }
    }

    fun beginOutputSelection(kind: ReportOutputKind, artifact: ReportArtifact): Boolean {
        val state = _uiState.value
        if (!state.canExport || state.artifact !== artifact) return false
        selectedOutputArtifact = artifact
        releaseOutputSelection = updatePromptGate?.hold()
        _uiState.update { it.copy(selectingOutput = kind) }
        return true
    }

    fun finishOutputSelection(kind: ReportOutputKind, action: (suspend (ReportArtifact) -> Unit)?) {
        if (_uiState.value.selectingOutput != kind) return
        val expected = selectedOutputArtifact
        val release = releaseOutputSelection
        selectedOutputArtifact = null
        releaseOutputSelection = null
        _uiState.update { it.copy(selectingOutput = null) }
        try {
            when {
                action == null -> showMessage("保存已取消。")
                expected == null || !_uiState.value.canExport -> showMessage("报告已更改，请重新选择保存位置。")
                else -> save(expected, action)
            }
        } finally {
            release?.invoke()
        }
    }

    /** 外部平台输出只使用已预览的同一文件。取消 SAF 不调用本方法。 */
    fun save(expectedArtifact: ReportArtifact? = null, action: suspend (ReportArtifact) -> Unit) {
        val state = _uiState.value
        val artifact = state.artifact ?: return
        if (!state.canExport) return
        if (expectedArtifact != null && artifact !== expectedArtifact) {
            showMessage("报告已更改，请重新选择保存位置。")
            return
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            activeSaveCount += 1
            _uiState.update { it.copy(isSaving = true, message = "") }
            val release = updatePromptGate?.hold()
            try {
                withContext(ioDispatcher) { action(artifact) }
                if (!clearedWhileSaving) {
                    _uiState.update { it.copy(message = "报告已保存。") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!clearedWhileSaving) {
                    _uiState.update { it.copy(message = "保存失败，请重新选择位置后再试。") }
                }
            } finally {
                activeSaveCount = (activeSaveCount - 1).coerceAtLeast(0)
                if (!clearedWhileSaving) {
                    _uiState.update { it.copy(isSaving = false) }
                } else if (activeSaveCount == 0) {
                    _uiState.value.artifact?.let(::discardSafely)
                }
                release?.invoke()
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun returnToOptions() {
        if (_uiState.value.isSaving || _uiState.value.selectingOutput != null) return
        ++generationId
        generationJob?.cancel()
        _uiState.value.artifact?.let(::discardSafely)
        _uiState.update {
            it.copy(
                options = ReportOptions(template = it.options.template),
                artifact = null,
                previewOptions = null,
                isGenerating = false,
                message = ""
            )
        }
        syncDateAndZoneIfIdle()
    }

    private fun discardSafely(artifact: ReportArtifact) {
        runCatching { generator.discard(artifact) }
    }

    override fun onCleared() {
        releaseOutputSelection?.invoke()
        releaseOutputSelection = null
        selectedOutputArtifact = null
        ++generationId
        generationJob?.cancel()
        if (activeSaveCount > 0) {
            clearedWhileSaving = true
        } else {
            _uiState.value.artifact?.let(::discardSafely)
        }
        super.onCleared()
    }
}
