package com.example.bloodpressurerecord.util

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class DateTimeInputFormatterTest {
    @Test
    fun `拒绝不存在的夏令时本地时间`() {
        assertNull(
            DateTimeInputFormatter.parse(
                "2026-03-08 02:30",
                ZoneId.of("America/New_York")
            )
        )
    }

    @Test
    fun `夏令时重复时间使用稳定的较早偏移`() {
        val zone = ZoneId.of("America/New_York")
        val parsed = DateTimeInputFormatter.parse("2026-11-01 01:30", zone)
        assertNotNull(parsed)
        assertEquals("2026-11-01 01:30", DateTimeInputFormatter.format(parsed!!, zone))
    }

    @Test
    fun `严格拒绝无效日期`() {
        assertNull(DateTimeInputFormatter.parse("2025-02-29 12:00"))
    }
}

