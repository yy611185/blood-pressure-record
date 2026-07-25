package com.example.bloodpressurerecord.ui.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.example.bloodpressurerecord.domain.time.EpochMillisRange
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange

enum class HistoryViewMode {
    CALENDAR,
    RECENT
}

enum class RecentPeriod {
    THIS_WEEK,
    THIS_MONTH
}

object HistoryDateRanges {
    fun recent(
        period: RecentPeriod,
        today: LocalDate,
        zoneId: ZoneId
    ): EpochMillisRange {
        return when (period) {
            RecentPeriod.THIS_WEEK -> {
                val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                EpochMillisRange(
                    startInclusive = monday.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    endExclusive = monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
            }
            RecentPeriod.THIS_MONTH -> YearMonth.from(today).toEpochMillisRange(zoneId)
        }
    }
}

data class CalendarDaySummary(
    val date: LocalDate,
    val recordCount: Int,
    val containsHighRisk: Boolean
)

data class CalendarMonthCell(
    val date: LocalDate?,
    val isInDisplayedMonth: Boolean
)

object CalendarMonthLayout {
    const val COLUMN_COUNT = 7

    /**
     * 星期从周日开始。跨月位置留空，行数按当前月份实际需要生成。
     */
    fun cells(month: YearMonth): List<CalendarMonthCell> {
        val leadingBlanks = month.atDay(1).dayOfWeek.value % COLUMN_COUNT
        return buildList {
            repeat(leadingBlanks) {
                add(CalendarMonthCell(date = null, isInDisplayedMonth = false))
            }
            for (day in 1..month.lengthOfMonth()) {
                add(CalendarMonthCell(month.atDay(day), isInDisplayedMonth = true))
            }
            while (size % COLUMN_COUNT != 0) {
                add(CalendarMonthCell(date = null, isInDisplayedMonth = false))
            }
        }
    }
}

enum class CalendarLoadingState {
    LOADING,
    CONTENT,
    ERROR
}
