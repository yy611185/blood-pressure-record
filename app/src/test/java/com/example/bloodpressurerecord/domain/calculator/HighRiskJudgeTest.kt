package com.example.bloodpressurerecord.domain.calculator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighRiskJudgeTest {
    @Test
    fun `超过阈值触发高风险`() {
        assertTrue(HighRiskJudge.shouldTrigger(182, 100))
        assertTrue(HighRiskJudge.shouldTrigger(150, 121))
    }

    @Test
    fun `未超过阈值不触发高风险`() {
        assertFalse(HighRiskJudge.shouldTrigger(180, 120))
    }
}
