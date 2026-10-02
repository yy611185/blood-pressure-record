package com.example.bloodpressurerecord.domain.time

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementPeriodTest {
    @Test
    fun labelsFollowLocalTimeBoundaries() {
        val zone = ZoneId.of("Asia/Taipei")
        val date = LocalDate.of(2026, 10, 1)
        val cases = mapOf(
            "00:00" to "凌晨", "04:59" to "凌晨",
            "05:00" to "清晨", "08:59" to "清晨",
            "09:00" to "上午", "11:59" to "上午",
            "12:00" to "下午", "17:59" to "下午",
            "18:00" to "晚上", "23:59" to "晚上"
        )
        cases.forEach { (time, expected) ->
            val timestamp = date.atTime(java.time.LocalTime.parse(time)).atZone(zone)
                .toInstant().toEpochMilli()
            assertEquals(time, expected, MeasurementPeriod.labelFor(timestamp, zone))
        }
    }
}
