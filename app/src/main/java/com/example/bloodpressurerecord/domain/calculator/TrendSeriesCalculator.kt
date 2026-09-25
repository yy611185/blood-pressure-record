package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object TrendSeriesCalculator {
    const val CHART_SAFE_MIN = 40
    /** 输入规则允许的最高收缩压；261–300 必须仍可在趋势图中看见。 */
    const val CHART_SAFE_MAX = 300
    /** Y 轴绝对上下界；图表自适应时不得越过，防止脏值把网格压扁。 */
    const val CHART_AXIS_MIN = 20
    const val CHART_AXIS_MAX = 320

    /** 舒张压的安全边界：输入规则允许 20–200。 */
    private const val DIASTOLIC_SAFE_MIN = 20
    private const val DIASTOLIC_SAFE_MAX = 200

    /** 固定参考线（收缩压 140 / 舒张压 90）。 */
    const val REFERENCE_SYSTOLIC = 140
    const val REFERENCE_DIASTOLIC = 90

    /** Y 轴目标主刻度间隔数（含上下边界，即约 5–6 条主网格）。 */
    private const val TARGET_TICK_INTERVALS = 5
    private val TICK_STEPS = intArrayOf(5, 10, 20, 25, 50, 100)

    fun rangeStart(range: TrendRange, nowMillis: Long, zoneId: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return when (range) {
            TrendRange.DAYS_7 -> today.minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
            TrendRange.DAYS_30 -> today.minusDays(29).atStartOfDay(zoneId).toInstant().toEpochMilli()
            TrendRange.ALL -> 0L
        }
    }

    /**
     * 数据粒度：7 天与 30 天保留每次测量，只有「全部」按自然日聚合成每日平均。
     * 每日平均节点仍保留当天的半开区间，点击后可回查当天全部原始记录。
     */
    fun aggregationFor(range: TrendRange): TrendAggregation = when (range) {
        TrendRange.DAYS_7,
        TrendRange.DAYS_30 -> TrendAggregation.RAW

        TrendRange.ALL -> TrendAggregation.DAILY
    }

    fun build(
        records: List<TrendRecord>,
        range: TrendRange,
        nowMillis: Long,
        zoneId: ZoneId,
        targetSystolic: Int? = null,
        targetDiastolic: Int? = null
    ): TrendSeries {
        val requestedStart = rangeStart(range, nowMillis, zoneId)
        val sorted = records
            .asSequence()
            .filter { it.measuredAt in requestedStart..nowMillis }
            .sortedBy { it.measuredAt }
            .toList()
        val aggregation = aggregationFor(range)
        val points = when (aggregation) {
            TrendAggregation.RAW -> sorted.map { it.toRawPoint() }
            TrendAggregation.DAILY -> sorted.toDailyPoints(zoneId)
        }
        val seriesStart = when {
            range != TrendRange.ALL -> requestedStart
            points.isNotEmpty() -> points.first().intervalStart
            else -> Instant.ofEpochMilli(nowMillis)
                .atZone(zoneId)
                .toLocalDate()
                .minusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        }
        val seriesEnd = nowMillis.coerceAtLeast(seriesStart + 1L)

        return TrendSeries(
            range = range,
            points = points,
            rawRecordCount = sorted.size,
            averageSystolic = sorted.takeIf { it.isNotEmpty() }
                ?.map { it.systolic }
                ?.average()
                ?.roundToInt(),
            averageDiastolic = sorted.takeIf { it.isNotEmpty() }
                ?.map { it.diastolic }
                ?.average()
                ?.roundToInt(),
            yAxis = calculateYAxis(points, targetSystolic, targetDiastolic),
            rangeStart = seriesStart,
            rangeEnd = seriesEnd,
            aggregation = aggregation,
            windowStart = requestedStart,
            firstMeasuredAt = sorted.firstOrNull()?.measuredAt,
            lastMeasuredAt = sorted.lastOrNull()?.measuredAt
        )
    }

    /**
     * Y 轴：先把参考线与目标线纳入视野，再把上下界吸附到「漂亮刻度」网格，
     * 使网格线数量稳定在 5–7 条（旧的按 10 递增会画出十余条密集网格）。
     */
    fun calculateYAxis(
        points: List<TrendPoint>,
        targetSystolic: Int?,
        targetDiastolic: Int?
    ): TrendYAxis {
        val values = buildList {
            points.forEach { point ->
                // 仅把超出输入规则的历史脏值限制到合法边界；合法的 261–300
                // 不再被旧的 260 上限吞掉。
                add(point.systolic.coerceIn(CHART_SAFE_MIN, CHART_SAFE_MAX))
                add(point.diastolic.coerceIn(DIASTOLIC_SAFE_MIN, DIASTOLIC_SAFE_MAX))
            }
            // 参考阈值也参与默认视野，避免常见血压区间看不到 90 / 140 的参考线。
            add(REFERENCE_DIASTOLIC)
            add(REFERENCE_SYSTOLIC)
            targetSystolic?.takeIf { it in CHART_SAFE_MIN..CHART_SAFE_MAX }?.let(::add)
            targetDiastolic?.takeIf { it in DIASTOLIC_SAFE_MIN..DIASTOLIC_SAFE_MAX }?.let(::add)
        }
        val rawMin = values.minOrNull() ?: 80
        val rawMax = values.maxOrNull() ?: 160

        val tickStep = chooseTickStep(rawMin, rawMax)
        var min = (floor((rawMin - 10) / tickStep.toDouble()) * tickStep)
            .toInt()
            .coerceAtLeast(CHART_AXIS_MIN)
        var max = (ceil((rawMax + 10) / tickStep.toDouble()) * tickStep)
            .toInt()
            .coerceAtMost(CHART_AXIS_MAX)
        // 上下界吸附到同一刻度网格；极端安全边界由绘制层额外补画首尾刻度。
        val remainder = (max - min) % tickStep
        if (remainder != 0) {
            val expandedMax = max + (tickStep - remainder)
            if (expandedMax <= CHART_AXIS_MAX) {
                max = expandedMax
            } else {
                min = (min - remainder).coerceAtLeast(CHART_AXIS_MIN)
            }
        }
        if (max <= min) {
            max = (min + tickStep).coerceAtMost(CHART_AXIS_MAX)
        }

        return TrendYAxis(min = min, max = max, tickStep = tickStep)
    }

    private fun chooseTickStep(rawMin: Int, rawMax: Int): Int {
        val span = (rawMax - rawMin + 20).coerceAtLeast(10)
        // 以「约 5 个间隔」为目标；用相对偏差挑最接近的漂亮步长，
        // 避免固定网格导致 50、60 … 150 这类十余条密集网格。
        return TICK_STEPS.minByOrNull { step ->
            abs(span.toDouble() / step - TARGET_TICK_INTERVALS)
        } ?: TICK_STEPS.last()
    }

    /** 网格线的具体数值（含上下界），与 [TrendYAxis.tickStep] 保持一致。 */
    fun tickValues(yAxis: TrendYAxis): List<Int> {
        val step = yAxis.tickStep.coerceAtLeast(1)
        val first = ceil(yAxis.min / step.toDouble()).toInt() * step
        val values = ArrayList<Int>(TARGET_TICK_INTERVALS + 2)
        var value = first
        while (value <= yAxis.max) {
            values += value
            value += step
        }
        return values
    }

    private fun TrendRecord.toRawPoint(): TrendPoint {
        return TrendPoint(
            id = id,
            timestamp = measuredAt,
            intervalStart = measuredAt,
            intervalEndExclusive = measuredAt.saturatedPlusOne(),
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            category = category,
            containsHighRiskReading = containsHighRiskReading,
            recordCount = 1,
            aggregation = TrendAggregation.RAW
        )
    }

    private fun List<TrendRecord>.toDailyPoints(zoneId: ZoneId): List<TrendPoint> {
        return groupBy {
            Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate()
        }.toSortedMap().map { (date, dayRecords) ->
            val range = date.toEpochMillisRange(zoneId)
            val systolic = dayRecords.map { it.systolic }.average().roundToInt()
            val diastolic = dayRecords.map { it.diastolic }.average().roundToInt()
            val pulseValues = dayRecords.mapNotNull { it.pulse }
            TrendPoint(
                id = "day:$date",
                timestamp = range.startInclusive,
                intervalStart = range.startInclusive,
                intervalEndExclusive = range.endExclusive,
                systolic = systolic,
                diastolic = diastolic,
                pulse = pulseValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                category = CategoryCalculator.calculate(systolic, diastolic).name,
                containsHighRiskReading = dayRecords.any { it.containsHighRiskReading },
                recordCount = dayRecords.size,
                aggregation = TrendAggregation.DAILY
            )
        }
    }

    private fun Long.saturatedPlusOne(): Long {
        return if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1L
    }
}
