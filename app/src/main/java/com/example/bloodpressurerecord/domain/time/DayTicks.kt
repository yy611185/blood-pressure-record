package com.example.bloodpressurerecord.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

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

/**
 * 支持动态读取系统时区与系统时间/时区广播唤醒的日期流：
 * 跨零点、手动改系统日期时间或切换时区后立即重新计算“今天”与下一次零点等待时长。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun dayTicks(
    zoneProvider: () -> ZoneId,
    clockMillis: () -> Long = System::currentTimeMillis,
    externalTrigger: Flow<Unit> = emptyFlow()
): Flow<LocalDate> = externalTrigger
    .onStart { emit(Unit) }
    .transformLatest {
        while (true) {
            val zone = zoneProvider()
            val nowMillis = clockMillis()
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            emit(today)
            val nextMidnightMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((nextMidnightMillis - nowMillis).coerceAtLeast(MIN_TICK_DELAY_MILLIS))
        }
    }
    .distinctUntilChanged()
