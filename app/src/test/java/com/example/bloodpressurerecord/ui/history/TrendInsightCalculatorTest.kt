package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.model.DayNightAverage
import com.example.bloodpressurerecord.domain.model.TrendRecord
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendInsightCalculatorTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val day = LocalDate.of(2026, 9, 25)

    @Test
    fun `分级分布与达标率基于原始记录并遵循严格目标和偏低界限`() {
        val records = listOf(
            record("normal", 11, 59, 118, 78),
            record("boundary", 12, 0, 135, 85),
            record("low", 8, 0, 85, 55),
            record("high", 20, 0, 180, 112)
        )
        val insights = TrendInsightCalculator.calculate(
            records = records,
            previousRecords = listOf(record("previous", 9, 0, 125, 82)),
            targetSystolic = 135,
            targetDiastolic = 85,
            zoneId = zone
        )

        assertEquals(25, insights.targetRate)
        assertEquals(100, insights.previousTargetRate)
        assertEquals(1, insights.categoryCounts[BloodPressureCategory.NORMAL])
        assertEquals(1, insights.categoryCounts[BloodPressureCategory.HIGH_NORMAL])
        assertEquals(1, insights.categoryCounts[BloodPressureCategory.LOW])
        assertEquals(1, insights.categoryCounts[BloodPressureCategory.STAGE3])
        assertEquals("high", insights.highestReading?.id)
        assertEquals("low", insights.lowestReading?.id)
        assertEquals(130 to 83, insights.periodAverage)
        assertEquals(4, insights.recordCount)
        assertEquals(1, insights.recordDays)
        assertEquals(2, insights.morningCount)
        assertEquals(1, insights.eveningCount)
        assertEquals(102 to 67, insights.morningAverage)
        assertEquals(180 to 112, insights.eveningAverage)
        assertEquals(1, insights.highRiskCount)
    }

    @Test
    fun `早晚边界按本地时间区分且凌晨下午仍计入期间统计`() {
        val records = listOf(
            record("midnight", 0, 0, 200, 110),
            record("before-morning", 4, 59, 190, 105),
            record("morning-start", 5, 0, 110, 70),
            record("morning-end", 11, 59, 120, 80),
            record("afternoon-start", 12, 0, 130, 85),
            record("before-evening", 17, 59, 135, 87),
            record("evening-start", 18, 0, 140, 90),
            record("evening-end", 23, 59, 160, 100),
            record("next-day", 5, 0, 150, 95).copy(
                measuredAt = day.plusDays(1).atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
            )
        )
        val insights = TrendInsightCalculator.calculate(records, emptyList(), null, null, zone)

        assertEquals(148 to 91, insights.periodAverage)
        assertEquals(9, insights.recordCount)
        assertEquals(2, insights.recordDays)
        assertEquals(127 to 82, insights.morningAverage)
        assertEquals(3, insights.morningCount)
        assertEquals(2, insights.morningDays)
        assertEquals(150 to 95, insights.eveningAverage)
        assertEquals(2, insights.eveningCount)
        assertEquals(1, insights.eveningDays)
        assertEquals(9, insights.categoryCounts.values.sum())
        assertEquals(2, insights.highRiskCount)
        assertEquals("midnight", insights.highestReading?.id)
    }

    @Test
    fun `昼夜边界按本地时间分类且每次 Session 等权平均`() {
        val records = listOf(
            record("midnight", 0, 0, 100, 60),
            record("night-end", 5, 59, 110, 70),
            record("day-start", 6, 0, 120, 80),
            record("day-end", 21, 59, 140, 90),
            record("night-start", 22, 0, 120, 80),
            record("late-night", 23, 0, 130, 90),
            record("next-midnight", 0, 0, 140, 100).copy(
                measuredAt = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            ),
            record("next-day-start", 6, 0, 160, 100).copy(
                measuredAt = day.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
            )
        )

        val insights = TrendInsightCalculator.calculate(records, emptyList(), null, null, zone)

        assertEquals(DayNightAverage(140, 90, 3, 2), insights.daytimeAverage)
        assertEquals(DayNightAverage(120, 80, 5, 2), insights.nighttimeAverage)
        assertEquals(128 to 84, insights.periodAverage)
        assertEquals(130 to 83, insights.morningAverage)
        assertEquals(130 to 87, insights.eveningAverage)
        assertEquals(8, insights.recordCount)
    }

    @Test
    fun `无目标和无记录时不生成假达标率`() {
        val insights = TrendInsightCalculator.calculate(
            records = emptyList(),
            previousRecords = emptyList(),
            targetSystolic = null,
            targetDiastolic = null,
            zoneId = zone
        )
        assertEquals(null, insights.targetRate)
        assertEquals(null, insights.previousTargetRate)
        assertEquals(null, insights.highestReading)
        assertEquals(emptyMap<BloodPressureCategory, Int>(), insights.categoryCounts)
        assertEquals(DayNightAverage(), insights.daytimeAverage)
        assertEquals(DayNightAverage(), insights.nighttimeAverage)
    }

    private fun record(id: String, hour: Int, minute: Int, systolic: Int, diastolic: Int) = TrendRecord(
        id = id,
        measuredAt = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
        systolic = systolic,
        diastolic = diastolic,
        pulse = null,
        category = "NORMAL",
        containsHighRiskReading = systolic >= 180 || diastolic >= 110
    )
}
