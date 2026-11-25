package com.example.bloodpressurerecord.domain.time

import java.time.LocalDate

/** 自然周的固定长度：周一起算的 7 天。 */
const val WEEK_LENGTH = 7

/**
 * 本周周一（ISO 周一为 1、周日为 7）。
 * 与历史月历及“本周”统计保持 100% 同源。
 */
fun weekStartMonday(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

/** 兼容保留：本周周日。 */
fun weekStartSunday(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value % 7).toLong())

/**
 * 以自然周（周一→周日）为单位展开的整周日期，固定 7 天、升序。
 *
 * 首页「这一周」的圆点与柱状图、以及对应的数据查询范围都以此为准，
 * 保证与历史日历统一为“一、二、三、四、五、六、日”。
 */
fun naturalWeekDates(date: LocalDate): List<LocalDate> {
    val start = weekStartMonday(date)
    return (0 until WEEK_LENGTH).map { start.plusDays(it.toLong()) }
}
