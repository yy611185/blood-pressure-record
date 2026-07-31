package com.example.bloodpressurerecord.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementTagsTest {
    @Test
    fun `时段默认场景覆盖全天并含边界`() {
        assertEquals("凌晨", MeasurementTags.defaultSceneFor(0))
        assertEquals("凌晨", MeasurementTags.defaultSceneFor(4))
        assertEquals("晨起", MeasurementTags.defaultSceneFor(5))
        assertEquals("晨起", MeasurementTags.defaultSceneFor(8))
        assertEquals("上午", MeasurementTags.defaultSceneFor(9))
        assertEquals("上午", MeasurementTags.defaultSceneFor(11))
        assertEquals("下午", MeasurementTags.defaultSceneFor(12))
        assertEquals("下午", MeasurementTags.defaultSceneFor(17))
        assertEquals("晚上", MeasurementTags.defaultSceneFor(18))
        assertEquals("晚上", MeasurementTags.defaultSceneFor(23))
    }

    @Test
    fun `所有小时的默认场景都在标准场景列表中`() {
        (0..23).forEach { hour ->
            assertTrue(MeasurementTags.defaultSceneFor(hour) in MeasurementTags.scenes)
        }
    }

    @Test
    fun `合并标签按因素表拆回症状与因素`() {
        val (symptoms, factors) = MeasurementTags.splitSymptomsAndFactors(
            listOf("头晕", "饮酒后", "无症状", "睡眠不足", "自定义备注标签")
        )
        assertEquals(setOf("头晕", "无症状", "自定义备注标签"), symptoms)
        assertEquals(setOf("饮酒后", "睡眠不足"), factors)
    }

    @Test
    fun `症状与因素列表无重名`() {
        assertTrue(MeasurementTags.symptoms.intersect(MeasurementTags.factors.toSet()).isEmpty())
    }
}
