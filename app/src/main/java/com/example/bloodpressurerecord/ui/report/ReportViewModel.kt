package com.example.bloodpressurerecord.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.ReportRepository
import com.example.bloodpressurerecord.domain.report.ReportBuilder
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
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
    val options: ReportOptions = ReportOptions(),
    val showIdentityConfirmation: Boolean = false,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val artifact: ReportArtifact? = null,
    val message: String = ""
)

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
        if (!_uiState.value.isGenerating) {
            _uiState.update { it.copy(options = it.options.copy(includeIdentity = include)) }
        }
    }

    fun dismissIdentityConfirmation() {
        _uiState.update { it.copy(showIdentityConfirmation = false) }
    }

    fun confirmAndGenerate() {
        if (_uiState.value.isGenerating || _uiState.value.isSaving) return
        val selected = _uiState.value
        val id = ++generationId
        generationJob?.cancel()
        _uiState.update { it.copy(showIdentityConfirmation = false, isGenerating = true, message = "") }
        generationJob = viewModelScope.launch {
            var pending: ReportArtifact? = null
            try {
                val snapshot = repository.loadSnapshot(selected.period, selected.options)
                val report = withContext(calculationDispatcher) {
                    ReportBuilder.build(snapshot, selected.period, selected.options, nowMillis())
                }
                if (report == null) {
                    _uiState.update { it.copy(isGenerating = false, message = "所选周期没有测量记录，请先记录血压。") }
                    return@launch
                }
                pending = generator.generate(report)
                if (id != generationId) {
                    generator.discard(pending)
                    pending = null
                    return@launch
                }
                _uiState.value.artifact?.let(generator::discard)
                _uiState.update { it.copy(isGenerating = false, artifact = pending) }
                pending = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (id == generationId) {
                    _uiState.update { it.copy(isGenerating = false, message = "报告生成失败，请重试。") }
                }
            } finally {
                pending?.let(generator::discard)
            }
        }
    }

    /** 外部平台输出只使用已预览的同一文件。取消 SAF 不调用本方法。 */
    fun save(action: suspend (ReportArtifact) -> Unit) {
        val state = _uiState.value
        val artifact = state.artifact ?: return
        if (state.isSaving || state.isGenerating) return
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
        _uiState.value.artifact?.let(generator::discard)
        _uiState.update { it.copy(options = ReportOptions(), artifact = null, isGenerating = false, message = "") }
    }

    override fun onCleared() {
        ++generationId
        generationJob?.cancel()
        _uiState.value.artifact?.let(generator::discard)
        super.onCleared()
    }
}
