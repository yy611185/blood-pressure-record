package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

data class TrendTimeTick(
    val timestamp: Long,
    val primary: String,
    val secondary: String? = null
)

/**
 * 可视时间窗口，用相对数据域的归一化比例保存。
 *
 * 用 `[0,1]` 比例而不是绝对毫秒有三个好处：
 * - 缩放/平移只做 Double 乘法，不会在极端时间范围下丢精度或溢出；
 * - 边界夹取退化成一次 `coerceIn(0.0, 1.0 - span)`，没有「域起点/终点」特判；
 * - 默认视野与当前视野是同一套坐标，双击复位不需要换算。
 *
 * 域 = [domainStartMillis, domainEndMillis]：7/30 天是自然周期窗口，
 * 「全部」是首条记录到当前时刻，因此夹取到域内就等价于「不拖出真实数据范围」。
 *
 * 注意：`zoom` 与 `spanRatio` 是两个独立变量。[minSpanRatio] 是像素可读性下限，
 * 缩放比例另有 [TrendChartMath.MAX_ZOOM] 上限，避免长按刻度被压到不可用。
 */
@Stable
class TrendTimeViewportState {
    var startRatio by mutableDoubleStateOf(0.0)
        private set

    var endRatio by mutableDoubleStateOf(1.0)
        private set

    /** 当前缩放倍数，1.0 表示铺满整个数据域。 */
    var zoom by mutableFloatStateOf(1f)
        private set

    /**
     * 视窗内容版本号：缩放 / 平移 / 复位每次改变窗口都会自增。
     *
     * 用于区分「视窗是自己动的」和「选中的点换了位置」——前者不应该把视窗拉回
     * 选中点，否则用户选中一个点后就再也拖不动图表了。
     */
    var revision by mutableIntStateOf(0)
        private set

    /** 双击复位用的默认视野。 */
    var defaultStartRatio by mutableDoubleStateOf(0.0)
        private set

    var defaultEndRatio by mutableDoubleStateOf(1.0)
        private set

    private var domainStartMillis: Long = 0L
    private var domainEndMillis: Long = 1L

    val spanRatio: Double
        get() = (endRatio - startRatio).coerceAtLeast(1e-9)

    /** 默认视野是否就是域全宽；全宽时「恢复默认」等于「回到整体」。 */
    val isAtDefault: Boolean
        get() = abs(startRatio - defaultStartRatio) < 1e-9 &&
            abs(endRatio - defaultEndRatio) < 1e-9

    fun startMillis(): Long = millisAt(startRatio)

    fun endMillis(): Long = millisAt(endRatio)

    /**
     * 让 [timestamp] 落在窗口中央，窗口宽度保持不变，并夹取回数据域内。
     *
     * 供「上一条 / 下一条」「明细」这类**外部**选点使用（见 TrendChart 的居中副作用）：
     * 这类选点不经过触摸，用户看不到“点在哪里”，所以必须把节点带回可视区。
     * 时间比例与 [millisAt] 同一套换算，因此不会出现“居中了但画在窗口外”的偏差。
     */
    fun centerOnRatio(timestamp: Long) {
        val span = spanRatio.coerceAtMost(1.0)
        val maxStart = (1.0 - span).coerceAtLeast(0.0)
        if (maxStart <= 0.0) return
        val ratio = ((timestamp - domainStartMillis).toDouble() / domainSpan().toDouble())
            .coerceIn(0.0, 1.0)
        val proposedStart = ratio - span / 2.0
        val newStart = proposedStart.coerceIn(0.0, maxStart)
        setWindow(newStart, newStart + span)
    }

    fun domainStartMillis(): Long = domainStartMillis

    fun domainEndMillis(): Long = domainEndMillis

