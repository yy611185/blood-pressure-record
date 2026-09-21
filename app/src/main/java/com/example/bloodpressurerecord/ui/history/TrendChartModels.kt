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
        val sortedPoints = if (points.zipWithNext().all { (a, b) ->
                a.timestamp <= b.timestamp
            }
        ) {
            points
        } else {
            points.sortedWith(compareBy<TrendPoint> { it.timestamp }.thenBy { it.id })
        }
        val first = sortedPoints.lowerBound(startInclusive)
        val afterLast = sortedPoints.upperBound(endInclusive)
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
        val step = max(1L, ceil(totalHours / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong())
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
        val step = if (totalDays <= 7L) {
            1L
        } else {
            max(1L, ceil(totalDays / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong())
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
        val step = max(1L, ceil(months / (maxTicks - 1).coerceAtLeast(1).toDouble()).toLong())
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
        val step = max(1, ceil(years / (maxTicks - 1).coerceAtLeast(1).toDouble()).toInt())
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
