package com.example.bloodpressurerecord.export.report

import com.example.bloodpressurerecord.domain.report.ReportLayoutSpec

/** Template values in PDF points; independent of Android screen density and font scaling. */
object ReportTheme {
    val layoutSpec: ReportLayoutSpec get() = ReportLayoutSpec(
        pageWidth = A4_WIDTH, pageHeight = A4_HEIGHT, margin = MARGIN,
        contentWidth = CONTENT_WIDTH, detailRowsPerPage = TABLE_ROWS,
        detailRowHeight = TABLE_ROW_HEIGHT, noteColumnWidth = TABLE_WIDTHS.last(),
        bodySize = BODY_SIZE, smallSize = CAPTION_SIZE, tinySize = TINY_SIZE,
        cellPadding = CELL_PADDING, summaryNoteLines = SUMMARY_NOTE_LINES,
        summaryMedicationCount = SUMMARY_MEDICATION_COUNT,
        summaryMedicationLabelWidth = MEDICATION_LABEL_WIDTH,
        summaryMedicationTimeWidth = MEDICATION_TIME_WIDTH,
        supplementLinesPerPage = SUPPLEMENT_LINES,
        trendPlotWidth = CONTENT_WIDTH - PLOT_LEFT_INSET - PLOT_RIGHT_INSET,
        trendLabelInterval = X_LABEL_INTERVAL,
        trendLabelGap = X_LABEL_GAP
    )
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val A4_WIDTH = 595.28f
    const val A4_HEIGHT = 841.89f
    const val POINTS_PER_INCH = 72f
    const val SUMMARY_IMAGE_DPI = 150
    const val PRINT_COPY_BUFFER_BYTES = 8192
    const val MARGIN = 40f
    const val CONTENT_WIDTH = 515.28f
    const val FOOTER_LINE_Y = 786f
    const val FOOTER_BASELINE = 799f
    const val TITLE_SIZE = 20f
    const val SECTION_SIZE = 13f
    const val BODY_SIZE = 10.5f
    const val CAPTION_SIZE = 8.5f
    const val TINY_SIZE = 7.5f
    const val HEADER_META_SIZE = 9.5f
    const val CORE_SIZE = 24f
    const val SECONDARY_SIZE = 18f
    const val HEADER_TITLE_Y = 62f
    const val HEADER_APP_Y = 78f
    const val HEADER_PERIOD_Y = 54f
    const val HEADER_GENERATED_Y = 69f
    const val HEADER_IDENTITY_Y = 99f
    const val HEADER_LINE_Y = 110f
    const val CORE_TOP = 124f
    const val CORE_HEIGHT = 70f
    val CORE_WIDTHS = listOf(150f, 91.28f, 91.28f, 91.28f, 91.28f)
    const val CORE_LABEL_OFFSET = 10f
    const val CORE_VALUE_OFFSET = 40f
    const val CORE_UNIT_OFFSET = 56f
    const val CORE_CELL_INSET = 12f
    const val CORE_VERTICAL_BOTTOM_INSET = 10f
    const val COVERAGE_Y = 210f
    const val INSUFFICIENT_Y = 221f
    const val TREND_TITLE_Y = 234f
    const val PLOT_TOP = 258f
    const val PLOT_HEIGHT = 200f
    const val PLOT_LEFT_INSET = 26f
    const val PLOT_RIGHT_INSET = 40f
    const val AXIS_MIN = 60
    const val AXIS_MAX = 180
    const val AXIS_STEP = 20
    const val AXIS_LABEL_OFFSET = 5f
    const val AXIS_UNIT_OFFSET = 9f
    const val AXIS_LABEL_BASELINE_OFFSET = 2.5f
    const val AXIS_TICK_LENGTH = 3f
    const val X_LABEL_OFFSET = 14f
    const val X_VERTICAL_LABEL_OFFSET = 5f
    const val X_LABEL_ROTATION = -90f
    const val X_LABEL_GAP = 2f
    const val X_LABEL_INTERVAL = 5
    const val REFERENCE_SYSTOLIC = 140
    const val REFERENCE_DIASTOLIC = 90
    const val REFERENCE_LABEL_OFFSET = 5f
    const val REFERENCE_DASH = 3f
    const val REFERENCE_DASH_GAP = 3f
    const val RANGE_WIDTH = 1.8f
    const val RANGE_ALPHA = 102
    const val CONNECTOR_WIDTH = 0.7f
    const val CONNECTOR_ALPHA = 115
    const val SYSTOLIC_RADIUS = 2.7f
    const val DIASTOLIC_RADIUS = 3.3f
    const val DIASTOLIC_STROKE = 1.2f
    const val OVERFLOW_ARROW = 3f
    const val OVERFLOW_NOTICE_X = 90f
    const val OVERFLOW_NOTICE_Y = 249f
    const val DISTRIBUTION_TITLE_Y = 503f
    const val HALF_GAP = 12f
    const val DISTRIBUTION_ROW_TOP = 518f
    const val DISTRIBUTION_ROW_HEIGHT = 17f
    const val DISTRIBUTION_BAR_INSET = 58f
    const val DISTRIBUTION_BAR_HEIGHT = 8f
    const val DISTRIBUTION_BAR_TOP_OFFSET = 5f
    const val DISTRIBUTION_COUNT_WIDTH = 30f
    const val DISTRIBUTION_NOTICE_Y = 635f
    const val MEDICATION_TITLE_Y = 503f
    const val MEDICATION_FIRST_Y = 525f
    const val MEDICATION_BLOCK_HEIGHT = 44f
    const val MEDICATION_NOTICE_Y = 641f
    const val MEDICATION_MORE_Y = 626f
    const val NOTICE_LINE_HEIGHT = 11f
    const val NOTES_TITLE_Y = 677f
    const val NOTES_FIRST_Y = 697f
    const val NOTES_LINE_HEIGHT = 15f
    const val NOTES_DATE_WIDTH = 40f
    const val SUMMARY_NOTE_LINES = 5
    const val SUMMARY_MEDICATION_COUNT = 2
    const val SUPPLEMENT_LINES = 30
    const val DETAIL_TITLE_Y = 58f
    const val DETAIL_LINE_Y = 70f
    const val DETAIL_METADATA_X = 294f
    const val TABLE_TOP = 84f
    const val TABLE_HEADER_HEIGHT = 17f
    const val TABLE_ROW_TOP = 118f
    const val TABLE_ROW_HEIGHT = 17.5f
    const val TABLE_ROWS = 30
    val TABLE_WIDTHS = listOf(78f, 60f, 36f, 42f, 60f, 36f, 42f, 161.28f)
    const val CELL_PADDING = 4f
    const val CELL_BASELINE_OFFSET = 12f
    const val DETAIL_LEGEND_Y = 658f
    const val SUPPLEMENT_TOP = 91f
    const val SUPPLEMENT_LINE_HEIGHT = 15f
    const val THIN_LINE = 0.5f
    const val LEGEND_CIRCLE_X = 230f
    const val LEGEND_DIAMOND_X = 307f
    const val LEGEND_RANGE_X = 390f
    const val LEGEND_REFERENCE_X = 466f
    const val LEGEND_LABEL_GAP = 8f
    const val LEGEND_BASELINE = 234f
    const val LEGEND_CENTER_OFFSET = 2.5f
    const val LEGEND_SAMPLE_LENGTH = 10f
    const val LEGEND_RANGE_HALF_HEIGHT = 5f
    const val MEDICATION_TIME_WIDTH = 95f
    const val MEDICATION_LABEL_WIDTH = 150f
    const val MEDICATION_INFO_OFFSET = 14f
    const val MEDICATION_STATUS_OFFSET = 27f
    const val CORNER_RADIUS = 2f
    const val WHITE = -0x1
    const val OPAQUE = 255
    const val TEXT = 0xFF1F2933.toInt()
    const val SECONDARY_TEXT = 0xFF6B7785.toInt()
    const val SEPARATOR = 0xFFE3E7EB.toInt()
    const val GRID = 0xFFEEF1F4.toInt()
    const val AXIS = 0xFFB8C1CA.toInt()
    const val ZEBRA = 0xFFF6F8FA.toInt()
    const val TABLE_HEADER = 0xFFEEF2F6.toInt()
    const val HIGH_BACKGROUND = 0xFFFCE9DC.toInt()
    const val SYSTOLIC = 0xFF1F5FA8.toInt()
    const val DIASTOLIC = 0xFFD9622B.toInt()
    const val REFERENCE = 0xFF8A95A1.toInt()
    const val PROGRESS_BACKGROUND = 0xFFEEF1F4.toInt()
    const val ADHERENCE = 0xFF4C8C6B.toInt()
    val CATEGORY_COLORS = listOf(
        0xFF8FA6BC.toInt(), 0xFF6FAE8B.toInt(), 0xFFE3B94E.toInt(),
        0xFFE58F4A.toInt(), 0xFFD2503A.toInt(), 0xFF9C2B2B.toInt()
    )
}
