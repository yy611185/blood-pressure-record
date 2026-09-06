package com.example.bloodpressurerecord.data.repository.backup

import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipFile
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
    val meta: Map<String, String>,
    val medications: List<BackupMedicationRow> = emptyList(),
    val medicationTimes: List<BackupMedicationTimeRow> = emptyList(),
    val medicationLogs: List<BackupMedicationLogRow> = emptyList()
)

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class BackupFileReader {
    private val formatter = DataFormatter()

    fun readXlsx(inputStream: InputStream, passphrase: CharArray? = null): BackupImportDocument {
        val tempFile = try {
            File.createTempFile("blood-pressure-backup-", ".xlsx")
        } catch (throwable: Exception) {
            throw BackupFormatException("无法准备备份文件校验空间", throwable)
        }
        try {
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = inputStream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > BackupImportLimits.MAX_FILE_BYTES) {
                        throw BackupFormatException("备份文件超过 10 MB 上限")
                    }
                    output.write(buffer, 0, count)
                }
            }
            // 加密容器先解密出明文 xlsx，再走与明文完全相同的校验和解析路径。
            val sourceFile = if (isEncryptedContainerFile(tempFile)) {
                val pass = passphrase
                    ?: throw BackupPassphraseRequiredException("该备份已加密，请输入备份口令。")
                val decrypted = try {
                    BackupCrypto.decrypt(tempFile.readBytes(), pass)
                } catch (throwable: Exception) {
                    if (throwable is BackupContainerFormatException ||
                        throwable is BackupPassphraseException
                    ) {
                        throw throwable
                    }
                    throw BackupContainerFormatException("解密备份失败", throwable)
                }
                try {
                    File.createTempFile("blood-pressure-backup-decrypted-", ".xlsx").apply {
                        writeBytes(decrypted)
                    }
                } catch (throwable: Exception) {
                    throw BackupFormatException("无法准备备份文件校验空间", throwable)
                }
            } else {
                tempFile
            }
            try {
                preflightZip(sourceFile)
                // 10 MB 的文件上限只限制压缩后字节；下面的中央目录和实际解压读取
                // 共同限制条目数、单条目大小、总解压大小和压缩炸弹比例。
                ZipSecureFile.setMinInflateRatio(BackupImportLimits.MIN_INFLATE_RATIO)
                ZipSecureFile.setMaxEntrySize(BackupImportLimits.MAX_UNCOMPRESSED_ENTRY_BYTES)
                val workbook = try {
                    XSSFWorkbook(sourceFile)
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
                        meta = meta,
                        medications = if (version >= 4) {
                            readMedications(workbook.getSheet(SHEET_MEDICATIONS)
                                ?: throw BackupFormatException("缺少必要工作表：$SHEET_MEDICATIONS"))
                        } else emptyList(),
                        medicationTimes = if (version >= 4) {
                            readMedicationTimes(workbook.getSheet(SHEET_MEDICATION_TIMES)
                                ?: throw BackupFormatException("缺少必要工作表：$SHEET_MEDICATION_TIMES"))
                        } else emptyList(),
                        medicationLogs = if (version >= 4) {
                            readMedicationLogs(workbook.getSheet(SHEET_MEDICATION_LOGS)
                                ?: throw BackupFormatException("缺少必要工作表：$SHEET_MEDICATION_LOGS"))
                        } else emptyList()
                    )
                }
            } finally {
                if (sourceFile !== tempFile) sourceFile.delete()
            }
        } finally {
            tempFile.delete()
        }
    }

    /** 只读文件头部魔数判断是否为加密备份容器。 */
    private fun isEncryptedContainerFile(file: File): Boolean {
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(BackupCrypto.HEADER_SIZE)
            var read = 0
            while (read < buffer.size) {
                val count = input.read(buffer, read, buffer.size - read)
                if (count < 0) break
                read += count
            }
            buffer.copyOf(read)
        }
        return BackupCrypto.isEncryptedContainer(header)
    }

    /** 在交给 POI 解析前先按 ZIP 中央目录和实际解压字节做有界预检。 */
    private fun preflightZip(file: File) {
        try {
            ZipFile(file).use { zip ->
                val names = hashSetOf<String>()
                var entryCount = 0
                var worksheetXmlCount = 0
                var totalUncompressed = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount += 1
                    if (entryCount > BackupImportLimits.MAX_ZIP_ENTRIES) {
                        throw BackupFormatException("xlsx 条目数量超过安全上限")
                    }
                    val normalizedName = entry.name.replace('\\', '/')
                    if (!names.add(normalizedName)) {
                        throw BackupFormatException("xlsx 包含重复条目：${entry.name}")
                    }
                    if (normalizedName.startsWith('/') ||
                        normalizedName.split('/').any { it == ".." } ||
                        normalizedName.contains('\u0000')
                    ) {
                        throw BackupFormatException("xlsx 包含不安全的条目路径")
                    }
                    if (normalizedName.startsWith("xl/worksheets/") &&
                        normalizedName.endsWith(".xml", ignoreCase = true)
                    ) {
                        worksheetXmlCount += 1
                        if (worksheetXmlCount > BackupImportLimits.MAX_WORKSHEET_XML_ENTRIES) {
                            throw BackupFormatException("xlsx 工作表数量超过安全上限")
                        }
                    }

                    val declaredSize = entry.size
                    if (declaredSize > BackupImportLimits.MAX_UNCOMPRESSED_ENTRY_BYTES) {
                        throw BackupFormatException("xlsx 单个条目解压后超过 64 MB")
                    }
                    val compressedSize = entry.compressedSize
                    if (declaredSize > 0L && compressedSize == 0L) {
                        throw BackupFormatException("xlsx 条目压缩比例异常")
                    }
                    if (declaredSize > 0L && compressedSize > 0L &&
                        declaredSize.toDouble() / compressedSize.toDouble() >
                        1.0 / BackupImportLimits.MIN_INFLATE_RATIO
                    ) {
                        throw BackupFormatException("xlsx 条目压缩比例超过安全上限")
                    }

                    var entryBytes = 0L
                    val isXml = normalizedName.endsWith(".xml", ignoreCase = true)
                    var xmlScanTail = ""
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalUncompressed += count
                            if (entryBytes > BackupImportLimits.MAX_UNCOMPRESSED_ENTRY_BYTES) {
                                throw BackupFormatException("xlsx 单个条目解压后超过 64 MB")
                            }
                            if (totalUncompressed > BackupImportLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw BackupFormatException("xlsx 累计解压大小超过安全上限")
                            }
                            if (isXml) {
                                val xmlChunk = String(buffer, 0, count, StandardCharsets.UTF_8)
                                val xmlText = (xmlScanTail + xmlChunk).uppercase(Locale.ROOT)
                                if (xmlText.contains("<!DOCTYPE") || xmlText.contains("<!ENTITY")) {
                                    throw BackupFormatException("xlsx XML 包含不受支持的外部实体声明")
                                }
                                xmlScanTail = xmlText.takeLast(XML_TOKEN_OVERLAP)
                            }
                        }
                    }
                    if (entryBytes > 0L && compressedSize <= 0L) {
                        throw BackupFormatException("xlsx 条目压缩比异常")
                    }
                    if (entryBytes > 0L && compressedSize > 0L &&
                        entryBytes.toDouble() / compressedSize.toDouble() >
                        1.0 / BackupImportLimits.MIN_INFLATE_RATIO
                    ) {
                        throw BackupFormatException("xlsx 条目压缩比超过安全上限")
                    }
                }
            }
        } catch (throwable: BackupFormatException) {
            throw throwable
        } catch (throwable: Exception) {
            throw BackupFormatException("文件损坏或不是有效的 Excel .xlsx 备份", throwable)
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

    private fun readMedications(sheet: Sheet): List<BackupMedicationRow> {
        val columns = sheet.headerColumns()
        requireColumns(columns, "backup_id", "name", "dosage", "enabled", "created_at")
        return sheet.dataRows().map { (rowIndex, row) ->
            val id = row.text(columns, "backup_id").trim()
            val name = row.text(columns, "name").trim()
            val enabled = row.strictBoolean(columns, "enabled")
            val createdAt = row.strictLong(columns, "created_at")
            if (id.isBlank() || name.isBlank() || enabled == null || createdAt == null) {
                throw BackupFormatException("药品第 ${rowIndex + 1} 行字段无效")
            }
            BackupMedicationRow(id, name, row.text(columns, "dosage").trim(), enabled, createdAt)
        }
    }

    private fun readMedicationTimes(sheet: Sheet): List<BackupMedicationTimeRow> {
        val columns = sheet.headerColumns()
        requireColumns(columns, "backup_id", "medication_backup_id", "time_text")
        return sheet.dataRows().map { (rowIndex, row) ->
            val id = row.text(columns, "backup_id").trim()
            val medicationId = row.text(columns, "medication_backup_id").trim()
            val time = row.text(columns, "time_text").trim()
            if (id.isBlank() || medicationId.isBlank() || !TIME_PATTERN.matches(time)) {
                throw BackupFormatException("服药时间第 ${rowIndex + 1} 行字段无效")
            }
            BackupMedicationTimeRow(id, medicationId, time)
        }
    }

    private fun readMedicationLogs(sheet: Sheet): List<BackupMedicationLogRow> {
        val columns = sheet.headerColumns()
        requireColumns(columns, "time_backup_id", "epoch_day", "taken_at")
        return sheet.dataRows().map { (rowIndex, row) ->
            val timeId = row.text(columns, "time_backup_id").trim()
            val epochDay = row.strictLong(columns, "epoch_day")
            val takenAt = row.strictLong(columns, "taken_at")
            if (timeId.isBlank() || epochDay == null || takenAt == null) {
                throw BackupFormatException("服药打卡第 ${rowIndex + 1} 行字段无效")
            }
            BackupMedicationLogRow(timeId, epochDay, takenAt)
        }
    }

    private fun Sheet.dataRows(): List<Pair<Int, Row>> = (1..lastRowNum).mapNotNull { rowIndex ->
        val row = getRow(rowIndex) ?: return@mapNotNull null
        if (row.isEffectivelyBlank()) null else rowIndex to row
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

    private fun Row.strictLong(columns: Map<String, Int>, name: String): Long? {
        val value = text(columns, name).trim()
        if (value.isBlank()) return null
        return runCatching { BigDecimal(value).stripTrailingZeros().longValueExact() }.getOrNull()
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
        private const val SHEET_MEDICATIONS = "药品"
        private const val SHEET_MEDICATION_TIMES = "服药时间"
        private const val SHEET_MEDICATION_LOGS = "服药打卡"
        private const val XML_TOKEN_OVERLAP = 8
        private val SUPPORTED_FORMAT_VERSIONS = setOf(2, 3, 4)
        private val TIME_PATTERN = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

    }
}
