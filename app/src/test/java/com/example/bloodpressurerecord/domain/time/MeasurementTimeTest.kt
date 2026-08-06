package com.example.bloodpressurerecord.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementTimeTest {
    @Test
    fun `未来测量时间只允许两分钟设备误差`() {
        val now = Instant.parse("2026-08-06T04:00:00Z").toEpochMilli()

        assertEquals(
            null,
            MeasurementTimestampValidator.validate(
                now + MeasurementTimestampValidator.FUTURE_TOLERANCE_MILLIS,
                now
            )
        )
        assertEquals(
            MeasurementTimestampValidator.FUTURE_MEASUREMENT_TIME_MESSAGE,
            MeasurementTimestampValidator.validate(
                now + MeasurementTimestampValidator.FUTURE_TOLERANCE_MILLIS + 1,
                now
            )
        )
        assertEquals(null, MeasurementTimestampValidator.validate(now - 1, now))
    }

    @Test
    fun `普通日期使用自然日半开区间`() {
        val range = LocalDate.of(2026, 7, 25)
            .toEpochMillisRange(ZoneId.of("Asia/Taipei"))

        assertEquals(Instant.parse("2026-07-24T16:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-07-25T16:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun `月末年末和闰日边界正确`() {
        val zone = ZoneId.of("UTC")
        assertEquals(
            Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(),
            LocalDate.of(2026, 12, 31).toEpochMillisRange(zone).endExclusive
        )
        assertEquals(
            Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(),
            LocalDate.of(2024, 2, 29).toEpochMillisRange(zone).endExclusive
        )
    }

    @Test
    fun `夏令时开始日是23小时`() {
        val range = LocalDate.of(2026, 3, 8)
            .toEpochMillisRange(ZoneId.of("America/New_York"))

        assertEquals(23L * 60 * 60 * 1_000, range.endExclusive - range.startInclusive)
    }

    @Test
    fun `夏令时结束日是25小时`() {
        val range = LocalDate.of(2026, 11, 1)
            .toEpochMillisRange(ZoneId.of("America/New_York"))

        assertEquals(25L * 60 * 60 * 1_000, range.endExclusive - range.startInclusive)
    }

    @Test
    fun `同一日期在不同时区生成不同绝对边界`() {
        val date = LocalDate.of(2026, 7, 25)
        val taipei = date.toEpochMillisRange(ZoneId.of("Asia/Taipei"))
        val losAngeles = date.toEpochMillisRange(ZoneId.of("America/Los_Angeles"))

        assertFalse(taipei.startInclusive == losAngeles.startInclusive)
    }

    @Test
    fun `当天最后一毫秒属于当天而下一天零点不属于`() {
        val range = LocalDate.of(2026, 7, 25).toEpochMillisRange(ZoneId.of("UTC"))

        assertTrue(range.endExclusive - 1 in range)
        assertFalse(range.endExclusive in range)
    }

    @Test
    fun `月份范围从月初到下月月初`() {
        val range = YearMonth.of(2024, 2).toEpochMillisRange(ZoneId.of("UTC"))

        assertEquals(Instant.parse("2024-02-01T00:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(), range.endExclusive)
    }
}
