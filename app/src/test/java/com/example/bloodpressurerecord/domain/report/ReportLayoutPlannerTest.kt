package com.example.bloodpressurerecord.domain.report

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReportLayoutPlannerTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val date = LocalDate.of(2026, 10, 11)
    private val metrics = ReportTextMetrics { text, size, _ -> text.codePointCount(0, text.length) * size }
    private val spec = ReportLayoutSpec()

    @Test fun `30和31天分页边界以及90和365天页数`() {
        listOf(30 to 2, 31 to 3, 90 to 4, 365 to 14).forEach { (days, expected) ->
            val records = (0 until days).flatMap { offset ->
                val day = date.minusDays(offset.toLong())
                listOf(record("am$offset", 8, day), record("pm$offset", 18, day))
            }
            val report = report(days, records)
            val plan = ReportLayoutPlanner.plan(report, spec, metrics)
            assertEquals(expected, plan.pageCount)
            val rows = plan.pages.filterIsInstance<ReportPage.DailyDetail>().flatMap { it.rows }
            assertEquals(days, rows.size)
            assertEquals(days * 2, report.measurementCount)
            assertEquals(days, report.validDays)
            assertEquals(report.period.startDate, rows.first().date)
            assertEquals(date, rows.last().date)
            assertTrue(plan.pages.filterIsInstance<ReportPage.DailyDetail>().all { it.rows.size <= 30 })
        }
    }

    @Test fun `同日多次早晚测量逐次保留并成对排列`() {
        val records = listOf(record("am1", 5), record("am2", 8), record("am3", 11), record("pm1", 18), record("pm2", 20))
        val plan = ReportLayoutPlanner.plan(report(30, records), spec, metrics)
        val rows = plan.pages.filterIsInstance<ReportPage.DailyDetail>().flatMap { it.rows }.filter { it.date == date }
        assertEquals(3, rows.size)
        assertEquals(listOf("am1", "am2", "am3"), rows.mapNotNull { it.morning?.id })
        assertEquals(listOf("pm1", "pm2"), rows.mapNotNull { it.evening?.id })
        assertNull(rows.last().evening)
        assertEquals(3, plan.pageCount)
    }

    @Test fun `短备注完整留在表格且摘要最多五行无需补充页`() {
        val records = (0..5).map { index -> record("$index", day = date.minusDays(index.toLong())).copy(note = "备注$index") }
        val plan = ReportLayoutPlanner.plan(report(30, records), spec, metrics)
        val summary = plan.pages.first() as ReportPage.Summary
        assertEquals(5, summary.noteLines.size)
        assertEquals(1, summary.hiddenNoteCount)
        assertEquals(2, plan.pageCount)
        assertTrue(plan.pages.filterIsInstance<ReportPage.DailyDetail>().flatMap { it.rows }.filter { it.morning != null }.all { "备注" in it.noteText })
    }

    @Test fun `表格备注按正文字号测宽且刚超边界时补充全文`() {
        val availableWidth = spec.noteColumnWidth - spec.cellPadding * 2
        val fittingNote = "1234567890"
        val overflowingNote = fittingNote + "1"
        val overflowingCell = "[1] $overflowingNote"
        assertTrue(metrics.width(overflowingCell, spec.smallSize, false) <= availableWidth)
        assertTrue(metrics.width(overflowingCell, spec.bodySize, false) > availableWidth)

        val fitting = ReportLayoutPlanner.plan(report(1, listOf(record("fit").copy(note = fittingNote))), spec, metrics)
        assertEquals("[1] $fittingNote", fitting.pages.filterIsInstance<ReportPage.DailyDetail>().single().rows.single().noteText)
        assertTrue(fitting.pages.none { it is ReportPage.Supplement })

        val overflowing = ReportLayoutPlanner.plan(report(1, listOf(record("overflow").copy(note = overflowingNote))), spec, metrics)
        val display = overflowing.pages.filterIsInstance<ReportPage.DailyDetail>().single().rows.single().noteText
        assertTrue(display.endsWith("…"))
        assertTrue(metrics.width(display, spec.bodySize, false) <= availableWidth)
        val complete = overflowing.pages.filterIsInstance<ReportPage.Supplement>().flatMap { it.lines }.joinToString("") { it.text }
        assertTrue(complete.contains(overflowingNote))
        assertEquals(3, overflowing.pageCount)
    }

    @Test fun `同一行早晚备注换行或合并过宽时两条全文都保留`() {
        val morningNote = "早间第一行\r\n早间第二行"
        val eveningNote = "晚间完整备注"
        val plan = ReportLayoutPlanner.plan(report(1, listOf(
            record("am").copy(note = morningNote), record("pm", 18).copy(note = eveningNote)
        )), spec, metrics)
        val row = plan.pages.filterIsInstance<ReportPage.DailyDetail>().single().rows.single()
        assertTrue(row.noteText.endsWith("…"))
        assertFalse(row.noteText.contains('\n'))
        val full = plan.pages.filterIsInstance<ReportPage.Supplement>().flatMap { it.lines }.joinToString("") { it.text }
        assertTrue(full.contains(morningNote.replace("\r\n", "")))
        assertTrue(full.contains(eveningNote))
        assertTrue(full.contains("[1]"))
        assertTrue(full.contains("[2]"))
    }

    @Test fun `补充明细三十行恰好一页三十一行新增页且没有遗漏`() {
        listOf(29 to 1, 30 to 2).forEach { (records, expectedSupplementPages) ->
            val plan = ReportLayoutPlanner.plan(report(1, (0 until records).map { record("other$it", 12) }), spec, metrics)
            val pages = plan.pages.filterIsInstance<ReportPage.Supplement>()
            assertEquals(expectedSupplementPages, pages.size)
            assertEquals(records + 1, pages.sumOf { it.lines.size })
            assertEquals(30, pages.first().lines.size)
            assertEquals(records, pages.flatMap { it.lines }.count { "12:00" in it.text })
            assertEquals(2 + expectedSupplementPages, plan.pageCount)
        }
    }

    @Test fun `长备注换行补充和其他时段不丢失并计入总页数`() {
        val note = "需要完整保留的备注".repeat(40) + "\n下一行🙂"
        val plan = ReportLayoutPlanner.plan(report(30, listOf(record("long").copy(note = note), record("other", 12))), spec, metrics)
        val detail = plan.pages.filterIsInstance<ReportPage.DailyDetail>().flatMap { it.rows }.last()
        assertTrue(detail.noteText.endsWith("…"))
        val lines = plan.pages.filterIsInstance<ReportPage.Supplement>().flatMap { it.lines }
        val full = lines.joinToString("") { it.text }
        assertTrue(full.contains(note.replace("\n", "")))
        assertTrue(full.contains("12:00"))
        assertTrue(full.contains("120/80"))
        assertTrue(full.contains("其他时段测量"))
        assertTrue(plan.pageCount > 2)
        assertTrue(plan.pages.filterIsInstance<ReportPage.Supplement>().all { it.lines.size <= 30 })
        assertTrue(lines.none { it.text.endsWith("\uD83D") || it.text.startsWith("\uDE42") })
    }

    @Test fun `多药品补充保留停用状态和次数不生成假依从率`() {
        val drugs = (1L..3L).map { ReportMedication(it, "药品$it", "5 mg", it != 3L, emptyList()) }
        val snapshot = ReportSnapshot(listOf(record("one")), medications = drugs)
        val report = requireNotNull(ReportBuilder.build(snapshot, ReportPeriod(date.minusDays(29), date, zone), ReportOptions(), 0))
        val plan = ReportLayoutPlanner.plan(report, spec, metrics)
        val summary = plan.pages.first() as ReportPage.Summary
        assertEquals(2, summary.medications.size)
        assertEquals(1, summary.hiddenMedicationCount)
        val extra = plan.pages.filterIsInstance<ReportPage.Supplement>().flatMap { it.lines }.joinToString("") { it.text }
        assertTrue(extra.contains("药品3"))
        assertTrue(extra.contains("当前停用"))
        assertTrue(extra.contains("保留打卡 0 次"))
        assertFalse(extra.contains("依从率 0%"))
    }

    @Test fun `摘要内药名或时间太长时完整内容进入补充`() {
        val drugName = "完整药品名称".repeat(10)
        val times = (12L downTo 1L).map { ReportMedicationTime(it, "08:${it.toString().padStart(2, '0')}", true) }
        val snapshot = ReportSnapshot(listOf(record("one")), medications = listOf(
            ReportMedication(1, drugName, "5 mg", true, times)))
        val report = requireNotNull(ReportBuilder.build(snapshot, ReportPeriod(date, date, zone), ReportOptions(), 0))
        val plan = ReportLayoutPlanner.plan(report, spec, metrics)
        val summary = plan.pages.first() as ReportPage.Summary
        assertEquals(1, summary.medications.size)
        assertEquals(0, summary.hiddenMedicationCount)
        val text = plan.pages.filterIsInstance<ReportPage.Supplement>().flatMap { it.lines }.joinToString("") { it.text }
        assertTrue(text.contains(drugName))
        val expectedTimes = times.map { it.timeText }.sorted()
        assertEquals(expectedTimes, summary.medications.single().activeTimes)
        assertTrue(text.contains(expectedTimes.joinToString("、")))
    }

    @Test fun `隐私内容不会进入任何页面计划`() {
        val snapshot = ReportSnapshot(
            listOf(record("one").copy(note = "私人备注")), ReportUserProfile("私人姓名", 60),
            listOf(ReportMedication(1, "私人药名", "5 mg", true, emptyList()))
        )
        val report = requireNotNull(ReportBuilder.build(snapshot, ReportPeriod(date, date, zone), ReportOptions(false, false, false), 0))
        val plan = ReportLayoutPlanner.plan(report, spec, metrics)
        val serialized = plan.toString()
        assertFalse(serialized.contains("私人备注"))
        assertFalse(serialized.contains("私人姓名"))
        assertFalse(serialized.contains("私人药名"))
    }

    private fun report(days: Int, records: List<ReportMeasurement>): BpReport = requireNotNull(ReportBuilder.build(
        ReportSnapshot(records), ReportPeriod(date.minusDays(days.toLong() - 1), date, zone), ReportOptions(), 0))

    private fun record(id: String, hour: Int = 8, day: LocalDate = date) =
        ReportMeasurement(id, day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), 120, 80, 70)
}
