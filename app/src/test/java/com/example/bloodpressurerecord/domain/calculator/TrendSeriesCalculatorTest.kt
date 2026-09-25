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
    fun thirtyDays_aggregatesByLocalDateInsteadOfDrawingEveryMeasurement() {
        val records = listOf(
            record("d1-a", millis("2026-07-10", 7), 120, 80),
            record("d1-b", millis("2026-07-10", 21), 130, 90),
            record("d2-a", millis("2026-07-11", 8), 140, 88),
            record("d3-a", millis("2026-07-12", 8), 126, 84),
            record("d3-b", millis("2026-07-12", 20), 128, 86)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_30, now, zone)

        assertEquals(5, series.rawRecordCount)
        assertEquals(3, series.points.size)
        assertTrue(series.points.all { it.aggregation == TrendAggregation.DAILY })
        assertEquals(listOf(2, 1, 2), series.points.map { it.recordCount })
        assertEquals(125, series.points.first().systolic)
        assertEquals(85, series.points.first().diastolic)
        assertEquals("day:2026-07-10", series.points.first().id)
        // 每日节点保留当天半开区间，点击后可回查当天全部原始记录。
        assertEquals(
            millis("2026-07-11", 0) - millis("2026-07-10", 0),
            series.points.first().intervalEndExclusive - series.points.first().intervalStart
        )
    }

    @Test
    fun aggregationFollowsRangeGranularity() {
        assertEquals(
            TrendAggregation.RAW,
            TrendSeriesCalculator.aggregationFor(TrendRange.DAYS_7)
        )
        assertEquals(
            TrendAggregation.DAILY,
            TrendSeriesCalculator.aggregationFor(TrendRange.DAYS_30)
        )
        assertEquals(
            TrendAggregation.DAILY,
            TrendSeriesCalculator.aggregationFor(TrendRange.ALL)
        )
    }

    @Test
    fun seriesReportsRealSampleWindowAndWindowStartSeparately() {
        val records = listOf(
            record("a", millis("2026-07-10", 7), 120, 80),
            record("b", millis("2026-07-23", 8), 130, 85)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_30, now, zone)

        assertEquals(millis("2026-06-24", 0), series.windowStart)
        assertEquals(millis("2026-06-24", 0), series.rangeStart)
        assertEquals(millis("2026-07-10", 7), series.firstMeasuredAt)
        assertEquals(millis("2026-07-23", 8), series.lastMeasuredAt)
    }

    @Test
    fun emptyAndSingleRecordSeriesStayRenderable() {
        val empty = TrendSeriesCalculator.build(emptyList(), TrendRange.DAYS_7, now, zone)
        assertEquals(0, empty.points.size)
        assertEquals(null, empty.firstMeasuredAt)
        assertEquals(null, empty.lastMeasuredAt)
        assertTrue(empty.yAxis.min < empty.yAxis.max)

        val single = TrendSeriesCalculator.build(
            listOf(record("only", millis("2026-07-23", 8), 118, 76)),
            TrendRange.DAYS_7,
            now,
            zone
        )
        assertEquals(1, single.points.size)
        assertEquals(single.points.first().timestamp, single.firstMeasuredAt)
        assertEquals(single.points.first().timestamp, single.lastMeasuredAt)
        assertTrue(TrendSeriesCalculator.tickValues(single.yAxis).size in 4..8)
    }

    @Test
    fun allRange_acceptsCrossYearRecordsAndLegalExtremes() {
        val records = listOf(
            record("old", millis("2024-12-31", 23), 88, 58),
            record("new-year", millis("2025-01-01", 0), 300, 200),
            record("invalid", millis("2025-01-02", 9), 999, 500)
        )

        val series = TrendSeriesCalculator.build(
            records = records,
            range = TrendRange.ALL,
            nowMillis = millis("2025-01-03", 12),
            zoneId = zone
        )

        assertEquals(3, series.points.size)
        assertTrue(series.yAxis.min >= 20)
        assertTrue(series.yAxis.max <= 320)
        // 合法的 300/200 必须落在坐标范围内。
        assertTrue(series.yAxis.max >= 300)
    }

    /**
     * 目标约为 5–6 个主刻度：旧的按 10 递增会画出 50、60 … 150 十余条密集网格。
     */
    @Test
    fun yAxisKeepsGridLineCountBetweenFiveAndSeven() {
        val scenarios = listOf(
            listOf(92 to 62, 123 to 89),
            listOf(105 to 70),
            listOf(118 to 76, 121 to 79),
            listOf(261 to 160, 300 to 200),
            listOf(45 to 25, 60 to 40),
            listOf(999 to 500),
            listOf(10 to 5)
        )

        scenarios.forEach { scenario ->
            val points = scenario.mapIndexed { index, (systolic, diastolic) ->
                record("p$index", millis("2026-07-22", 8), systolic, diastolic).toTrendPoint()
            }
            val axis = TrendSeriesCalculator.calculateYAxis(points, null, null)
            val gridLines = TrendSeriesCalculator.tickValues(axis)
            assertTrue(
                "网格数量应在 5–7 条之间，实际 ${gridLines.size}：$gridLines（$axis）",
                gridLines.size in 5..7
            )
            assertTrue(gridLines.first() >= axis.min)
            assertTrue(gridLines.last() <= axis.max)
        }
    }

    @Test
    fun yAxisGridValuesRemainOnStepGridAndInsideBounds() {
        val axis = TrendSeriesCalculator.calculateYAxis(
            points = listOf(record("a", millis("2026-07-22", 8), 128, 82).toTrendPoint()),
            targetSystolic = 130,
            targetDiastolic = 80
        )

        val values = TrendSeriesCalculator.tickValues(axis)

        assertEquals(axis.min, values.first())
        assertEquals(axis.max, values.last())
        assertTrue(values.zipWithNext().all { (a, b) -> b - a == axis.tickStep })
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

    /**
     * 常见血压区间：上下界吸附到漂亮刻度网格，90/140 参考线可见，
     * 主刻度约 5–6 个间隔（旧行为是 50–150 + 10 步长的 11 条密集网格）。
     */
    @Test
    fun commonBloodPressureRange_snapsToCompactNiceTicksAndKeepsReferencesVisible() {
        val points = listOf(
            record("low", millis("2026-07-22", 8), 92, 62),
            record("high", millis("2026-07-23", 8), 123, 89)
        ).map { it.toTrendPoint() }

        val axis = TrendSeriesCalculator.calculateYAxis(
            points = points,
            targetSystolic = null,
            targetDiastolic = null
        )

        assertEquals(40, axis.min)
        assertEquals(160, axis.max)
        assertEquals(20, axis.tickStep)
        assertTrue(TrendSeriesCalculator.REFERENCE_SYSTOLIC in axis.min..axis.max)
        assertTrue(TrendSeriesCalculator.REFERENCE_DIASTOLIC in axis.min..axis.max)
        assertTrue(TrendSeriesCalculator.tickValues(axis).size <= 7)
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

    /**
     * 需求验收尺度：7 天约 35 条、30 天约 181 条、全部约 1000 条原始记录，
     * 图表点数与耗时都必须可控。
     */
    @Test
    fun typicalDataVolumesStayLightAfterAggregation() {
        val hourMillis = 60L * 60L * 1_000L
        val dayMillis = 24L * hourMillis

        // 7 天约 35 条：保留每次测量。
        val weekRecords = (0 until 35).map { index ->
            record(
                id = "w$index",
                measuredAt = now - (35L - index) * 4L * hourMillis,
                systolic = 112 + index % 25,
                diastolic = 68 + index % 18
            )
        }
        val weekSeries = TrendSeriesCalculator.build(weekRecords, TrendRange.DAYS_7, now, zone)
        assertEquals(35, weekSeries.rawRecordCount)
        assertEquals(35, weekSeries.points.size)
        assertTrue(weekSeries.points.all { it.aggregation == TrendAggregation.RAW })

        // 30 天约 181 条：聚合后每天一个节点。
        val windowStart = TrendSeriesCalculator.rangeStart(TrendRange.DAYS_30, now, zone)
        val thirtyDayRecords = (0 until 180).map { index ->
            record(
                id = "m$index",
                measuredAt = windowStart + (index / 6L) * dayMillis + (index % 6L) * 2L * hourMillis,
                systolic = 108 + index % 38,
                diastolic = 66 + index % 26
            )
        }
        val thirtyDaySeries =
            TrendSeriesCalculator.build(thirtyDayRecords, TrendRange.DAYS_30, now, zone)
        assertEquals(180, thirtyDaySeries.rawRecordCount)
        assertEquals(30, thirtyDaySeries.points.size)
        assertTrue(thirtyDaySeries.points.all { it.aggregation == TrendAggregation.DAILY })
        assertEquals(6, thirtyDaySeries.points.first().recordCount)

        // 全部约 1000 条：同样按自然日聚合，耗时可控。
        val allRecords = (0 until 1_000).map { index ->
            record(
                id = "a$index",
                measuredAt = now - (999L - index) * 10L * hourMillis,
                systolic = 106 + index % 46,
                diastolic = 64 + index % 30
            )
        }
        lateinit var allSeries: com.example.bloodpressurerecord.domain.model.TrendSeries
        val elapsedMillis = measureTimeMillis {
            allSeries = TrendSeriesCalculator.build(allRecords, TrendRange.ALL, now, zone)
        }

        assertEquals(1_000, allSeries.rawRecordCount)
        assertTrue(allSeries.points.size <= 418)
        assertTrue("1000 条聚合约 ${elapsedMillis}ms", elapsedMillis < 500)
        // 每个自然日一个节点，缺测日期不会补零，折线按真实 timestamp 断档。
        assertTrue(allSeries.points.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun lineBreakKeepsGapsWiderThanTwoDaysUnconnected() {
        val dayMillis = 24L * 60L * 60L * 1_000L
        val records = listOf(
            record("before-gap", now - 20L * dayMillis, 120, 80),
            record("after-gap", now - 4L * dayMillis, 130, 85),
            record("recent", now - 1L * dayMillis, 125, 82)
        )

        val series = TrendSeriesCalculator.build(records, TrendRange.DAYS_30, now, zone)

        assertEquals(3, series.points.size)
        val gapMillis = series.points[1].timestamp - series.points[0].timestamp
        // 绘制层对超过约 2 天的间隔重新 moveTo，缺测不会被直连。
        assertTrue(gapMillis > 2L * dayMillis)
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
