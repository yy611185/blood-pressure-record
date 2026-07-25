package com.example.bloodpressurerecord.reminder

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Taipei")

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
}
