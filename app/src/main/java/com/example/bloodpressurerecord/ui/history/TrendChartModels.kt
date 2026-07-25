package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

data class TrendTimeTick(
    val timestamp: Long,
    val primary: String,
    val secondary: String? = null
)

@Stable
class TrendTimeViewportState {
    var startMillis by mutableLongStateOf(0L)
        private set

    var endMillis by mutableLongStateOf(1L)
        private set

    val spanMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(1L)

    fun reset(seriesStart: Long, seriesEnd: Long) {
        startMillis = seriesStart
        endMillis = seriesEnd.coerceAtLeast(seriesStart + 1L)
    }

    fun zoomBy(
        zoomChange: Float,
        focusMillis: Long,
        seriesStart: Long,
        seriesEnd: Long,
        minSpanMillis: Long
    ) {
        val fullSpan = (seriesEnd - seriesStart).coerceAtLeast(1L)
        if (fullSpan <= minSpanMillis) {
            reset(seriesStart, seriesEnd)
            return
        }
        val oldSpan = spanMillis
        val newSpan = (oldSpan / zoomChange.coerceIn(0.2f, 5f))
            .roundToLong()
            .coerceIn(minSpanMillis.coerceAtMost(fullSpan), fullSpan)
        val focusRatio = ((focusMillis - startMillis).toDouble() / oldSpan.toDouble())
            .coerceIn(0.0, 1.0)
        val proposedStart = focusMillis - (newSpan * focusRatio).roundToLong()
        setWindow(proposedStart, proposedStart + newSpan, seriesStart, seriesEnd)
    }

    fun panBy(deltaMillis: Long, seriesStart: Long, seriesEnd: Long) {
        setWindow(startMillis + deltaMillis, endMillis + deltaMillis, seriesStart, seriesEnd)
    }

    private fun setWindow(
        proposedStart: Long,
        proposedEnd: Long,
        seriesStart: Long,
        seriesEnd: Long
    ) {
        val fullSpan = (seriesEnd - seriesStart).coerceAtLeast(1L)
        val requestedSpan = (proposedEnd - proposedStart).coerceIn(1L, fullSpan)
        val clampedStart = proposedStart.coerceIn(seriesStart, seriesEnd - requestedSpan)
        startMillis = clampedStart
        endMillis = clampedStart + requestedSpan
    }
}

object TrendChartMath {
    private const val HOUR_MILLIS = 60L * 60L * 1_000L
    private const val DAY_MILLIS = 24L * HOUR_MILLIS

    fun minViewportSpan(range: TrendRange): Long = when (range) {
        TrendRange.DAYS_7 -> HOUR_MILLIS
        TrendRange.DAYS_30 -> 6L * HOUR_MILLIS
        TrendRange.ALL -> 7L * DAY_MILLIS
    }

    fun visiblePoints(
        points: List<TrendPoint>,
        startInclusive: Long,
        endInclusive: Long
    ): List<TrendPoint> {
        if (points.isEmpty()) return emptyList()
        val first = points.lowerBound(startInclusive)
        val afterLast = points.upperBound(endInclusive)
        if (first >= afterLast) return emptyList()
        return points.subList(first, afterLast)
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

    fun timeAtX(x: Float, left: Float, right: Float, start: Long, end: Long): Long {
        val ratio = ((x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return start + ((end - start) * ratio).roundToLong()
    }

    fun xOfTime(timestamp: Long, left: Float, right: Float, start: Long, end: Long): Float {
        val span = (end - start).coerceAtLeast(1L)
        val ratio = ((timestamp - start).toDouble() / span.toDouble()).coerceIn(-0.05, 1.05)
        return left + (ratio * (right - left)).toFloat()
    }

    fun timeTicks(
        startMillis: Long,
        endMillis: Long,
        zoneId: ZoneId,
        maxTicks: Int = 6
    ): List<TrendTimeTick> {
        if (endMillis <= startMillis) return emptyList()
        val start = Instant.ofEpochMilli(startMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endMillis).atZone(zoneId)
        val spanDays = ChronoUnit.HOURS.between(start, end).coerceAtLeast(1) / 24.0
        return when {
            spanDays <= 2.0 -> hourlyTicks(start, end, maxTicks)
            spanDays <= 45.0 -> dailyTicks(start, end, maxTicks)
            spanDays <= 730.0 -> monthlyTicks(start, end, maxTicks)
            else -> yearlyTicks(start, end, maxTicks)
        }
    }

    private fun hourlyTicks(start: ZonedDateTime, end: ZonedDateTime, maxTicks: Int): List<TrendTimeTick> {
        val totalHours = ChronoUnit.HOURS.between(start, end).coerceAtLeast(1)
        val step = max(1L, ceil(totalHours / maxTicks.toDouble()).toLong())
        var cursor = start.truncatedTo(ChronoUnit.HOURS).plusHours(step)
        return buildList {
            while (!cursor.isAfter(end)) {
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
    }

    private fun dailyTicks(start: ZonedDateTime, end: ZonedDateTime, maxTicks: Int): List<TrendTimeTick> {
        val totalDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).coerceAtLeast(1)
        val step = max(1L, ceil(totalDays / maxTicks.toDouble()).toLong())
        var cursor = start.toLocalDate().plusDays(step).atStartOfDay(start.zone)
        return buildList {
            while (!cursor.isAfter(end)) {
                add(
                    TrendTimeTick(
                        timestamp = cursor.toInstant().toEpochMilli(),
                        primary = cursor.format(DateTimeFormatter.ofPattern("MM-dd"))
                    )
                )
                cursor = cursor.plusDays(step)
            }
        }
    }

    private fun monthlyTicks(start: ZonedDateTime, end: ZonedDateTime, maxTicks: Int): List<TrendTimeTick> {
        val firstMonth = start.withDayOfMonth(1).toLocalDate()
        val lastMonth = end.withDayOfMonth(1).toLocalDate()
        val months = ChronoUnit.MONTHS.between(firstMonth, lastMonth).coerceAtLeast(1)
        val step = max(1L, ceil(months / maxTicks.toDouble()).toLong())
        var cursor = firstMonth.plusMonths(step).atStartOfDay(start.zone)
        return buildList {
            while (!cursor.isAfter(end)) {
                add(
                    TrendTimeTick(
                        timestamp = cursor.toInstant().toEpochMilli(),
                        primary = cursor.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    )
                )
                cursor = cursor.plusMonths(step)
            }
        }
    }

    private fun yearlyTicks(start: ZonedDateTime, end: ZonedDateTime, maxTicks: Int): List<TrendTimeTick> {
        val startYear = start.year
        val years = (end.year - startYear).coerceAtLeast(1)
        val step = max(1, ceil(years / maxTicks.toDouble()).toInt())
        var year = startYear + step
        return buildList {
            while (year <= end.year) {
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
