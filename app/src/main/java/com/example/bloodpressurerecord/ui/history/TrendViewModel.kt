package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendDayDetails(
    val point: TrendPoint,
    val records: List<TrendRecord> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

data class TrendUiState(
    val range: TrendRange = TrendRange.DAYS_30,
    val metric: TrendMetricType = TrendMetricType.BOTH,
    val series: TrendSeries = TrendSeries(
        range = TrendRange.DAYS_30,
        points = emptyList(),
        rawRecordCount = 0,
        averageSystolic = null,
        averageDiastolic = null,
        yAxis = TrendYAxis(70, 150, 10),
        rangeStart = 0L,
        rangeEnd = 1L
    ),
    val targetSystolic: Int? = null,
    val targetDiastolic: Int? = null,
    val dayDetails: TrendDayDetails? = null
)

class TrendViewModel(
    private val trendRepository: TrendRepository,
    settingsRepository: SettingsRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {
    private val selectedRange = MutableStateFlow(TrendRange.DAYS_30)
    private val selectedMetric = MutableStateFlow(TrendMetricType.BOTH)
    private val details = MutableStateFlow<TrendDayDetails?>(null)

    private val seriesState = selectedRange.flatMapLatest { range ->
        val queryStartedAt = clockMillis()
        val start = TrendSeriesCalculator.rangeStart(range, queryStartedAt, zoneId)
        combine(
            trendRepository.observeRecords(start, Long.MAX_VALUE),
            settingsRepository.observeSettings()
        ) { records, settings ->
            val now = clockMillis()
            val targetSystolic = settings.userProfile.targetSystolic
            val targetDiastolic = settings.userProfile.targetDiastolic
            TrendSeriesState(
                series = TrendSeriesCalculator.build(
                    records = records,
                    range = range,
                    nowMillis = now,
                    zoneId = zoneId,
                    targetSystolic = targetSystolic,
                    targetDiastolic = targetDiastolic
                ),
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic
            )
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<TrendUiState> = combine(
        selectedRange,
        selectedMetric,
        seriesState,
        details
    ) { range, metric, seriesState, dayDetails ->
        TrendUiState(
            range = range,
            metric = metric,
            series = seriesState.series,
            targetSystolic = seriesState.targetSystolic,
            targetDiastolic = seriesState.targetDiastolic,
            dayDetails = dayDetails
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrendUiState()
    )

    fun setRange(range: TrendRange) {
        selectedRange.value = range
        details.value = null
    }

    fun setMetric(metric: TrendMetricType) {
        selectedMetric.value = metric
    }

    fun openPointDetails(point: TrendPoint) {
        if (point.aggregation != TrendAggregation.DAILY) return
        details.value = TrendDayDetails(point = point)
        viewModelScope.launch {
            runCatching {
                trendRepository.getRecords(point.intervalStart, point.intervalEndExclusive)
            }.onSuccess { records ->
                details.update { current ->
                    current?.takeIf { it.point.id == point.id }?.copy(
                        records = records,
                        loading = false,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                details.update { current ->
                    current?.takeIf { it.point.id == point.id }?.copy(
                        loading = false,
                        error = throwable.message ?: "无法读取当天记录"
                    )
                }
            }
        }
    }

    fun dismissDayDetails() {
        details.value = null
    }

    private data class TrendSeriesState(
        val series: TrendSeries,
        val targetSystolic: Int?,
        val targetDiastolic: Int?
    )
}
