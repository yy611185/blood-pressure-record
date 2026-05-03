package com.example.bloodpressurerecord.data.repository.backup

import java.io.InputStream
import java.io.OutputStream
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class BackupFileWriter {
    private var templateLoadError: Throwable? = null

    fun writeXlsx(
        payload: BackupExportPayload,
        outputStream: OutputStream,
        templateInputStream: InputStream? = null
    ) {
        createWorkbook(templateInputStream).use { workbook ->
            val headerStyle = findTemplateHeaderStyle(workbook) ?: createDefaultHeaderStyle(workbook)

            writeInstructionsSheet(workbook, headerStyle, payload.instructions)
            writeMeasurementsSheet(workbook, headerStyle, payload.measurements)
            writeKeyValueSheet(workbook, "用户资料", headerStyle, payload.userProfile.map { it.key to it.value.orEmpty() })
            writeKeyValueSheet(workbook, "导出信息", headerStyle, payload.meta.map { it.key to it.value })

            workbook.write(outputStream)
            outputStream.flush()
        }
    }

    fun getTemplateLoadError(): Throwable? = templateLoadError

    private fun createWorkbook(templateInputStream: InputStream?): XSSFWorkbook {
        templateLoadError = null
        return if (templateInputStream != null) {
            runCatching { XSSFWorkbook(templateInputStream) }
                .onFailure { templateLoadError = it }
                .getOrElse { XSSFWorkbook() }
        } else {
            XSSFWorkbook()
        }
    }

    private fun findTemplateHeaderStyle(workbook: XSSFWorkbook): CellStyle? {
        return REQUIRED_SHEET_NAMES
            .asSequence()
            .mapNotNull { name -> workbook.getSheet(name)?.getRow(0)?.getCell(0)?.cellStyle }
            .firstOrNull()
    }

    private fun createDefaultHeaderStyle(workbook: XSSFWorkbook): CellStyle {
        return workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.PALE_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }
    }

    private fun writeInstructionsSheet(
        workbook: XSSFWorkbook,
        headerStyle: CellStyle,
        rows: List<Pair<String, String>>
    ) {
        val sheet = workbook.getOrCreateClearedSheet("使用说明", keepHeaderRow = true)
        val headerRow = sheet.getRow(0) ?: sheet.createRow(0)
        headerRow.writeCell(0, "项目", headerStyle)
        headerRow.writeCell(1, "说明", headerStyle)

        rows.forEachIndexed { index, item ->
            sheet.createRow(index + 1).apply {
                writeCell(0, item.first)
                writeCell(1, item.second)
            }
        }
        sheet.createFreezePane(0, 1)
        sheet.setColumnWidth(0, 18 * 256)
        sheet.setColumnWidth(1, 68 * 256)
    }

    private fun writeMeasurementsSheet(
        workbook: XSSFWorkbook,
        headerStyle: CellStyle,
        rows: List<BackupMeasurementRow>
    ) {
        val sheet = workbook.getOrCreateClearedSheet("测量记录", keepHeaderRow = true)
        val headerRow = sheet.getRow(0) ?: sheet.createRow(0)
        MEASUREMENT_COLUMNS.forEachIndexed { index, title -> headerRow.writeCell(index, title, headerStyle) }

        rows.forEachIndexed { rowIndex, item ->
            val row = sheet.createRow(rowIndex + 1)
            val cells = buildMeasurementCells(item)
            cells.forEachIndexed { index, value -> row.writeCell(index, value) }
        }

        sheet.createFreezePane(0, 1)
        MEASUREMENT_COLUMNS.indices.forEach { index ->
            val width = when (MEASUREMENT_COLUMNS[index]) {
                "record_id" -> 38
                "measured_at", "created_at", "updated_at" -> 21
                "note" -> 36
                else -> 13
            }
            sheet.setColumnWidth(index, width * 256)
        }
    }

    private fun writeKeyValueSheet(
        workbook: XSSFWorkbook,
        sheetName: String,
        headerStyle: CellStyle,
        rows: List<Pair<String, String>>
    ) {
        val sheet = workbook.getOrCreateClearedSheet(sheetName, keepHeaderRow = true)
        val headerRow = sheet.getRow(0) ?: sheet.createRow(0)
        headerRow.writeCell(0, "key", headerStyle)
        headerRow.writeCell(1, "value", headerStyle)

        rows.forEachIndexed { index, item ->
            sheet.createRow(index + 1).apply {
                writeCell(0, item.first)
                writeCell(1, item.second)
            }
        }
        sheet.createFreezePane(0, 1)
        sheet.setColumnWidth(0, 28 * 256)
        sheet.setColumnWidth(1, 34 * 256)
    }

    private fun buildMeasurementCells(item: BackupMeasurementRow): List<Any?> {
        val readingCells = (0 until BackupExportService.MAX_EXPORT_READING_GROUPS).flatMap { index ->
            val reading = item.readings.getOrNull(index)
            listOf(reading?.systolic, reading?.diastolic, reading?.pulse)
        }

        return listOf(
            item.recordId,
            item.measuredAt,
            item.date,
            item.time,
            item.groupCount
        ) + readingCells + listOf(
            item.avgSystolic,
            item.avgDiastolic,
            item.avgPulse,
            item.level,
            item.highAlert,
            item.note,
            item.createdAt,
            item.updatedAt
        )
    }

    private fun Row.writeCell(index: Int, value: Any?, style: CellStyle? = null) {
        val cell = getCell(index) ?: createCell(index)
        if (style != null) cell.cellStyle = style
        when (value) {
            null -> cell.setBlank()
            is Boolean -> cell.setCellValue(value)
            is Number -> cell.setCellValue(value.toDouble())
            else -> cell.setCellValue(value.toString())
        }
    }

    private fun XSSFWorkbook.getOrCreateClearedSheet(sheetName: String, keepHeaderRow: Boolean = false): XSSFSheet {
        val sheet = getSheet(sheetName) ?: createSheet(sheetName)
        val startRow = if (keepHeaderRow && sheet.lastRowNum >= 0) 1 else 0
        for (index in sheet.lastRowNum downTo startRow) {
            sheet.getRow(index)?.let(sheet::removeRow)
        }
        return sheet
    }

    companion object {
        const val TEMPLATE_ASSET_NAME = "backup_template_v1.xlsx"
        private val REQUIRED_SHEET_NAMES = listOf("使用说明", "测量记录", "用户资料", "导出信息")
        val MEASUREMENT_COLUMNS = listOf(
            "record_id",
            "measured_at",
            "date",
            "time",
            "group_count",
            "sys_1",
            "dia_1",
            "pulse_1",
            "sys_2",
            "dia_2",
            "pulse_2",
            "sys_3",
            "dia_3",
            "pulse_3",
            "sys_4",
            "dia_4",
            "pulse_4",
            "sys_5",
            "dia_5",
            "pulse_5",
            "avg_sys",
            "avg_dia",
            "avg_pulse",
            "level",
            "high_alert",
            "note",
            "created_at",
            "updated_at"
        )
    }
}
