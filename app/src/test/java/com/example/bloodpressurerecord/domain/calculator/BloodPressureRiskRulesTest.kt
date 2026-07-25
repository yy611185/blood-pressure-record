package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageResult
import com.example.bloodpressurerecord.domain.model.ReadingValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodPressureRiskRulesTest {
    @Test
    fun `单组高风险但平均值未达到仍标记高风险`() {
        val readings = listOf(
            ReadingValue(190, 90, 70),
            ReadingValue(110, 70, 72)
        )
        val average = AverageCalculator.calculate(readings)

        assertFalse(BloodPressureRules.isHighRisk(average.avgSystolic, average.avgDiastolic))
        assertTrue(BloodPressureRules.containsHighRiskReading(readings, average))
    }

    @Test
    fun `平均值达到高风险时标记高风险`() {
        val readings = listOf(
            ReadingValue(120, 80, 70),
            ReadingValue(122, 82, 72)
        )
        val highRiskAverage = AverageResult(181, 81, 71)

        assertTrue(BloodPressureRules.containsHighRiskReading(readings, highRiskAverage))
    }

    @Test
    fun `全部读数和平均值正常时不标记高风险`() {
        val readings = listOf(
            ReadingValue(118, 76, 68),
            ReadingValue(122, 78, 70)
        )

        assertFalse(
            BloodPressureRules.containsHighRiskReading(
                readings,
                AverageCalculator.calculate(readings)
            )
        )
    }
}
