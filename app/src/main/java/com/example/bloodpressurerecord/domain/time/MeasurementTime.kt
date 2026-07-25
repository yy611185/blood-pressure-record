package com.example.bloodpressurerecord.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 数据库存储的是 UTC 时间线上的绝对时间点（Unix epoch 毫秒）。
 * 自然日/月查询统一在指定时区计算边界，并使用半开区间。
 */
data class EpochMillisRange(
    val startInclusive: Long,
    val endExclusive: Long
) {
    init {
        require(startInclusive < endExclusive) { "时间范围必须为非空半开区间" }
    }

    operator fun contains(epochMillis: Long): Boolean {
        return epochMillis >= startInclusive && epochMillis < endExclusive
    }
}

fun LocalDate.toEpochMillisRange(zoneId: ZoneId): EpochMillisRange {
    return EpochMillisRange(
        startInclusive = atStartOfDay(zoneId).toInstant().toEpochMilli(),
        endExclusive = plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    )
}

fun YearMonth.toEpochMillisRange(zoneId: ZoneId): EpochMillisRange {
    val firstDay = atDay(1)
    return EpochMillisRange(
        startInclusive = firstDay.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        endExclusive = plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    )
}

fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
    return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}
