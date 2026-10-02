package com.example.bloodpressurerecord.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementTagsTest {
    @Test
    fun `合并标签按因素表拆回症状与因素`() {
        val (symptoms, factors) = MeasurementTags.splitSymptomsAndFactors(
            listOf("头晕", "饮酒后", "无症状", "睡眠不足", "自定义备注标签")
        )
        assertEquals(setOf("头晕", "无症状", "自定义备注标签"), symptoms)
        assertEquals(setOf("饮酒后", "睡眠不足"), factors)
    }

}
