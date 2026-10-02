package com.example.bloodpressurerecord.domain.model

/** 固定日间或夜间时段内的 Session 代表值统计，不表示睡眠血压。 */
data class DayNightAverage(
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val sessionCount: Int = 0,
    val recordDays: Int = 0
)
