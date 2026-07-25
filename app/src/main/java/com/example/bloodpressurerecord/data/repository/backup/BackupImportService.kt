package com.example.bloodpressurerecord.data.repository.backup

import androidx.room.withTransaction
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.domain.calculator.BloodPressureRules
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupImportResult(
    val sessionCount: Int,
    val readingCount: Int
)

class BackupImportService(
    private val database: AppDatabase,
    private val appSettingsStore: AppSettingsStore,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun importXlsx(inputStream: InputStream): BackupImportResult = withContext(Dispatchers.IO) {
        val document = BackupFileReader().readXlsx(inputStream)
        val zone = document.meta["timezone"]
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val now = clockMillis()
        var readingCount = 0

        database.withTransaction {
            document.measurements.forEach { item ->
                val measuredAt = parseDateTime(item.measuredAt, zone)
                    ?: error("记录 ${item.recordId} 的 measured_at 无法识别")
                val category = BloodPressureRules.category(item.avgSystolic, item.avgDiastolic)
                database.measurementSessionDao().deleteReadingsBySessionId(item.recordId)
                database.measurementSessionDao().insertSession(
                    MeasurementSessionEntity(
                        id = item.recordId,
                        measuredAt = measuredAt,
                        scene = item.scene ?: "备份导入",
                        note = item.note,
                        symptomsJson = item.symptomsJson,
                        avgSystolic = item.avgSystolic,
                        avgDiastolic = item.avgDiastolic,
                        avgPulse = item.avgPulse,
                        category = category.name,
                        highRiskAlertTriggered = BloodPressureRules.isHighRisk(
                            item.avgSystolic,
                            item.avgDiastolic
                        ),
                        createdAt = parseDateTime(item.createdAt, zone) ?: now,
                        updatedAt = parseDateTime(item.updatedAt, zone) ?: now
                    )
                )
                val readings = item.readings.mapIndexed { index, reading ->
                    MeasurementReadingEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = item.recordId,
                        orderIndex = index + 1,
                        systolic = reading.systolic,
                        diastolic = reading.diastolic,
                        pulse = reading.pulse
                    )
                }
                database.measurementSessionDao().insertReadings(readings)
                readingCount += readings.size
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
                        updatedAt = now
                    )
                )
            }
        }

        restoreSettings(document.userProfile)
        BackupImportResult(document.measurements.size, readingCount)
    }

    private suspend fun restoreSettings(values: Map<String, String>) {
        values["large_text_enabled"]?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setLargeTextEnabled(it) }
        values["high_risk_alert_enabled"]?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setHighRiskAlertEnabled(it) }
        (values["show_trend_chart"] ?: values["display_show_target_line"])
            ?.toBooleanStrictOrNull()
            ?.let { appSettingsStore.setShowTrendChart(it) }
        values["morning_reminder_time"].nonBlank()
            ?.let { appSettingsStore.setMorningReminderTime(it) }
        values["evening_reminder_time"].nonBlank()
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

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.toIntOrNullSafe(): Int? = nonBlank()?.toIntOrNull()

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
