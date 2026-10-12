package com.example.bloodpressurerecord.domain.report

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReportPeriodTest {
    @Test fun `最近周期包含今天并受365天限制`() {
        val zone = ZoneId.of("Asia/Taipei")
        val today = LocalDate.of(2026, 10, 11)
        val period = ReportPeriod.recent(30, today.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        assertEquals(LocalDate.of(2026, 9, 12), period.startDate)
        assertEquals(today, period.endDate)
        assertEquals(30, period.dayCount)
        assertEquals(365, ReportPeriod(today.minusDays(364), today, zone).dayCount)
        assertThrows(IllegalArgumentException::class.java) { ReportPeriod(today.minusDays(365), today, zone) }
        assertThrows(IllegalArgumentException::class.java) { ReportPeriod(today, today.minusDays(1), zone) }
        assertThrows(IllegalArgumentException::class.java) { ReportPeriod.recent(0, 0, zone) }
    }

    @Test fun `夏令时首尾使用自然日而非固定24小时`() {
        val zone = ZoneId.of("America/New_York")
        val spring = LocalDate.of(2026, 3, 8)
        val springPeriod = ReportPeriod(spring, spring, zone)
        assertEquals(23L * 60 * 60 * 1000, springPeriod.endExclusive - springPeriod.startInclusive)
        val fall = LocalDate.of(2026, 11, 1)
        val fallPeriod = ReportPeriod(fall, fall, zone)
        assertEquals(25L * 60 * 60 * 1000, fallPeriod.endExclusive - fallPeriod.startInclusive)
    }
}
