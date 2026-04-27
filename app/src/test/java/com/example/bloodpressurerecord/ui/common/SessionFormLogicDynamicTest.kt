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
        assertEquals("2期偏高（STAGE2）", result.categoryLabel)
    }
}

