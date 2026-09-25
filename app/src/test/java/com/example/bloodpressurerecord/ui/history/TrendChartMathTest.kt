package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartMathTest {
    @Test
    fun xAxis_usesRealTimeDistanceInsteadOfOrdinalIndex() {
        val start = 0L
        val end = 10_000L

        val earlyX = TrendChartMath.xOfTime(1_000L, 0f, 100f, start, end)
        val lateX = TrendChartMath.xOfTime(9_000L, 0f, 100f, start, end)

        assertEquals(10f, earlyX, 0.001f)
        assertEquals(90f, lateX, 0.001f)
    }

    @Test
    fun visiblePoints_usesSortedTimeWindowBoundaries() {
        val points = (0 until 10).map { point(timestamp = it * 1_000L) }

        val visible = TrendChartMath.visiblePoints(points, 2_500L, 6_000L)

        assertEquals(listOf(3_000L, 4_000L, 5_000L, 6_000L), visible.map { it.timestamp })
    }

    @Test
    fun visiblePoints_keepsDuplicateTimestampsAtWindowBoundary() {
        val points = listOf(
            point(timestamp = 1_000L, id = "a"),
            point(timestamp = 2_000L, id = "b"),
            point(timestamp = 2_000L, id = "c"),
            point(timestamp = 3_000L, id = "d")
        )

        val visible = TrendChartMath.visiblePoints(points, 2_000L, 2_000L)

        assertEquals(listOf("b", "c"), visible.map { it.id })
    }

    @Test
    fun visiblePoints_sortsUnorderedInputBeforeBinarySearchAndRendering() {
        val points = listOf(
            point(timestamp = 3_000L),
            point(timestamp = 1_000L),
            point(timestamp = 2_000L)
        )

        val visible = TrendChartMath.visiblePoints(points, 1_000L, 3_000L)

        assertEquals(listOf(1_000L, 2_000L, 3_000L), visible.map { it.timestamp })
    }

    @Test
    fun sharedSampling_keepsOneAndTwoSparsePointsUnchanged() {
        val one = listOf(point(timestamp = 1_000L))
        val two = one + point(timestamp = 2_000L)

        assertEquals(one, TrendChartMath.sampleShared(one, maxPoints = 60))
        assertEquals(two, TrendChartMath.sampleShared(two, maxPoints = 60))
    }

    @Test
    fun sharedSampling_preservesFirstLastAndBoundedPointCount() {
        val points = (0 until 10_000).map { index ->
            point(
                timestamp = index.toLong(),
                systolic = 110 + index % 70,
                diastolic = 60 + index % 45
            )
        }

        val sampled = TrendChartMath.sampleShared(points, maxPoints = 600)

        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
        assertTrue(sampled.size <= 600)
        assertTrue(sampled.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun viewport_handlesSinglePointRangeWithoutInvalidBounds() {
        val viewport = TrendTimeViewportState()

        viewport.reset(defaultStart = 5_000L, defaultEnd = 5_001L)
        viewport.zoomBy(
            zoomChange = 2f,
            focusMillis = 5_000L,
            seriesStart = 5_000L,
            seriesEnd = 5_001L,
            minSpanMillis = TrendChartMath.minViewportSpan(TrendRange.DAYS_7)
        )

        assertEquals(5_000L, viewport.startMillis)
        assertEquals(5_001L, viewport.endMillis)
    }

    @Test
    fun viewport_panClampsAtBothSeriesBoundaries() {
        val viewport = TrendTimeViewportState()
        viewport.reset(defaultStart = 0L, defaultEnd = 10_000L)
        viewport.zoomBy(
            zoomChange = 2f,
            focusMillis = 5_000L,
            seriesStart = 0L,
            seriesEnd = 10_000L,
            minSpanMillis = 1_000L
        )

        viewport.panBy(deltaMillis = -100_000L, seriesStart = 0L, seriesEnd = 10_000L)
        assertEquals(0L, viewport.startMillis)
        viewport.panBy(deltaMillis = 100_000L, seriesStart = 0L, seriesEnd = 10_000L)
        assertEquals(10_000L, viewport.endMillis)
    }

    @Test
    fun ticks_areChronologicalAndInsideViewport() {
        val start = 1_700_000_000_000L
        val end = start + 30L * 24L * 60L * 60L * 1_000L

        val ticks = TrendChartMath.timeTicks(start, end, ZoneId.of("Asia/Taipei"))

        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.timestamp in start..end })
        assertTrue(ticks.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun tickCount_isDynamicAndBoundedByCanvasWidth() {
        assertEquals(3, TrendChartMath.maxTickCount(120))
        assertEquals(5, TrendChartMath.maxTickCount(400))
        assertEquals(7, TrendChartMath.maxTickCount(2_000))
    }

    @Test
    fun thirtyDayTicks_keepViewportStartAndEndVisible() {
        val zone = ZoneId.of("Asia/Taipei")
        val start = java.time.LocalDate.of(2026, 8, 23)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = java.time.LocalDate.of(2026, 9, 21)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val ticks = TrendChartMath.timeTicks(start, end, zone, maxTicks = 7)

        assertEquals("08-23", ticks.first().primary)
        assertEquals("09-21", ticks.last().primary)
    }

    @Test
    fun monthlyTicks_useCompactLabelsAndKeepEveryMonthWhenTheyFit() {
        val zone = ZoneId.of("Asia/Taipei")
        val start = java.time.LocalDate.of(2026, 5, 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = java.time.LocalDate.of(2026, 9, 21)
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val ticks = TrendChartMath.timeTicks(start, end, zone, maxTicks = 7)

        assertEquals(listOf("05月", "06月", "07月", "08月", "09月"), ticks.map { it.primary })
    }

    @Test
    fun tickCollisionFilter_keepsLabelsInsideBoundsAndSeparated() {
        val selected = TrendChartMath.nonOverlappingTickIndices(
            centers = listOf(0f, 25f, 50f, 75f, 100f),
            widths = listOf(30f, 30f, 30f, 30f, 30f),
            left = 0f,
            right = 100f,
            minimumGap = 4f
        )

        assertEquals(listOf(0, 2, 4), selected)
    }

    @Test
    fun tickCollisionFilter_keepsOnlyEndWhenBoundaryLabelsCannotFitTogether() {
        val selected = TrendChartMath.nonOverlappingTickIndices(
            centers = listOf(0f, 40f, 80f),
            widths = listOf(48f, 48f, 48f),
            left = 0f,
            right = 80f,
            minimumGap = 8f
        )

        assertEquals(listOf(2), selected)
    }

    /**
     * 最近 7 天只有 9/19–9/21 有记录时，默认视野应聚焦这三天，
     * 而不是把数据留在左侧、右侧空出一大片。
     */
    @Test
    fun defaultViewportFocusesSparseThreeDaySampleInsideSevenDayWindow() {
        val zone = ZoneId.of("Asia/Taipei")
        val windowStart = LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2026, 9, 25).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val points = listOf(
            point(timestamp = LocalDate.of(2026, 9, 19).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()),
            point(timestamp = LocalDate.of(2026, 9, 20).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()),
            point(timestamp = LocalDate.of(2026, 9, 21).atTime(20, 0).atZone(zone).toInstant().toEpochMilli())
        )

        val (start, end) = TrendChartMath.defaultViewport(
            points = points,
            range = TrendRange.DAYS_7,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )

        val spanDays = (end - start) / (24.0 * 60 * 60 * 1000)
        val minSpanDays = TrendChartMath.minDefaultViewportSpan(TrendRange.DAYS_7) /
            (24.0 * 60 * 60 * 1000)
        // 聚焦到样本附近（最多 5 天），又不是把三天压成一条线。
        assertTrue("默认视野跨度应小于 5 天，实际 $spanDays", spanDays < 5.0)
        assertTrue(
            "默认视野不应小于可辨识窗口 $minSpanDays 天，实际 $spanDays",
            spanDays >= minSpanDays
        )
        assertTrue(start <= points.first().timestamp)
        assertTrue(end >= points.last().timestamp)
        assertTrue(start >= windowStart)
        assertTrue(end <= windowEnd)
        // 左右 padding 对称且有限，数据不会被挤到某一侧。
        val leftPadding = points.first().timestamp - start
        val rightPadding = end - points.last().timestamp
        assertTrue(abs(leftPadding - rightPadding) <= 60_000L)
        assertTrue(leftPadding in 1L..(24L * 60L * 60L * 1000L))
    }

    @Test
    fun defaultViewportKeepsFullWindowWhenSampleCoversWholeMonth() {
        val zone = ZoneId.of("Asia/Taipei")
        val windowStart = LocalDate.of(2026, 8, 23).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2026, 9, 21).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val points = (0L until 30L).map { day ->
            point(
                timestamp = windowStart + day * 24L * 60L * 60L * 1000L + 8L * 60L * 60L * 1000L
            )
        }

        val (start, end) = TrendChartMath.defaultViewport(
            points = points,
            range = TrendRange.DAYS_30,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )

        assertEquals(windowStart, start)
        assertEquals(windowEnd, end)
    }

    @Test
    fun defaultViewportHandlesEmptyAndSinglePointWithoutInvalidBounds() {
        val zone = ZoneId.of("Asia/Taipei")
        val windowStart = LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = LocalDate.of(2026, 9, 25).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        val empty = TrendChartMath.defaultViewport(
            points = emptyList(),
            range = TrendRange.DAYS_7,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )
        assertEquals(windowStart, empty.first)
        assertEquals(windowEnd, empty.second)

        val singleMillis = LocalDate.of(2026, 9, 24).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val single = TrendChartMath.defaultViewport(
            points = listOf(point(timestamp = singleMillis)),
            range = TrendRange.DAYS_7,
            windowStart = windowStart,
            windowEndInclusive = windowEnd
        )
        assertTrue(single.first < single.second)
        assertTrue(single.first <= singleMillis)
        assertTrue(single.second >= singleMillis)
        assertTrue(single.first >= windowStart)
        assertTrue(single.second <= windowEnd)
    }

    /** 跨月、跨年的聚焦视野仍能给出可读刻度。 */
    @Test
    fun focusedViewportProducesReadableTicksAcrossMonthAndYearBoundaries() {
        val zone = ZoneId.of("Asia/Taipei")
        val crossMonthStart = LocalDate.of(2026, 8, 30).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val crossMonthEnd = LocalDate.of(2026, 9, 3).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val crossMonthTicks = TrendChartMath.timeTicks(crossMonthStart, crossMonthEnd, zone, maxTicks = 7)

        assertTrue(crossMonthTicks.size >= 2)
        assertTrue(crossMonthTicks.all { it.timestamp in crossMonthStart..crossMonthEnd })
        assertTrue(crossMonthTicks.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })

        val crossYearStart = LocalDate.of(2025, 12, 30).atStartOfDay(zone).toInstant().toEpochMilli()
        val crossYearEnd = LocalDate.of(2026, 1, 3).atStartOfDay(zone).toInstant().toEpochMilli()
        val crossYearTicks = TrendChartMath.timeTicks(crossYearStart, crossYearEnd, zone, maxTicks = 7)

        assertTrue(crossYearTicks.size >= 2)
        assertTrue(crossYearTicks.all { it.timestamp in crossYearStart..crossYearEnd })
    }

    @Test
    fun timeAtXAndXOfTimeKeepRealTimeProportionsInsideFocusedViewport() {
        val start = 1_700_000_000_000L
        val end = start + 3L * 24L * 60L * 60L * 1000L
        val middle = start + (end - start) / 2L

        val middleX = TrendChartMath.xOfTime(middle, 0f, 200f, start, end)

        assertEquals(100f, middleX, 0.01f)
        assertEquals(middle.toFloat(), TrendChartMath.timeAtX(middleX, 0f, 200f, start, end).toFloat(), 1f)
    }

    private fun point(
        timestamp: Long,
        id: String = "p-$timestamp",
        systolic: Int = 120,
        diastolic: Int = 80
    ): TrendPoint {
        return TrendPoint(
            id = id,
            timestamp = timestamp,
            intervalStart = timestamp,
            intervalEndExclusive = timestamp + 1,
            systolic = systolic,
            diastolic = diastolic,
            pulse = null,
            category = "NORMAL",
            containsHighRiskReading = false,
            recordCount = 1,
            aggregation = TrendAggregation.RAW
        )
    }
}
