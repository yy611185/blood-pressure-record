package com.example.bloodpressurerecord.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.ZonedDateTime

object DateTimeInputFormatter {
    private val formatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)

    fun nowText(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String = format(nowMillis, zoneId)

    fun format(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zoneId).format(formatter)
    }

    fun parse(text: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        return try {
            val localDateTime = LocalDateTime.parse(text.trim(), formatter)
            val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
            if (validOffsets.isEmpty()) return null
            ZonedDateTime.ofLocal(localDateTime, zoneId, validOffsets.first())
                .toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
