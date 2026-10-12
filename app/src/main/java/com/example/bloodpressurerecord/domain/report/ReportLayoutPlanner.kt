package com.example.bloodpressurerecord.domain.report

import com.example.bloodpressurerecord.domain.calculator.SessionStatisticsCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun interface ReportTextMetrics {
    fun width(text: String, size: Float, bold: Boolean): Float
}

/** 由渲染主题注入模板尺寸；领域层不依赖 Android 字体或画布。 */
data class ReportLayoutSpec(
    val pageWidth: Float = 595.28f,
    val pageHeight: Float = 841.89f,
    val margin: Float = 40f,
    val contentWidth: Float = 515.28f,
    val detailRowsPerPage: Int = 30,
    val detailRowHeight: Float = 17.5f,
    val noteColumnWidth: Float = 161.28f,
    val bodySize: Float = 10.5f,
    val smallSize: Float = 8.5f,
    val tinySize: Float = 7.5f,
    val cellPadding: Float = 4f,
    val summaryNoteLines: Int = 5,
    val summaryMedicationCount: Int = 2,
    val summaryMedicationLabelWidth: Float = 150f,
    val summaryMedicationTimeWidth: Float = 95f,
    val supplementLinesPerPage: Int = 30,
    val trendPlotWidth: Float = 449.28f,
    val trendLabelInterval: Int = 5,
    val trendLabelGap: Float = 2f
) {
    init {
        require(detailRowsPerPage > 0 && supplementLinesPerPage > 0)
        require(summaryNoteLines >= 0 && summaryMedicationCount >= 0)
        require(contentWidth > 0 && noteColumnWidth > cellPadding * 2)
        require(trendPlotWidth > 0 && trendLabelInterval > 0 && trendLabelGap >= 0 && tinySize > 0)
    }
}

data class ReportTextLine(val text: String, val bold: Boolean = false, val prefix: String? = null)
/** xOffset only shifts the date label; its measurement point and axis tick stay at the day slot center. */
data class TrendDateLabel(val dayIndex: Int, val text: String, val vertical: Boolean, val xOffset: Float = 0f)
data class ReportDetailRow(
    val date: LocalDate, val morning: ReportMeasurement?, val evening: ReportMeasurement?, val noteText: String,
    val morningCount: Int = if (morning == null) 0 else 1,
    val eveningCount: Int = if (evening == null) 0 else 1,
    val morningTimeLabel: String? = null,
    val eveningTimeLabel: String? = null
)
sealed interface ReportPage {
    data class Summary(
        val noteLines: List<ReportTextLine>,
        val medications: List<ReportMedicationSummary>,
        val hiddenMedicationCount: Int,
        val hiddenNoteCount: Int,
        val dateLabels: List<TrendDateLabel> = emptyList()
    ) : ReportPage
    data class DailyDetail(val rows: List<ReportDetailRow>) : ReportPage
    data class Supplement(val lines: List<ReportTextLine>) : ReportPage
}
data class ReportLayoutPlan(val report: BpReport, val pages: List<ReportPage>) {
    val pageCount: Int get() = pages.size
}

