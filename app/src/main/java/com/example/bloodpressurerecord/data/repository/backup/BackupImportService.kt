package com.example.bloodpressurerecord.data.repository.backup

import androidx.room.withTransaction
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.calculator.MeasurementDerivation
import com.example.bloodpressurerecord.domain.model.ReadingValue
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupImportLimits {
    const val MAX_FILE_BYTES = 10 * 1024 * 1024
    const val MAX_RECORDS = 5_000
    const val MAX_TOTAL_READING_ROWS = MAX_RECORDS * MeasurementInputRules.MAX_READING_COUNT
    const val MAX_RECORD_ID_LENGTH = 200
}

enum class BackupImportErrorCode {
    INVALID_TIME,
    INVALID_READING_COUNT,
    INVALID_READING,
    DUPLICATE_READING_ORDER,
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
        return "备份导入完成：新增 $insertedCount 条，覆盖 $replacedCount 条，" +
            "自动修正 $correctedCount 条，跳过 $skippedCount 条，错误 $errorCount 条。"
    }
}

class BackupImportService(
    private val database: AppDatabase,
    private val appSettingsStore: AppSettingsStore,
    private val beforeRecordWrite: (Int) -> Unit = {}
) {
    suspend fun importXlsx(inputStream: InputStream): BackupImportResult = withContext(Dispatchers.IO) {
        val document = BackupFileReader().readXlsx(
            SizeLimitedInputStream(inputStream, BackupImportLimits.MAX_FILE_BYTES)
        )
        val zone = document.meta["timezone"]
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: throw BackupFormatException("备份时区无效，未执行导入")

        val errors = mutableListOf<BackupImportError>()
        val preparedRecords = document.measurements.mapNotNull { source ->
            prepareRecord(source, zone, errors)
        }
        val skippedCount = document.measurements.size - preparedRecords.size
        if (preparedRecords.isEmpty()) {
            return@withContext BackupImportResult(
                insertedCount = 0,
                replacedCount = 0,
                correctedCount = 0,
                skippedCount = skippedCount,
                readingCount = 0,
                errors = errors.toList()
            )
        }

        val dao = database.measurementSessionDao()
        val existingIds = hashSetOf<String>()
        preparedRecords.map { it.session.id }
            .chunked(DATABASE_BATCH_SIZE)
            .forEach { ids -> existingIds += dao.getExistingSessionIds(ids) }
        database.withTransaction {
            preparedRecords.chunked(DATABASE_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                batch.indices.forEach { indexInBatch ->
                    beforeRecordWrite(batchIndex * DATABASE_BATCH_SIZE + indexInBatch)
                }
                val ids = batch.map { it.session.id }
                dao.deleteReadingsBySessionIds(ids)
                dao.insertSessions(batch.map { it.session })
                dao.insertReadings(batch.flatMap { it.readings })
            }

            document.userProfile.takeIf { it.isNotEmpty() }?.let { profile ->
                database.userProfileDao().upsert(
                    UserProfileEntity(
                        id = 1,
                        name = profile["name"].nonBlank(),
                        age = profile["age"].toIntOrNullSafe(),
                        gender = profile["sex"].nonBlank(),
                        targetSystolic = profile["target_sys"].toIntOrNullSafe(),
                        targetDiastolic = profile["target_dia"].toIntOrNullSafe(),
                        updatedAt = preparedRecords.maxOf { it.session.updatedAt }
                    )
                )
            }
        }

        runCatching { restoreSettings(document.userProfile) }
            .onFailure {
                errors += BackupImportError(
                    code = BackupImportErrorCode.SETTINGS_NOT_RESTORED,
                    sourceRowNumber = null,
                    message = "测量记录已导入，但部分显示或提醒设置未能恢复。"
                )
            }

        BackupImportResult(
            insertedCount = preparedRecords.count { it.session.id !in existingIds },
            replacedCount = preparedRecords.count { it.session.id in existingIds },
            correctedCount = preparedRecords.count { it.wasCorrected },
            skippedCount = skippedCount,
            readingCount = preparedRecords.sumOf { it.readings.size },
            errors = errors.toList()
        )
    }

    private fun prepareRecord(
        source: BackupImportMeasurement,
        zoneId: ZoneId,
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

        val derived = MeasurementDerivation.derive(readingValues)
        val createdAt = parseDateTime(source.createdAt, zoneId) ?: measuredAt
        val updatedAt = parseDateTime(source.updatedAt, zoneId) ?: createdAt
        val corrected = source.backupGroupCount != readingValues.size ||
            source.backupAvgSystolic != derived.average.avgSystolic ||
            source.backupAvgDiastolic != derived.average.avgDiastolic ||
            source.backupAvgPulse != derived.average.avgPulse ||
            source.backupLevel?.uppercase() != derived.category.name ||
            source.backupHighAlert != derived.containsHighRiskReading ||
            parseDateTime(source.createdAt, zoneId) == null ||
            parseDateTime(source.updatedAt, zoneId) == null

        val session = MeasurementSessionEntity(
            id = source.recordId,
            measuredAt = measuredAt,
            scene = source.scene ?: "备份导入",
            note = source.note,
            symptomsJson = source.symptomsJson,
            avgSystolic = derived.average.avgSystolic,
            avgDiastolic = derived.average.avgDiastolic,
            avgPulse = derived.average.avgPulse,
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

    private suspend fun restoreSettings(values: Map<String, String>) {
        values["large_text_enabled"]?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setLargeTextEnabled(it) }
        values["high_risk_alert_enabled"]?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setHighRiskAlertEnabled(it) }
        (values["show_trend_chart"] ?: values["display_show_target_line"])
            ?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setShowTrendChart(it) }
        values["morning_reminder_time"].validTime()
            ?.let { appSettingsStore.setMorningReminderTime(it) }
        values["evening_reminder_time"].validTime()
            ?.let { appSettingsStore.setEveningReminderTime(it) }
        values["morning_reminder_enabled"]?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setMorningReminderEnabled(it) }
        values["evening_reminder_enabled"]?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setEveningReminderEnabled(it) }
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

    private fun BackupImportMeasurement.error(
        code: BackupImportErrorCode,
        message: String
    ): BackupImportError {
        return BackupImportError(code, sourceRowNumber, "第 $sourceRowNumber 行：$message")
    }

    private fun deterministicReadingId(recordId: String, orderIndex: Int): String {
        return UUID.nameUUIDFromBytes(
            "$recordId:$orderIndex".toByteArray(StandardCharsets.UTF_8)
        ).toString()
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    private fun String?.toIntOrNullSafe(): Int? = nonBlank()?.toIntOrNull()
    private fun String?.validTime(): String? = nonBlank()?.takeIf {
        runCatching { LocalTime.parse(it) }.isSuccess
    }

    private data class PreparedImportRecord(
        val session: MeasurementSessionEntity,
        val readings: List<MeasurementReadingEntity>,
        val wasCorrected: Boolean
    )

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