    /** 设定数据域与默认视野，并立即跳转过去（范围切换 / 跨零点 / 双击复位）。 */
    fun reset(
        domainStart: Long,
        domainEnd: Long,
        defaultStart: Long,
        defaultEnd: Long
    ) {
        val safeDomainStart = domainStart
        val safeDomainEnd = domainEnd.coerceAtLeast(safeDomainStart + 1L)
        domainStartMillis = safeDomainStart
        domainEndMillis = safeDomainEnd

        val domainSpan = (safeDomainEnd - safeDomainStart).toDouble()
        val safeDefaultStart = defaultStart.coerceIn(safeDomainStart, safeDomainEnd)
        val safeDefaultEnd = defaultEnd.coerceIn(safeDefaultStart + 1L, safeDomainEnd)
        defaultStartRatio = (safeDefaultStart - safeDomainStart) / domainSpan
        defaultEndRatio = (safeDefaultEnd - safeDomainStart) / domainSpan
        startRatio = defaultStartRatio
        endRatio = defaultEndRatio
        zoom = (1.0 / spanRatio).toFloat().coerceIn(1f, TrendChartMath.MAX_ZOOM)
        revision++
    }

    /** 回到 [reset] 设定的默认视野与位置。 */
    fun resetToDefault() {
        startRatio = defaultStartRatio
        endRatio = defaultEndRatio
        zoom = (1.0 / spanRatio).toFloat().coerceIn(1f, TrendChartMath.MAX_ZOOM)
        revision++
    }

