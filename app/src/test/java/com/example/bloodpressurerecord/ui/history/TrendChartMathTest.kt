package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import java.time.ZoneId
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

        viewport.reset(seriesStart = 5_000L, seriesEnd = 5_001L)
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
        viewport.reset(seriesStart = 0L, seriesEnd = 10_000L)
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
        assertEquals(2, TrendChartMath.maxTickCount(120))
        assertEquals(4, TrendChartMath.maxTickCount(400))
        assertEquals(6, TrendChartMath.maxTickCount(2_000))
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
