package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.model.TrendSeries
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendAggregationCalculatorTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = millis("2026-10-03T12:00")

    @Test
    fun rawPathKeepsExistingSeriesAndDefaultPointFields() {
        val records = listOf(record("a", "2026-10-02T08:00", 128, 82))

        val series = build(records, TrendAggregation.RAW)

        assertEquals(TrendSeriesCalculator.build(records, TrendRange.DAYS_7, now, zone), series)
        val point = series.points.single()
        assertEquals(point.systolic, point.systolicMin)
        assertEquals(point.systolic, point.systolicMax)
        assertEquals(point.diastolic, point.diastolicMin)
        assertEquals(point.diastolic, point.diastolicMax)
        assertEquals(1, point.pulseRecordCount)
        assertEquals(listOf("a"), point.sourceRecordIds)
    }

    @Test
    fun naturalDaysSplitAtLocalMidnightAndKeepEmptyDaysEmpty() {
        val records = listOf(
            record("after", "2026-10-02T00:00", 130, 85),
            record("before", "2026-10-01T23:59", 120, 80),
            record("earlier", "2026-09-29T08:00", 125, 82)
        )

        val points = build(records).points

        assertEquals(listOf("day:2026-09-29", "day:2026-10-01", "day:2026-10-02"), points.map { it.id })
        assertEquals(millis("2026-10-01T00:00"), points[1].intervalStart)
        assertEquals(millis("2026-10-02T00:00"), points[1].intervalEndExclusive)
        assertEquals(points[1].intervalEndExclusive, points[2].intervalStart)
        assertEquals(listOf("before"), points[1].sourceRecordIds)
        assertEquals(listOf("after"), points[2].sourceRecordIds)
    }

    @Test
    fun dailyRangeRetainsExtremesAndSourceRecordsWhileMeanUsesEqualSessionWeights() {
        val records = listOf(
            record("late", "2026-10-02T20:00", 300, 200, 110).copy(containsHighRiskReading = true),
            record("early", "2026-10-02T08:00", 100, 60, 50),
            record("middle", "2026-10-02T14:00", 101, 61, null)
        )

        val series = build(records)
        val point = series.points.single()

        assertEquals(167, point.systolic)
        assertEquals(107, point.diastolic)
        assertEquals(100, point.systolicMin)
        assertEquals(300, point.systolicMax)
        assertEquals(60, point.diastolicMin)
        assertEquals(200, point.diastolicMax)
        assertEquals(80, point.pulse)
        assertEquals(50, point.pulseMin)
        assertEquals(110, point.pulseMax)
        assertEquals(2, point.pulseRecordCount)
        assertEquals(3, point.recordCount)
        assertEquals(listOf("early", "middle", "late"), point.sourceRecordIds)
        assertEquals(millis("2026-10-02T14:00"), point.timestamp)
        assertTrue(point.containsHighRiskReading)
        assertTrue(series.yAxis.max >= 300)
        assertTrue(series.yAxis.min <= 60)
    }

    @Test
    fun periodMeansUseAllSessionsInsteadOfEqualDailyMeans() {
        val series = build(
            listOf(
                record("a", "2026-10-01T08:00", 100, 60, 60),
                record("b", "2026-10-01T12:00", 100, 60, null),
                record("c", "2026-10-01T20:00", 100, 60, null),
                record("d", "2026-10-02T08:00", 160, 100, 100)
            )
        )

        assertEquals(2, series.points.size)
        assertEquals(4, series.rawRecordCount)
        assertEquals(115, series.averageSystolic)
        assertEquals(70, series.averageDiastolic)
        assertEquals(80, series.averagePulse)
    }

    @Test
    fun rangeFilteringClipsHalfOpenBucketsAndIncludesSampleAtNow() {
        val rangeStart = millis("2026-09-27T00:00")
        val records = listOf(
            record("outside", "2026-09-26T23:59", 110, 70),
            record("first", "2026-09-27T00:00", 120, 80),
            record("now", "2026-10-03T12:00", 130, 85),
            record("future", "2026-10-03T12:01", 140, 90)
        )

        val series = build(records)

        assertEquals(2, series.rawRecordCount)
        assertEquals(rangeStart, series.points.first().intervalStart)
        assertEquals(now + 1L, series.points.last().intervalEndExclusive)
        assertEquals(now, series.points.last().timestamp)
        assertTrue(series.points.all { it.timestamp >= it.intervalStart && it.timestamp < it.intervalEndExclusive })

        val all = build(listOf(records[2]), range = TrendRange.ALL)
        assertEquals(now, all.points.single().intervalStart)
        assertEquals(now + 1L, all.points.single().intervalEndExclusive)
        assertTrue(all.points.single().timestamp in all.rangeStart..all.rangeEnd)
    }

    @Test
    fun singleSampleAndMissingPulseDoNotCreateExtraValues() {
        val series = build(listOf(record("only", "2026-10-03T08:00", 118, 76, null)))
        val point = series.points.single()

        assertEquals(1, point.recordCount)
        assertEquals(118, point.systolicMin)
        assertEquals(118, point.systolicMax)
        assertEquals(76, point.diastolicMin)
        assertEquals(76, point.diastolicMax)
        assertEquals(millis("2026-10-03T08:00"), point.timestamp)
        assertNull(point.pulse)
        assertNull(point.pulseMin)
        assertNull(point.pulseMax)
        assertEquals(0, point.pulseRecordCount)
        assertNull(series.averagePulse)

        val empty = build(emptyList())
        assertTrue(empty.points.isEmpty())
        assertEquals(0, empty.rawRecordCount)
        assertNull(empty.averageSystolic)
        assertNull(empty.firstMeasuredAt)
        assertTrue(empty.yAxis.max > empty.yAxis.min)
    }

    @Test
    fun legacyDailyKeepsItsAverageAggregationType() {
        val series = build(
            listOf(
                record("a", "2026-10-02T08:00", 120, 80),
                record("b", "2026-10-02T20:00", 130, 85)
            ),
            aggregation = TrendAggregation.DAILY
        )

        assertEquals(TrendAggregation.DAILY, series.aggregation)
        assertEquals(TrendAggregation.DAILY, series.points.single().aggregation)
        assertEquals(125, series.points.single().systolic)
        assertEquals(83, series.points.single().diastolic)
    }

    private fun build(
        records: List<TrendRecord>,
        aggregation: TrendAggregation = TrendAggregation.DAILY_RANGE,
        range: TrendRange = TrendRange.DAYS_7
    ): TrendSeries = TrendAggregationCalculator.build(records, range, now, zone, aggregation)

    private fun record(
        id: String,
        time: String,
        systolic: Int,
        diastolic: Int,
        pulse: Int? = 70
    ): TrendRecord = TrendRecord(id, millis(time), systolic, diastolic, pulse, "NORMAL")

    private fun millis(time: String): Long = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
}
