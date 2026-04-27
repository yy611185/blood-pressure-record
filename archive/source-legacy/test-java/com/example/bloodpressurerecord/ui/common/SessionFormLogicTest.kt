package com.example.bloodpressurerecord.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionFormLogicTest {
    @Test
    fun `两组有效读数可通过校验`() {
        val result = SessionFormLogic.validateAndBuildReadings(
            reading1 = SessionReadingInputUi("120", "80", "70"),
            reading2 = SessionReadingInputUi("130", "85", "72"),
            reading3 = SessionReadingInputUi(),
            showThird = false
        )
        assertEquals(null, result.error)
        assertEquals(2, result.readings.size)
    }

    @Test
    fun `自动计算能给出分级`() {
        val derived = SessionFormLogic.recomputeDerived(
            reading1 = SessionReadingInputUi("150", "95", "80"),
            reading2 = SessionReadingInputUi("145", "90", "82"),
            reading3 = SessionReadingInputUi(),
            showThird = false
        )
        assertNotNull(derived.avgSystolic)
        assertEquals("2期偏高（STAGE2）", derived.categoryLabel)
    }
}
