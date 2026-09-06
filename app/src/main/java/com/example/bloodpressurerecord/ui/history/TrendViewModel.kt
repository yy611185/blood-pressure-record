package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val dayDetails: TrendDayDetails? = null,
    val summary: TrendTextSummary = TrendTextSummary()
)

data class TrendTextSummary(
    val recordCount: Int = 0,
    val averageSystolic: Int? = null,
    val averageDiastolic: Int? = null,
    val highestSystolic: Int? = null,
    val highestDiastolic: Int? = null,
    val lowestSystolic: Int? = null,
    val lowestDiastolic: Int? = null,
    val systolicChange: Int? = null,
    val diastolicChange: Int? = null,
    val highRiskCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrendViewModel(
    private val trendRepository: TrendRepository,
    settingsRepository: SettingsRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    computeContext: CoroutineContext = Dispatchers.Default,
    todayTicks: Flow<LocalDate> = flow {
        emit(Instant.ofEpochMilli(clockMillis()).atZone(zoneId).toLocalDate())
    }
) : ViewModel() {
    private val selectedRange = MutableStateFlow(TrendRange.DAYS_30)
    private val selectedMetric = MutableStateFlow(TrendMetricType.BOTH)
    private val details = MutableStateFlow<TrendDayDetails?>(null)

    // 查询边界随“今天”滚动：上界取当天结束（半开区间），
    // 打开页面后新增的记录能实时进入折线，跨零点后 7/30 天窗口自动前移。
    private val seriesState = combine(
        selectedRange,
        todayTicks
    ) { range, today -> range to today }.flatMapLatest { (range, today) ->
        val anchorMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val start = TrendSeriesCalculator.rangeStart(range, anchorMillis, zoneId)
        val endExclusive = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val previousStart = previousRangeStart(range, start)
        combine(
            trendRepository.observeRecords(start, endExclusive),
            trendRepository.observeStatistics(start, endExclusive),
            if (previousStart == null) {
                kotlinx.coroutines.flow.flowOf(PeriodStatistics())
            } else {
                trendRepository.observeStatistics(previousStart, start)
            },
            settingsRepository.observeSettings()
        ) { records, statistics, previousStatistics, settings ->
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
                statistics = statistics,
                previousStatistics = previousStatistics,
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic
            )
        }.flowOn(computeContext)
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
            dayDetails = dayDetails,
            summary = buildSummary(seriesState.statistics, seriesState.previousStatistics)
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
                    if (current?.point?.id != point.id) current else current.copy(
                        records = records, loading = false, error = null
                    )
                }
            }.onFailure { throwable ->
                details.update { current ->
                    if (current?.point?.id != point.id) current else current.copy(
                        loading = false, error = throwable.message ?: "无法读取当天记录"
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
        val statistics: PeriodStatistics,
        val previousStatistics: PeriodStatistics,
        val targetSystolic: Int?,
        val targetDiastolic: Int?
    )

    private fun previousRangeStart(range: TrendRange, currentStart: Long): Long? {
        val days = when (range) {
            TrendRange.DAYS_7 -> 7L
            TrendRange.DAYS_30 -> 30L
            TrendRange.ALL -> return null
        }
        return Instant.ofEpochMilli(currentStart).atZone(zoneId)
            .toLocalDate().minusDays(days).atStartOfDay(zoneId)
            .toInstant().toEpochMilli()
    }

    private fun buildSummary(
        statistics: PeriodStatistics,
        previousStatistics: PeriodStatistics
    ): TrendTextSummary {
        if (statistics.recordCount == 0) return TrendTextSummary()
        val avgSys = statistics.averageSystolic?.roundToInt()
        val avgDia = statistics.averageDiastolic?.roundToInt()
        val systolicChange = statistics.averageSystolic?.let { current ->
            previousStatistics.averageSystolic?.let { previous -> (current - previous).roundToInt() }
        }
        val diastolicChange = statistics.averageDiastolic?.let { current ->
            previousStatistics.averageDiastolic?.let { previous -> (current - previous).roundToInt() }
        }
        return TrendTextSummary(
            recordCount = statistics.recordCount,
            averageSystolic = avgSys,
            averageDiastolic = avgDia,
            highestSystolic = statistics.highestSystolic,
            highestDiastolic = statistics.highestDiastolic,
            lowestSystolic = statistics.lowestSystolic,
            lowestDiastolic = statistics.lowestDiastolic,
            systolicChange = systolicChange,
            diastolicChange = diastolicChange,
            highRiskCount = statistics.highRiskCount
        )
    }
}
