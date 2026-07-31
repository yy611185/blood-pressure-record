package com.example.bloodpressurerecord.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

private const val MIN_TICK_DELAY_MILLIS = 1_000L

/**
 * 发出当前本地日期，并在每个本地零点后自动发出新的一天。
 *
 * 页面用它驱动“今日 / 本周 / 最近 N 天”这类随日期滚动的查询范围，
 * 避免在 ViewModel 构造时把“今天”捕获成常量导致跨零点后数据过期。
 */
fun dayTicks(
    zoneId: ZoneId = ZoneId.systemDefault(),
    clockMillis: () -> Long = System::currentTimeMillis
): Flow<LocalDate> = flow {
    while (true) {
        val nowMillis = clockMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        emit(today)
        val nextMidnightMillis = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        delay((nextMidnightMillis - nowMillis).coerceAtLeast(MIN_TICK_DELAY_MILLIS))
    }
}.distinctUntilChanged()
