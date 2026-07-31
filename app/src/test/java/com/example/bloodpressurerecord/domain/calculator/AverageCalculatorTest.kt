package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.AverageStrategy
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

    @Test
    fun `弃用第一组策略只对其余读数取平均`() {
        val result = AverageCalculator.calculate(
            listOf(
                ReadingValue(systolic = 160, diastolic = 100, pulse = 90),
                ReadingValue(systolic = 120, diastolic = 80, pulse = 70),
                ReadingValue(systolic = 124, diastolic = 82, pulse = 72)
            ),
            strategy = AverageStrategy.DISCARD_FIRST
        )

        assertEquals(122, result.avgSystolic)
        assertEquals(81, result.avgDiastolic)
        assertEquals(71, result.avgPulse)
    }

    @Test
    fun `弃用第一组策略在仅一组读数时退化为全部平均`() {
        val result = AverageCalculator.calculate(
            listOf(ReadingValue(systolic = 130, diastolic = 85, pulse = 75)),
            strategy = AverageStrategy.DISCARD_FIRST
        )

        assertEquals(130, result.avgSystolic)
        assertEquals(85, result.avgDiastolic)
    }

    @Test
    fun `弃用第一组时两组读数使用第二组`() {
        val result = AverageCalculator.calculate(
            listOf(
                ReadingValue(systolic = 150, diastolic = 95, pulse = 88),
                ReadingValue(systolic = 126, diastolic = 82, pulse = 70)
            ),
            strategy = AverageStrategy.DISCARD_FIRST
        )

        assertEquals(126, result.avgSystolic)
        assertEquals(82, result.avgDiastolic)
    }
}
