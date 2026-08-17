package com.example.bloodpressurerecord.data.repository.backup

import androidx.room.withTransaction
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.calculator.MeasurementDerivation
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.ReadingValue
import com.example.bloodpressurerecord.data.scan.ScanSessionIds
import com.example.bloodpressurerecord.domain.time.MeasurementTimestampValidator
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray

object BackupImportLimits {
    const val MAX_FILE_BYTES = 10 * 1024 * 1024
    const val MAX_RECORDS = 5_000
    const val MAX_TOTAL_READING_ROWS = MAX_RECORDS * MeasurementInputRules.MAX_READING_COUNT
    const val MAX_RECORD_ID_LENGTH = ScanSessionIds.MAX_LENGTH
    const val MAX_ZIP_ENTRIES = 512
    const val MAX_UNCOMPRESSED_ENTRY_BYTES = 64L * 1024 * 1024
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 128L * 1024 * 1024
    const val MAX_WORKSHEET_XML_ENTRIES = 64
    const val MIN_INFLATE_RATIO = 0.01
}

enum class BackupImportErrorCode {
    INVALID_TIME,
    FUTURE_MEASUREMENT_TIME,
    INVALID_READING_COUNT,
    INVALID_READING,
    DUPLICATE_READING_ORDER,
    INVALID_AVERAGE_STRATEGY,
    SETTINGS_NOT_RESTORED
}

data class BackupImportError(
    val code: BackupImportErrorCode,
    val sourceRowNumber: Int?,
    val message: String
)

data class BackupImportResult(
    val insertedCount: Int,
    val replacedCount: Int,
    val correctedCount: Int,
    val skippedCount: Int,
    val readingCount: Int,
    val errors: List<BackupImportError>
) {
    val errorCount: Int
        get() = errors.size

    fun toUserMessage(): String {
        val settingsWarning = if (errors.any { it.code == BackupImportErrorCode.SETTINGS_NOT_RESTORED }) {
            "选定内容已导入，但设置恢复失败。"
        } else {
            ""
        }
        return "备份导入完成：新增 $insertedCount 条，覆盖 $replacedCount 条，" +
            "自动修正 $correctedCount 条，跳过 $skippedCount 条，错误 $errorCount 条。" +
            settingsWarning
    }
}

data class BackupImportOptions(
    val importMeasurements: Boolean = true,
    val restoreUserProfile: Boolean = false,
    val restoreDisplaySettings: Boolean = false,
    val restoreReminderSettings: Boolean = false
)

class BackupImportPreview internal constructor(
    val validRecordCount: Int,
    val insertedCount: Int,
    val replacedCount: Int,
    val correctedCount: Int,
    val skippedCount: Int,
    val readingCount: Int,
    val errors: List<BackupImportError>,
    val changesName: Boolean,
    val changesAgeAndGender: Boolean,
    val changesTargetPressure: Boolean,
    val changesDisplaySettings: Boolean,
    val changesReminderTimes: Boolean,
    val changesReminderEnabled: Boolean,
    internal val preparedRecords: List<PreparedImportRecord>,
    internal val userProfile: Map<String, String>
) {
    val errorCount: Int
        get() = errors.size
}

internal data class PreparedImportRecord(
    val session: MeasurementSessionEntity,
    val readings: List<MeasurementReadingEntity>,
    val wasCorrected: Boolean
)

