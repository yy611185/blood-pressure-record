package com.example.bloodpressurerecord.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionFormLogicDynamicTest {
    @Test
    fun validate_dynamic_readings_success() {
        val result = SessionFormLogic.validateAndBuildReadings(
            readings = listOf(
                SessionReadingInputUi("120", "80", "70"),
                SessionReadingInputUi("126", "82", "72"),
                SessionReadingInputUi("130", "84", "74"),
                SessionReadingInputUi("132", "86", "76")
            ),
            requiredCount = 2
        )
        assertNull(result.error)
        assertEquals(4, result.readings.size)
    }

    @Test
    fun derive_dynamic_readings_average_and_category() {
        val result = SessionFormLogic.recomputeDerived(
            readings = listOf(
                SessionReadingInputUi("150", "95", "80"),
                SessionReadingInputUi("145", "90", "82"),
                SessionReadingInputUi("140", "88", "84"),
                SessionReadingInputUi("138", "86", "80")
            ),
            requiredCount = 2
        )
        assertEquals(143, result.avgSystolic)
        assertEquals(90, result.avgDiastolic)
        // 中国指南：143/90 属 1 级高血压
        assertEquals("1级高血压", result.categoryLabel)
    }

    @Test
    fun derive_with_discard_first_strategy_ignores_first_group() {
        val result = SessionFormLogic.recomputeDerived(
            readings = listOf(
                SessionReadingInputUi("160", "100", "90"),
                SessionReadingInputUi("120", "78", "70"),
                SessionReadingInputUi("122", "78", "72")
            ),
            requiredCount = 2,
            strategy = com.example.bloodpressurerecord.domain.model.AverageStrategy.DISCARD_FIRST
        )
        assertEquals(121, result.avgSystolic)
        assertEquals(78, result.avgDiastolic)
        assertEquals("正常高值", result.categoryLabel)
    }
}

