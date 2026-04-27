package com.example.bloodpressurerecord.data.repository.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvCodecTest {
    @Test
    fun encodeAndDecode_withCommaAndQuote_roundTrip() {
        val original = listOf(
            "normal",
            "含,逗号",
            "含\"双引号\""
        )

        val encoded = CsvCodec.encodeRow(original)
        val decoded = CsvCodec.decodeRow(encoded)

        assertEquals(original, decoded)
    }
}
