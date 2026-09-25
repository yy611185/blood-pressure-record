package com.example.bloodpressurerecord.domain.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalWeekTest {

    @Test
    fun `weekStartSunday returns the same day for sunday`() {
        val sunday = LocalDate.of(2026, 7, 26)
        assertEquals(LocalDate.of(2026, 7, 26), weekStartSunday(sunday))
    }

    @Test
    fun `weekStartSunday returns the previous sunday for weekdays`() {
        // 2026-07-25 是周六
        assertEquals(LocalDate.of(2026, 7, 19), weekStartSunday(LocalDate.of(2026, 7, 25)))
        // 2026-07-20 是周一
        assertEquals(LocalDate.of(2026, 7, 19), weekStartSunday(LocalDate.of(2026, 7, 20)))
        // 2026-07-24 是周五
        assertEquals(LocalDate.of(2026, 7, 19), weekStartSunday(LocalDate.of(2026, 7, 24)))
    }

    @Test
    fun `weekStartSunday crosses month boundary`() {
        // 2026-08-01 是周六，本周日落在 7 月
        assertEquals(LocalDate.of(2026, 7, 26), weekStartSunday(LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun `naturalWeekDates always spans sunday through saturday`() {
        val week = naturalWeekDates(LocalDate.of(2026, 7, 25))
        assertEquals(WEEK_LENGTH, week.size)
        assertEquals(LocalDate.of(2026, 7, 19), week.first())
        assertEquals(LocalDate.of(2026, 7, 25), week.last())
        assertEquals(
            listOf(7, 1, 2, 3, 4, 5, 6),
            week.map { it.dayOfWeek.value }
        )
    }

    @Test
    fun `naturalWeekDates is stable for every day of the same week`() {
        val expected = naturalWeekDates(LocalDate.of(2026, 7, 22))
        (19..25).forEach { day ->
            assertEquals(expected, naturalWeekDates(LocalDate.of(2026, 7, day)))
        }
    }
}
