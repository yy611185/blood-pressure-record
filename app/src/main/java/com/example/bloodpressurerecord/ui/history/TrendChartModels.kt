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

internal enum class ChartDragIntent { WAIT, PAN, YIELD }

/**
 * 可视时间窗口，内部使用归一化比例计算，恢复与持久化使用绝对时间。
 *
 * 用 `[0,1]` 比例而不是绝对毫秒有三个好处：
 * - 缩放/平移只做 Double 乘法，不会在极端时间范围下丢精度或溢出；
 * - 边界夹取退化成一次 `coerceIn(0.0, 1.0 - span)`，没有「域起点/终点」特判；
 * - 默认视野与当前视野是同一套坐标，双击复位不需要换算。
 *
 * 域 = [domainStartMillis, domainEndMillis]：7/30 天是自然周期窗口，
 * 「全部」是首条记录到当前时刻，因此夹取到域内就等价于「不拖出真实数据范围」。
 *
 * `zoom` 始终由实际窗口跨度计算；最小时间跨度与屏幕像素密度无关。
 */
@Stable
class TrendTimeViewportState(
    private val onWindowChanged: (Long, Long) -> Unit = { _, _ -> }
) {
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

    fun startMillis(): Long {
        // 数据域更新即使比例不变，也必须触发面板重新计算绝对时间。
        revision
        return millisAt(startRatio)
    }

    fun endMillis(): Long {
        revision
        return millisAt(endRatio)
    }

    /**
     * 让 [timestamp] 落在窗口中央，窗口宽度保持不变，并夹取回数据域内。
     *
     * 供 ensureVisible 在「上一条 / 下一条」选中窗口外节点时使用：
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

    /** 仅在目标不可见时平移，不因重新布局或重新选中而居中。 */
    fun ensureVisible(timestamp: Long) {
        if (timestamp !in startMillis()..endMillis()) centerOnRatio(timestamp)
    }

    /** 最新实测节点落在右侧；跨度保持不变，夹取回数据域。 */
    fun moveToLatest(timestamp: Long) {
        val span = spanRatio.coerceAtMost(1.0)
        val end = ((timestamp - domainStartMillis).toDouble() / domainSpan()).coerceIn(span, 1.0)
        setWindow(end - span, end)
    }

    fun domainStartMillis(): Long = domainStartMillis

    fun domainEndMillis(): Long = domainEndMillis

    /** 初始化数据域与默认视野；已有绝对时间快照时恢复到快照。 */
    fun reset(
        domainStart: Long,
        domainEnd: Long,
        defaultStart: Long,
        defaultEnd: Long,
        restoredViewport: Pair<Long, Long>? = null
    ) {
        val safeDomainStart = domainStart
        val safeDomainEnd = domainEnd.coerceAtLeast(safeDomainStart + 1L)
        domainStartMillis = safeDomainStart
        domainEndMillis = safeDomainEnd

        val domainSpan = (safeDomainEnd - safeDomainStart).toDouble()
        val safeDefaultStart = defaultStart.coerceIn(safeDomainStart, safeDomainEnd - 1L)
        val safeDefaultEnd = defaultEnd.coerceIn(safeDefaultStart + 1L, safeDomainEnd)
        defaultStartRatio = (safeDefaultStart - safeDomainStart) / domainSpan
        defaultEndRatio = (safeDefaultEnd - safeDomainStart) / domainSpan
        val restoredSpan = restoredViewport?.let { (it.second - it.first).coerceIn(1L, safeDomainEnd - safeDomainStart) }
        val start = if (restoredSpan != null) {
            restoredViewport!!.first.coerceIn(safeDomainStart, safeDomainEnd - restoredSpan)
        } else safeDefaultStart
        val end = if (restoredSpan != null) start + restoredSpan else safeDefaultEnd
        setWindow((start - safeDomainStart) / domainSpan, (end - safeDomainStart) / domainSpan, force = true)
    }

    /**
     * 同一范围内数据更新时刷新数据域和默认视野。用户已移动的绝对时间窗口保持
     * 原位；若旧窗口超出新域，则维持原宽度并夹到最近的合法位置。
     */
    fun updateDomain(
        domainStart: Long,
        domainEnd: Long,
        defaultStart: Long,
        defaultEnd: Long
    ) {
        val oldStart = startMillis()
        val oldEnd = endMillis()
        val safeDomainStart = domainStart
        val safeDomainEnd = domainEnd.coerceAtLeast(safeDomainStart + 1L)
        val domainSpan = safeDomainEnd - safeDomainStart
        val safeDefaultStart = defaultStart.coerceIn(safeDomainStart, safeDomainEnd - 1L)
        val safeDefaultEnd = defaultEnd.coerceIn(safeDefaultStart + 1L, safeDomainEnd)
        val oldSpan = (oldEnd - oldStart).coerceAtLeast(1L).coerceAtMost(domainSpan)
        val newStart = oldStart.coerceIn(safeDomainStart, safeDomainEnd - oldSpan)

        domainStartMillis = safeDomainStart
        domainEndMillis = safeDomainEnd
        defaultStartRatio = (safeDefaultStart - safeDomainStart).toDouble() / domainSpan
        defaultEndRatio = (safeDefaultEnd - safeDomainStart).toDouble() / domainSpan
        val newStartRatio = (newStart - safeDomainStart).toDouble() / domainSpan
        val newEndRatio = (newStart + oldSpan - safeDomainStart).toDouble() / domainSpan
        setWindow(newStartRatio, newEndRatio, force = newStart != oldStart || newStart + oldSpan != oldEnd)
    }

    /** 回到 [reset] 设定的默认视野与位置。 */
    fun resetToDefault() {
        setWindow(defaultStartRatio, defaultEndRatio, force = true)
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

    private fun setWindow(start: Double, end: Double, force: Boolean = false) {
        val span = (end - start).coerceIn(1e-9, 1.0)
        val newStart = start.coerceIn(0.0, (1.0 - span).coerceAtLeast(0.0))
        val changed = newStart != startRatio || (newStart + span) != endRatio
        startRatio = newStart
        endRatio = newStart + span
        zoom = (1.0 / span).toFloat()
        if (changed || force) {
            revision++
            onWindowChanged(startMillis(), endMillis())
        }
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
        val ratio = (timestamp - viewportStartMillis).toDouble() / viewportSpanMillis.toDouble()
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
    private const val Y_AXIS_STABLE_MMHG = 20

    /** 放大到该倍数以上，单指横向拖动才开始平移时间轴。 */
    const val PAN_ZOOM_THRESHOLD = 1.02f

    /** 方向锁定使用按下后的累计二维位移；进入平移后才消费逐帧增量。 */
    internal fun dragIntent(dx: Float, dy: Float, touchSlop: Float, canPan: Boolean): ChartDragIntent {
        if (kotlin.math.hypot(dx, dy) <= touchSlop) return ChartDragIntent.WAIT
        return if (canPan && abs(dx) > abs(dy)) ChartDragIntent.PAN else ChartDragIntent.YIELD
    }

    /** 所有历史范围均可查看一小时细节，不把最小时间窗口绑定到总历史或像素宽度。 */
    @Suppress("UNUSED_PARAMETER")
    fun minSpanRatio(range: TrendRange, domainSpanMillis: Long, plotWidthPx: Float): Double {
        if (domainSpanMillis <= 0L) return 1.0
        return (minViewportSpan(range).toDouble() / domainSpanMillis).coerceAtMost(1.0)
    }

    @Suppress("UNUSED_PARAMETER")
    fun minViewportSpan(range: TrendRange): Long = HOUR_MILLIS

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
        val minSpan = minDefaultViewportSpan(range).coerceAtMost(safeWindowEnd - safeWindowStart)

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
        return proportional.coerceIn(minOf(MINUTE_MILLIS * 20L, minSpan), minSpan)
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
        endInclusive: Long,
        alreadySorted: Boolean = false
    ): List<TrendPoint> {
        if (points.isEmpty()) return emptyList()
        val sortedPoints = if (alreadySorted || points.zipWithNext().all { (a, b) ->
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
        val nearestTimestamp = when {
            insertion <= 0 -> points.first().timestamp
            insertion >= points.size -> points.last().timestamp
            timestamp - points[insertion - 1].timestamp <= points[insertion].timestamp - timestamp ->
                points[insertion - 1].timestamp
            else -> points[insertion].timestamp
        }
        return points.subList(points.lowerBound(nearestTimestamp), points.upperBound(nearestTimestamp))
            .minBy { it.id }
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
        val best = visible.asSequence()
            .filter { projection.hitsNode(it, x, y, showSystolic, showDiastolic, touchRadiusPx) }
            .minWithOrNull(compareBy<TrendPoint> {
                projection.nearestDistance(it, x, y, showSystolic, showDiastolic)
            }.thenBy { it.timestamp }.thenBy { it.id })
        return best ?: nearestPoint(visible, projection.timeAtX(x))
    }

    /** 脉搏为空的记录仍可通过时间位置选中，只是不参与节点命中。 */
    fun hitTestPulse(
        projection: ChartProjection,
        visible: List<TrendPoint>,
        x: Float,
        y: Float,
        touchRadiusPx: Float
    ): TrendPoint? {
        val nearestByTime = nearestPoint(visible, projection.timeAtX(x))
        val best = visible.asSequence().filter { point ->
            point.pulse != null && abs(x - projection.xOfTime(point.timestamp)) <= touchRadiusPx &&
                abs(y - projection.yOfValue(point.pulse)) <= touchRadiusPx
        }.minWithOrNull(compareBy<TrendPoint> {
            kotlin.math.hypot(x - projection.xOfTime(it.timestamp), y - projection.yOfValue(it.pulse!!))
        }.thenBy { it.timestamp }.thenBy { it.id })
        return best ?: nearestByTime
    }

    /** 每个像素桶保留脉搏极值。缺值不进采样，而由原始序列的段号阻止跨缺值连线。 */
    fun samplePulse(points: List<TrendPoint>, maxPoints: Int, selectedId: String? = null): List<TrendPoint> {
        val present = points.filter { it.pulse != null }
        if (present.size <= maxPoints || maxPoints < 4) return present
        val bucketCount = (maxPoints / 4).coerceAtLeast(1)
        val bucketSize = ceil(points.size / bucketCount.toDouble()).toInt().coerceAtLeast(1)
        val selected = linkedSetOf<Int>()
        var start = 0
        while (start < points.size) {
            val end = (start + bucketSize).coerceAtMost(points.size)
            val valid = (start until end).filter { points[it].pulse != null }
            if (valid.isNotEmpty()) {
                selected += valid.first()
                selected += valid.last()
                selected += valid.minBy { points[it].pulseMin!! }
                selected += valid.maxBy { points[it].pulseMax!! }
            }
            start = end
        }
        // 缺测边界和选中点独立保留，不能为了数量预算错误跨段连线。
        points.forEachIndexed { index, point ->
            if (point.pulse == null) {
                if (index > 0 && points[index - 1].pulse != null) selected += index - 1
                if (index < points.lastIndex && points[index + 1].pulse != null) selected += index + 1
            } else {
                if (point.id == selectedId) selected += index
                if (index > 0 && points[index - 1].pulse != null &&
                    point.timestamp - points[index - 1].timestamp > 2L * DAY_MILLIS) {
                    selected += index - 1
                    selected += index
                }
            }
        }
        return selected.sorted().map(points::get)
    }

    /** 原始血压序列先确定缺测段，采样产生的新时间间隔不会制造缺测。 */
    fun bloodPressureSegmentIds(points: List<TrendPoint>, gapMillis: Long): Map<String, Int> {
        var segment = 0
        return points.mapIndexed { index, point ->
            if (index > 0 && point.timestamp - points[index - 1].timestamp > gapMillis) segment++
            point.id to segment
        }.toMap()
    }

    /** null 与超过两天的缺测都另起一段；降采样后仍可据此断开折线。 */
    fun pulseSegmentIds(points: List<TrendPoint>, gapMillis: Long): Map<String, Int> {
        val segments = HashMap<String, Int>(points.size)
        var segment = 0
        var previous: TrendPoint? = null
        points.forEach { point ->
            if (point.pulse == null) {
                segment++
                previous = null
            } else {
                if (previous != null && point.timestamp - previous!!.timestamp > gapMillis) segment++
                segments[point.id] = segment
                previous = point
            }
        }
        return segments
    }

    fun stablePulseYAxis(previous: TrendYAxis?, visible: List<TrendPoint>): TrendYAxis? {
        val values = visible.flatMap { point -> listOfNotNull(point.pulseMin, point.pulseMax) }
        if (values.isEmpty()) return previous
        val min = values.min()
        val max = values.max()
        val target = pulseYAxis(min, max)
        if (previous == null || min < previous.min || max > previous.max) return target
        val slack = maxOf(20, previous.tickStep * 3)
        return if (target.min - previous.min >= slack || previous.max - target.max >= slack) {
            target
        } else previous
    }

    fun pulseYAxis(dataMin: Int, dataMax: Int): TrendYAxis {
        val spread = (dataMax - dataMin).coerceAtLeast(0)
        val padding = maxOf(5, spread / 5)
        val desiredSpan = spread + padding * 2
        val step = listOf(5, 10, 20, 25, 50, 100, 200, 500)
            .firstOrNull { desiredSpan <= it * 7 } ?: 1000
        val min = floor((dataMin - padding) / step.toDouble()).toInt() * step
        val max = ceil((dataMax + padding) / step.toDouble()).toInt() * step
        return TrendYAxis(min, maxOf(max, min + step), step)
    }

    /**
     * 绘制与连线共用的降采样。
     *
     * **只降低绘制密度，不删除任何真实数据**：每个桶都保留四个极值索引，
     * 首尾点一定保留，因此曲线形状与极值位置不变。视窗内点数小于
     * [maxPoints] 时原样返回，不做任何裁剪。
     */
    fun sampleShared(
        points: List<TrendPoint>,
        maxPoints: Int,
        selectedId: String? = null
    ): List<TrendPoint> {
        if (points.size <= maxPoints || maxPoints < 8) return points
        // 每桶首尾及 SYS/DIA 极值共六点；额外保留缺测断线的两侧和选中点。
        // 必须保留的边界可能超过预算，不能截断后半区极值以满足数量上限。
        val bucketCount = (maxPoints / 6).coerceAtLeast(1)
        val bucketSize = ceil(points.size / bucketCount.toDouble()).toInt().coerceAtLeast(1)
        val selected = linkedSetOf<Int>()
        for (start in points.indices step bucketSize) {
            val end = (start + bucketSize).coerceAtMost(points.size)
            val indices = start until end
            selected += start
            selected += end - 1
            selected += indices.minBy { points[it].systolicMin }
            selected += indices.maxBy { points[it].systolicMax }
            selected += indices.minBy { points[it].diastolicMin }
            selected += indices.maxBy { points[it].diastolicMax }
        }
        points.forEachIndexed { index, point ->
            if (point.id == selectedId) selected += index
            if (index > 0 && point.timestamp - points[index - 1].timestamp > 2L * DAY_MILLIS) {
                selected += index - 1
                selected += index
            }
        }
        return selected.sorted().map(points::get)
    }

    fun maxTickCount(plotWidthDp: Int): Int {
        return (plotWidthDp / 80).coerceIn(3, 7)
    }

    /** 选中节点最后独立覆盖绘制，不依赖采样是否包含它，也不改变折线数据。 */
    fun nodePoints(
        renderPoints: List<TrendPoint>,
        selected: TrendPoint?,
        showAllNodes: Boolean,
        viewportStart: Long,
        viewportEnd: Long
    ): List<TrendPoint> = buildList {
        if (showAllNodes) addAll(renderPoints.filter {
            it.id != selected?.id && it.timestamp in viewportStart..viewportEnd
        })
        selected?.takeIf { it.timestamp in viewportStart..viewportEnd }?.let(::add)
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
        val ratio = (timestamp - start).toDouble() / span.toDouble()
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
    fun stableYAxis(
        previous: TrendYAxis,
        visible: List<TrendPoint>,
        targetSystolic: Int? = null,
        targetDiastolic: Int? = null
    ): TrendYAxis {
        if (visible.isEmpty()) return previous
        val values = buildList {
            visible.forEach { point ->
                add(point.systolicMin.coerceIn(
                    TrendSeriesCalculator.CHART_SAFE_MIN,
                    TrendSeriesCalculator.CHART_SAFE_MAX
                ))
                add(point.systolicMax.coerceIn(TrendSeriesCalculator.CHART_SAFE_MIN, TrendSeriesCalculator.CHART_SAFE_MAX))
                add(point.diastolicMin.coerceIn(20, 200))
                add(point.diastolicMax.coerceIn(20, 200))
            }
            add(TrendSeriesCalculator.REFERENCE_DIASTOLIC)
            add(TrendSeriesCalculator.REFERENCE_SYSTOLIC)
            targetSystolic?.takeIf {
                it in TrendSeriesCalculator.CHART_SAFE_MIN..TrendSeriesCalculator.CHART_SAFE_MAX
            }?.let(::add)
            targetDiastolic?.takeIf { it in 20..200 }?.let(::add)
        }
        val dataMin = values.min()
        val dataMax = values.max()

        val target = buildYAxis(dataMin, dataMax)
        val mustExpand = dataMin < previous.min || dataMax > previous.max
        val shrunkEnough =
            (target.min - previous.min > Y_AXIS_STABLE_MMHG) ||
                (previous.max - target.max > Y_AXIS_STABLE_MMHG)
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

    /** 网格密度受实际绘图区高度与已测量字体约束，放大字体时仍保持标签间距。 */
    fun yAxisTickValues(
        axis: TrendYAxis,
        plotHeightPx: Float,
        labelHeightPx: Float,
        minimumGapPx: Float,
        dense: Boolean = false
    ): List<Int> {
        val span = (axis.max - axis.min).coerceAtLeast(1)
        val labelSpacing = (labelHeightPx + minimumGapPx).coerceAtLeast(1f)
        val maxIntervals = floor(plotHeightPx / labelSpacing).toInt().coerceAtLeast(1)
        val requiredStep = maxOf(if (dense) 5 else axis.tickStep, ceil(span.toDouble() / maxIntervals).toInt())
        val step = listOf(5, 10, 20, 25, 50, 100, 200, 500)
            .firstOrNull { it >= requiredStep } ?: requiredStep
        val ticks = (axis.min..axis.max step step).toMutableList()
        if (ticks.last() != axis.max) {
            val finalGapPx = (axis.max - ticks.last()).toFloat() / span * plotHeightPx
            if (finalGapPx < labelSpacing && ticks.size > 1) ticks.removeAt(ticks.lastIndex)
            if (plotHeightPx >= labelSpacing) ticks += axis.max
        }
        return ticks
    }

    fun timeTicks(
        startMillis: Long,
        endMillis: Long,
        zoneId: ZoneId,
        maxTicks: Int = 7,
        showTimeLabels: Boolean = false
    ): List<TrendTimeTick> {
        if (endMillis <= startMillis) return emptyList()
        val safeMaxTicks = maxTicks.coerceIn(2, 7)
        val start = Instant.ofEpochMilli(startMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endMillis).atZone(zoneId)
        val spanDays = ChronoUnit.HOURS.between(start, end).coerceAtLeast(1) / 24.0
        val ticks = when {
            spanDays <= 2.0 -> hourlyTicks(start, end, safeMaxTicks)
            spanDays <= 45.0 -> dailyTicks(start, end, safeMaxTicks)
            spanDays <= 730.0 -> monthlyTicks(start, end, safeMaxTicks)
            else -> yearlyTicks(start, end, safeMaxTicks)
        }
        if (!showTimeLabels || spanDays <= 2.0) return ticks
        val dateFormat = DateTimeFormatter.ofPattern(if (start.year == end.year) "MM-dd" else "yy-MM-dd")
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        return ticks.map { tick ->
            val time = Instant.ofEpochMilli(tick.timestamp).atZone(zoneId)
            tick.copy(primary = time.format(dateFormat), secondary = time.format(timeFormat))
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
