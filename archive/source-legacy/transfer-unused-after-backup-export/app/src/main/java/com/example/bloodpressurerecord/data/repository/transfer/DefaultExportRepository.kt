package com.example.bloodpressurerecord.data.repository.transfer

import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.FileOutputStream

class DefaultExportRepository(
    private val sessionDao: MeasurementSessionDao,
    private val fileStore: TransferFileStore
) : ExportRepository {
    private val columns = listOf(
        "session_id",
        "measured_at",
        "scene",
        "avg_systolic",
        "avg_diastolic",
        "avg_pulse",
        "category",
        "high_risk",
        "note",
        "symptoms"
    )

    override suspend fun exportCsv(): Result<ExportResult> = runCatching {
        withContext(Dispatchers.IO) {
            val rows = sessionDao.getAllSessionsWithReadings()
            val file = fileStore.createExportFile("csv")
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(CsvCodec.encodeRow(columns))
                rows.forEach { item ->
                    writer.appendLine(
                        CsvCodec.encodeRow(
                            listOf(
                                item.session.id,
                                item.session.measuredAt.toString(),
                                item.session.scene,
                                item.session.avgSystolic.toString(),
                                item.session.avgDiastolic.toString(),
                                item.session.avgPulse?.toString().orEmpty(),
                                item.session.category,
                                item.session.highRiskAlertTriggered.toString(),
                                item.session.note.orEmpty(),
                                parseSymptoms(item.session.symptomsJson).joinToString("|")
                            )
                        )
                    )
                }
            }
            ExportResult(
                format = "CSV",
                filePath = file.absolutePath,
                rowCount = rows.size
            )
        }
    }

    override suspend fun exportXlsx(): Result<ExportResult> = runCatching {
        withContext(Dispatchers.IO) {
            val rows = sessionDao.getAllSessionsWithReadings()
            val file = fileStore.createExportFile("xlsx")
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("sessions")
                val headerRow = sheet.createRow(0)
                columns.forEachIndexed { index, text -> headerRow.createCell(index).setCellValue(text) }
                rows.forEachIndexed { rowIndex, item ->
                    val row = sheet.createRow(rowIndex + 1)
                    row.createCell(0).setCellValue(item.session.id)
                    row.createCell(1).setCellValue(item.session.measuredAt.toString())
                    row.createCell(2).setCellValue(item.session.scene)
                    row.createCell(3).setCellValue(item.session.avgSystolic.toString())
                    row.createCell(4).setCellValue(item.session.avgDiastolic.toString())
                    row.createCell(5).setCellValue(item.session.avgPulse?.toString().orEmpty())
                    row.createCell(6).setCellValue(item.session.category)
                    row.createCell(7).setCellValue(item.session.highRiskAlertTriggered.toString())
                    row.createCell(8).setCellValue(item.session.note.orEmpty())
                    row.createCell(9).setCellValue(parseSymptoms(item.session.symptomsJson).joinToString("|"))
                }
                columns.indices.forEach(sheet::autoSizeColumn)
                FileOutputStream(file).use { output -> workbook.write(output) }
            }
            ExportResult(
                format = "XLSX",
                filePath = file.absolutePath,
                rowCount = rows.size
            )
        }
    }

    private fun parseSymptoms(symptomsJson: String?): List<String> {
        if (symptomsJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(symptomsJson)
            List(array.length()) { index -> array.getString(index) }
        }.getOrDefault(emptyList())
    }
}
