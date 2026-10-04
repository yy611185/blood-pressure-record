package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.SettingsRepository
import com.example.bloodpressurerecord.data.repository.TrendRepository
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.calculator.TrendAggregationCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendDayDetails(
    val point: TrendPoint,
    val records: List<TrendRecord> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val selectedRecordId: String? = null,
    val title: String = "测量记录"
)

data class TrendUiState(
    val range: TrendRange = TrendRange.DAYS_30,
    val metric: TrendMetricType = TrendMetricType.BOTH,
    val aggregation: TrendAggregation = TrendAggregation.RAW,
    val selectedPointId: String? = null,
    val showPulse: Boolean = true,
    val fullscreenShowPulse: Boolean = false,
    val showTimeLabels: Boolean = false,
    val fullscreen: Boolean = false,
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
    val insights: TrendInsights = TrendInsights()
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
    },
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val selectedRange = MutableStateFlow(
        restoredEnum("trend.range", TrendRange.DAYS_30)
    )
    private val restoredAggregation = restoredEnum("trend.aggregation", TrendAggregation.RAW)
    // 日范围入口已移除；旧日桶状态迁移回每次测量，同时保留绝对视窗。
    private val selectedAggregation = MutableStateFlow(TrendAggregation.RAW).also {
        savedStateHandle["trend.aggregation"] = TrendAggregation.RAW.name
    }
    private val displayOptions = MutableStateFlow(DisplayOptions(
        metric = restoredEnum("trend.metric", TrendMetricType.BOTH),
        selectedPointId = savedStateHandle["trend.selectedPointId"],
        showPulse = savedStateHandle["trend.showPulse"] ?: true,
        fullscreenShowPulse = savedStateHandle["trend.fullscreenShowPulse"] ?: false,
        showTimeLabels = savedStateHandle["trend.showTimeLabels"] ?: false,
        fullscreen = savedStateHandle["trend.fullscreen"] ?: false
    ))
    private val details = MutableStateFlow<TrendDayDetails?>(null)
    private var detailsJob: Job? = null
    private var detailsRequest = 0L
    private var selectionTimestamp: Long? = savedStateHandle["trend.selectedTimestamp"]
    private var pendingSelectionMapping: Pair<TrendAggregation, Long>? =
        (savedStateHandle.get<Long>("trend.selectionMappingTimestamp")
            ?: selectionTimestamp.takeIf { restoredAggregation != TrendAggregation.RAW })
            ?.let { TrendAggregation.RAW to it }
            .also { mapping ->
                // 聚合名迁移先写入；记录还未加载时重建也不能丢失待恢复的选择。
                if (mapping != null) savedStateHandle["trend.selectionMappingTimestamp"] = mapping.second
            }

    val chartController = TrendChartController(
        initialViewport = restoredViewport(),
        onViewportChanged = { start, end ->
            savedStateHandle["trend.viewportStart"] = start
            savedStateHandle["trend.viewportEnd"] = end
        }
    )

    // 查询边界随“今天”滚动：上界取当天结束（半开区间），
    // 打开页面后新增的记录能实时进入折线，跨零点后 7/30 天窗口自动前移。
    private val seriesState = combine(
        selectedRange,
        selectedAggregation,
        todayTicks
    ) { range, aggregation, today -> Triple(range, aggregation, today) }
        .flatMapLatest { (range, aggregation, today) ->
        val anchorMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val start = TrendSeriesCalculator.rangeStart(range, anchorMillis, zoneId)
        val endExclusive = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val previousStart = previousRangeStart(range, start)
        combine(
            trendRepository.observeRecords(start, endExclusive),
            if (previousStart == null) {
                kotlinx.coroutines.flow.flowOf(emptyList<TrendRecord>())
            } else {
                trendRepository.observeRecords(previousStart, start)
            },
            settingsRepository.observeSettings()
        ) { records, previousRecords, settings ->
            val now = clockMillis()
            val currentRecords = records.filter { it.measuredAt in start until endExclusive && it.measuredAt <= now }
            val targetSystolic = settings.userProfile.targetSystolic
            val targetDiastolic = settings.userProfile.targetDiastolic
            TrendSeriesState(
                series = TrendAggregationCalculator.build(
                    records = currentRecords,
                    range = range,
                    nowMillis = now,
                    zoneId = zoneId,
                    aggregation = aggregation,
                    targetSystolic = targetSystolic,
                    targetDiastolic = targetDiastolic
                ),
                insights = TrendInsightCalculator.calculate(
                    records = currentRecords,
                    previousRecords = previousRecords,
                    targetSystolic = targetSystolic,
                    targetDiastolic = targetDiastolic,
                    zoneId = zoneId
                ),
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic
            )
        }.flowOn(computeContext)
    }.onEach { seriesState ->
        reconcileSelection(seriesState.series)
    }

    val uiState: StateFlow<TrendUiState> = combine(
        selectedRange,
        selectedAggregation,
        displayOptions,
        seriesState,
        details
    ) { range, aggregation, options, seriesState, dayDetails ->
        TrendUiState(
            range = range,
            metric = options.metric,
            aggregation = aggregation,
            selectedPointId = options.selectedPointId,
            showPulse = options.showPulse,
            fullscreenShowPulse = options.fullscreenShowPulse,
            showTimeLabels = options.showTimeLabels,
            fullscreen = options.fullscreen,
            series = seriesState.series,
            targetSystolic = seriesState.targetSystolic,
            targetDiastolic = seriesState.targetDiastolic,
            dayDetails = dayDetails,
            insights = seriesState.insights
        )
    }.filter { state ->
        // 范围或粒度刚切换时等待对应计算结果，避免新控件与旧图表短暂混用。
        state.series.range == state.range && state.series.aggregation == state.aggregation
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrendUiState(
            range = selectedRange.value,
            aggregation = selectedAggregation.value,
            metric = displayOptions.value.metric,
            selectedPointId = displayOptions.value.selectedPointId,
            showPulse = displayOptions.value.showPulse,
            fullscreenShowPulse = displayOptions.value.fullscreenShowPulse,
            showTimeLabels = displayOptions.value.showTimeLabels,
            fullscreen = displayOptions.value.fullscreen
        )
    )

    fun setRange(range: TrendRange) {
        if (selectedRange.value == range) return
        clearSelectionMapping()
        writeSelection(null)
        dismissDayDetails()
        savedStateHandle.remove<Long>("trend.viewportStart")
        savedStateHandle.remove<Long>("trend.viewportEnd")
        chartController.clearSavedViewport()
        savedStateHandle["trend.range"] = range.name
        selectedRange.value = range
    }

    fun setMetric(metric: TrendMetricType) {
        savedStateHandle["trend.metric"] = metric.name
        displayOptions.update { it.copy(metric = metric) }
    }

    fun setAggregation(aggregation: TrendAggregation) {
        // 保留旧调用契约，已废弃的聚合选择不能重新启用范围图。
        if (aggregation != TrendAggregation.RAW) return
        savedStateHandle["trend.aggregation"] = TrendAggregation.RAW.name
    }

    fun selectPoint(point: TrendPoint?) {
        clearSelectionMapping()
        val hadDetails = details.value != null
        writeSelection(point)
        if (hadDetails) {
            if (point == null) dismissDayDetails() else openPointDetails(point)
        }
    }

    fun setShowPulse(showPulse: Boolean) {
        savedStateHandle["trend.showPulse"] = showPulse
        displayOptions.update { it.copy(showPulse = showPulse) }
    }

    fun setFullscreenShowPulse(showPulse: Boolean) {
        savedStateHandle["trend.fullscreenShowPulse"] = showPulse
        displayOptions.update { it.copy(fullscreenShowPulse = showPulse) }
    }

    fun setShowTimeLabels(showTimeLabels: Boolean) {
        savedStateHandle["trend.showTimeLabels"] = showTimeLabels
        displayOptions.update { it.copy(showTimeLabels = showTimeLabels) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        savedStateHandle["trend.fullscreen"] = fullscreen
        displayOptions.update { it.copy(fullscreen = fullscreen) }
    }

    fun selectPrevious() = selectAdjacent(-1)

    fun selectNext() = selectAdjacent(1)

    private fun selectAdjacent(direction: Int) {
        val points = uiState.value.series.points
        if (points.isEmpty()) return
        val currentIndex = points.indexOfFirst { it.id == displayOptions.value.selectedPointId }
            .takeIf { it >= 0 } ?: points.lastIndex
        val target = points.getOrNull(currentIndex + direction) ?: return
        selectPoint(target)
        chartController.ensureVisible(target.timestamp)
    }

    fun resetChart() {
        selectPoint(null)
        chartController.reset()
    }

    fun moveToLatest() {
        chartController.moveToLatest()
    }

    /**
     * 打开某一点所属日期的原始测量明细。
     *
     * 每次测量定位原记录；每日范围只展示实际参与该桶统计的记录。
     */
    fun openPointDetails(point: TrendPoint) {
        val timestamp = selectionTimestamp.takeIf { displayOptions.value.selectedPointId == point.id }
            ?: point.timestamp
        writeSelection(point, timestamp)
        detailsJob?.cancel()
        val request = ++detailsRequest
        val date = Instant.ofEpochMilli(point.timestamp).atZone(zoneId).toLocalDate()
        details.value = TrendDayDetails(
            point = point,
            selectedRecordId = point.id.takeIf { point.aggregation == TrendAggregation.RAW },
            title = "$date ${if (point.aggregation == TrendAggregation.RAW) "当天记录" else "每日范围记录"}"
        )
        detailsJob = viewModelScope.launch {
            val (startInclusive, endExclusive) = dayRangeOf(point)
            try {
                val now = clockMillis()
                val sourceIds = point.sourceRecordIds.toSet()
                val records = trendRepository.getRecords(startInclusive, endExclusive)
                    .filter { record ->
                        record.measuredAt in startInclusive until endExclusive && record.measuredAt <= now &&
                            (point.aggregation == TrendAggregation.RAW || record.id in sourceIds)
                    }.sortedWith(compareBy<TrendRecord> { it.measuredAt }.thenBy { it.id })
                details.update { current ->
                    if (request != detailsRequest || current?.point?.id != point.id) current else current.copy(
                        records = records, loading = false, error = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Exception) {
                details.update { current ->
                    if (request != detailsRequest || current?.point?.id != point.id) current else current.copy(
                        loading = false, error = throwable.message ?: "无法读取当天记录"
                    )
                }
            }
        }
    }

    /** 节点所属自然日的半开区间。 */
    private fun dayRangeOf(point: TrendPoint): Pair<Long, Long> {
        if (point.aggregation != TrendAggregation.RAW) {
            return point.intervalStart to point.intervalEndExclusive
        }
        val date = Instant.ofEpochMilli(point.timestamp).atZone(zoneId).toLocalDate()
        val range = date.toEpochMillisRange(zoneId)
        return range.startInclusive to range.endExclusive
    }

    fun dismissDayDetails() {
        ++detailsRequest
        detailsJob?.cancel()
        detailsJob = null
        details.value = null
    }

    private fun writeSelection(point: TrendPoint?, timestamp: Long? = point?.timestamp) {
        selectionTimestamp = timestamp
        savedStateHandle["trend.selectedPointId"] = point?.id
        savedStateHandle["trend.selectedTimestamp"] = timestamp
        displayOptions.update { it.copy(selectedPointId = point?.id) }
    }

    private fun reconcileSelection(series: TrendSeries) {
        if (series.range != selectedRange.value || series.aggregation != selectedAggregation.value) return
        val mapping = pendingSelectionMapping
        if (mapping != null && mapping.first == series.aggregation) {
            // 初次订阅可能先收到空序列，等实际记录到达后再恢复旧日桶选择。
            if (series.points.isEmpty()) return
            val timestamp = mapping.second
            val target = if (series.aggregation == TrendAggregation.RAW) {
                series.points.minByOrNull { kotlin.math.abs(it.timestamp - timestamp) }
            } else {
                series.points.firstOrNull { timestamp in it.intervalStart until it.intervalEndExclusive }
            }
            clearSelectionMapping()
            writeSelection(target, timestamp.takeIf { target != null })
        } else {
            val selectedId = displayOptions.value.selectedPointId ?: return
            val selected = series.points.firstOrNull { it.id == selectedId }
            if (selected == null) {
                writeSelection(null)
                dismissDayDetails()
            } else if (details.value?.point != null && details.value?.point != selected) {
                openPointDetails(selected)
            }
        }
    }

    private fun restoredViewport(): Pair<Long, Long>? {
        val start = savedStateHandle.get<Long>("trend.viewportStart") ?: return null
        val end = savedStateHandle.get<Long>("trend.viewportEnd") ?: return null
        return (start to end).takeIf { end > start }
    }

    private fun clearSelectionMapping() {
        pendingSelectionMapping = null
        savedStateHandle.remove<Long>("trend.selectionMappingTimestamp")
    }

    private inline fun <reified T : Enum<T>> restoredEnum(key: String, fallback: T): T =
        savedStateHandle.get<String>(key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
            ?: fallback

    private data class DisplayOptions(
        val metric: TrendMetricType,
        val selectedPointId: String?,
        val showPulse: Boolean,
        val fullscreenShowPulse: Boolean,
        val showTimeLabels: Boolean,
        val fullscreen: Boolean
    )

    private data class TrendSeriesState(
        val series: TrendSeries,
        val insights: TrendInsights,
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
}
