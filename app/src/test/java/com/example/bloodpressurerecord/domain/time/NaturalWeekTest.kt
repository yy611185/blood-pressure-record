package com.example.bloodpressurerecord.domain.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalWeekTest {

    @Test
    fun `weekStartMonday returns the same day for monday`() {
        val monday = LocalDate.of(2026, 7, 20)
        assertEquals(LocalDate.of(2026, 7, 20), weekStartMonday(monday))
    }

    @Test
    fun `weekStartMonday returns the previous monday for all other days`() {
        // 2026-07-26 是周日，属于 7/20（周一）所在自然周
        assertEquals(LocalDate.of(2026, 7, 20), weekStartMonday(LocalDate.of(2026, 7, 26)))
        // 2026-07-25 是周六
        assertEquals(LocalDate.of(2026, 7, 20), weekStartMonday(LocalDate.of(2026, 7, 25)))
        // 2026-07-24 是周五
        assertEquals(LocalDate.of(2026, 7, 20), weekStartMonday(LocalDate.of(2026, 7, 24)))
    }

    @Test
    fun `weekStartMonday crosses month boundary`() {
        // 2026-08-01 是周六，本周一落在 7 月 27 日
        assertEquals(LocalDate.of(2026, 7, 27), weekStartMonday(LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun `naturalWeekDates always spans monday through sunday`() {
        val week = naturalWeekDates(LocalDate.of(2026, 7, 25))
        assertEquals(WEEK_LENGTH, week.size)
        assertEquals(LocalDate.of(2026, 7, 20), week.first())
        assertEquals(LocalDate.of(2026, 7, 26), week.last())
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            week.map { it.dayOfWeek.value }
        )
    }

    @Test
    fun `naturalWeekDates is stable for every day of the same week`() {
        val expected = naturalWeekDates(LocalDate.of(2026, 7, 22))
        (20..26).forEach { day ->
            assertEquals(expected, naturalWeekDates(LocalDate.of(2026, 7, day)))
        }
    }

    @Test
    fun `weekStartSunday remains consistent for legacy callers`() {
        assertEquals(LocalDate.of(2026, 7, 26), weekStartSunday(LocalDate.of(2026, 7, 26)))
        assertEquals(LocalDate.of(2026, 7, 19), weekStartSunday(LocalDate.of(2026, 7, 25)))
    }
}
