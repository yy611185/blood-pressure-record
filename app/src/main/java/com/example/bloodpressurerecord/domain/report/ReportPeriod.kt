package com.example.bloodpressurerecord.domain.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 本地自然日闭区间；查询使用实际时区下的 epoch 半开区间。 */
data class ReportPeriod(val startDate: LocalDate, val endDate: LocalDate, val zoneId: ZoneId) {
    init {
        require(!endDate.isBefore(startDate)) { "统计结束日期不能早于开始日期" }
        require(ChronoUnit.DAYS.between(startDate, endDate) < ReportPolicy.MAX_PERIOD_DAYS) {
            "统计周期最多为 ${ReportPolicy.MAX_PERIOD_DAYS} 天"
        }
    }
    val dayCount: Int get() = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    val startInclusive: Long get() = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endExclusive: Long get() = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    companion object {
        fun recent(days: Int, nowMillis: Long, zoneId: ZoneId): ReportPeriod {
            require(days in 1..ReportPolicy.MAX_PERIOD_DAYS) { "统计周期必须为 1–365 天" }
            val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            return ReportPeriod(today.minusDays(days.toLong() - 1), today, zoneId)
        }
    }
}
