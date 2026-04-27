package com.example.bloodpressurerecord.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class BloodPressureLevelCalculatorTest {
    @Test
    fun `三级偏高阈值返回三级文本`() {
        assertEquals("3级偏高（仅供参考）", BloodPressureLevelCalculator.calculateLabel(180, 95))
    }

    @Test
    fun `空值返回待判断`() {
        assertEquals("待判断", BloodPressureLevelCalculator.calculateLabel(null, 80))
    }
}
