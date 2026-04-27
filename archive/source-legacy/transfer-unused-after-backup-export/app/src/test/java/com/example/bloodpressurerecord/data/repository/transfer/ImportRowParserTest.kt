package com.example.bloodpressurerecord.data.repository.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRowParserTest {
    @Test
    fun parse_validTwoReadings_success() {
        val row = mapOf(
            "datetime" to "2026-04-01 08:30",
            "scene" to "晨起",
            "reading1_sbp" to "135",
            "reading1_dbp" to "85",
            "reading1_pulse" to "70",
            "reading2_sbp" to "132",
            "reading2_dbp" to "84",
            "reading2_pulse" to "72",
            "reading3_sbp" to "",
            "reading3_dbp" to "",
            "reading3_pulse" to "",
            "note" to "晨起测量",
            "symptoms" to "无症状|头晕"
        )

        val result = ImportRowParser.parse(row)

        assertTrue(result.isSuccess)
        val input = result.getOrThrow().input
        assertEquals("晨起", input.scene)
        assertEquals(2, input.readings.size)
        assertEquals(listOf("无症状", "头晕"), input.symptoms)
    }

    @Test
    fun parse_invalidDiastolicHigherThanSystolic_fail() {
        val row = mapOf(
            "datetime" to "2026-04-01 08:30",
            "scene" to "其他",
            "reading1_sbp" to "110",
            "reading1_dbp" to "120",
            "reading2_sbp" to "120",
            "reading2_dbp" to "80"
        )

        val result = ImportRowParser.parse(row)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("diastolic cannot be greater than systolic") == true)
    }

    @Test
    fun validateHeader_missingRequiredColumn_fail() {
        val header = listOf(
            "datetime",
            "scene",
            "reading1_sbp",
            "reading1_dbp",
            "reading2_sbp"
        )

        val error = ImportRowParser.validateHeader(header)

        assertTrue(error?.contains("Missing required columns") == true)
    }
}
