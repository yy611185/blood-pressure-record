package com.example.bloodpressurerecord.domain.time

import java.time.LocalDate

/** 自然周的固定长度：周日起算的 7 天。 */
const val WEEK_LENGTH = 7

/**
 * 本周周日。
 *
 * ISO [java.time.DayOfWeek] 中周一为 1、周日为 7，取 `value % 7` 正好得到
 * “距上一个周日过了几天”，因此周日返回自身。
 */
fun weekStartSunday(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value % 7).toLong())

/**
 * 以自然周（周日→周六）为单位展开的整周日期，固定 7 天、升序。
 *
 * 首页「这一周」的圆点与柱状图、以及对应的数据查询范围都以此为准，
 * 保证任何一天打开看到的时间轴都是稳定的“日、一、二、三、四、五、六”。
 */
fun naturalWeekDates(date: LocalDate): List<LocalDate> {
    val start = weekStartSunday(date)
    return (0 until WEEK_LENGTH).map { start.plusDays(it.toLong()) }
}
