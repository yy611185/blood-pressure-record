package com.example.bloodpressurerecord.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateTimeInputFormatter {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun nowText(): String = format(System.currentTimeMillis())

    fun format(millis: Long): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(formatter)
    }

    fun parse(text: String): Long? {
        return try {
            val localDateTime = LocalDateTime.parse(text.trim(), formatter)
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
