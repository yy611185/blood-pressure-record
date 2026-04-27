package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.ReadingValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AverageCalculatorTest {
    @Test
    fun `calculate 返回平均收缩压舒张压和脉搏`() {
        val result = AverageCalculator.calculate(
            listOf(
                ReadingValue(systolic = 120, diastolic = 80, pulse = 70),
                ReadingValue(systolic = 130, diastolic = 90, pulse = 80)
            )
        )

        assertEquals(125, result.avgSystolic)
        assertEquals(85, result.avgDiastolic)
        assertEquals(75, result.avgPulse)
    }

    @Test
    fun `脉搏全部为空时平均脉搏为空`() {
        val result = AverageCalculator.calculate(
            listOf(
                ReadingValue(systolic = 118, diastolic = 76, pulse = null),
                ReadingValue(systolic = 122, diastolic = 78, pulse = null)
            )
        )

        assertNull(result.avgPulse)
    }
}
