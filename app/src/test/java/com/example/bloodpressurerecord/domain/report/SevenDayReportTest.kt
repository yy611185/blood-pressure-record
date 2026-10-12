package com.example.bloodpressurerecord.domain.report

import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SevenDayReportTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val start = LocalDate.of(2026, 10, 5)
    private val period = ReportPeriod(start, start.plusDays(6), zone)
    private val options = ReportOptions(template = ReportTemplate.HOME_SEVEN_DAY)
    private val metrics = ReportTextMetrics { text, size, _ -> text.length * size }

    @Test fun `首日与全部时段按各次代表值等权统计任意时段使当天有效`() {
        val records = listOf(
            measurement("first", 0, 8, 100, 60),
            measurement("last-am", 6, 8, 180, 100),
            measurement("last-pm", 6, 18, 120, 80),
            measurement("last-noon", 6, 12, 200, 110)
        )
        val report = build(records)
        assertEquals(ReportTemplate.HOME_SEVEN_DAY, report.template)
        assertEquals(ReportAverage(150, 88, 70), report.average)
        assertEquals(2, report.validDays)
        assertEquals(4, report.measurementCount)
        assertEquals(ReportAverage(100, 60, 70), report.days.first().average)
        assertEquals(ReportAverage(167, 97, 70), report.days.last().average)
        assertEquals(CategoryCalculator.calculate(167, 97), report.days.last().category)
        assertTrue(report.days.first().evening.isEmpty())
        assertTrue(report.insufficientData)

        val noonOnly = build(listOf(measurement("noon", 3, 12, 140, 90)))
        assertEquals(1, noonOnly.validDays)
        assertEquals(ReportAverage(140, 90, 70), noonOnly.average)
    }

    @Test fun `七行明细合并时段多次代表值而原始读数与所有备注保留补充`() {
        val records = listOf(
            measurement("am1", 0, 6, 100, 60).copy(pulse = null, note = "早一"),
            measurement("am2", 0, 8, 160, 100).copy(pulse = 80, note = "早二"),
            measurement("pm", 0, 18, 180, 110).copy(note = "晚间"),
            measurement("noon", 0, 12, 140, 90).copy(note = "午间")
        )
        val report = build(records)
        val plan = ReportLayoutPlanner.plan(report, ReportLayoutSpec(), metrics)
        val rows = plan.pages.filterIsInstance<ReportPage.DailyDetail>().single().rows
        assertEquals(7, rows.size)
        assertEquals(start, rows.first().date)
        assertEquals(period.endDate, rows.last().date)
        assertEquals(130, rows.first().morning?.systolic)
        assertEquals(80, rows.first().morning?.diastolic)
        assertEquals(80, rows.first().morning?.pulse)
        assertEquals(2, rows.first().morningCount)
        assertEquals(ReportPolicy.MULTIPLE_MEASUREMENTS, rows.first().morningTimeLabel)
        assertEquals(1, rows.first().eveningCount)
        assertNull(rows.first().eveningTimeLabel)
        assertNull(rows.last().morning)
        assertEquals(0, rows.last().morningCount)
        assertEquals(listOf("am1", "am2"), report.days.first().morning.map { it.id })
        assertEquals(130, report.days.first().systolicMin)
        assertEquals(180, report.days.first().systolicMax)
        assertEquals(80, report.days.first().diastolicMin)
        assertEquals(110, report.days.first().diastolicMax)
        val general = requireNotNull(ReportBuilder.build(ReportSnapshot(records), period, ReportOptions(), 0))
        assertEquals(100, general.days.first().systolicMin)
        assertEquals(180, general.days.first().systolicMax)
        assertEquals(60, general.days.first().diastolicMin)
        assertEquals(110, general.days.first().diastolicMax)
        val supplement = plan.pages.filterIsInstance<ReportPage.Supplement>().flatMap { it.lines }.joinToString("") { it.text }
        listOf("06:00", "08:00", "100/60", "160/100", "12:00", "早一", "早二", "晚间", "午间").forEach {
            assertTrue("补充内容应保留 $it", supplement.contains(it))
        }
        assertEquals(3, plan.pageCount)
    }

    @Test fun `专用模板只接受七天普通模板仍逐次保留明细`() {
        val thirtyDays = ReportPeriod(start, start.plusDays(29), zone)
        assertThrows(IllegalArgumentException::class.java) {
            ReportBuilder.build(ReportSnapshot(), thirtyDays, options, 0)
        }
        val records = listOf(measurement("a", 0, 6, 120, 80), measurement("b", 0, 8, 140, 90))
        val general = requireNotNull(ReportBuilder.build(ReportSnapshot(records), thirtyDays, ReportOptions(), 0))
        val plan = ReportLayoutPlanner.plan(general, ReportLayoutSpec(), metrics)
        val rows = plan.pages.filterIsInstance<ReportPage.DailyDetail>().flatMap { it.rows }
        assertEquals(ReportTemplate.GENERAL, general.template)
        assertEquals(31, rows.size)
        assertEquals(listOf("a", "b"), rows.filter { it.date == start }.mapNotNull { it.morning?.id })
        assertTrue(rows.all { it.morningTimeLabel == null && it.eveningTimeLabel == null })
    }

    @Test fun `专用模板隐私关闭后补充页也不保留被隐藏信息`() {
        val snapshot = ReportSnapshot(
            listOf(measurement("a", 0, 6, 120, 80).copy(note = "私人备注"), measurement("b", 0, 8, 140, 90)),
            profile = ReportUserProfile("私人姓名", 50),
            medications = listOf(ReportMedication(1, "私人药名", "5 mg", true, emptyList()))
        )
        val report = requireNotNull(ReportBuilder.build(snapshot, period, options.copy(includeNotes = false, includeMedications = false), 0))
        val plan = ReportLayoutPlanner.plan(report, ReportLayoutSpec(), metrics)
        val content = plan.toString()
        listOf("私人备注", "私人姓名", "私人药名").forEach { assertFalse(content.contains(it)) }
        assertEquals(ReportTemplate.HOME_SEVEN_DAY, report.template)
    }

    private fun build(records: List<ReportMeasurement>) = requireNotNull(
        ReportBuilder.build(ReportSnapshot(records), period, options, 0)
    )

    private fun measurement(id: String, offset: Long, hour: Int, systolic: Int, diastolic: Int) =
        ReportMeasurement(id, start.plusDays(offset).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), systolic, diastolic, 70)
}
