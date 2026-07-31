package com.example.bloodpressurerecord.ui.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthLayoutTest {
    @Test
    fun `普通月份按周一开头排列`() {
        // 2026-07-01 是周三：周一开头时前面留 2 个空位。
        val cells = CalendarMonthLayout.cells(YearMonth.of(2026, 7))
        assertEquals(2, cells.indexOfFirst { it.date == LocalDate.of(2026, 7, 1) })
        assertEquals(LocalDate.of(2026, 7, 31), cells.last { it.date != null }.date)
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun `周一开头与本周统计使用同一种周定义`() {
        // 2026-06-01 恰好是周一：日历首格与“本周”起点一致，都不留空位。
        val cells = CalendarMonthLayout.cells(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), cells.first().date)
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

    @Test
    fun `本周固定从周一开始且使用半开区间`() {
        val zone = ZoneId.of("Asia/Taipei")
        val range = HistoryDateRanges.recent(
            RecentPeriod.THIS_WEEK,
            LocalDate.of(2026, 7, 26),
            zone
        )

        assertEquals(LocalDate.of(2026, 7, 20), localDate(range.startInclusive, zone))
        assertEquals(LocalDate.of(2026, 7, 27), localDate(range.endExclusive, zone))
    }

    @Test
    fun `本月跨年到下一年且尊重本地时区`() {
        val zone = ZoneId.of("America/New_York")
        val range = HistoryDateRanges.recent(
            RecentPeriod.THIS_MONTH,
            LocalDate.of(2026, 12, 31),
            zone
        )

        assertEquals(LocalDate.of(2026, 12, 1), localDate(range.startInclusive, zone))
        assertEquals(LocalDate.of(2027, 1, 1), localDate(range.endExclusive, zone))
    }

    private fun dates(month: YearMonth): List<LocalDate> =
        CalendarMonthLayout.cells(month).mapNotNull { it.date }

    private fun localDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
}
