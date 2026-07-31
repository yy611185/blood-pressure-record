package com.example.bloodpressurerecord.data.repository.backup

import java.io.InputStream
import java.math.BigDecimal
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

data class BackupImportReading(
    val sourceRowNumber: Int,
    val orderIndex: Int?,
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
    val pulseWasInvalid: Boolean
)

data class BackupImportMeasurement(
    val sourceRowNumber: Int,
    val recordId: String,
    val measuredAt: String,
    val backupGroupCount: Int?,
    val backupAvgSystolic: Int?,
    val backupAvgDiastolic: Int?,
    val backupAvgPulse: Int?,
    val backupLevel: String?,
    val backupHighAlert: Boolean?,
    val scene: String?,
    val symptomsJson: String?,
    val note: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val readings: List<BackupImportReading>,
    val backupAverageStrategy: String? = null
)

data class BackupImportDocument(
    val measurements: List<BackupImportMeasurement>,
    val userProfile: Map<String, String>,
    val meta: Map<String, String>
)

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class BackupFileReader {
    private val formatter = DataFormatter()

    fun readXlsx(inputStream: InputStream): BackupImportDocument {
        // 10 MB 的文件上限只限制压缩后字节；恶意 xlsx 解压后可膨胀上百倍导致 OOM。
        // 这里显式限制解压比例与解压后的单条目大小，超限时 POI 抛异常并中止导入。
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO)
        ZipSecureFile.setMaxEntrySize(MAX_UNCOMPRESSED_ENTRY_BYTES)
        val workbook = try {
            XSSFWorkbook(inputStream)
        } catch (throwable: Exception) {
            throw BackupFormatException("文件损坏或不是有效的 Excel .xlsx 备份", throwable)
        }

        return workbook.use {
            val metaSheet = workbook.getSheet(SHEET_META)
                ?: throw BackupFormatException("缺少必要工作表：$SHEET_META")
            val meta = metaSheet.readKeyValues()
            val version = meta["export_format_version"]?.toIntOrNull()
                ?: throw BackupFormatException("缺少或无法识别备份格式版本")
            if (version !in SUPPORTED_FORMAT_VERSIONS) {
                throw BackupFormatException("不支持的备份格式版本：$version")
            }

            val measurementSheet = workbook.getSheet(SHEET_MEASUREMENTS)
                ?: throw BackupFormatException("缺少必要工作表：$SHEET_MEASUREMENTS")
            val readingsSheet = workbook.getSheet(SHEET_READINGS)
                ?: throw BackupFormatException("缺少必要工作表：$SHEET_READINGS")

            val readingRows = readReadingRows(readingsSheet)
            if (readingRows.size > BackupImportLimits.MAX_TOTAL_READING_ROWS) {
                throw BackupFormatException("原始读数总数超过允许上限")
            }
            val measurements = readMeasurements(
                measurementSheet,
                readingRows.groupBy { it.recordId },
                formatVersion = version
            )
            if (measurements.size > BackupImportLimits.MAX_RECORDS) {
                throw BackupFormatException("测量记录总数超过允许上限")
            }
            val duplicateIds = measurements.groupingBy { it.recordId }
                .eachCount()
                .filterValues { it > 1 }
            if (duplicateIds.isNotEmpty()) {
                throw BackupFormatException("备份中存在重复 record_id，未执行导入")
            }
            val knownIds = measurements.mapTo(hashSetOf()) { it.recordId }
            if (readingRows.any { it.recordId !in knownIds }) {
                throw BackupFormatException("原始读数引用了不存在的测量记录")
            }

            BackupImportDocument(
                measurements = measurements,
                userProfile = workbook.getSheet(SHEET_PROFILE)?.readKeyValues().orEmpty(),
                meta = meta
            )
        }
    }

    private fun readMeasurements(
        sheet: Sheet,
        readingRows: Map<String, List<RawReadingRow>>,
        formatVersion: Int
    ): List<BackupImportMeasurement> {
        val columns = sheet.headerColumns()
        requireColumns(
            columns,
            "record_id",
            "measured_at",
            "group_count",
            "avg_sys",
            "avg_dia",
            "avg_pulse",
            "level",
            "high_alert"
        )
        return (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
            if (row.isEffectivelyBlank()) return@mapNotNull null
            val recordId = row.text(columns, "record_id").trim()
            if (recordId.isBlank()) {
                throw BackupFormatException("测量记录第 ${rowIndex + 1} 行缺少 record_id")
            }
            if (recordId.length > BackupImportLimits.MAX_RECORD_ID_LENGTH) {
                throw BackupFormatException("测量记录第 ${rowIndex + 1} 行的 record_id 过长")
            }

            BackupImportMeasurement(
                sourceRowNumber = rowIndex + 1,
                recordId = recordId,
                measuredAt = row.text(columns, "measured_at").trim(),
                backupGroupCount = row.strictInt(columns, "group_count"),
                backupAvgSystolic = row.strictInt(columns, "avg_sys"),
                backupAvgDiastolic = row.strictInt(columns, "avg_dia"),
                backupAvgPulse = row.strictInt(columns, "avg_pulse"),
                backupAverageStrategy = row.optionalText(columns, "average_strategy")
                    .also {
                        if (formatVersion >= 3 && it == null) {
                            throw BackupFormatException(
                                "测量记录第 ${rowIndex + 1} 行缺少 average_strategy"
                            )
                        }
                    },
                backupLevel = row.optionalText(columns, "level"),
                backupHighAlert = row.strictBoolean(columns, "high_alert"),
                scene = row.optionalText(columns, "scene"),
                symptomsJson = row.optionalText(columns, "symptoms_json"),
                note = row.optionalText(columns, "note"),
                createdAt = row.optionalText(columns, "created_at"),
                updatedAt = row.optionalText(columns, "updated_at"),
                readings = readingRows[recordId].orEmpty()
                    .sortedWith(compareBy<RawReadingRow> { it.orderIndex ?: Int.MAX_VALUE }.thenBy { it.sourceRowNumber })
                    .map { raw ->
                        BackupImportReading(
                            sourceRowNumber = raw.sourceRowNumber,
                            orderIndex = raw.orderIndex,
                            systolic = raw.systolic,
                            diastolic = raw.diastolic,
                            pulse = raw.pulse,
                            pulseWasInvalid = raw.pulseWasInvalid
                        )
                    }
            )
        }
    }

    private fun readReadingRows(sheet: Sheet): List<RawReadingRow> {
        val columns = sheet.headerColumns()
        requireColumns(columns, "record_id", "order_index", "systolic", "diastolic", "pulse")
        return (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
            if (row.isEffectivelyBlank()) return@mapNotNull null
            val recordId = row.text(columns, "record_id").trim()
            if (recordId.isBlank()) {
                throw BackupFormatException("原始读数第 ${rowIndex + 1} 行缺少 record_id")
            }
            val pulseRaw = row.text(columns, "pulse").trim()
            val pulse = pulseRaw.toStrictIntOrNull()
            RawReadingRow(
                recordId = recordId,
                sourceRowNumber = rowIndex + 1,
                orderIndex = row.strictInt(columns, "order_index"),
                systolic = row.strictInt(columns, "systolic"),
                diastolic = row.strictInt(columns, "diastolic"),
                pulse = pulse,
                pulseWasInvalid = pulseRaw.isNotEmpty() && pulse == null
            )
        }
    }

    private fun Sheet.headerColumns(): Map<String, Int> {
        val header = getRow(0) ?: throw BackupFormatException("工作表“$sheetName”缺少表头")
        if (header.lastCellNum < 0) throw BackupFormatException("工作表“$sheetName”表头为空")
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

    private fun Row.isEffectivelyBlank(): Boolean {
        if (lastCellNum < 0) return true
        return (firstCellNum.toInt().coerceAtLeast(0) until lastCellNum.toInt())
            .all { formatter.formatCellValue(getCell(it)).isBlank() }
    }

    private fun Row.text(columns: Map<String, Int>, name: String): String {
        val index = columns[name] ?: return ""
        return formatter.formatCellValue(getCell(index))
    }

    private fun Row.optionalText(columns: Map<String, Int>, name: String): String? {
        return text(columns, name).trim().takeIf { it.isNotEmpty() }
    }

    private fun Row.strictInt(columns: Map<String, Int>, name: String): Int? {
        return text(columns, name).trim().toStrictIntOrNull()
    }

    private fun Row.strictBoolean(columns: Map<String, Int>, name: String): Boolean? {
        return when (text(columns, name).trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private fun String.toStrictIntOrNull(): Int? {
        if (isBlank()) return null
        return runCatching {
            BigDecimal(this).stripTrailingZeros().let { number ->
                if (number.scale() > 0) null else number.intValueExact()
            }
        }.getOrNull()
    }

    private fun requireColumns(columns: Map<String, Int>, vararg names: String) {
        val missing = names.filterNot(columns::containsKey)
        if (missing.isNotEmpty()) {
            throw BackupFormatException("备份文件缺少必要列：${missing.joinToString()}")
        }
    }

    private data class RawReadingRow(
        val recordId: String,
        val sourceRowNumber: Int,
        val orderIndex: Int?,
        val systolic: Int?,
        val diastolic: Int?,
        val pulse: Int?,
        val pulseWasInvalid: Boolean
    )

    companion object {
        private const val SHEET_MEASUREMENTS = "测量记录"
        private const val SHEET_READINGS = "原始读数"
        private const val SHEET_PROFILE = "用户资料"
        private const val SHEET_META = "导出信息"
        private val SUPPORTED_FORMAT_VERSIONS = setOf(2, 3)

        /** 最多允许压缩比 1:100（POI 默认值，显式声明避免被其他代码放宽）。 */
        private const val MIN_INFLATE_RATIO = 0.01

        /**
         * 解压后单个 zip 条目上限。按 5,000 条记录 × 20 组读数的合法最大备份估算，
         * 工作表 XML 不应超过此值；恶意构造的超大条目会被 POI 拒绝。
         */
        private const val MAX_UNCOMPRESSED_ENTRY_BYTES = 64L * 1024 * 1024
    }
}
