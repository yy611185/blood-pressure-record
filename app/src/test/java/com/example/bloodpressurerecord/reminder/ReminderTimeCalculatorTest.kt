package com.example.bloodpressurerecord.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun delayedMedicationOccurrenceSurvivesResyncAndIsConsumedOnlyOnce() {
        val day = MedicationAlarmDay(LocalDate.of(2026, 7, 23), LocalTime.of(8, 0), zone)
        val late = Instant.parse("2026-07-23T00:03:00Z").toEpochMilli()

        assertEquals(day.today, day.pending(day.today, late))
        assertTrue(day.isDue(day.today, day.today, false, late))
        assertEquals(day.tomorrow, day.pending(day.tomorrow, late))
        assertFalse(day.isDue(day.tomorrow, day.today, false, late))
        assertTrue(day.isDue(day.tomorrow, null, true, late))
        assertFalse(day.isDue(day.tomorrow, null, false, late))
    }

    @Test
    fun overnightMedicationDeliveryDropsYesterdayAndRestoresTodaysSlot() {
        val yesterday = Instant.parse("2026-07-22T00:00:00Z").toEpochMilli()
        val day = MedicationAlarmDay(LocalDate.of(2026, 7, 23), LocalTime.of(8, 0), zone)
        val late = Instant.parse("2026-07-23T00:03:00Z").toEpochMilli()

        assertFalse(day.isDue(yesterday, yesterday, false, late))
        assertEquals(day.today, day.pending(yesterday, late))
        assertEquals(day.tomorrow, day.pending(null, late))
    }

    @Test
    fun futureTimeSchedulesToday() {
        val result = ReminderTimeCalculator.nextTriggerMillis(
            "21:00",
            LocalDateTime.of(2026, 7, 23, 20, 0),
            zone
        )

        assertEquals(
            Instant.parse("2026-07-23T13:00:00Z").toEpochMilli(),
            result
        )
    }

    @Test
    fun elapsedTimeSchedulesNextDay() {
        val result = ReminderTimeCalculator.nextTriggerMillis(
            "07:30",
            LocalDateTime.of(2026, 7, 23, 8, 0),
            zone
        )

        assertEquals(
            Instant.parse("2026-07-23T23:30:00Z").toEpochMilli(),
            result
        )
    }

    @Test
    fun invalidTimeIsRejected() {
        assertNull(
            ReminderTimeCalculator.nextTriggerMillis(
                "25:00",
                LocalDateTime.of(2026, 7, 23, 8, 0),
                zone
            )
        )
    }

    @Test
    fun daylightSavingStartUsesZoneRules() {
        val newYork = ZoneId.of("America/New_York")
        val result = ReminderTimeCalculator.nextTriggerMillis(
            "07:30",
            LocalDateTime.of(2026, 3, 7, 8, 0),
            newYork
        )

        assertEquals(Instant.parse("2026-03-08T11:30:00Z").toEpochMilli(), result)
    }

    @Test
    fun changedTimezoneProducesTriggerForNewZone() {
        val current = LocalDateTime.of(2026, 7, 25, 6, 0)
        val taipei = ReminderTimeCalculator.nextTriggerMillis("07:30", current, ZoneId.of("Asia/Taipei"))
        val tokyo = ReminderTimeCalculator.nextTriggerMillis("07:30", current, ZoneId.of("Asia/Tokyo"))

        assertEquals(60L * 60 * 1_000, taipei!! - tokyo!!)
    }
}
