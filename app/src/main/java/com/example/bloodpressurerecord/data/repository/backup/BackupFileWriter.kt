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
    fun writeXlsx(
        payload: BackupExportPayload,
        outputStream: OutputStream,
        templateInputStream: InputStream? = null
    ) {
        createWorkbook(templateInputStream).use { workbook ->
            val headerStyle = findTemplateHeaderStyle(workbook) ?: createDefaultHeaderStyle(workbook)

            writeInstructionsSheet(workbook, headerStyle, payload.instructions)
            writeMeasurementsSheet(workbook, headerStyle, payload.measurements)
            writeReadingsSheet(workbook, headerStyle, payload.readings)
            writeKeyValueSheet(workbook, "用户资料", headerStyle, payload.userProfile.map { it.key to it.value.orEmpty() })
            writeKeyValueSheet(workbook, "导出信息", headerStyle, payload.meta.map { it.key to it.value })
            REQUIRED_SHEET_NAMES.forEachIndexed { index, name ->
                workbook.setSheetOrder(name, index)
            }

            workbook.write(outputStream)
            outputStream.flush()
        }
    }

    private fun createWorkbook(templateInputStream: InputStream?): XSSFWorkbook {
        return if (templateInputStream != null) {
            runCatching { XSSFWorkbook(templateInputStream) }
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
        val sheet = workbook.getOrCreateClearedSheet("使用说明")
        val headerRow = sheet.createRow(0)
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
        val sheet = workbook.getOrCreateClearedSheet("测量记录")
        val headerRow = sheet.createRow(0)
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
        val sheet = workbook.getOrCreateClearedSheet(sheetName)
        val headerRow = sheet.createRow(0)
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

    private fun writeReadingsSheet(
        workbook: XSSFWorkbook,
        headerStyle: CellStyle,
        rows: List<BackupReadingRow>
    ) {
        val sheet = workbook.getOrCreateClearedSheet("原始读数")
        val headerRow = sheet.createRow(0)
        READING_COLUMNS.forEachIndexed { index, title -> headerRow.writeCell(index, title, headerStyle) }
        rows.forEachIndexed { rowIndex, item ->
            sheet.createRow(rowIndex + 1).apply {
                writeCell(0, item.recordId)
                writeCell(1, item.orderIndex)
                writeCell(2, item.systolic)
                writeCell(3, item.diastolic)
                writeCell(4, item.pulse)
            }
        }
        sheet.createFreezePane(0, 1)
        sheet.setColumnWidth(0, 38 * 256)
        READING_COLUMNS.indices.drop(1).forEach { sheet.setColumnWidth(it, 14 * 256) }
    }

    private fun buildMeasurementCells(item: BackupMeasurementRow): List<Any?> {
        return listOf(
            item.recordId,
            item.measuredAt,
            item.date,
            item.time,
            item.groupCount,
            item.avgSystolic,
            item.avgDiastolic,
            item.avgPulse,
            item.level,
            item.highAlert,
            item.scene,
            item.symptomsJson,
            item.note,
            item.createdAt,
            item.updatedAt,
            item.averageStrategy
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

    private fun XSSFWorkbook.getOrCreateClearedSheet(sheetName: String): XSSFSheet {
        val sheet = getSheet(sheetName) ?: createSheet(sheetName)
        for (index in sheet.lastRowNum downTo 0) {
            sheet.getRow(index)?.let(sheet::removeRow)
        }
        return sheet
    }

    companion object {
        const val TEMPLATE_ASSET_NAME = "backup_template_v1.xlsx"
        private val REQUIRED_SHEET_NAMES = listOf("使用说明", "测量记录", "原始读数", "用户资料", "导出信息")
        val MEASUREMENT_COLUMNS = listOf(
            "record_id",
            "measured_at",
            "date",
            "time",
            "group_count",
            "avg_sys",
            "avg_dia",
            "avg_pulse",
            "level",
            "high_alert",
            "scene",
            "symptoms_json",
            "note",
            "created_at",
            "updated_at",
            "average_strategy"
        )
        val READING_COLUMNS = listOf(
            "record_id",
            "order_index",
            "systolic",
            "diastolic",
            "pulse"
        )
    }
}
