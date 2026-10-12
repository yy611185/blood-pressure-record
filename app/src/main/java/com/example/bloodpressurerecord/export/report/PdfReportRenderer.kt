package com.example.bloodpressurerecord.export.report

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.domain.report.*
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Android drawing only: statistics, text wrapping and page breaks are planned in the domain layer. */
class PdfReportRenderer(private val appName: String) {
    private val regular = Typeface.DEFAULT
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.SIMPLIFIED_CHINESE)
    private val shortDate = DateTimeFormatter.ofPattern("M/d", Locale.SIMPLIFIED_CHINESE)
    private val detailDate = DateTimeFormatter.ofPattern("MM-dd E", Locale.SIMPLIFIED_CHINESE)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)

    fun textMetrics(): ReportTextMetrics = ReportTextMetrics { value, size, isBold ->
        textPaint(size, ReportTheme.TEXT, isBold).measureText(value)
    }

    /** Publish only a complete PDF. A failed render cannot leave a readable partial report. */
    fun render(plan: ReportLayoutPlan, target: File, pageIndices: List<Int>? = null) {
        val indices = pageIndices ?: plan.pages.indices.toList()
        require(indices.isNotEmpty() && indices.distinct().size == indices.size)
        require(indices.all { it in plan.pages.indices })
        require(!target.exists()) { "Report destination already exists" }
        val parent = requireNotNull(target.parentFile)
        check(parent.mkdirs() || parent.isDirectory)
        val partial = File(parent, "${target.name}.partial")
        try {
            val document = PdfDocument()
            try {
                indices.forEachIndexed { outputIndex, pageIndex ->
                    val info = PdfDocument.PageInfo.Builder(
                        ReportTheme.PAGE_WIDTH, ReportTheme.PAGE_HEIGHT, outputIndex + 1
                    ).create()
                    val page = document.startPage(info)
                    try {
                        val canvas = page.canvas
                        canvas.drawColor(ReportTheme.WHITE)
                        when (val content = plan.pages[pageIndex]) {
                            is ReportPage.Summary -> drawSummary(canvas, plan.report, content)
                            is ReportPage.DailyDetail -> drawDaily(canvas, plan.report, content)
                            is ReportPage.Supplement -> drawSupplement(canvas, plan.report, content)
                        }
                        drawFooter(canvas, pageIndex + 1, plan.pageCount)
                    } finally {
                        document.finishPage(page)
                    }
                }
                partial.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }
            check(partial.renameTo(target)) { "Unable to publish report" }
        } finally {
            partial.delete()
        }
    }

    private fun drawSummary(canvas: Canvas, report: BpReport, summary: ReportPage.Summary): Float {
        drawSummaryHeader(canvas, report)
        drawCore(canvas, report)
        drawTrend(canvas, report)
        drawDistribution(canvas, report)
        drawMedications(canvas, summary)
        drawNotes(canvas, summary)
        return ReportTheme.FOOTER_LINE_Y - ReportTheme.MARGIN
    }

    private fun drawSummaryHeader(canvas: Canvas, report: BpReport): Float {
        text(canvas, ReportPolicy.TITLE, ReportTheme.MARGIN, ReportTheme.HEADER_TITLE_Y,
            ReportTheme.TITLE_SIZE, bold = true)
        text(canvas, appName, ReportTheme.MARGIN, ReportTheme.HEADER_APP_Y,
            ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
        text(canvas, "统计周期  ${periodText(report)}", right, ReportTheme.HEADER_PERIOD_Y,
            ReportTheme.HEADER_META_SIZE, align = Paint.Align.RIGHT)
        val generated = Instant.ofEpochMilli(report.generatedAtMillis).atZone(report.period.zoneId)
        text(canvas, "生成日期  ${dateFormat.format(generated)}", right, ReportTheme.HEADER_GENERATED_Y,
            ReportTheme.HEADER_META_SIZE, align = Paint.Align.RIGHT)
        report.profile?.let { profile ->
            val name = profile.name?.takeIf { it.isNotBlank() } ?: "未填写"
            val age = profile.age?.let { "$it 岁" } ?: "未填写"
            clippedText(canvas, "姓名  $name    年龄  $age", ReportTheme.MARGIN,
                ReportTheme.HEADER_IDENTITY_Y, ReportTheme.CONTENT_WIDTH, ReportTheme.BODY_SIZE)
        }
        line(canvas, ReportTheme.MARGIN, ReportTheme.HEADER_LINE_Y, right, ReportTheme.HEADER_LINE_Y)
        return ReportTheme.HEADER_LINE_Y - ReportTheme.MARGIN
    }

    private fun drawCore(canvas: Canvas, report: BpReport): Float {
        val labels = listOf("平均血压", "早晨均值", "晚间均值", "平均脉搏", "有效测量")
        val values = listOf(bp(report.average), bp(report.morningAverage), bp(report.eveningAverage),
            report.average.pulse?.toString() ?: "—", "${report.validDays}/${report.period.dayCount}")
        val units = listOf("mmHg", "mmHg", "mmHg", "次/分", "天 · 共 ${report.measurementCount} 次")
        var x = ReportTheme.MARGIN
        labels.indices.forEach { index ->
            val inset = if (index == 0) 0f else ReportTheme.CORE_CELL_INSET
            text(canvas, labels[index], x + inset, ReportTheme.CORE_TOP + ReportTheme.CORE_LABEL_OFFSET,
                ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
            text(canvas, values[index], x + inset, ReportTheme.CORE_TOP + ReportTheme.CORE_VALUE_OFFSET,
                if (index == 0) ReportTheme.CORE_SIZE else ReportTheme.SECONDARY_SIZE, bold = true)
            text(canvas, units[index], x + inset, ReportTheme.CORE_TOP + ReportTheme.CORE_UNIT_OFFSET,
                ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
            x += ReportTheme.CORE_WIDTHS[index]
            if (index < labels.lastIndex) line(canvas, x, ReportTheme.CORE_TOP, x,
                ReportTheme.CORE_TOP + ReportTheme.CORE_HEIGHT - ReportTheme.CORE_VERTICAL_BOTTOM_INSET)
        }
        line(canvas, ReportTheme.MARGIN, ReportTheme.CORE_TOP + ReportTheme.CORE_HEIGHT,
            right, ReportTheme.CORE_TOP + ReportTheme.CORE_HEIGHT)
        text(canvas, "数据覆盖：有效测量 ${report.validDays}/${report.period.dayCount} 天，共 ${report.measurementCount} 次。",
            ReportTheme.MARGIN, ReportTheme.COVERAGE_Y, ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
        if (report.insufficientData) text(canvas, ReportPolicy.INSUFFICIENT_DATA,
            ReportTheme.MARGIN, ReportTheme.INSUFFICIENT_Y, ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
        return ReportTheme.COVERAGE_Y - ReportTheme.CORE_TOP
    }

    private fun drawTrend(canvas: Canvas, report: BpReport): Float {
        text(canvas, "血压趋势", ReportTheme.MARGIN, ReportTheme.TREND_TITLE_Y,
            ReportTheme.SECTION_SIZE, bold = true)
        drawLegend(canvas)
        val left = ReportTheme.MARGIN + ReportTheme.PLOT_LEFT_INSET
        val plotRight = right - ReportTheme.PLOT_RIGHT_INSET
        val bottom = ReportTheme.PLOT_TOP + ReportTheme.PLOT_HEIGHT
        text(canvas, "mmHg", left - ReportTheme.PLOT_LEFT_INSET,
            ReportTheme.PLOT_TOP - ReportTheme.AXIS_UNIT_OFFSET, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        for (value in ReportTheme.AXIS_MIN..ReportTheme.AXIS_MAX step ReportTheme.AXIS_STEP) {
            val y = valueY(value)
            line(canvas, left, y, plotRight, y, ReportTheme.GRID)
            text(canvas, value.toString(), left - ReportTheme.AXIS_LABEL_OFFSET,
                y + ReportTheme.AXIS_LABEL_BASELINE_OFFSET, ReportTheme.TINY_SIZE,
                ReportTheme.SECONDARY_TEXT, align = Paint.Align.RIGHT)
        }
        line(canvas, left, bottom, plotRight, bottom, ReportTheme.AXIS)
        listOf(ReportTheme.REFERENCE_SYSTOLIC, ReportTheme.REFERENCE_DIASTOLIC).forEach { reference ->
            line(canvas, left, valueY(reference), plotRight, valueY(reference), ReportTheme.REFERENCE,
                dashed = true)
            text(canvas, "参考 $reference", plotRight + ReportTheme.REFERENCE_LABEL_OFFSET,
                valueY(reference) + ReportTheme.AXIS_LABEL_BASELINE_OFFSET,
                ReportTheme.TINY_SIZE, ReportTheme.REFERENCE)
        }
        val slot = (plotRight - left) / report.period.dayCount
        fun pointX(index: Int) = left + (index + 0.5f) * slot
        report.days.forEachIndexed { index, day ->
            if (index % ReportTheme.X_LABEL_INTERVAL == 0 || index == report.days.lastIndex) {
                val x = pointX(index)
                line(canvas, x, bottom, x, bottom + ReportTheme.AXIS_TICK_LENGTH, ReportTheme.AXIS)
                text(canvas, shortDate.format(day.date), x, bottom + ReportTheme.X_LABEL_OFFSET,
                    ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT, align = Paint.Align.CENTER)
            }
        }
        var previousSystolic: Pair<Float, Float>? = null
        var previousDiastolic: Pair<Float, Float>? = null
        report.days.forEachIndexed { index, day ->
            val x = pointX(index)
            drawRange(canvas, x, day.systolicMin, day.systolicMax, ReportTheme.SYSTOLIC)
            drawRange(canvas, x, day.diastolicMin, day.diastolicMax, ReportTheme.DIASTOLIC)
            day.average.systolic?.let { value ->
                val y = valueY(value)
                previousSystolic?.takeIf { report.validDays > 1 }?.let { previous ->
                    line(canvas, previous.first, previous.second, x, y, ReportTheme.SYSTOLIC,
                        ReportTheme.CONNECTOR_WIDTH, ReportTheme.CONNECTOR_ALPHA)
                }
                previousSystolic = x to y
                marker(canvas, x, y, ReportTheme.SYSTOLIC, true)
                overflow(canvas, x, value, ReportTheme.SYSTOLIC)
            }
            day.average.diastolic?.let { value ->
                val y = valueY(value)
                previousDiastolic?.takeIf { report.validDays > 1 }?.let { previous ->
                    line(canvas, previous.first, previous.second, x, y, ReportTheme.DIASTOLIC,
                        ReportTheme.CONNECTOR_WIDTH, ReportTheme.CONNECTOR_ALPHA)
                }
                previousDiastolic = x to y
                marker(canvas, x, y, ReportTheme.DIASTOLIC, false)
                overflow(canvas, x, value, ReportTheme.DIASTOLIC)
            }
        }
        if (report.days.any { day -> listOfNotNull(day.average.systolic, day.average.diastolic,
                day.systolicMin, day.systolicMax, day.diastolicMin, day.diastolicMax)
                .any { it !in ReportTheme.AXIS_MIN..ReportTheme.AXIS_MAX } }) {
            text(canvas, "箭头表示超出图示范围，完整读数见明细。", ReportTheme.MARGIN,
                ReportTheme.OVERFLOW_NOTICE_Y, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        }
        return bottom + ReportTheme.X_LABEL_OFFSET - ReportTheme.TREND_TITLE_Y
    }

    private fun drawLegend(canvas: Canvas) {
        val baseline = ReportTheme.LEGEND_BASELINE
        val centerY = baseline - ReportTheme.LEGEND_CENTER_OFFSET
        marker(canvas, ReportTheme.LEGEND_CIRCLE_X, centerY, ReportTheme.SYSTOLIC, true)
        text(canvas, "收缩压", ReportTheme.LEGEND_CIRCLE_X + ReportTheme.LEGEND_LABEL_GAP,
            baseline, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        marker(canvas, ReportTheme.LEGEND_DIAMOND_X, centerY, ReportTheme.DIASTOLIC, false)
        text(canvas, "舒张压", ReportTheme.LEGEND_DIAMOND_X + ReportTheme.LEGEND_LABEL_GAP,
            baseline, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        line(canvas, ReportTheme.LEGEND_RANGE_X, centerY - ReportTheme.LEGEND_RANGE_HALF_HEIGHT,
            ReportTheme.LEGEND_RANGE_X, centerY + ReportTheme.LEGEND_RANGE_HALF_HEIGHT,
            ReportTheme.AXIS, ReportTheme.RANGE_WIDTH)
        text(canvas, "当日早晚范围", ReportTheme.LEGEND_RANGE_X + ReportTheme.LEGEND_LABEL_GAP,
            baseline, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        line(canvas, ReportTheme.LEGEND_REFERENCE_X, centerY,
            ReportTheme.LEGEND_REFERENCE_X + ReportTheme.LEGEND_SAMPLE_LENGTH, centerY,
            ReportTheme.REFERENCE, dashed = true)
        text(canvas, "参考线（诊室标准）",
            ReportTheme.LEGEND_REFERENCE_X + ReportTheme.LEGEND_SAMPLE_LENGTH + ReportTheme.CELL_PADDING,
            baseline, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
    }

    private fun drawRange(canvas: Canvas, x: Float, minimum: Int?, maximum: Int?, color: Int) {
        if (minimum == null || maximum == null) return
        line(canvas, x, valueY(minimum), x, valueY(maximum), color,
            ReportTheme.RANGE_WIDTH, ReportTheme.RANGE_ALPHA)
        overflow(canvas, x, minimum, color)
        if (minimum != maximum) overflow(canvas, x, maximum, color)
    }

    private fun marker(canvas: Canvas, x: Float, y: Float, color: Int, systolic: Boolean) {
        if (systolic) {
            canvas.drawCircle(x, y, ReportTheme.SYSTOLIC_RADIUS, fillPaint(color))
        } else {
            val radius = ReportTheme.DIASTOLIC_RADIUS
            val diamond = Path().apply {
                moveTo(x, y - radius); lineTo(x + radius, y); lineTo(x, y + radius)
                lineTo(x - radius, y); close()
            }
            canvas.drawPath(diamond, fillPaint(ReportTheme.WHITE))
            canvas.drawPath(diamond, strokePaint(color, ReportTheme.DIASTOLIC_STROKE))
        }
    }

    private fun overflow(canvas: Canvas, x: Float, value: Int, color: Int) {
        if (value in ReportTheme.AXIS_MIN..ReportTheme.AXIS_MAX) return
        val y = valueY(value)
        val direction = if (value > ReportTheme.AXIS_MAX) 1 else -1
        val arrow = ReportTheme.OVERFLOW_ARROW
        canvas.drawPath(Path().apply {
            moveTo(x, y); lineTo(x - arrow, y + direction * arrow * 2)
            lineTo(x + arrow, y + direction * arrow * 2); close()
        }, fillPaint(color))
    }

    private fun drawDistribution(canvas: Canvas, report: BpReport): Float {
        text(canvas, "分级分布（按日均值）", ReportTheme.MARGIN, ReportTheme.DISTRIBUTION_TITLE_Y,
            ReportTheme.SECTION_SIZE, bold = true)
        val width = ReportTheme.CONTENT_WIDTH / 2 - ReportTheme.HALF_GAP
        val barX = ReportTheme.MARGIN + ReportTheme.DISTRIBUTION_BAR_INSET
        val barWidth = width - ReportTheme.DISTRIBUTION_BAR_INSET - ReportTheme.DISTRIBUTION_COUNT_WIDTH
        val labels = listOf("偏低", "正常", "正常高值", "1 级", "2 级", "3 级")
        BloodPressureCategory.entries.forEachIndexed { index, category ->
            val top = ReportTheme.DISTRIBUTION_ROW_TOP + index * ReportTheme.DISTRIBUTION_ROW_HEIGHT
            text(canvas, labels[index], ReportTheme.MARGIN, top + ReportTheme.CELL_BASELINE_OFFSET,
                ReportTheme.BODY_SIZE)
            val barTop = top + ReportTheme.DISTRIBUTION_BAR_TOP_OFFSET
            rect(canvas, barX, barTop, barWidth, ReportTheme.DISTRIBUTION_BAR_HEIGHT,
                ReportTheme.PROGRESS_BACKGROUND, rounded = true)
            val count = report.categoryDays[category] ?: 0
            if (count > 0 && report.validDays > 0) rect(canvas, barX, barTop,
                barWidth * count / report.validDays, ReportTheme.DISTRIBUTION_BAR_HEIGHT,
                ReportTheme.CATEGORY_COLORS[index], rounded = true)
            text(canvas, "$count 天", ReportTheme.MARGIN + width, top + ReportTheme.CELL_BASELINE_OFFSET,
                ReportTheme.BODY_SIZE, align = Paint.Align.RIGHT)
        }
        text(canvas, "分级复用应用成人诊室标准，按日均值统计。", ReportTheme.MARGIN,
            ReportTheme.DISTRIBUTION_NOTICE_Y, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        text(canvas, ReportPolicy.SCENE_NOTICE, ReportTheme.MARGIN,
            ReportTheme.DISTRIBUTION_NOTICE_Y + ReportTheme.NOTICE_LINE_HEIGHT,
            ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        return ReportTheme.DISTRIBUTION_NOTICE_Y - ReportTheme.DISTRIBUTION_TITLE_Y
    }

    private fun drawMedications(canvas: Canvas, summary: ReportPage.Summary): Float {
        val x = ReportTheme.MARGIN + ReportTheme.CONTENT_WIDTH / 2 + ReportTheme.HALF_GAP
        val width = right - x
        text(canvas, "服药情况", x, ReportTheme.MEDICATION_TITLE_Y, ReportTheme.SECTION_SIZE, bold = true)
        if (summary.medications.isEmpty()) text(canvas, "未提供药品资料", x,
            ReportTheme.MEDICATION_FIRST_Y, ReportTheme.BODY_SIZE, ReportTheme.SECONDARY_TEXT)
        summary.medications.forEachIndexed { index, medication ->
            val y = ReportTheme.MEDICATION_FIRST_Y + index * ReportTheme.MEDICATION_BLOCK_HEIGHT
            val label = "${medication.name} ${medication.dosage}"
            val times = medication.activeTimes.joinToString("、").ifEmpty { "未设置" }
            clippedText(canvas, label, x, y, ReportTheme.MEDICATION_LABEL_WIDTH,
                ReportTheme.BODY_SIZE, bold = true)
            clippedText(canvas, times, right - ReportTheme.MEDICATION_TIME_WIDTH,
                y, ReportTheme.MEDICATION_TIME_WIDTH, ReportTheme.CAPTION_SIZE,
                ReportTheme.SECONDARY_TEXT, align = Paint.Align.RIGHT)
            clippedText(canvas, "当前${if (medication.enabled) "启用" else "停用"} · 保留打卡 ${medication.intakeCount} 次",
                x, y + ReportTheme.MEDICATION_INFO_OFFSET, width, ReportTheme.BODY_SIZE)
            if (textMetrics().width(label, ReportTheme.BODY_SIZE, true) > ReportTheme.MEDICATION_LABEL_WIDTH ||
                textMetrics().width(times, ReportTheme.CAPTION_SIZE, false) > ReportTheme.MEDICATION_TIME_WIDTH) {
                text(canvas, "完整药品信息详见补充页", x, y + ReportTheme.MEDICATION_STATUS_OFFSET,
                    ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
            }
        }
        if (summary.hiddenMedicationCount > 0) text(canvas,
            "另有 ${summary.hiddenMedicationCount} 种药品见补充明细", x, ReportTheme.MEDICATION_MORE_Y,
            ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
        text(canvas, "仅列当前药品及保留的打卡次数；历史计划不足，", x,
            ReportTheme.MEDICATION_NOTICE_Y, ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        text(canvas, "无法统计依从率、漏服或调药。", x,
            ReportTheme.MEDICATION_NOTICE_Y + ReportTheme.NOTICE_LINE_HEIGHT,
            ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        return ReportTheme.MEDICATION_NOTICE_Y - ReportTheme.MEDICATION_TITLE_Y
    }

    private fun drawNotes(canvas: Canvas, summary: ReportPage.Summary): Float {
        text(canvas, "备注", ReportTheme.MARGIN, ReportTheme.NOTES_TITLE_Y, ReportTheme.SECTION_SIZE, bold = true)
        if (summary.noteLines.isEmpty()) text(canvas, "未提供备注", ReportTheme.MARGIN,
            ReportTheme.NOTES_FIRST_Y, ReportTheme.BODY_SIZE, ReportTheme.SECONDARY_TEXT)
        summary.noteLines.forEachIndexed { index, planned ->
            drawTextLine(canvas, planned, ReportTheme.MARGIN,
                ReportTheme.NOTES_FIRST_Y + index * ReportTheme.NOTES_LINE_HEIGHT)
        }
        if (summary.hiddenNoteCount > 0) text(canvas,
            "另有 ${summary.hiddenNoteCount} 条备注见明细页", ReportTheme.MARGIN,
            ReportTheme.NOTES_FIRST_Y + ReportTheme.SUMMARY_NOTE_LINES * ReportTheme.NOTES_LINE_HEIGHT,
            ReportTheme.CAPTION_SIZE, ReportTheme.SECONDARY_TEXT)
        return ReportTheme.SUMMARY_NOTE_LINES * ReportTheme.NOTES_LINE_HEIGHT
    }

    private fun drawDetailHeader(canvas: Canvas, report: BpReport, supplement: Boolean): Float {
        text(canvas, "${ReportPolicy.TITLE} · ${if (supplement) "补充明细" else "逐日明细"}",
            ReportTheme.MARGIN, ReportTheme.DETAIL_TITLE_Y, ReportTheme.SECTION_SIZE, bold = true)
        val identity = report.profile?.name?.takeIf { it.isNotBlank() }?.let { "$it  " }.orEmpty()
        clippedText(canvas, identity + periodText(report), ReportTheme.DETAIL_METADATA_X,
            ReportTheme.DETAIL_TITLE_Y, right - ReportTheme.DETAIL_METADATA_X,
            ReportTheme.CAPTION_SIZE, align = Paint.Align.RIGHT)
        line(canvas, ReportTheme.MARGIN, ReportTheme.DETAIL_LINE_Y, right, ReportTheme.DETAIL_LINE_Y)
        return ReportTheme.DETAIL_LINE_Y - ReportTheme.MARGIN
    }

    private fun drawDaily(canvas: Canvas, report: BpReport, detail: ReportPage.DailyDetail): Float {
        drawDetailHeader(canvas, report, false)
        drawTableHeader(canvas)
        val boundaries = tableBoundaries()
        detail.rows.forEachIndexed { rowIndex, row ->
            val top = ReportTheme.TABLE_ROW_TOP + rowIndex * ReportTheme.TABLE_ROW_HEIGHT
            if (rowIndex % 2 == 0) rect(canvas, ReportTheme.MARGIN, top,
                ReportTheme.CONTENT_WIDTH, ReportTheme.TABLE_ROW_HEIGHT, ReportTheme.ZEBRA)
            cell(canvas, detailDate.format(row.date).replace("星期", "周"), boundaries[0], top,
                ReportTheme.TABLE_WIDTHS[0])
            drawReadingCells(canvas, row.morning, report, boundaries, 1, top)
            drawReadingCells(canvas, row.evening, report, boundaries, 4, top)
            cell(canvas, row.noteText, boundaries[7], top, ReportTheme.TABLE_WIDTHS[7])
        }
        line(canvas, ReportTheme.MARGIN,
            ReportTheme.TABLE_ROW_TOP + detail.rows.size * ReportTheme.TABLE_ROW_HEIGHT, right,
            ReportTheme.TABLE_ROW_TOP + detail.rows.size * ReportTheme.TABLE_ROW_HEIGHT)
        text(canvas, "加粗并带底色：高于参考线（${ReportTheme.REFERENCE_SYSTOLIC}/${ReportTheme.REFERENCE_DIASTOLIC}）。—：该时段无记录。每个时段为当次多组读数的平均值。",
            ReportTheme.MARGIN, ReportTheme.DETAIL_LEGEND_Y,
            ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        return ReportTheme.DETAIL_LEGEND_Y - ReportTheme.DETAIL_TITLE_Y
    }

    private fun drawTableHeader(canvas: Canvas): Float {
        val xs = tableBoundaries()
        rect(canvas, ReportTheme.MARGIN, ReportTheme.TABLE_TOP, ReportTheme.CONTENT_WIDTH,
            ReportTheme.TABLE_HEADER_HEIGHT * 2, ReportTheme.TABLE_HEADER)
        val firstBaseline = ReportTheme.TABLE_TOP + ReportTheme.CELL_BASELINE_OFFSET
        val secondBaseline = firstBaseline + ReportTheme.TABLE_HEADER_HEIGHT
        text(canvas, "日期", xs[0] + ReportTheme.TABLE_WIDTHS[0] / 2, firstBaseline,
            ReportTheme.BODY_SIZE, bold = true, align = Paint.Align.CENTER)
        text(canvas, "早", (xs[1] + xs[4]) / 2, firstBaseline,
            ReportTheme.BODY_SIZE, bold = true, align = Paint.Align.CENTER)
        text(canvas, "晚", (xs[4] + xs[7]) / 2, firstBaseline,
            ReportTheme.BODY_SIZE, bold = true, align = Paint.Align.CENTER)
        text(canvas, "备注", (xs[7] + right) / 2, firstBaseline,
            ReportTheme.BODY_SIZE, bold = true, align = Paint.Align.CENTER)
        listOf(1, 4).forEach { start ->
            listOf("血压", "脉搏", "时间").forEachIndexed { offset, label ->
                val index = start + offset
                text(canvas, label, xs[index] + ReportTheme.TABLE_WIDTHS[index] / 2,
                    secondBaseline, ReportTheme.BODY_SIZE, bold = true, align = Paint.Align.CENTER)
            }
        }
        return ReportTheme.TABLE_HEADER_HEIGHT * 2
    }

    private fun drawReadingCells(canvas: Canvas, record: ReportMeasurement?, report: BpReport,
        boundaries: List<Float>, start: Int, top: Float) {
        val high = record != null && (record.systolic >= ReportTheme.REFERENCE_SYSTOLIC ||
            record.diastolic >= ReportTheme.REFERENCE_DIASTOLIC)
        if (high) rect(canvas, boundaries[start], top, ReportTheme.TABLE_WIDTHS[start],
            ReportTheme.TABLE_ROW_HEIGHT, ReportTheme.HIGH_BACKGROUND)
        cell(canvas, record?.let { "${it.systolic}/${it.diastolic}" } ?: "—", boundaries[start], top,
            ReportTheme.TABLE_WIDTHS[start], bold = high)
        cell(canvas, record?.pulse?.toString() ?: "—", boundaries[start + 1], top,
            ReportTheme.TABLE_WIDTHS[start + 1])
        val time = record?.let {
            timeFormat.format(Instant.ofEpochMilli(it.measuredAt).atZone(report.period.zoneId))
        } ?: "—"
        cell(canvas, time, boundaries[start + 2], top, ReportTheme.TABLE_WIDTHS[start + 2])
    }

    private fun drawSupplement(canvas: Canvas, report: BpReport, supplement: ReportPage.Supplement): Float {
        drawDetailHeader(canvas, report, true)
        supplement.lines.forEachIndexed { index, planned ->
            drawTextLine(canvas, planned, ReportTheme.MARGIN,
                ReportTheme.SUPPLEMENT_TOP + index * ReportTheme.SUPPLEMENT_LINE_HEIGHT)
        }
        return supplement.lines.size * ReportTheme.SUPPLEMENT_LINE_HEIGHT
    }

    private fun drawTextLine(canvas: Canvas, line: ReportTextLine, x: Float, y: Float) {
        var bodyX = x
        line.prefix?.let { prefix ->
            text(canvas, prefix, x, y, ReportTheme.BODY_SIZE, bold = true)
            bodyX += textMetrics().width(prefix, ReportTheme.BODY_SIZE, true)
        }
        text(canvas, line.text, bodyX, y, ReportTheme.BODY_SIZE, bold = line.bold)
    }

    private fun drawFooter(canvas: Canvas, page: Int, total: Int): Float {
        line(canvas, ReportTheme.MARGIN, ReportTheme.FOOTER_LINE_Y, right, ReportTheme.FOOTER_LINE_Y)
        text(canvas, ReportPolicy.DISCLAIMER, ReportTheme.MARGIN, ReportTheme.FOOTER_BASELINE,
            ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT)
        text(canvas, "$appName  第 $page 页 / 共 $total 页", right, ReportTheme.FOOTER_BASELINE,
            ReportTheme.TINY_SIZE, ReportTheme.SECONDARY_TEXT, align = Paint.Align.RIGHT)
        return ReportTheme.FOOTER_BASELINE - ReportTheme.FOOTER_LINE_Y
    }

    private fun cell(canvas: Canvas, value: String, x: Float, top: Float, width: Float,
        bold: Boolean = false) {
        canvas.save()
        canvas.clipRect(x + ReportTheme.CELL_PADDING, top, x + width - ReportTheme.CELL_PADDING,
            top + ReportTheme.TABLE_ROW_HEIGHT)
        text(canvas, value, x + ReportTheme.CELL_PADDING, top + ReportTheme.CELL_BASELINE_OFFSET,
            ReportTheme.BODY_SIZE, bold = bold)
        canvas.restore()
    }

    private fun clippedText(canvas: Canvas, value: String, x: Float, y: Float, width: Float,
        size: Float, color: Int = ReportTheme.TEXT, bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT) {
        val paint = textPaint(size, color, bold)
        var display = value.replace('\n', ' ').replace('\r', ' ')
        if (paint.measureText(display) > width) {
            var end = display.length
            while (end > 0 && paint.measureText(display.substring(0, end) + "…") > width) {
                end = display.offsetByCodePoints(end, -1)
            }
            display = display.substring(0, end) + "…"
        }
        text(canvas, display, if (align == Paint.Align.RIGHT) x + width else x,
            y, size, color, bold, align)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float,
        color: Int = ReportTheme.TEXT, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        canvas.drawText(value, x, y, textPaint(size, color, bold).apply { textAlign = align })
    }

    private fun textPaint(size: Float, color: Int, isBold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (isBold) bold else regular
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        color: Int = ReportTheme.SEPARATOR, width: Float = ReportTheme.THIN_LINE,
        alpha: Int = ReportTheme.OPAQUE, dashed: Boolean = false) {
        canvas.drawLine(x1, y1, x2, y2, strokePaint(color, width).apply {
            this.alpha = alpha
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(ReportTheme.REFERENCE_DASH,
                ReportTheme.REFERENCE_DASH_GAP), 0f)
        })
    }

    private fun rect(canvas: Canvas, x: Float, y: Float, width: Float, height: Float,
        color: Int, rounded: Boolean = false) {
        if (rounded) canvas.drawRoundRect(x, y, x + width, y + height,
            ReportTheme.CORNER_RADIUS, ReportTheme.CORNER_RADIUS, fillPaint(color))
        else canvas.drawRect(x, y, x + width, y + height, fillPaint(color))
    }

    private fun valueY(value: Int): Float = ReportTheme.PLOT_TOP + ReportTheme.PLOT_HEIGHT *
        (ReportTheme.AXIS_MAX - value.coerceIn(ReportTheme.AXIS_MIN, ReportTheme.AXIS_MAX)) /
        (ReportTheme.AXIS_MAX - ReportTheme.AXIS_MIN)

    private fun bp(average: ReportAverage): String = if (average.systolic == null || average.diastolic == null)
        "—" else "${average.systolic}/${average.diastolic}"

    private fun periodText(report: BpReport) = "${dateFormat.format(report.period.startDate)} 至 " +
        "${dateFormat.format(report.period.endDate)}（${report.period.dayCount} 天）"

    private fun tableBoundaries(): List<Float> {
        var x = ReportTheme.MARGIN
        return ReportTheme.TABLE_WIDTHS.map { width -> x.also { x += width } }
    }

    private val right: Float get() = ReportTheme.MARGIN + ReportTheme.CONTENT_WIDTH
}
