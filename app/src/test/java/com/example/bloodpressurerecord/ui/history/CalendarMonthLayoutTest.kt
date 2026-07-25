package com.example.bloodpressurerecord.ui.history

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthLayoutTest {
    @Test
    fun `普通月份按周日开头排列`() {
        val cells = CalendarMonthLayout.cells(YearMonth.of(2026, 7))
        assertEquals(3, cells.indexOfFirst { it.date == LocalDate.of(2026, 7, 1) })
        assertEquals(LocalDate.of(2026, 7, 31), cells.last { it.date != null }.date)
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun `平年二月有28天而闰年二月有29天`() {
        assertEquals(28, dates(YearMonth.of(2025, 2)).size)
        assertEquals(29, dates(YearMonth.of(2024, 2)).size)
        assertTrue(LocalDate.of(2024, 2, 29) in dates(YearMonth.of(2024, 2)))
    }

    @Test
    fun `30天和31天月份不使用硬编码`() {
        assertEquals(30, dates(YearMonth.of(2026, 4)).size)
        assertEquals(31, dates(YearMonth.of(2026, 7)).size)
    }

    @Test
    fun `跨年切换保持正确年月`() {
        assertEquals(YearMonth.of(2027, 1), YearMonth.of(2026, 12).plusMonths(1))
        assertEquals(YearMonth.of(2025, 12), YearMonth.of(2026, 1).minusMonths(1))
    }

    @Test
    fun `跨月位置留空`() {
        val first = CalendarMonthLayout.cells(YearMonth.of(2026, 8)).first()
        assertNull(first.date)
        assertTrue(!first.isInDisplayedMonth)
    }

    private fun dates(month: YearMonth): List<LocalDate> =
        CalendarMonthLayout.cells(month).mapNotNull { it.date }
}

