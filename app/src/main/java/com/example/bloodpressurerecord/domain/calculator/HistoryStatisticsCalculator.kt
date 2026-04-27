package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.PeriodAverage
import com.example.bloodpressurerecord.domain.model.SessionStatPoint
import com.example.bloodpressurerecord.domain.model.StatisticsResult
import com.example.bloodpressurerecord.domain.model.TrendDayPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

object HistoryStatisticsCalculator {
    fun calculate(
        sessions: List<SessionStatPoint>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StatisticsResult {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val recent7 = sessions.filterByDays(today, zoneId, 7)
        val recent30 = sessions.filterByDays(today, zoneId, 30)
        val weekCount = sessions.countThisWeek(today, zoneId)
        val highRisk30 = recent30.count { it.highRisk }

        return StatisticsResult(
            average7d = recent7.toPeriodAverage(),
            average30d = recent30.toPeriodAverage(),
            weekRecordCount = weekCount,
            highRiskCount30d = highRisk30,
            trend7d = recent7.toDailyTrend(zoneId),
            trend30d = recent30.toDailyTrend(zoneId)
        )
    }

    private fun List<SessionStatPoint>.toPeriodAverage(): PeriodAverage {
        if (isEmpty()) return PeriodAverage(avgSystolic = null, avgDiastolic = null)
        return PeriodAverage(
            avgSystolic = map { it.avgSystolic }.average().roundToInt(),
            avgDiastolic = map { it.avgDiastolic }.average().roundToInt()
        )
    }

    private fun List<SessionStatPoint>.toDailyTrend(zoneId: ZoneId): List<TrendDayPoint> {
        val formatter = DateTimeFormatter.ofPattern("MM-dd")
        return groupBy {
            Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate()
        }.toSortedMap()
            .map { (date, list) ->
                TrendDayPoint(
                    dateKey = date.format(formatter),
                    avgSystolic = list.map { it.avgSystolic }.average().roundToInt(),
                    avgDiastolic = list.map { it.avgDiastolic }.average().roundToInt(),
                    count = list.size
                )
            }
    }

    private fun List<SessionStatPoint>.filterByDays(
        today: LocalDate,
        zoneId: ZoneId,
        days: Int
    ): List<SessionStatPoint> {
        val start = today.minusDays((days - 1).toLong())
        return filter {
            val date = Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate()
            !date.isBefore(start) && !date.isAfter(today)
        }
    }

    private fun List<SessionStatPoint>.countThisWeek(today: LocalDate, zoneId: ZoneId): Int {
        val firstDayOfWeek = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
        val lastDayOfWeek = firstDayOfWeek.plusDays(6)
        return count {
            val date = Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate()
            !date.isBefore(firstDayOfWeek) && !date.isAfter(lastDayOfWeek)
        }
    }
}
