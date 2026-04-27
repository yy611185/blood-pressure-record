package com.example.bloodpressurerecord.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BloodPressureLevelCalculatorTest {
    @Test
    fun `高压达到三级阈值时返回三级偏高`() {
        assertEquals(
            BloodPressureLevel.GRADE_3,
            BloodPressureLevelCalculator.calculate(182, 95)
        )
    }

    @Test
    fun `舒张压为空时返回待判断`() {
        assertEquals(
            BloodPressureLevel.UNKNOWN,
            BloodPressureLevelCalculator.calculate(120, null)
        )
    }

    @Test
    fun `正常高值区间返回正常高值`() {
        assertEquals(
            BloodPressureLevel.NORMAL_HIGH,
            BloodPressureLevelCalculator.calculate(132, 84)
        )
    }
}
