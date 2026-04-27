package com.example.bloodpressurerecord.domain.calculator

import com.example.bloodpressurerecord.domain.model.SessionStatPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryStatisticsCalculatorTest {
    private val zone = ZoneId.of("Asia/Hong_Kong")
    private fun millisOf(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `能正确统计7天30天平均与次数`() {
        val now = millisOf("2026-04-11")
        val sessions = listOf(
            SessionStatPoint(millisOf("2026-04-11"), 130, 85, false),
            SessionStatPoint(millisOf("2026-04-10"), 140, 90, true),
            SessionStatPoint(millisOf("2026-04-06"), 120, 78, false),
            SessionStatPoint(millisOf("2026-03-25"), 150, 92, true),
            SessionStatPoint(millisOf("2026-03-10"), 160, 95, true)
        )

        val result = HistoryStatisticsCalculator.calculate(sessions, nowMillis = now, zoneId = zone)

        assertEquals(130, result.average7d.avgSystolic)
        assertEquals(84, result.average7d.avgDiastolic)
        assertEquals(135, result.average30d.avgSystolic)
        assertEquals(86, result.average30d.avgDiastolic)
        assertEquals(3, result.weekRecordCount)
        assertEquals(2, result.highRiskCount30d)
    }

    @Test
    fun `趋势按日聚合`() {
        val now = millisOf("2026-04-11")
        val sessions = listOf(
            SessionStatPoint(millisOf("2026-04-11"), 130, 85, false),
            SessionStatPoint(millisOf("2026-04-11") + 60_000, 150, 95, true),
            SessionStatPoint(millisOf("2026-04-10"), 120, 80, false)
        )
        val result = HistoryStatisticsCalculator.calculate(sessions, nowMillis = now, zoneId = zone)
        val last = result.trend7d.last()

        assertEquals("04-11", last.dateKey)
        assertEquals(140, last.avgSystolic)
        assertEquals(90, last.avgDiastolic)
        assertEquals(2, last.count)
    }
}
