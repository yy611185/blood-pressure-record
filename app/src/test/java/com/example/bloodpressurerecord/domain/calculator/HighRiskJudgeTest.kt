package com.example.bloodpressurerecord.domain.calculator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighRiskJudgeTest {
    @Test
    fun `达到阈值触发高风险`() {
        assertTrue(BloodPressureRules.isHighRisk(182, 100))
        assertTrue(BloodPressureRules.isHighRisk(150, 121))
    }

    @Test
    fun `恰好在边界也触发高风险`() {
        // ≥180 和/或 ≥120（含边界）
        assertTrue(BloodPressureRules.isHighRisk(180, 80))
        assertTrue(BloodPressureRules.isHighRisk(150, 120))
        assertTrue(BloodPressureRules.isHighRisk(180, 120))
    }

    @Test
    fun `低于阈值不触发高风险`() {
        assertFalse(BloodPressureRules.isHighRisk(179, 119))
        assertFalse(BloodPressureRules.isHighRisk(140, 90))
    }
}
