package com.example.bloodpressurerecord.domain.time

import java.time.Instant
import java.time.ZoneId

object MeasurementPeriod {
    fun labelFor(measuredAt: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return when (Instant.ofEpochMilli(measuredAt).atZone(zoneId).hour) {
            in 0..4 -> "凌晨"
            in 5..8 -> "清晨"
            in 9..11 -> "上午"
            in 12..17 -> "下午"
            else -> "晚上"
        }
    }
}
