package com.example.bloodpressurerecord.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartMathTest {
    @Test
    fun yRange_usesVisibleValuesWithPadding() {
        val points = listOf(
            point(timestamp = 1L, systolic = 128, diastolic = 82),
            point(timestamp = 2L, systolic = 132, diastolic = 84)
        )

        val range = TrendChartMath.yRange(points, showSystolic = true, showDiastolic = true)

        assertTrue(range.min <= 74)
        assertTrue(range.max >= 140)
        assertTrue(range.max - range.min < 90)
    }

    @Test
    fun axisLabels_singleDayShowsEveryVisiblePointWithTimeFirst() {
        val points = (0 until 4).map { index ->
            point(
                timestamp = index.toLong(),
                date = "04-23",
                time = "08:0$index"
            )
        }

        val labels = TrendChartMath.axisLabels(points, visibleStart = 0f, visibleEnd = 3f, singleDayMode = true)

        assertEquals(4, labels.size)
        assertEquals("08:00", labels.first().primary)
        assertEquals("04-23", labels.first().secondary)
    }

    @Test
    fun axisLabels_overviewSamplesDenseData() {
        val points = (0 until 40).map { index ->
            point(timestamp = index.toLong(), date = "04-${(index % 30) + 1}", time = "08:00")
        }

        val labels = TrendChartMath.axisLabels(points, visibleStart = 0f, visibleEnd = 39f, singleDayMode = false)

        assertTrue(labels.size < points.size)
        assertEquals(0, labels.first().index)
        assertEquals(39, labels.last().index)
    }

    private fun point(
        timestamp: Long,
        systolic: Int = 120,
        diastolic: Int = 80,
        date: String = "04-23",
        time: String = "08:00"
    ): TrendChartPoint {
        return TrendChartPoint(
            recordId = "r-$timestamp",
            timestamp = timestamp,
            systolic = systolic,
            diastolic = diastolic,
            pulse = null,
            level = "NORMAL",
            labelDate = date,
            labelTime = time
        )
    }
}
