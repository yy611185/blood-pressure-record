package com.example.bloodpressurerecord.ui.history

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
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
        assertEquals(2, insights.morningCount)
        assertEquals(2, insights.afternoonCount)
        assertEquals(102 to 67, insights.morningAverage)
        assertEquals(158 to 99, insights.afternoonAverage)
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
    }

    private fun record(id: String, hour: Int, minute: Int, systolic: Int, diastolic: Int) = TrendRecord(
        id = id,
        measuredAt = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
        systolic = systolic,
        diastolic = diastolic,
        pulse = null,
        category = "NORMAL"
    )
}