class BackupImportService(
    private val database: AppDatabase,
    private val appSettingsStore: AppSettingsStore,
    private val beforeRecordWrite: (Int) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /** 兼容旧调用方：完整导入等价于预览后勾选所有可恢复内容并提交。 */
    suspend fun importXlsx(inputStream: InputStream): BackupImportResult {
        return commitImport(
            previewXlsx(inputStream),
            BackupImportOptions(
                importMeasurements = true,
                restoreUserProfile = true,
                restoreDisplaySettings = true,
                restoreReminderSettings = true
            )
        )
    }

    suspend fun previewXlsx(inputStream: InputStream): BackupImportPreview = withContext(Dispatchers.IO) {
        val document = BackupFileReader().readXlsx(
            SizeLimitedInputStream(inputStream, BackupImportLimits.MAX_FILE_BYTES)
        )
        val zone = document.meta["timezone"]
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: throw BackupFormatException("备份时区无效，未执行导入")

        val errors = mutableListOf<BackupImportError>()
        val validationNow = nowMillis()
        val preparedRecords = document.measurements.mapNotNull { source ->
            prepareRecord(source, zone, validationNow, errors)
        }
        val skippedCount = document.measurements.size - preparedRecords.size
        val dao = database.measurementSessionDao()
        val existingIds = hashSetOf<String>()
        preparedRecords.map { it.session.id }
            .chunked(DATABASE_BATCH_SIZE)
            .forEach { ids -> existingIds += dao.getExistingSessionIds(ids) }
        val currentProfile = database.userProfileDao().getProfile()
        val currentSettings = appSettingsStore.settingsFlow.first()
        BackupImportPreview(
            validRecordCount = preparedRecords.size,
            insertedCount = preparedRecords.count { it.session.id !in existingIds },
            replacedCount = preparedRecords.count { it.session.id in existingIds },
            correctedCount = preparedRecords.count { it.wasCorrected },
            skippedCount = skippedCount,
            readingCount = preparedRecords.sumOf { it.readings.size },
            errors = errors.toList(),
            changesName = document.userProfile["name"].nonBlank() != currentProfile?.name,
            changesAgeAndGender = document.userProfile["age"].toIntOrNullSafe() != currentProfile?.age ||
                document.userProfile["sex"].nonBlank() != currentProfile?.gender,
            changesTargetPressure = document.userProfile["target_sys"].toIntOrNullSafe() !=
                currentProfile?.targetSystolic ||
                document.userProfile["target_dia"].toIntOrNullSafe() != currentProfile?.targetDiastolic,
            changesDisplaySettings = displaySettingsDiffer(document.userProfile, currentSettings),
            changesReminderTimes = reminderTimesDiffer(document.userProfile, currentSettings),
            changesReminderEnabled = reminderEnabledDiffer(document.userProfile, currentSettings),
            preparedRecords = preparedRecords,
            userProfile = document.userProfile
        )
    }

    suspend fun commitImport(
        preview: BackupImportPreview,
        options: BackupImportOptions
    ): BackupImportResult = withContext(Dispatchers.IO) {
        val errors = preview.errors.toMutableList()
        val records = if (options.importMeasurements) preview.preparedRecords else emptyList()
        database.withTransaction {
            records.chunked(DATABASE_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                batch.indices.forEach { indexInBatch ->
                    beforeRecordWrite(batchIndex * DATABASE_BATCH_SIZE + indexInBatch)
                }
                val ids = batch.map { it.session.id }
                database.measurementSessionDao().deleteReadingsBySessionIds(ids)
                database.measurementSessionDao().insertSessions(batch.map { it.session })
                database.measurementSessionDao().insertReadings(batch.flatMap { it.readings })
            }
            if (options.restoreUserProfile && preview.userProfile.isNotEmpty()) {
                val profile = preview.userProfile
                database.userProfileDao().upsert(
                    UserProfileEntity(
                        id = 1,
                        name = profile["name"].nonBlank(),
                        age = profile["age"].toIntOrNullSafe(),
                        gender = profile["sex"].nonBlank(),
                        targetSystolic = profile["target_sys"].toIntOrNullSafe(),
                        targetDiastolic = profile["target_dia"].toIntOrNullSafe(),
                        updatedAt = records.maxOfOrNull { it.session.updatedAt } ?: nowMillis()
                    )
                )
            }
        }

        if (options.restoreDisplaySettings || options.restoreReminderSettings) {
            runCatching {
                appSettingsStore.restoreFromBackup(
                    values = preview.userProfile,
                    restoreDisplaySettings = options.restoreDisplaySettings,
                    restoreReminderSettings = options.restoreReminderSettings
                )
            }.onFailure {
                errors += BackupImportError(
                    code = BackupImportErrorCode.SETTINGS_NOT_RESTORED,
                    sourceRowNumber = null,
                    message = "选定内容已导入，但部分显示或提醒设置未能恢复。"
                )
            }
        }

        BackupImportResult(
            insertedCount = if (options.importMeasurements) preview.insertedCount else 0,
            replacedCount = if (options.importMeasurements) preview.replacedCount else 0,
            correctedCount = if (options.importMeasurements) preview.correctedCount else 0,
            skippedCount = preview.skippedCount,
            readingCount = if (options.importMeasurements) preview.readingCount else 0,
            errors = errors.toList()
        )
    }

    private fun prepareRecord(
        source: BackupImportMeasurement,
        zoneId: ZoneId,
        validationNow: Long,
        errors: MutableList<BackupImportError>
    ): PreparedImportRecord? {
        val measuredAt = parseDateTime(source.measuredAt, zoneId)
        if (measuredAt == null) {
            errors += source.error(
                BackupImportErrorCode.INVALID_TIME,
                "测量记录的时间字段无效。"
            )
            return null
        }
        if (MeasurementTimestampValidator.validate(measuredAt, validationNow) != null) {
            errors += source.error(
                BackupImportErrorCode.FUTURE_MEASUREMENT_TIME,
                MeasurementTimestampValidator.FUTURE_MEASUREMENT_TIME_MESSAGE
            )
            return null
        }
        if (source.readings.size !in
            MeasurementInputRules.MIN_READING_COUNT..MeasurementInputRules.MAX_READING_COUNT
        ) {
            errors += source.error(
                BackupImportErrorCode.INVALID_READING_COUNT,
                "每条记录必须包含 ${MeasurementInputRules.MIN_READING_COUNT} 至 " +
                    "${MeasurementInputRules.MAX_READING_COUNT} 组原始读数。"
            )
            return null
        }
        val orderIndexes = source.readings.mapNotNull { it.orderIndex }
        if (orderIndexes.size != source.readings.size ||
            orderIndexes.any { it <= 0 } ||
            orderIndexes.distinct().size != orderIndexes.size
        ) {
            errors += source.error(
                BackupImportErrorCode.DUPLICATE_READING_ORDER,
                "原始读数序号缺失、无效或重复。"
            )
            return null
        }

        val readingValues = source.readings.map { reading ->
            val systolic = reading.systolic
            val diastolic = reading.diastolic
            if (systolic == null || diastolic == null || reading.pulseWasInvalid) {
                errors += source.error(
                    BackupImportErrorCode.INVALID_READING,
                    "原始读数必须是合法整数。"
                )
                return null
            }
            ReadingValue(systolic, diastolic, reading.pulse)
        }
        if (readingValues.any { MeasurementInputRules.validateReading(it) != null }) {
            errors += source.error(
                BackupImportErrorCode.INVALID_READING,
                "原始读数超出允许范围，或舒张压未低于收缩压。"
            )
            return null
        }

        val explicitStrategy = source.backupAverageStrategy?.let { raw ->
            AverageStrategy.entries.firstOrNull { it.name == raw.trim().uppercase() }
                ?: run {
                    errors += source.error(
                        BackupImportErrorCode.INVALID_AVERAGE_STRATEGY,
                        "平均策略字段无效。"
                    )
                    return null
                }
        }

        // v3 直接使用显式策略；v2 为兼容旧备份，按收缩压、舒张压和脉搏三项平均值反推。
        val allDerived = MeasurementDerivation.derive(readingValues, AverageStrategy.ALL)
        val strategy = if (explicitStrategy != null) {
            explicitStrategy
        } else if (matchesBackupAverages(source, allDerived)) {
            AverageStrategy.ALL
        } else {
            MeasurementDerivation.derive(readingValues, AverageStrategy.DISCARD_FIRST)
                .takeIf { matchesBackupAverages(source, it) }
                ?.let { AverageStrategy.DISCARD_FIRST }
                ?: AverageStrategy.ALL
        }
        val derived = MeasurementDerivation.derive(readingValues, strategy)
        val createdAt = parseDateTime(source.createdAt, zoneId) ?: measuredAt
        val updatedAt = parseDateTime(source.updatedAt, zoneId) ?: createdAt
        val (symptomsJson, symptomsCorrected) = normalizeSymptomsJson(source.symptomsJson)
        val corrected = source.backupGroupCount != readingValues.size ||
            source.backupAvgSystolic != derived.average.avgSystolic ||
            source.backupAvgDiastolic != derived.average.avgDiastolic ||
            source.backupAvgPulse != derived.average.avgPulse ||
            source.backupLevel?.uppercase() != derived.category.name ||
            source.backupHighAlert != derived.containsHighRiskReading ||
            parseDateTime(source.createdAt, zoneId) == null ||
            parseDateTime(source.updatedAt, zoneId) == null ||
            symptomsCorrected

        val session = MeasurementSessionEntity(
            id = source.recordId,
            measuredAt = measuredAt,
            scene = source.scene ?: "备份导入",
            note = source.note,
            symptomsJson = symptomsJson,
            avgSystolic = derived.average.avgSystolic,
            avgDiastolic = derived.average.avgDiastolic,
            avgPulse = derived.average.avgPulse,
            averageStrategy = strategy.name,
            category = derived.category.name,
            containsHighRiskReading = derived.containsHighRiskReading,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        val readings = source.readings.zip(readingValues).map { (sourceReading, reading) ->
            val orderIndex = requireNotNull(sourceReading.orderIndex)
            MeasurementReadingEntity(
                id = deterministicReadingId(source.recordId, orderIndex),
                sessionId = source.recordId,
                orderIndex = orderIndex,
                systolic = reading.systolic,
                diastolic = reading.diastolic,
                pulse = reading.pulse
            )
        }
        return PreparedImportRecord(session, readings, corrected)
    }

    private fun parseDateTime(value: String?, zoneId: ZoneId): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun displaySettingsDiffer(
        values: Map<String, String>,
        current: AppSettings
    ): Boolean {
        return listOf(
            values["large_text_enabled"]?.toBooleanStrictOrNull()
                ?.let { it != current.largeTextEnabled },
            values["high_risk_alert_enabled"]?.toBooleanStrictOrNull()
                ?.let { it != current.highRiskAlertEnabled },
            values["discard_first_reading"]?.toBooleanStrictOrNull()
                ?.let { it != current.discardFirstReading },
            (values["show_trend_chart"] ?: values["display_show_target_line"])
                ?.toBooleanStrictOrNull()?.let { it != current.showTrendChart }
        ).any { it == true }
    }

    private fun reminderTimesDiffer(
        values: Map<String, String>,
        current: AppSettings
    ): Boolean {
        return listOf(
            values["morning_reminder_time"]?.takeIf(::isValidTime)
                ?.let { it != current.morningReminderTime },
            values["evening_reminder_time"]?.takeIf(::isValidTime)
                ?.let { it != current.eveningReminderTime }
        ).any { it == true }
    }

    private fun reminderEnabledDiffer(
        values: Map<String, String>,
        current: AppSettings
    ): Boolean {
        return listOf(
            values["morning_reminder_enabled"]?.toBooleanStrictOrNull()
                ?.let { it != current.morningReminderEnabled },
            values["evening_reminder_enabled"]?.toBooleanStrictOrNull()
                ?.let { it != current.eveningReminderEnabled }
        ).any { it == true }
    }

    private fun isValidTime(value: String): Boolean {
        return Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
    }

    private fun BackupImportMeasurement.error(
        code: BackupImportErrorCode,
        message: String
    ): BackupImportError {
        return BackupImportError(code, sourceRowNumber, "第 $sourceRowNumber 行：$message")
    }

    private fun matchesBackupAverages(
        source: BackupImportMeasurement,
        derived: com.example.bloodpressurerecord.domain.calculator.MeasurementDerivedValues
    ): Boolean {
        return source.backupAvgSystolic == derived.average.avgSystolic &&
            source.backupAvgDiastolic == derived.average.avgDiastolic &&
            source.backupAvgPulse == derived.average.avgPulse
    }

    /**
     * 症状列以 JSON 数组为标准格式。合法数组会规范化后入库；
     * 手工编辑成纯文本时按常见分隔符拆成症状列表并计入“自动修正”，
     * 不再原样写库后在读取端被静默丢弃。
     */
    private fun normalizeSymptomsJson(raw: String?): Pair<String?, Boolean> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null to false
        val parsed = runCatching {
            val array = JSONArray(text)
            List(array.length()) { index -> array.getString(index).trim() }
                .filter { it.isNotEmpty() }
        }.getOrNull()
        if (parsed != null) {
            return (if (parsed.isEmpty()) null else JSONArray(parsed).toString()) to false
        }
        val items = text.split('、', '，', ',', '；', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return (if (items.isEmpty()) null else JSONArray(items).toString()) to true
    }

    private fun deterministicReadingId(recordId: String, orderIndex: Int): String {
        return UUID.nameUUIDFromBytes(
            "$recordId:$orderIndex".toByteArray(StandardCharsets.UTF_8)
        ).toString()
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    private fun String?.toIntOrNullSafe(): Int? = nonBlank()?.toIntOrNull()

    companion object {
        private const val DATABASE_BATCH_SIZE = 250
        private val DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT)
    }
}

private class SizeLimitedInputStream(
    input: InputStream,
    private val maximumBytes: Int
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) accountFor(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) accountFor(count.toLong())
        return count
    }

    override fun skip(count: Long): Long {
        val skipped = super.skip(count)
        if (skipped > 0) accountFor(skipped)
        return skipped
    }

    private fun accountFor(count: Long) {
        bytesRead += count
        if (bytesRead > maximumBytes) {
            throw BackupFormatException("备份文件超过 10 MB 上限")
        }
    }
}
