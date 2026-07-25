package com.example.bloodpressurerecord.data.repository.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileWriterTest {
    @Test
    fun writeXlsx_createsVersion2SheetsWithUnlimitedReadingRows() {
        val payload = samplePayload()

        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(payload, output)
            output.toByteArray()
        }

        assertWorkbookContainsExportedMeasurement(bytes)
    }

    @Test
    fun writeXlsx_usesTemplateAndWritesHistoryRowsIntoIt() {
        val template = listOf(
            File("app/src/main/assets/${BackupFileWriter.TEMPLATE_ASSET_NAME}"),
            File("src/main/assets/${BackupFileWriter.TEMPLATE_ASSET_NAME}")
        ).first { it.exists() }
        assertTrue("Template asset should exist: ${template.absolutePath}", template.exists())

        val bytes = ByteArrayOutputStream().use { output ->
            FileInputStream(template).use { input ->
                BackupFileWriter().writeXlsx(samplePayload(), output, input)
            }
            output.toByteArray()
        }

        assertWorkbookContainsExportedMeasurement(bytes)
    }

    private fun samplePayload(): BackupExportPayload {
        return BackupExportPayload(
            instructions = listOf("文件用途" to "本地备份"),
            measurements = listOf(
                BackupMeasurementRow(
                    recordId = "record-1",
                    measuredAt = "2026-04-23 08:30:00",
                    date = "2026-04-23",
                    time = "08:30",
                    groupCount = 7,
                    readings = listOf(
                        BackupReadingValue(120, 80, 72),
                        BackupReadingValue(118, 78, null)
                    ),
                    avgSystolic = 119,
                    avgDiastolic = 79,
                    avgPulse = 72,
                    level = "NORMAL",
                    highAlert = false,
                    scene = "晨起",
                    symptomsJson = "[\"头晕\"]",
                    note = "morning",
                    createdAt = "2026-04-23 08:31:00",
                    updatedAt = "2026-04-23 08:31:00"
                )
            ),
            readings = (1..7).map { index ->
                BackupReadingRow(
                    recordId = "record-1",
                    orderIndex = index,
                    systolic = 118 + index,
                    diastolic = 78 + index,
                    pulse = 70 + index
                )
            },
            userProfile = listOf(BackupUserProfileItem("target_sys", "120")),
            meta = listOf(
                BackupMetaItem("export_format_version", "2"),
                BackupMetaItem("total_records", "1"),
                BackupMetaItem("measurement_sessions_count", "1"),
                BackupMetaItem("measurement_readings_count", "7")
            ),
            diagnostics = BackupExportDiagnostics(
                sessionCount = 1,
                readingCount = 7
            )
        )
    }

    private fun assertWorkbookContainsExportedMeasurement(bytes: ByteArray) {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            assertEquals(
                listOf("使用说明", "测量记录", "原始读数", "用户资料", "导出信息"),
                workbook.sheetIterator().asSequence().map { it.sheetName }.toList()
            )

            val measurementSheet = workbook.getSheet("测量记录")
            val header = measurementSheet.getRow(0)
            BackupFileWriter.MEASUREMENT_COLUMNS.forEachIndexed { index, name ->
                assertEquals(name, header.getCell(index).stringCellValue)
            }

            val row = measurementSheet.getRow(1)
            assertEquals("record-1", row.getCell(0).stringCellValue)
            assertEquals(7.0, row.getCell(4).numericCellValue, 0.0)
            assertEquals(119.0, row.getCell(5).numericCellValue, 0.0)
            assertEquals(79.0, row.getCell(6).numericCellValue, 0.0)
            assertEquals(72.0, row.getCell(7).numericCellValue, 0.0)
            assertEquals(false, row.getCell(9).booleanCellValue)
            assertEquals("晨起", row.getCell(10).stringCellValue)
            assertEquals("[\"头晕\"]", row.getCell(11).stringCellValue)

            val readingsSheet = workbook.getSheet("原始读数")
            assertEquals(7, readingsSheet.lastRowNum)
            assertEquals("record-1", readingsSheet.getRow(7).getCell(0).stringCellValue)
            assertEquals(7.0, readingsSheet.getRow(7).getCell(1).numericCellValue, 0.0)

            val metaSheet = workbook.getSheet("导出信息")
            assertEquals("export_format_version", metaSheet.getRow(1).getCell(0).stringCellValue)
            assertEquals("2", metaSheet.getRow(1).getCell(1).stringCellValue)
            assertEquals("measurement_sessions_count", metaSheet.getRow(3).getCell(0).stringCellValue)
            assertEquals("1", metaSheet.getRow(3).getCell(1).stringCellValue)
            assertEquals("measurement_readings_count", metaSheet.getRow(4).getCell(0).stringCellValue)
            assertEquals("7", metaSheet.getRow(4).getCell(1).stringCellValue)
        }
    }
}

private fun org.apache.poi.ss.usermodel.Cell?.blankOrEmpty(): Boolean {
    return this == null || stringCellValue.isEmpty()
}
