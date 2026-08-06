package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class TrendSeriesCalculatorTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = millis("2026-07-23", 12)

    @Test
    fun sevenDays_preservesEveryMeasurementAndRealTimestamp() {
        val records = listOf(
            record("a", millis("2026-07-20", 7), 120, 80),
            record("b", millis("2026-07-20", 21), 130, 85),
            record("c", millis("2026-07-23", 8), 125, 82)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_7, now, zone)

        assertEquals(3, series.points.size)
        assertTrue(series.points.all { it.aggregation == TrendAggregation.RAW })
        assertEquals(records.map { it.measuredAt }, series.points.map { it.timestamp })
    }

    @Test
    fun allRange_aggregatesByLocalDateAndKeepsRecordCount() {
        val records = listOf(
            record("a", millis("2026-07-20", 7), 120, 80),
            record("b", millis("2026-07-20", 21), 130, 90),
            record("c", millis("2026-07-21", 8), 140, 88)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.ALL, now, zone)

        assertEquals(2, series.points.size)
        assertEquals(2, series.points.first().recordCount)
        assertEquals(125, series.points.first().systolic)
        assertEquals(85, series.points.first().diastolic)
        assertTrue(series.points.all { it.aggregation == TrendAggregation.DAILY })
    }

    @Test
    fun allRange_groupsByDeviceTimezoneAcrossUtcDateBoundary() {
        val newYork = ZoneId.of("America/New_York")
        val records = listOf(
            record("late", Instant.parse("2026-07-21T03:30:00Z").toEpochMilli(), 120, 80),
            record("early", Instant.parse("2026-07-21T04:30:00Z").toEpochMilli(), 140, 90)
        )

        val series = TrendSeriesCalculator.build(
            records = records,
            range = TrendRange.ALL,
            nowMillis = Instant.parse("2026-07-22T00:00:00Z").toEpochMilli(),
            zoneId = newYork
        )

        assertEquals(2, series.points.size)
        assertEquals(listOf(1, 1), series.points.map { it.recordCount })
    }

    @Test
    fun rangeFiltering_excludesOldAndFutureRecords() {
        val records = listOf(
            record("old", millis("2026-07-01", 8), 120, 80),
            record("current", millis("2026-07-22", 8), 125, 82),
            record("future", millis("2026-07-24", 8), 130, 85)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_7, now, zone)

        assertEquals(listOf("current"), series.points.map { it.id })
    }

    @Test
    fun outlierDoesNotExpandYAxisBeyondChartSafetyEnvelope() {
        val records = listOf(
            record("normal", millis("2026-07-22", 8), 120, 80),
            record("outlier", millis("2026-07-23", 8), 999, 500)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_7, now, zone)

        assertTrue(series.yAxis.max <= 320)
        assertTrue(series.yAxis.min >= 20)
    }

    @Test
    fun yAxis_contains_high_systolic_points_and_targets_without_expanding_for_invalid_values() {
        val values = listOf(261, 280, 300)
        val points = values.mapIndexed { index, systolic ->
            record("high-$systolic", millis("2026-07-${20 + index}", 8), systolic, 80)
        } + record("invalid", millis("2026-07-23", 9), 999, 500)

        val axis = TrendSeriesCalculator.calculateYAxis(
            points = points.map { it.toTrendPoint() },
            targetSystolic = 290,
            targetDiastolic = 190
        )

        assertTrue(axis.min <= 80)
        assertTrue(axis.max >= 300)
        assertTrue(axis.max <= 320)
        assertTrue(290 in axis.min..axis.max)
        assertTrue(190 in axis.min..axis.max)
    }

    @Test
    fun tenThousandRecordsBecomeAtMostOnePointPerDayInAllRange() {
        val start = LocalDate.of(2000, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val records = (0 until 10_000).map { index ->
            TrendRecord(
                id = index.toString(),
                measuredAt = start + index * 4L * 60L * 60L * 1_000L,
                systolic = 110 + index % 50,
                diastolic = 65 + index % 30,
                pulse = 60 + index % 40,
                category = "NORMAL"
            )
        }
        val farFuture = records.last().measuredAt + 1L

        lateinit var series: com.example.bloodpressurerecord.domain.model.TrendSeries
        val elapsedMillis = measureTimeMillis {
            series = TrendSeriesCalculator.build(records, TrendRange.ALL, farFuture, zone)
        }

        assertEquals(10_000, series.rawRecordCount)
        assertTrue(series.points.size <= 1_668)
        assertTrue("10k aggregation took ${elapsedMillis}ms", elapsedMillis < 1_500)
    }

    private fun record(id: String, measuredAt: Long, systolic: Int, diastolic: Int): TrendRecord {
        return TrendRecord(id, measuredAt, systolic, diastolic, 70, "NORMAL")
    }

    private fun TrendRecord.toTrendPoint() = com.example.bloodpressurerecord.domain.model.TrendPoint(
        id = id,
        timestamp = measuredAt,
        intervalStart = measuredAt,
        intervalEndExclusive = measuredAt + 1,
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
        category = category,
        containsHighRiskReading = containsHighRiskReading,
        recordCount = 1,
        aggregation = TrendAggregation.RAW
    )

    private fun millis(date: String, hour: Int): Long {
        return LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    }
}
