package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryCalculatorTest {
    @Test
    fun `大于严重阈值返回 SEVERE`() {
        assertEquals(BloodPressureCategory.SEVERE, CategoryCalculator.calculate(181, 90))
    }

    @Test
    fun `满足 stage2 阈值返回 STAGE2`() {
        assertEquals(BloodPressureCategory.STAGE2, CategoryCalculator.calculate(145, 88))
    }

    @Test
    fun `满足 elevated 阈值返回 ELEVATED`() {
        assertEquals(BloodPressureCategory.ELEVATED, CategoryCalculator.calculate(125, 79))
    }
}