object ReportLayoutPlanner {
    private val dateFormat = DateTimeFormatter.ofPattern("MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val trendDateFormat = DateTimeFormatter.ofPattern("M/d")

    fun plan(report: BpReport, spec: ReportLayoutSpec, metrics: ReportTextMetrics): ReportLayoutPlan {
        val sevenDay = report.template == ReportTemplate.HOME_SEVEN_DAY
        require(!sevenDay || report.period.dayCount == ReportPolicy.SEVEN_DAY_LENGTH)
        val notesByMeasurement = report.notes.associateBy { it.measurementId }
        val fullNotesNeeded = linkedSetOf<Int>()
        val rows = report.days.flatMap { day ->
            (0 until if (sevenDay) 1 else maxOf(day.morning.size, day.evening.size, 1)).map { index ->
                val morning = if (sevenDay) aggregate(day.morning) else day.morning.getOrNull(index)
                val evening = if (sevenDay) aggregate(day.evening) else day.evening.getOrNull(index)
                val noteMeasurements = if (sevenDay) day.morning + day.evening + day.other else listOfNotNull(morning, evening)
                val notes = noteMeasurements.mapNotNull { notesByMeasurement[it.id] }
                val text = notes.joinToString("；") { "[${it.reference}] ${it.text}" }
                val width = spec.noteColumnWidth - spec.cellPadding * 2
                // The daily table uses the body font; a smaller measurement could hide text without a supplement.
                val display = fitSingleLine(text, width, spec.bodySize, metrics)
                if (display != text) notes.forEach { fullNotesNeeded += it.reference }
                ReportDetailRow(
                    day.date, morning, evening, display,
                    if (sevenDay) day.morning.size else if (morning == null) 0 else 1,
                    if (sevenDay) day.evening.size else if (evening == null) 0 else 1,
                    ReportPolicy.MULTIPLE_MEASUREMENTS.takeIf { sevenDay && day.morning.size > 1 },
                    ReportPolicy.MULTIPLE_MEASUREMENTS.takeIf { sevenDay && day.evening.size > 1 }
                )
            }
        }

        val summaryLines = ArrayList<ReportTextLine>()
        var completedSummaryNotes = 0
        report.notes.forEach { note ->
            val prefix = "${dateFormat.format(note.date)} [${note.reference}] "
            val width = (spec.contentWidth - metrics.width(prefix, spec.bodySize, true)).coerceAtLeast(spec.bodySize)
            val lines = wrap(note.text, width, spec.bodySize, metrics)
            val room = spec.summaryNoteLines - summaryLines.size
            if (room > 0) {
                lines.take(room).forEachIndexed { index, text ->
                    summaryLines += ReportTextLine(text, prefix = prefix.takeIf { index == 0 })
                }
                if (lines.size <= room) completedSummaryNotes++
            }
        }

        val supplement = ArrayList<ReportTextLine>()
        fun addWrapped(text: String, bold: Boolean = false) {
            wrap(text, spec.contentWidth, spec.bodySize, metrics, bold).forEach {
                supplement += ReportTextLine(it, bold)
            }
        }
        fun addMeasurement(record: ReportMeasurement, periodLabel: String = "") {
            val local = Instant.ofEpochMilli(record.measuredAt).atZone(report.period.zoneId)
            val note = notesByMeasurement[record.id]
            addWrapped("${dateFormat.format(local)} ${timeFormat.format(local)} $periodLabel ${record.systolic}/${record.diastolic} mmHg  脉搏 ${record.pulse?.toString() ?: "—"} 次/分${note?.let { "  备注 [${it.reference}]" } ?: ""}")
            note?.let { fullNotesNeeded += it.reference }
        }
        if (sevenDay) {
            val repeatedPeriods = report.days.flatMap { day ->
                listOf("早" to day.morning, "晚" to day.evening).filter { it.second.size > 1 }
            }
            if (repeatedPeriods.isNotEmpty()) {
                addWrapped(ReportPolicy.MULTIPLE_MEASUREMENTS_DETAIL, true)
                repeatedPeriods.forEach { (label, records) -> records.forEach { addMeasurement(it, label) } }
            }
        }
        val otherRecords = report.days.flatMap { it.other }
        if (otherRecords.isNotEmpty()) {
            addWrapped("其他时段测量", true)
            otherRecords.forEach { addMeasurement(it) }
        }
        val longNotes = report.notes.filter { it.reference in fullNotesNeeded }
        if (longNotes.isNotEmpty()) {
            addWrapped("完整备注", true)
            longNotes.forEach { note -> addWrapped("${dateFormat.format(note.date)} [${note.reference}] ${note.text}") }
        }
        val extraMedications = report.medications.drop(spec.summaryMedicationCount)
        val supplementaryMedications = report.medications.filterIndexed { index, medication ->
            index >= spec.summaryMedicationCount ||
                metrics.width("${medication.name} ${medication.dosage}", spec.bodySize, true) > spec.summaryMedicationLabelWidth ||
                metrics.width(medication.activeTimes.joinToString("、"), spec.smallSize, false) > spec.summaryMedicationTimeWidth
        }
        if (supplementaryMedications.isNotEmpty()) {
            addWrapped("用药补充", true)
            supplementaryMedications.forEach { medication ->
                addWrapped("${medication.name} ${medication.dosage}", true)
                addWrapped("当前${if (medication.enabled) "启用" else "停用"}；时间：${medication.activeTimes.joinToString("、").ifEmpty { "未设置" }}；保留打卡 ${medication.intakeCount} 次")
            }
            addWrapped(ReportPolicy.MEDICATION_NOTICE)
        }
        return ReportLayoutPlan(report, buildList {
            add(ReportPage.Summary(
                summaryLines, report.medications.take(spec.summaryMedicationCount), extraMedications.size,
                report.notes.size - completedSummaryNotes, trendDateLabels(report, spec, metrics)
            ))
            rows.chunked(spec.detailRowsPerPage).forEach { add(ReportPage.DailyDetail(it)) }
            supplement.chunked(spec.supplementLinesPerPage).forEach { add(ReportPage.Supplement(it)) }
        })
    }

    /** 仅合并明细单元格；ReportDay 中的逐次测量及总体统计保持不变。 */
    private fun aggregate(records: List<ReportMeasurement>): ReportMeasurement? {
        if (records.size <= 1) return records.firstOrNull()
        val pressure = requireNotNull(SessionStatisticsCalculator.pressureAverage(records, { it.systolic }, { it.diastolic }))
        return records.first().copy(
            systolic = pressure.first, diastolic = pressure.second,
            pulse = SessionStatisticsCalculator.pulseAverage(records) { it.pulse }, note = null
        )
    }

    private fun trendDateLabels(report: BpReport, spec: ReportLayoutSpec, metrics: ReportTextMetrics): List<TrendDateLabel> {
        val indices = report.days.indices.filter { it % spec.trendLabelInterval == 0 || it == report.days.lastIndex }
        val texts = indices.map { trendDateFormat.format(report.days[it].date) }
        val slotWidth = spec.trendPlotWidth / report.period.dayCount
        val labelWidths = texts.map { metrics.width(it, spec.tinySize, false) }
        val maxLabelWidth = labelWidths.maxOrNull() ?: 0f
        val vertical = slotWidth * spec.trendLabelInterval < maxLabelWidth + spec.trendLabelGap
        return indices.mapIndexed { index, dayIndex ->
            val offset = if (index == indices.lastIndex && index > 0) {
                val separation = (dayIndex - indices[index - 1]) * slotWidth
                val minimumSeparation = if (vertical) spec.tinySize + spec.trendLabelGap else
                    (labelWidths[index - 1] + labelWidths[index]) / 2 + spec.trendLabelGap
                (minimumSeparation - separation).coerceAtLeast(0f)
            } else 0f
            TrendDateLabel(dayIndex, texts[index], vertical, offset)
        }
    }

    private fun fitSingleLine(text: String, width: Float, size: Float, metrics: ReportTextMetrics): String {
        if ('\n' !in text && '\r' !in text && metrics.width(text, size, false) <= width) return text
        val first = text.lineSequence().firstOrNull().orEmpty()
        var end = 0
        while (end < first.length) {
            val next = first.offsetByCodePoints(end, 1)
            if (metrics.width(first.substring(0, next) + "…", size, false) > width) break
            end = next
        }
        return first.substring(0, end) + "…"
    }

    private fun wrap(
        text: String, width: Float, size: Float, metrics: ReportTextMetrics, bold: Boolean = false
    ): List<String> = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { paragraph ->
        if (paragraph.isEmpty()) return@flatMap listOf("")
        val lines = ArrayList<String>()
        var start = 0
        var cursor = 0
        while (cursor < paragraph.length) {
            val next = paragraph.offsetByCodePoints(cursor, 1)
            if (cursor > start && metrics.width(paragraph.substring(start, next), size, bold) > width) {
                lines += paragraph.substring(start, cursor)
                start = cursor
            }
            cursor = next
        }
        lines += paragraph.substring(start)
        lines
    }
}
