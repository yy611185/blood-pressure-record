package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分级采用《中国高血压防治指南》成人诊室血压标准。
 */
class CategoryCalculatorTest {
    @Test
    fun `正常范围`() {
        assertEquals(BloodPressureCategory.NORMAL, CategoryCalculator.calculate(110, 70))
        assertEquals(BloodPressureCategory.NORMAL, CategoryCalculator.calculate(119, 79))
    }

    @Test
    fun `正常高值 120-139 或 80-89`() {
        assertEquals(BloodPressureCategory.HIGH_NORMAL, CategoryCalculator.calculate(120, 70))
        assertEquals(BloodPressureCategory.HIGH_NORMAL, CategoryCalculator.calculate(125, 79))
        assertEquals(BloodPressureCategory.HIGH_NORMAL, CategoryCalculator.calculate(110, 80))
        assertEquals(BloodPressureCategory.HIGH_NORMAL, CategoryCalculator.calculate(139, 89))
        // 旧 ACC/AHA 标准会把 13283 判为 1 级；中国指南属正常高值
        assertEquals(BloodPressureCategory.HIGH_NORMAL, CategoryCalculator.calculate(132, 83))
    }

    @Test
    fun `1级 140-159 或 90-99`() {
        assertEquals(BloodPressureCategory.STAGE1, CategoryCalculator.calculate(140, 70))
        assertEquals(BloodPressureCategory.STAGE1, CategoryCalculator.calculate(110, 90))
        assertEquals(BloodPressureCategory.STAGE1, CategoryCalculator.calculate(159, 99))
    }

    @Test
    fun `2级 160-179 或 100-109`() {
        assertEquals(BloodPressureCategory.STAGE2, CategoryCalculator.calculate(160, 88))
        assertEquals(BloodPressureCategory.STAGE2, CategoryCalculator.calculate(145, 100))
        assertEquals(BloodPressureCategory.STAGE2, CategoryCalculator.calculate(179, 109))
    }

    @Test
    fun `3级 大于等于180 或 110`() {
        assertEquals(BloodPressureCategory.STAGE3, CategoryCalculator.calculate(180, 80))
        assertEquals(BloodPressureCategory.STAGE3, CategoryCalculator.calculate(120, 110))
        assertEquals(BloodPressureCategory.STAGE3, CategoryCalculator.calculate(181, 90))
    }

    @Test
    fun `收缩压和舒张压就高不就低`() {
        // 收缩压 1 级、舒张压 2 级 → 取更高的 2 级
        assertEquals(BloodPressureCategory.STAGE2, CategoryCalculator.calculate(150, 105))
    }

    @Test
    fun `偏低提示 小于90 或 小于60`() {
        assertEquals(BloodPressureCategory.LOW, CategoryCalculator.calculate(85, 70))
        assertEquals(BloodPressureCategory.LOW, CategoryCalculator.calculate(100, 55))
        assertEquals(BloodPressureCategory.LOW, CategoryCalculator.calculate(89, 59))
    }

    @Test
    fun `升高分级优先于偏低提示`() {
        // 收缩压达正常高值、舒张压偏低：按升高一侧归类
        assertEquals(BloodPressureCategory.HIGH_NORMAL, CategoryCalculator.calculate(125, 55))
    }
}
