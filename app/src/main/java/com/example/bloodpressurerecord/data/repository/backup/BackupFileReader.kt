package com.example.bloodpressurerecord.data.repository.backup

import java.io.InputStream
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

data class BackupImportMeasurement(
    val recordId: String,
    val measuredAt: String,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val scene: String?,
    val symptomsJson: String?,
    val note: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val readings: List<BackupReadingValue>
)

data class BackupImportDocument(
    val measurements: List<BackupImportMeasurement>,
    val userProfile: Map<String, String>,
    val meta: Map<String, String>
)

class BackupFileReader {
    private val formatter = DataFormatter()

    fun readXlsx(inputStream: InputStream): BackupImportDocument {
        return XSSFWorkbook(inputStream).use { workbook ->
            val measurementSheet = workbook.getSheet("测量记录")
                ?: error("不是受支持的备份文件：缺少“测量记录”工作表")
            val readingRows = workbook.getSheet("原始读数")
                ?.let(::readReadingRows)
                .orEmpty()
                .groupBy { it.first }

            val measurements = readMeasurements(measurementSheet, readingRows)
            if (measurements.isEmpty()) {
                error("备份文件中没有可导入的测量记录")
            }
            BackupImportDocument(
                measurements = measurements,
                userProfile = workbook.getSheet("用户资料")?.readKeyValues().orEmpty(),
                meta = workbook.getSheet("导出信息")?.readKeyValues().orEmpty()
            )
        }
    }

    private fun readMeasurements(
        sheet: Sheet,
        readingRows: Map<String, List<Pair<String, BackupReadingValue>>>
    ): List<BackupImportMeasurement> {
        val columns = sheet.headerColumns()
        requireColumns(columns, "record_id", "measured_at", "avg_sys", "avg_dia")
        return (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
            val recordId = row.text(columns, "record_id").trim()
            if (recordId.isBlank()) return@mapNotNull null
            val avgSystolic = row.int(columns, "avg_sys")
                ?: error("测量记录第 ${rowIndex + 1} 行缺少 avg_sys")
            val avgDiastolic = row.int(columns, "avg_dia")
                ?: error("测量记录第 ${rowIndex + 1} 行缺少 avg_dia")

            val readings = readingRows[recordId]
                ?.map { it.second }
                ?.takeIf { it.isNotEmpty() }
                ?: readLegacyInlineReadings(row, columns)
                    .takeIf { it.isNotEmpty() }
                ?: listOf(
                    BackupReadingValue(
                        systolic = avgSystolic,
                        diastolic = avgDiastolic,
                        pulse = row.int(columns, "avg_pulse")
                    )
                )

            BackupImportMeasurement(
                recordId = recordId,
                measuredAt = row.text(columns, "measured_at"),
                avgSystolic = avgSystolic,
                avgDiastolic = avgDiastolic,
                avgPulse = row.int(columns, "avg_pulse"),
                scene = row.optionalText(columns, "scene"),
                symptomsJson = row.optionalText(columns, "symptoms_json"),
                note = row.optionalText(columns, "note"),
                createdAt = row.optionalText(columns, "created_at"),
                updatedAt = row.optionalText(columns, "updated_at"),
                readings = readings
            )
        }
    }

    private fun readReadingRows(sheet: Sheet): List<Pair<String, BackupReadingValue>> {
        val columns = sheet.headerColumns()
        requireColumns(columns, "record_id", "order_index", "systolic", "diastolic")
        return (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
            val recordId = row.text(columns, "record_id").trim()
            if (recordId.isBlank()) return@mapNotNull null
            val order = row.int(columns, "order_index") ?: rowIndex
            val systolic = row.int(columns, "systolic")
                ?: error("原始读数第 ${rowIndex + 1} 行缺少 systolic")
            val diastolic = row.int(columns, "diastolic")
                ?: error("原始读数第 ${rowIndex + 1} 行缺少 diastolic")
            Triple(
                recordId,
                order,
                BackupReadingValue(systolic, diastolic, row.int(columns, "pulse"))
            )
        }.sortedWith(compareBy<Triple<String, Int, BackupReadingValue>> { it.first }.thenBy { it.second })
            .map { it.first to it.third }
    }

    private fun readLegacyInlineReadings(
        row: Row,
        columns: Map<String, Int>
    ): List<BackupReadingValue> {
        return generateSequence(1) { it + 1 }
            .takeWhile { columns.containsKey("sys_$it") || columns.containsKey("dia_$it") }
            .mapNotNull { index ->
                val systolic = row.int(columns, "sys_$index")
                val diastolic = row.int(columns, "dia_$index")
                if (systolic == null || diastolic == null) null
                else BackupReadingValue(systolic, diastolic, row.int(columns, "pulse_$index"))
            }
            .toList()
    }

    private fun Sheet.headerColumns(): Map<String, Int> {
        val header = getRow(0) ?: error("工作表“$sheetName”缺少表头")
        return (header.firstCellNum.toInt().coerceAtLeast(0) until header.lastCellNum.toInt())
            .mapNotNull { index ->
                formatter.formatCellValue(header.getCell(index)).trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { it to index }
            }
            .toMap()
    }

    private fun Sheet.readKeyValues(): Map<String, String> {
        return (1..lastRowNum).mapNotNull { index ->
            val row = getRow(index) ?: return@mapNotNull null
            val key = formatter.formatCellValue(row.getCell(0)).trim()
            if (key.isEmpty()) null else key to formatter.formatCellValue(row.getCell(1)).trim()
        }.toMap()
    }

    private fun Row.text(columns: Map<String, Int>, name: String): String {
        val index = columns[name] ?: return ""
        return formatter.formatCellValue(getCell(index))
    }

    private fun Row.optionalText(columns: Map<String, Int>, name: String): String? {
        return text(columns, name).trim().takeIf { it.isNotEmpty() }
    }

    private fun Row.int(columns: Map<String, Int>, name: String): Int? {
        val raw = text(columns, name).trim()
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
    }

    private fun requireColumns(columns: Map<String, Int>, vararg names: String) {
        val missing = names.filterNot(columns::containsKey)
        require(missing.isEmpty()) { "备份文件缺少必要列：${missing.joinToString()}" }
    }
}
