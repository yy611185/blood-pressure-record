package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class TrendChartPoint(
    val recordId: String,
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val level: String,
    val labelDate: String,
    val labelTime: String
)

data class TrendAxisLabel(
    val index: Int,
    val primary: String,
    val secondary: String? = null
)

data class TrendChartYRange(
    val min: Int,
    val max: Int
)

@Stable
class TrendChartViewportState(
    initialCenterIndex: Float = 0f,
    initialVisibleSpan: Float = 1f
) {
    var centerIndex by mutableFloatStateOf(initialCenterIndex)
        private set

    var visibleSpan by mutableFloatStateOf(initialVisibleSpan)
        private set

    fun reset(pointCount: Int, defaultVisibleCount: Int) {
        val maxIndex = (pointCount - 1).coerceAtLeast(0).toFloat()
        val span = TrendChartMath.defaultVisibleSpan(pointCount, defaultVisibleCount)
        visibleSpan = span
        centerIndex = (maxIndex - span / 2f).coerceIn(span / 2f, maxIndex - span / 2f)
    }

    fun zoomBy(zoomChange: Float, focusIndex: Float, pointCount: Int) {
        if (pointCount <= 1) return
        val maxSpan = (pointCount - 1).toFloat()
        val oldSpan = visibleSpan.coerceIn(MIN_VISIBLE_SPAN, maxSpan)
        val newSpan = (oldSpan / zoomChange.coerceAtLeast(0.2f)).coerceIn(MIN_VISIBLE_SPAN, maxSpan)
        val start = centerIndex - oldSpan / 2f
        val focusRatio = ((focusIndex - start) / oldSpan).coerceIn(0f, 1f)
        val nextStart = (focusIndex - newSpan * focusRatio).coerceIn(0f, maxSpan - newSpan)
        visibleSpan = newSpan
        centerIndex = nextStart + newSpan / 2f
    }

    fun panBy(deltaIndex: Float, pointCount: Int) {
        if (pointCount <= 1) return
        val maxIndex = (pointCount - 1).toFloat()
        val half = visibleSpan / 2f
        centerIndex = (centerIndex + deltaIndex).coerceIn(half, maxIndex - half)
    }

    fun visibleStart(): Float = centerIndex - visibleSpan / 2f

    fun visibleEnd(): Float = centerIndex + visibleSpan / 2f

    companion object {
        const val MIN_VISIBLE_SPAN = 1f
    }
}

object TrendChartMath {
    fun defaultVisibleSpan(pointCount: Int, defaultVisibleCount: Int): Float {
        if (pointCount <= 1) return 1f
        return (min(pointCount, defaultVisibleCount).coerceAtLeast(2) - 1).toFloat()
    }

    fun visiblePoints(points: List<TrendChartPoint>, start: Float, end: Float): List<IndexedValue<TrendChartPoint>> {
        if (points.isEmpty()) return emptyList()
        val first = floor(start).toInt().coerceAtLeast(0)
        val last = ceil(end).toInt().coerceAtMost(points.lastIndex)
        return (first..last).map { IndexedValue(it, points[it]) }
    }

    fun yRange(
        visiblePoints: List<TrendChartPoint>,
        showSystolic: Boolean,
        showDiastolic: Boolean
    ): TrendChartYRange {
        val values = buildList {
            visiblePoints.forEach { point ->
                if (showSystolic) add(point.systolic)
                if (showDiastolic) add(point.diastolic)
            }
        }
        if (values.isEmpty()) return TrendChartYRange(60, 160)
        val low = values.min()
        val high = values.max()
        val rawRange = (high - low).coerceAtLeast(12)
        val padding = max(8, (rawRange * 0.2f).toInt())
        return TrendChartYRange(
            min = (low - padding).coerceAtLeast(40),
            max = high + padding
        )
    }

    fun axisLabels(
        points: List<TrendChartPoint>,
        visibleStart: Float,
        visibleEnd: Float,
        singleDayMode: Boolean
    ): List<TrendAxisLabel> {
        val visible = visiblePoints(points, visibleStart, visibleEnd)
        if (visible.isEmpty()) return emptyList()
        val step = when {
            singleDayMode -> 1
            visible.size <= 8 -> 1
            visible.size <= 16 -> 2
            visible.size <= 32 -> 4
            else -> max(1, ceil(visible.size / 7f).toInt())
        }
        return visible.mapIndexedNotNull { order, indexed ->
            val point = indexed.value
            val shouldShow = order == 0 || order == visible.lastIndex || order % step == 0
            if (!shouldShow) {
                null
            } else if (singleDayMode) {
                TrendAxisLabel(index = indexed.index, primary = point.labelTime, secondary = point.labelDate)
            } else {
                TrendAxisLabel(index = indexed.index, primary = point.labelDate, secondary = point.labelTime)
            }
        }
    }

    fun isSingleDayMode(points: List<TrendChartPoint>, visibleStart: Float, visibleEnd: Float): Boolean {
        val visible = visiblePoints(points, visibleStart, visibleEnd)
        if (visible.size < 2) return false
        val days = visible.map { it.value.localDate() }.toSet()
        return days.size == 1 || (visibleEnd - visibleStart) <= 2.2f
    }

    fun targetIndexFromX(x: Float, left: Float, right: Float, visibleStart: Float, visibleEnd: Float): Float {
        val ratio = ((x - left) / (right - left)).coerceIn(0f, 1f)
        return visibleStart + ratio * (visibleEnd - visibleStart)
    }
}

fun TrendChartPoint.localDate(): LocalDate {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
}

fun trendChartPointFromSession(session: com.example.bloodpressurerecord.data.repository.SessionRecord): TrendChartPoint {
    val zoned = Instant.ofEpochMilli(session.measuredAt).atZone(ZoneId.systemDefault())
    return TrendChartPoint(
        recordId = session.id,
        timestamp = session.measuredAt,
        systolic = session.avgSystolic,
        diastolic = session.avgDiastolic,
        pulse = session.avgPulse,
        level = session.category,
        labelDate = zoned.format(DateTimeFormatter.ofPattern("MM-dd")),
        labelTime = zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
    )
}
