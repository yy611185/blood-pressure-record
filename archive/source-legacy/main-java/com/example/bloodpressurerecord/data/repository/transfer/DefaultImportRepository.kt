package com.example.bloodpressurerecord.data.repository.transfer

import android.net.Uri
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream

class DefaultImportRepository(
    private val fileStore: TransferFileStore,
    private val bloodPressureRepository: BloodPressureRepository
) : ImportRepository {
    override suspend fun importCsv(): Result<ImportResult> = runCatching {
        withContext(Dispatchers.IO) {
            val file = fileStore.findImportFile("csv")
                ?: error("未找到可导入的 CSV 文件，请先放入应用私有目录 import/")
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.isEmpty()) error("CSV 文件为空")
            val header = CsvCodec.decodeRow(lines.first()).map { it.trim().lowercase() }
            ImportRowParser.validateHeader(header)?.let { error(it) }

            val errors = mutableListOf<ImportErrorLine>()
            var successRows = 0
            var totalRows = 0

            lines.drop(1).forEachIndexed { index, line ->
                val displayLineNumber = index + 2
                if (line.isBlank()) return@forEachIndexed
                totalRows += 1
                val cells = CsvCodec.decodeRow(line)
                val rowMap = header.mapIndexed { columnIndex, key ->
                    key to cells.getOrElse(columnIndex) { "" }
                }.toMap()
                val parseResult = ImportRowParser.parse(rowMap)
                if (parseResult.isFailure) {
                    errors += ImportErrorLine(
                        lineNumber = displayLineNumber,
                        reason = parseResult.exceptionOrNull()?.message ?: "字段校验失败"
                    )
                    return@forEachIndexed
                }
                val input = parseResult.getOrThrow().input
                val save = bloodPressureRepository.saveSession(input)
                if (save.isSuccess) {
                    successRows += 1
                } else {
                    errors += ImportErrorLine(
                        lineNumber = displayLineNumber,
                        reason = save.exceptionOrNull()?.message ?: "保存失败"
                    )
                }
            }

            ImportResult(
                format = "CSV",
                filePath = file.absolutePath,
                totalRows = totalRows,
                successRows = successRows,
                failedRows = errors.size,
                errors = errors
            )
        }
    }

    override suspend fun importXlsx(): Result<ImportResult> = runCatching {
        withContext(Dispatchers.IO) {
            val file = fileStore.findImportFile("xlsx")
                ?: error("未找到可导入的 XLSX 文件，请先放入应用私有目录 import/")
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheetAt(0) ?: error("XLSX 文件不包含有效工作表")
                    val formatter = DataFormatter()
                    val headerRow = sheet.getRow(0) ?: error("XLSX 缺少表头")
                    val header = (0 until headerRow.lastCellNum.toInt())
                        .map { idx -> formatter.formatCellValue(headerRow.getCell(idx)).trim().lowercase() }
                    ImportRowParser.validateHeader(header)?.let { error(it) }

                    val errors = mutableListOf<ImportErrorLine>()
                    var successRows = 0
                    var totalRows = 0

                    for (rowIndex in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val values = (0 until header.size).map { idx ->
                            formatter.formatCellValue(row.getCell(idx)).trim()
                        }
                        if (values.all { it.isBlank() }) continue
                        totalRows += 1
                        val rowMap = header.mapIndexed { index, key -> key to values[index] }.toMap()
                        val parsed = ImportRowParser.parse(rowMap)
                        if (parsed.isFailure) {
                            errors += ImportErrorLine(
                                lineNumber = rowIndex + 1,
                                reason = parsed.exceptionOrNull()?.message ?: "字段校验失败"
                            )
                            continue
                        }
                        val save = bloodPressureRepository.saveSession(parsed.getOrThrow().input)
                        if (save.isSuccess) {
                            successRows += 1
                        } else {
                            errors += ImportErrorLine(
                                lineNumber = rowIndex + 1,
                                reason = save.exceptionOrNull()?.message ?: "保存失败"
                            )
                        }
                    }

                    ImportResult(
                        format = "XLSX",
                        filePath = file.absolutePath,
                        totalRows = totalRows,
                        successRows = successRows,
                        failedRows = errors.size,
                        errors = errors
                    )
                }
            }
        }
    }

    override suspend fun stageImportFromUri(uri: Uri, fileNameHint: String?): Result<String> {
        return fileStore.stageImportFromUri(uri, fileNameHint).map { file ->
            "文件已复制到应用私有目录：${file.absolutePath}"
        }
    }
}