    /**
     * 以 [focusRatio]（绘图区内的归一化位置）为中心缩放。
     *
     * 该点对应的时间在缩放前后保持在同一像素位置，即「以手势中心缩放」。
     */
    fun zoomBy(zoomChange: Float, focusRatio: Double, minSpanRatio: Double) {
        val oldSpan = spanRatio
        // 域内不足 1ms 时无法再放大，直接跳过，避免出现零宽窗口。
        if (domainEndMillis - domainStartMillis <= 1L) return
        val maxZoomSpan = 1.0 / domainSpan().toDouble()
        val newSpan = (oldSpan / zoomChange.coerceIn(0.2f, 5f))
            .coerceIn(minSpanRatio.coerceAtLeast(maxZoomSpan), 1.0)
        val anchorTime = startRatio + focusRatio * oldSpan
        val focusInsideViewport = if (oldSpan > 0.0) {
            ((anchorTime - startRatio) / oldSpan).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
        val proposedStart = anchorTime - newSpan * focusInsideViewport
        val maxStart = (1.0 - newSpan).coerceAtLeast(0.0)
        val newStart = proposedStart.coerceIn(0.0, maxStart)
        setWindow(newStart, newStart + newSpan)
    }

    /** 按归一化比例平移，并夹取在数据域内。 */
    fun panBy(deltaRatio: Double) {
        val span = spanRatio.coerceAtMost(1.0)
        val maxStart = (1.0 - span).coerceAtLeast(0.0)
        val newStart = (startRatio + deltaRatio).coerceIn(0.0, maxStart)
        setWindow(newStart, newStart + span)
    }

    fun millisAt(ratio: Double): Long {
        val span = domainSpan()
        val offset = (ratio.coerceIn(0.0, 1.0) * span.toDouble()).roundToLong()
        return (domainStartMillis + offset).coerceIn(domainStartMillis, domainEndMillis)
    }

    private fun domainSpan(): Long = (domainEndMillis - domainStartMillis).coerceAtLeast(1L)

    private fun setWindow(start: Double, end: Double) {
        val span = (end - start).coerceIn(1e-9, 1.0)
        val newStart = start.coerceIn(0.0, (1.0 - span).coerceAtLeast(0.0))
        val changed = newStart != startRatio || (newStart + span) != endRatio
        startRatio = newStart
        endRatio = newStart + span
        zoom = (1.0 / span).toFloat().coerceIn(1f, TrendChartMath.MAX_ZOOM)
        if (changed) revision++
    }
}

/** 一帧内唯一的「像素 ↔ 时间 / 数值」换算器，绘制与命中测试共用同一实例。 */
data class ChartProjection(
    val geometry: ChartGeometry,
    val domainStart: Long,
    val domainEnd: Long,
    val startRatio: Double,
    val endRatio: Double,
    val yAxis: TrendYAxis
) {
    private val viewportStartMillis: Long
        get() = domainStart + ((domainEnd - domainStart) * startRatio).roundToLong()

    private val viewportSpanMillis: Long
        get() = ((domainEnd - domainStart) * (endRatio - startRatio)).roundToLong().coerceAtLeast(1L)

    fun xOfTime(timestamp: Long): Float {
        val ratio = ((timestamp - viewportStartMillis).toDouble() / viewportSpanMillis.toDouble())
            .coerceIn(-0.05, 1.05)
        return geometry.left + (ratio * (geometry.right - geometry.left)).toFloat()
    }

    fun timeAtX(x: Float): Long {
        val ratio = ((x - geometry.left) / (geometry.right - geometry.left).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        return viewportStartMillis + (viewportSpanMillis * ratio).roundToLong()
    }

    fun yOfValue(value: Int): Float {
        val range = (yAxis.max - yAxis.min).coerceAtLeast(1)
        val safeValue = value.coerceIn(yAxis.min, yAxis.max)
        val ratio = (safeValue - yAxis.min).toFloat() / range.toFloat()
        return geometry.bottom - ratio * (geometry.bottom - geometry.top)
    }

    /** 触点是否落在节点附近的矩形热区内（不要求精确点中视觉圆点）。 */
    fun hitsNode(
        point: TrendPoint,
        x: Float,
        y: Float,
        showSystolic: Boolean,
        showDiastolic: Boolean,
        touchRadiusPx: Float
    ): Boolean {
        val nodeX = xOfTime(point.timestamp)
        if (abs(x - nodeX) > touchRadiusPx) return false
        if (showSystolic && abs(y - yOfValue(point.systolic)) <= touchRadiusPx) return true
        if (showDiastolic && abs(y - yOfValue(point.diastolic)) <= touchRadiusPx) return true
        return false
    }

    /** 数值范围内两个序列点到触点的最小距离，用于「先看命中、再看邻近」的判断。 */
    fun nearestDistance(
        point: TrendPoint,
        x: Float,
        y: Float,
        showSystolic: Boolean,
        showDiastolic: Boolean
    ): Float {
        val nodeX = xOfTime(point.timestamp)
        val dx = x - nodeX
        var best = Float.MAX_VALUE
        if (showSystolic) {
            val dy = y - yOfValue(point.systolic)
            best = minOf(best, kotlin.math.hypot(dx, dy))
        }
        if (showDiastolic) {
            val dy = y - yOfValue(point.diastolic)
            best = minOf(best, kotlin.math.hypot(dx, dy))
        }
        return best
    }
}

/** 图表绘图区几何：与密度换算一次，绘制和手势都从这里取。 */
data class ChartGeometry(
    val width: Float,
    val height: Float,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float
) {
    val plotWidth: Float
        get() = (right - left).coerceAtLeast(1f)

    val plotHeight: Float
        get() = (bottom - top).coerceAtLeast(1f)

    companion object {
        fun create(size: androidx.compose.ui.unit.IntSize, density: Float, bottomPadding: Float): ChartGeometry {
            val width = size.width.toFloat().coerceAtLeast(1f)
            val height = size.height.toFloat().coerceAtLeast(1f)
            return ChartGeometry(
                width = width,
                height = height,
                left = 38f * density,
                right = (width - 14f * density).coerceAtLeast(39f * density),
                top = 20f * density,
                bottom = (height - bottomPadding).coerceAtLeast(21f * density)
            )
        }
    }
}

object TrendChartMath {
    private const val MINUTE_MILLIS = 60L * 1_000L
    private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
    private const val DAY_MILLIS = 24L * HOUR_MILLIS

    /** Y 轴自适应时的数值稳定带：变化不超过该值就沿用旧轴，避免拖动时逐帧抖动。 */
    private const val Y_AXIS_STABLE_MMHG = 2

    /** 缩放下限的像素目标：可视窗口再窄也至少覆盖约 16dp 的刻度间隔。 */
    private const val MIN_PIXELS_PER_TICK_DP = 16f

    /** 缩放上限：可视窗口再宽也至少覆盖约 120dp 的时间跨度。 */
    private const val MIN_ZOOM_SPAN_DP = 120f

    const val MAX_ZOOM = 400f

    /** 放大到该倍数以上，单指横向拖动才开始平移时间轴。 */
    const val PAN_ZOOM_THRESHOLD = 1.02f

    /**
     * 可视窗口的像素可读性下限：[plotWidthPx] 越窄，允许缩放到的最小时间跨度越小；
     * 但始终再夹一层「当前范围对应的最小可读跨度」，避免 7 天被放大到几秒钟。
     */
    fun minSpanRatio(range: TrendRange, domainSpanMillis: Long, plotWidthPx: Float): Double {
        if (domainSpanMillis <= 0L || plotWidthPx <= 0f) return 0.02
        val pixelsPerMillis = plotWidthPx / domainSpanMillis.toDouble()
        val byPixels = MIN_PIXELS_PER_TICK_DP / pixelsPerMillis
        val byRange = minViewportSpan(range).toDouble()
        val byAbsolute = MIN_ZOOM_SPAN_DP / pixelsPerMillis
        val minSpan = maxOf(byPixels, byRange, byAbsolute)
        return (minSpan / domainSpanMillis.toDouble()).coerceIn(1e-6, 1.0)
    }

    fun minViewportSpan(range: TrendRange): Long = when (range) {
        TrendRange.DAYS_7 -> HOUR_MILLIS
        TrendRange.DAYS_30 -> 6L * HOUR_MILLIS
        TrendRange.ALL -> 7L * DAY_MILLIS
    }

    /** 双击 / 初次进入时的最小可辨识窗口，避免单点或极短样本被拉到一条竖线。 */
    fun minDefaultViewportSpan(range: TrendRange): Long = when (range) {
        TrendRange.DAYS_7 -> 2L * HOUR_MILLIS
        TrendRange.DAYS_30 -> 3L * DAY_MILLIS
        TrendRange.ALL -> 14L * DAY_MILLIS
    }

    /**
     * 默认视野：聚焦真实有数据的区间并留少量左右 padding。
     *
     * 之前默认铺满整个周期窗口，最近 7 天只有 3 天有记录时数据全挤在左侧、
     * 右侧大片空白；这里在**不改变真实时间比例**的前提下只裁剪视野，
     * 缺测断档、双指缩放和平移逻辑都不受影响。
     */
    fun defaultViewport(
        points: List<TrendPoint>,
        range: TrendRange,
        windowStart: Long,
        windowEndInclusive: Long
    ): Pair<Long, Long> {
        val safeWindowStart = windowStart.coerceAtMost(windowEndInclusive)
        val safeWindowEnd = windowEndInclusive.coerceAtLeast(safeWindowStart + 1L)
        if (points.isEmpty()) return safeWindowStart to safeWindowEnd

        val firstMillis = points.minOf { it.timestamp }
        val lastMillis = points.maxOf { it.timestamp }
        val minSpan = minDefaultViewportSpan(range)

        if (lastMillis - firstMillis >= minSpan) {
            val padding = viewportPadding(firstMillis, lastMillis, minSpan)
            val start = (firstMillis - padding).coerceIn(safeWindowStart, safeWindowEnd - minSpan)
            val end = (lastMillis + padding).coerceAtLeast(start + minSpan)
            return start to end.coerceIn(start + minSpan, safeWindowEnd)
        }

        val center = firstMillis / 2L + lastMillis / 2L + (firstMillis % 2L + lastMillis % 2L) / 2L
        val halfSpan = minSpan / 2L
        var start = (center - halfSpan).coerceIn(safeWindowStart, safeWindowEnd - minSpan)
        var end = (start + minSpan).coerceAtLeast(start + 1L)
        if (end > safeWindowEnd) {
            end = safeWindowEnd
            start = (end - minSpan).coerceAtLeast(safeWindowStart)
        }
        return start to end
    }

    private fun viewportPadding(firstMillis: Long, lastMillis: Long, minSpan: Long): Long {
        val dataSpan = (lastMillis - firstMillis).coerceAtLeast(0L)
        val proportional = (dataSpan / 12L).coerceAtLeast(MINUTE_MILLIS * 30L)
        return proportional.coerceIn(MINUTE_MILLIS * 20L, minSpan)
    }

    /**
     * 需要绘制的点：可视窗口内的全部点，外加窗口左右各一个点。
     *
     * 外扩恰好一个点（而不是把窗口撑到样本边界）有两个原因：
     * - 折线在缩放/平移时仍然连到绘图区左右边界，不会在边界处缺一截；
     * - 旧写法会把「窗口之外一直到样本首尾」的点全部取出来。视窗被缩放到很窄
     *   并远离样本边界时，这些点会被 [ChartProjection.xOfTime] 钳在 ±5% 处，
     *   在边界外堆成一条竖直的点列（这正是折线越界的来源）。
     *
     * 首尾点不会被裁掉：窗口覆盖整个样本时，两端本来就没有窗口外的点。
     */
    fun visiblePoints(
        points: List<TrendPoint>,
        startInclusive: Long,
        endInclusive: Long
    ): List<TrendPoint> {
        if (points.isEmpty()) return emptyList()
        val sortedPoints = if (points.zipWithNext().all { (a, b) ->
                a.timestamp <= b.timestamp
            }
        ) {
            points
        } else {
            points.sortedWith(compareBy<TrendPoint> { it.timestamp }.thenBy { it.id })
        }
        val windowStart = minOf(startInclusive, endInclusive)
        val windowEnd = maxOf(startInclusive, endInclusive)
        val first = (sortedPoints.lowerBound(windowStart) - 1).coerceAtLeast(0)
        val afterLast = (sortedPoints.upperBound(windowEnd) + 1).coerceAtMost(sortedPoints.size)
        if (first >= afterLast) return emptyList()
        return sortedPoints.subList(first, afterLast)
    }

    fun nearestPoint(points: List<TrendPoint>, timestamp: Long): TrendPoint? {
        if (points.isEmpty()) return null
        val insertion = points.lowerBound(timestamp)
        if (insertion <= 0) return points.first()
        if (insertion >= points.size) return points.last()
        val before = points[insertion - 1]
        val after = points[insertion]
        return if (timestamp - before.timestamp <= after.timestamp - timestamp) before else after
    }

    /** 触点优先命中节点热区；没有命中时退化到「按 X 最近的可见点」。 */
    fun hitTest(
        projection: ChartProjection,
        visible: List<TrendPoint>,
        x: Float,
        y: Float,
        showSystolic: Boolean,
        showDiastolic: Boolean,
        touchRadiusPx: Float
    ): TrendPoint? {
        if (visible.isEmpty()) return null
        visible.forEach { point ->
            if (projection.hitsNode(point, x, y, showSystolic, showDiastolic, touchRadiusPx)) {
                return point
            }
        }
        var best: TrendPoint? = null
        var bestDistance = touchRadiusPx
        visible.forEach { point ->
            val distance = projection.nearestDistance(point, x, y, showSystolic, showDiastolic)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = point
            }
        }
        return best ?: nearestPoint(visible, projection.timeAtX(x))
    }

    /**
     * 绘制与连线共用的降采样。
     *
     * **只降低绘制密度，不删除任何真实数据**：每个桶都保留四个极值索引，
     * 首尾点一定保留，因此曲线形状与极值位置不变。视窗内点数小于
     * [maxPoints] 时原样返回，不做任何裁剪。
     */
    fun sampleShared(points: List<TrendPoint>, maxPoints: Int): List<TrendPoint> {
        if (points.size <= maxPoints || maxPoints < 8) return points
        val bucketCount = (maxPoints / 4).coerceAtLeast(2)
        val bucketSize = ceil(points.size / bucketCount.toDouble()).toInt().coerceAtLeast(1)
        val selected = linkedSetOf<Int>()
        selected += 0
        selected += points.lastIndex
        var start = 0
        while (start < points.size) {
            val end = (start + bucketSize).coerceAtMost(points.size)
            val indices = start until end
            selected += indices.minByOrNull { points[it].systolic } ?: start
            selected += indices.maxByOrNull { points[it].systolic } ?: start
            selected += indices.minByOrNull { points[it].diastolic } ?: start
            selected += indices.maxByOrNull { points[it].diastolic } ?: start
            start = end
        }
        val ordered = selected.sorted()
        if (ordered.size <= maxPoints) return ordered.map(points::get)
        val kept = buildList {
            add(ordered.first())
            addAll(ordered.subList(1, ordered.lastIndex).take(maxPoints - 2))
            add(ordered.last())
        }
        return kept.distinct().sorted().map(points::get)
    }

    fun maxTickCount(plotWidthDp: Int): Int {
        return (plotWidthDp / 80).coerceIn(3, 7)
    }

    fun nonOverlappingTickIndices(
        centers: List<Float>,
        widths: List<Float>,
        left: Float,
        right: Float,
        minimumGap: Float
    ): List<Int> {
        require(centers.size == widths.size)
        if (centers.isEmpty() || right <= left) return emptyList()
        if (centers.size == 1) return listOf(0)

        fun bounds(index: Int): Pair<Float, Float> {
            val width = widths[index].coerceAtLeast(0f)
            val labelLeft = (centers[index] - width / 2f)
                .coerceIn(left, (right - width).coerceAtLeast(left))
            return labelLeft to (labelLeft + width)
        }

        // 首尾刻度承载“当前视野从哪里到哪里”的信息，优先保留。
        // 中间刻度只在不会撞到首尾标签时加入，避免旧的贪心算法把最新日期丢掉。
        val selected = mutableListOf(0)
        var previousRight = bounds(0).second
        val lastIndex = centers.lastIndex
        val lastLeft = bounds(lastIndex).first

        for (index in 1 until lastIndex) {
            val (labelLeft, labelRight) = bounds(index)
            if (labelLeft >= previousRight + minimumGap &&
                labelRight <= lastLeft - minimumGap
            ) {
                selected += index
                previousRight = labelRight
            }
        }

        if (lastLeft >= previousRight + minimumGap) {
            selected += lastIndex
        } else if (selected.size == 1) {
            // 极窄视图只保留终点，不能为了首尾同时显示而允许文字重叠。
            return listOf(lastIndex)
        }
        return selected
    }

    fun timeAtX(x: Float, left: Float, right: Float, start: Long, end: Long): Long {
        val ratio = ((x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return start + ((end - start) * ratio).roundToLong()
    }

    fun xOfTime(timestamp: Long, left: Float, right: Float, start: Long, end: Long): Float {
        val span = (end - start).coerceAtLeast(1L)
        val ratio = ((timestamp - start).toDouble() / span.toDouble()).coerceIn(-0.05, 1.05)
        return left + (ratio * (right - left)).toFloat()
    }

    /**
     * Y 轴自适应：只用当前可视窗口内的数据，并保留上一帧的轴做滞回。
     *
     * - 数据超出当前轴（会被裁切）时立即扩展；
     * - 数据在轴内时，只有跨度明显缩小/上移超过 [Y_AXIS_STABLE_MMHG] 才收紧，
     *   避免拖动时逐帧抖动；
     * - 上下界吸附到 10 mmHg 网格，主刻度间隔优先取 10 mmHg。
     */
    fun stableYAxis(previous: TrendYAxis, visible: List<TrendPoint>): TrendYAxis {
        if (visible.isEmpty()) return previous
        val dataMin = visible.minOf { minOf(it.diastolic, it.systolic) }
            .coerceIn(TrendSeriesCalculator.CHART_SAFE_MIN, TrendSeriesCalculator.CHART_SAFE_MAX)
        val dataMax = visible.maxOf { maxOf(it.diastolic, it.systolic) }
            .coerceIn(TrendSeriesCalculator.CHART_SAFE_MIN, TrendSeriesCalculator.CHART_SAFE_MAX)

        val target = buildYAxis(dataMin, dataMax)
        val mustExpand = dataMin < target.min || dataMax > target.max
        val shrunkEnough =
            (previous.min - target.min >= Y_AXIS_STABLE_MMHG) ||
                (target.max - previous.max >= Y_AXIS_STABLE_MMHG)
        val next = if (mustExpand || shrunkEnough) target else previous
        // 兜底：任何情况下都不允许轴裁掉可视数据。
        if (dataMin >= next.min && dataMax <= next.max) return next
        return next.copy(
            min = minOf(next.min, dataMin).coerceAtLeast(TrendSeriesCalculator.CHART_AXIS_MIN),
            max = maxOf(next.max, dataMax).coerceAtMost(TrendSeriesCalculator.CHART_AXIS_MAX)
        )
    }

    private fun buildYAxis(dataMin: Int, dataMax: Int): TrendYAxis {
        val step = chooseTickStep(dataMin, dataMax)
        val lower = floor((dataMin - 10) / step.toDouble()) * step
        val upper = ceil((dataMax + 10) / step.toDouble()) * step
        var min = lower.toInt().coerceAtLeast(TrendSeriesCalculator.CHART_AXIS_MIN)
        var max = upper.toInt().coerceAtMost(TrendSeriesCalculator.CHART_AXIS_MAX)
        if (max <= min) max = (min + step).coerceAtMost(TrendSeriesCalculator.CHART_AXIS_MAX)
        // 跨度不能被 step 整除时把上界再抬一格，保持网格整齐。
        val remainder = (max - min) % step
        if (remainder != 0 && max + (step - remainder) <= TrendSeriesCalculator.CHART_AXIS_MAX) {
            max += step - remainder
        }
        return TrendYAxis(min = min, max = max, tickStep = step)
    }

    /** 主刻度间隔：优先 10 mmHg；跨度很大时才退到 20/25/50 以控制网格条数。 */
    private fun chooseTickStep(dataMin: Int, dataMax: Int): Int {
        val span = (dataMax - dataMin + 20).coerceAtLeast(10)
        return when {
            span <= 80 -> 10
            span <= 160 -> 20
            span <= 240 -> 25
            else -> 50
        }
    }

    fun timeTicks(
        startMillis: Long,
        endMillis: Long,
        zoneId: ZoneId,
        maxTicks: Int = 7
    ): List<TrendTimeTick> {
        if (endMillis <= startMillis) return emptyList()
        val safeMaxTicks = maxTicks.coerceIn(2, 7)
        val start = Instant.ofEpochMilli(startMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endMillis).atZone(zoneId)
        val spanDays = ChronoUnit.HOURS.between(start, end).coerceAtLeast(1) / 24.0
        return when {
            spanDays <= 2.0 -> hourlyTicks(start, end, safeMaxTicks)
            spanDays <= 45.0 -> dailyTicks(start, end, safeMaxTicks)
            spanDays <= 730.0 -> monthlyTicks(start, end, safeMaxTicks)
            else -> yearlyTicks(start, end, safeMaxTicks)
        }
    }

    private fun hourlyTicks(
        start: ZonedDateTime,
        end: ZonedDateTime,
        maxTicks: Int
    ): List<TrendTimeTick> {
        val totalHours = ChronoUnit.HOURS.between(start, end).coerceAtLeast(1)
        val step = niceStep(
            required = ceil(totalHours / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong(),
            candidates = longArrayOf(1, 2, 3, 4, 6, 12, 24)
        )
        val startTick = TrendTimeTick(
            timestamp = start.toInstant().toEpochMilli(),
            primary = start.format(DateTimeFormatter.ofPattern("HH:mm")),
            secondary = start.format(DateTimeFormatter.ofPattern("MM-dd"))
        )
        val endTick = TrendTimeTick(
            timestamp = end.toInstant().toEpochMilli(),
            primary = end.format(DateTimeFormatter.ofPattern("HH:mm")),
            secondary = end.format(DateTimeFormatter.ofPattern("MM-dd"))
        )
        var cursor = start.truncatedTo(ChronoUnit.HOURS).plusHours(step)
        val interior = buildList {
            while (cursor.isBefore(end)) {
                add(
                    TrendTimeTick(
                        timestamp = cursor.toInstant().toEpochMilli(),
                        primary = cursor.format(DateTimeFormatter.ofPattern("HH:mm")),
                        secondary = cursor.format(DateTimeFormatter.ofPattern("MM-dd"))
                    )
                )
                cursor = cursor.plusHours(step)
            }
        }
        return ticksWithBoundaries(startTick, endTick, interior)
    }

    private fun dailyTicks(
        start: ZonedDateTime,
        end: ZonedDateTime,
        maxTicks: Int
    ): List<TrendTimeTick> {
        val totalDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).coerceAtLeast(1)
        val step = if (totalDays <= maxTicks - 1L) {
            1L
        } else {
            niceStep(
                required = ceil(totalDays / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong(),
                candidates = longArrayOf(1, 2, 3, 7, 14, 30, 60, 90)
            )
        }
        val formatter = DateTimeFormatter.ofPattern("MM-dd")
        val startTick = TrendTimeTick(
            timestamp = start.toInstant().toEpochMilli(),
            primary = start.format(formatter)
        )
        val endTick = TrendTimeTick(
            timestamp = end.toInstant().toEpochMilli(),
            primary = end.format(formatter)
        )
        var cursor = start.toLocalDate().plusDays(step).atStartOfDay(start.zone)
        val interior = buildList {
            while (cursor.isBefore(end)) {
                add(
                    TrendTimeTick(
                        timestamp = cursor.toInstant().toEpochMilli(),
                        primary = cursor.format(formatter)
                    )
                )
                cursor = cursor.plusDays(step)
            }
        }
        return ticksWithBoundaries(startTick, endTick, interior)
    }

    private fun monthlyTicks(
        start: ZonedDateTime,
        end: ZonedDateTime,
        maxTicks: Int
    ): List<TrendTimeTick> {
        val firstMonth = start.withDayOfMonth(1).toLocalDate()
        val lastMonth = end.withDayOfMonth(1).toLocalDate()
        val months = ChronoUnit.MONTHS.between(firstMonth, lastMonth).coerceAtLeast(1)
        val step = if (months <= maxTicks - 1L) {
            1L
        } else {
            niceStep(
                required = ceil(months / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong(),
                candidates = longArrayOf(1, 2, 3, 6, 12, 24)
            )
        }
        val formatter = if (start.year == end.year) {
            DateTimeFormatter.ofPattern("MM月")
        } else {
            DateTimeFormatter.ofPattern("yy-MM")
        }
        val startTick = TrendTimeTick(
            timestamp = start.toInstant().toEpochMilli(),
            primary = start.format(formatter)
        )
        val endTick = TrendTimeTick(
            timestamp = end.toInstant().toEpochMilli(),
            primary = end.format(formatter)
        )
        var cursor = firstMonth.plusMonths(step).atStartOfDay(start.zone)
        val interior = buildList {
            while (cursor.isBefore(end)) {
                add(
                    TrendTimeTick(
                        timestamp = cursor.toInstant().toEpochMilli(),
                        primary = cursor.format(formatter)
                    )
                )
                cursor = cursor.plusMonths(step)
            }
        }
        return ticksWithBoundaries(startTick, endTick, interior)
    }

    private fun yearlyTicks(
        start: ZonedDateTime,
        end: ZonedDateTime,
        maxTicks: Int
    ): List<TrendTimeTick> {
        val years = (end.year - start.year).coerceAtLeast(1)
        val step = niceStep(
            required = ceil(years / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong(),
            candidates = longArrayOf(1, 2, 5, 10, 20, 50)
        ).toInt().coerceAtLeast(1)
        val startTick = TrendTimeTick(
            timestamp = start.toInstant().toEpochMilli(),
            primary = start.year.toString()
        )
        val endTick = TrendTimeTick(
            timestamp = end.toInstant().toEpochMilli(),
            primary = end.year.toString()
        )
        var year = start.year + step
        val interior = buildList {
            while (year < end.year) {
                val cursor = LocalDate.of(year, 1, 1).atStartOfDay(start.zone)
                add(
                    TrendTimeTick(
                        timestamp = cursor.toInstant().toEpochMilli(),
                        primary = year.toString()
                    )
                )
                year += step
            }
        }
        return ticksWithBoundaries(startTick, endTick, interior)
    }

    /** 在候选中选第一个不小于需求值的步长，让刻度落在人们熟悉的间隔上。 */
    private fun niceStep(required: Long, candidates: LongArray): Long {
        val need = required.coerceAtLeast(1L)
        return candidates.firstOrNull { it >= need } ?: candidates.last()
    }

    private fun ticksWithBoundaries(
        start: TrendTimeTick,
        end: TrendTimeTick,
        interior: List<TrendTimeTick>
    ): List<TrendTimeTick> {
        if (start.primary == end.primary && start.secondary == end.secondary) {
            return listOf(end)
        }
        return buildList {
            add(start)
            interior.forEach { tick ->
                val duplicatesStart =
                    tick.primary == start.primary && tick.secondary == start.secondary
                val duplicatesEnd =
                    tick.primary == end.primary && tick.secondary == end.secondary
                if (!duplicatesStart && !duplicatesEnd) add(tick)
            }
            add(end)
        }.sortedBy { it.timestamp }
    }

    private fun List<TrendPoint>.lowerBound(timestamp: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (this[mid].timestamp < timestamp) low = mid + 1 else high = mid
        }
        return low
    }

    private fun List<TrendPoint>.upperBound(timestamp: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (this[mid].timestamp <= timestamp) low = mid + 1 else high = mid
        }
        return low
    }
}
