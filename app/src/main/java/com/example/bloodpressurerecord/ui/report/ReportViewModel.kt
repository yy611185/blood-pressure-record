package com.example.bloodpressurerecord.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.domain.report.ReportBuilder
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import com.example.bloodpressurerecord.domain.report.ReportPolicy
import com.example.bloodpressurerecord.domain.report.ReportTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportUiState(
    val period: ReportPeriod,
    val periodPresetDays: Int? = 30,
    val options: ReportOptions = ReportOptions(),
    val showIdentityConfirmation: Boolean = false,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val artifact: ReportArtifact? = null,
    val previewOptions: ReportOptions? = null,
    val message: String = ""
) {
    val hasCurrentPreview: Boolean get() = artifact != null && previewOptions == options &&
        artifact.plan.report.period == period
    val canExport: Boolean get() = hasCurrentPreview && !isGenerating && !isSaving
}

class ReportViewModel(
    private val repository: ReportRepository,
    private val generator: ReportGenerator,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ReportUiState(period = ReportPeriod.recent(30, nowMillis(), zoneId))
    )
    val uiState = _uiState.asStateFlow()
    private var generationJob: Job? = null
    private var generationId = 0L

    init {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                // 清理失败不阻止记录或报告流程，也不暴露文件名及异常原文。
                runCatching { generator.cleanup() }
            }
        }
    }

    fun requestPreview() {
        if (_uiState.value.isGenerating || _uiState.value.isSaving) return
        _uiState.update {
            it.copy(showIdentityConfirmation = true, options = it.options.copy(includeIdentity = false), message = "")
        }
    }

    fun setIncludeIdentity(include: Boolean) {
        changeOptions(_uiState.value.options.copy(includeIdentity = include))
    }

    fun setIncludeNotes(include: Boolean) = changeOptions(_uiState.value.options.copy(includeNotes = include))
    fun setIncludeMedications(include: Boolean) = changeOptions(_uiState.value.options.copy(includeMedications = include))

    private fun changeOptions(options: ReportOptions) {
        val state = _uiState.value
        if (state.isSaving || state.options == options) return
        _uiState.update { it.copy(options = options, message = "") }
        if (state.artifact != null) beginGeneration()
    }

    fun selectRecentPeriod(days: Int) {
        if (days !in listOf(7, 30, 90) || _uiState.value.isGenerating || _uiState.value.isSaving) return
        if (_uiState.value.options.template == ReportTemplate.HOME_SEVEN_DAY && days != 7) return
        selectPeriod(ReportPeriod.recent(days, nowMillis(), zoneId), days)
    }

    fun selectTemplate(template: ReportTemplate) {
        val state = _uiState.value
        if (state.isGenerating || state.isSaving || state.options.template == template) return
        state.artifact?.let(::discardSafely)
        val period = if (template == ReportTemplate.HOME_SEVEN_DAY) {
            ReportPeriod(state.period.endDate.minusDays(6), state.period.endDate, zoneId)
        } else state.period
        _uiState.update { it.copy(options = it.options.copy(template = template, includeIdentity = false),
            period = period, periodPresetDays = if (template == ReportTemplate.HOME_SEVEN_DAY && it.periodPresetDays != null) 7 else it.periodPresetDays,
            artifact = null, previewOptions = null, showIdentityConfirmation = false, message = "") }
    }

    fun selectCustomPeriod(start: LocalDate, end: LocalDate): Boolean {
        if (_uiState.value.isGenerating || _uiState.value.isSaving) return false
        val today = Instant.ofEpochMilli(nowMillis()).atZone(zoneId).toLocalDate()
        val period = runCatching { ReportPeriod(start, end, zoneId) }.getOrNull()
        if (_uiState.value.options.template == ReportTemplate.HOME_SEVEN_DAY && period?.dayCount != 7) {
            showMessage("7 日监测的起止日期须恰好包含 7 个自然日。")
            return false
        }
        if (period == null || end.isAfter(today)) {
            showMessage("请选择不晚于今天的有效日期范围，最多 ${ReportPolicy.MAX_PERIOD_DAYS} 天。")
            return false
        }
        selectPeriod(period, null)
        return true
    }

    private fun selectPeriod(period: ReportPeriod, presetDays: Int?) {
        _uiState.value.artifact?.let(::discardSafely)
        _uiState.update { it.copy(period = period, periodPresetDays = presetDays, artifact = null, previewOptions = null, message = "") }
    }

    fun dismissIdentityConfirmation() {
        _uiState.update { it.copy(showIdentityConfirmation = false) }
    }

    fun confirmAndGenerate() {
        if (_uiState.value.isGenerating || _uiState.value.isSaving) return
        beginGeneration()
    }

    fun regeneratePreview() {
        if (_uiState.value.artifact == null || _uiState.value.isSaving) return
        beginGeneration()
    }

    private fun beginGeneration() {
        val selected = _uiState.value
        val id = ++generationId
        generationJob?.cancel()
        _uiState.update { it.copy(showIdentityConfirmation = false, isGenerating = true,
            previewOptions = null, message = "") }
        generationJob = viewModelScope.launch {
            var pending: ReportArtifact? = null
            try {
                val snapshot = repository.loadSnapshot(selected.period, selected.options)
                val report = withContext(calculationDispatcher) {
                    ReportBuilder.build(snapshot, selected.period, selected.options, nowMillis())
                }
                if (report == null) {
                    if (id == generationId) _uiState.update {
                        it.copy(isGenerating = false, message = ReportPolicy.NO_DATA)
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
                _uiState.update { it.copy(isGenerating = false, artifact = pending, previewOptions = selected.options) }
                pending = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (id == generationId) {
                    _uiState.update { it.copy(isGenerating = false, message = "报告生成失败，请重试。") }
                }
            } finally {
                pending?.let(::discardSafely)
            }
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
        _uiState.update { it.copy(isSaving = true, message = "") }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { action(artifact) }
                _uiState.update { it.copy(message = "报告已保存。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "保存失败，请重新选择位置后再试。") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun returnToOptions() {
        if (_uiState.value.isSaving) return
        ++generationId
        generationJob?.cancel()
        _uiState.value.artifact?.let(::discardSafely)
        _uiState.update { it.copy(options = ReportOptions(template = it.options.template), artifact = null, previewOptions = null,
            showIdentityConfirmation = false, isGenerating = false, message = "") }
    }

    private fun discardSafely(artifact: ReportArtifact) {
        runCatching { generator.discard(artifact) }
    }

    override fun onCleared() {
        ++generationId
        generationJob?.cancel()
        _uiState.value.artifact?.let(::discardSafely)
        super.onCleared()
    }
}
