package com.example.bloodpressurerecord.domain.calculator

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 已保存的每次测量代表值等权参与统计，不重算历史原始读数。 */
object SessionStatisticsCalculator {
    fun isMorning(measuredAt: Long, zoneId: ZoneId): Boolean =
        Instant.ofEpochMilli(measuredAt).atZone(zoneId).hour in 5..11

    fun isEvening(measuredAt: Long, zoneId: ZoneId): Boolean =
        Instant.ofEpochMilli(measuredAt).atZone(zoneId).hour in 18..23

    fun <T> pressureAverage(
        values: List<T>, systolic: (T) -> Int, diastolic: (T) -> Int
    ): Pair<Int, Int>? = values.takeIf { it.isNotEmpty() }?.let {
        it.map(systolic).average().roundToInt() to it.map(diastolic).average().roundToInt()
    }

    fun <T> pulseAverage(values: List<T>, pulse: (T) -> Int?): Int? =
        values.mapNotNull(pulse).takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}
