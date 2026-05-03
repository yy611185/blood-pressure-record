package com.example.bloodpressurerecord.data.repository.backup

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao
import com.example.bloodpressurerecord.data.db.dao.LegacyBloodPressureRecordRow
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionWithReadings
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BackupExportService(
    private val sessionDao: MeasurementSessionDao,
    private val measurementDao: BloodPressureMeasurementDao,
    private val userProfileDao: UserProfileDao,
    private val appSettingsStore: AppSettingsStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun buildPayload(appName: String, appVersion: String): BackupExportPayload = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getAllSessionsWithReadings().sortedBy { it.session.measuredAt }
        val legacyMeasurements = measurementDao.getAll()
        val legacyRecordRows = readLegacyRecordRows()
        val measurementRows = buildMeasurementRows(
            sessions = sessions,
            legacyMeasurements = legacyMeasurements,
            legacyRecordRows = legacyRecordRows
        )
        val settings = appSettingsStore.settingsFlow.first()
        val profile = userProfileDao.getProfile()
        val exportedAt = formatDateTime(clockMillis())
        val diagnostics = BackupExportDiagnostics(
            sessionCount = sessionDao.countSessions(),
            readingCount = sessionDao.countReadings(),
            legacyBpMeasurementCount = measurementDao.countAll(),
            legacyBloodPressureRecordCount = legacyRecordRows.size
        )

        BackupExportPayload(
            instructions = buildInstructions(),
            measurements = measurementRows,
            userProfile = buildUserProfileItems(profile, settings),
            meta = buildMetaItems(
                appName = appName,
                appVersion = appVersion,
                exportedAt = exportedAt,
                totalRecords = measurementRows.size,
                diagnostics = diagnostics
            ),
            diagnostics = diagnostics
        )
    }

    private suspend fun readLegacyRecordRows(): List<LegacyBloodPressureRecordRow> {
        if (measurementDao.tableExists("blood_pressure_records") <= 0) return emptyList()
        return measurementDao.getLegacyBloodPressureRecords(
            SimpleSQLiteQuery("SELECT id, memberName, systolic, diastolic, pulse, measuredAtMillis, level, remark FROM blood_pressure_records ORDER BY measuredAtMillis ASC")
        )
    }

    private fun buildMeasurementRows(
        sessions: List<MeasurementSessionWithReadings>,
        legacyMeasurements: List<BloodPressureMeasurementEntity>,
        legacyRecordRows: List<LegacyBloodPressureRecordRow>
    ): List<BackupMeasurementRow> {
        return buildList {
            addAll(sessions.map(::toMeasurementRow))
            addAll(legacyMeasurements.map(::toMeasurementRow))
            addAll(legacyRecordRows.map(::toMeasurementRow))
        }.sortedBy { it.measuredAt }
    }

    private fun toMeasurementRow(item: MeasurementSessionWithReadings): BackupMeasurementRow {
        val session = item.session
        val sortedReadings = item.readings
            .sortedBy { it.orderIndex }
            .take(MAX_EXPORT_READING_GROUPS)
            .map { reading ->
                BackupReadingValue(
                    systolic = reading.systolic,
                    diastolic = reading.diastolic,
                    pulse = reading.pulse
                )
            }

        return BackupMeasurementRow(
            recordId = session.id,
            measuredAt = formatDateTime(session.measuredAt),
            date = formatDate(session.measuredAt),
            time = formatTime(session.measuredAt),
            groupCount = sortedReadings.size,
            readings = sortedReadings,
            avgSystolic = session.avgSystolic,
            avgDiastolic = session.avgDiastolic,
            avgPulse = session.avgPulse,
            level = session.category,
            highAlert = session.highRiskAlertTriggered,
            note = session.note,
            createdAt = formatDateTime(session.createdAt),
            updatedAt = formatDateTime(session.updatedAt)
        )
    }

    private fun toMeasurementRow(item: BloodPressureMeasurementEntity): BackupMeasurementRow {
        return BackupMeasurementRow(
            recordId = "bp_measurements:${item.id}",
            measuredAt = formatDateTime(item.measuredAtMillis),
            date = formatDate(item.measuredAtMillis),
            time = formatTime(item.measuredAtMillis),
            groupCount = 1,
            readings = listOf(BackupReadingValue(item.systolic, item.diastolic, item.pulse)),
            avgSystolic = item.systolic,
            avgDiastolic = item.diastolic,
            avgPulse = item.pulse,
            level = item.level,
            highAlert = item.systolic >= 180 || item.diastolic >= 120,
            note = "兼容旧版单次记录；成员：${item.memberName}",
            createdAt = null,
            updatedAt = null
        )
    }

    private fun toMeasurementRow(item: LegacyBloodPressureRecordRow): BackupMeasurementRow {
        return BackupMeasurementRow(
            recordId = "blood_pressure_records:${item.id}",
            measuredAt = formatDateTime(item.measuredAtMillis),
            date = formatDate(item.measuredAtMillis),
            time = formatTime(item.measuredAtMillis),
            groupCount = 1,
            readings = listOf(BackupReadingValue(item.systolic, item.diastolic, item.pulse)),
            avgSystolic = item.systolic,
            avgDiastolic = item.diastolic,
            avgPulse = item.pulse,
            level = item.level.orEmpty(),
            highAlert = item.systolic >= 180 || item.diastolic >= 120,
            note = item.remark.orEmpty().ifBlank { "兼容旧版单次记录；成员：${item.memberName.orEmpty()}" },
            createdAt = null,
            updatedAt = null
        )
    }

    private fun buildInstructions(): List<Pair<String, String>> = listOf(
        "文件用途" to "用于家庭血压记录的本地备份与换机迁移。",
        "数据来源" to "数据仅来自本机本地数据库，不包含云端或服务器数据。",
        "注意事项" to "请尽量不要修改工作表名称和列名，以便未来版本稳定导回。",
        "隐私说明" to "导出文件只会保存到你选择的位置，不会上传服务器。",
        "医疗声明" to "本文件仅供健康记录参考，不替代专业医疗诊断或治疗建议。"
    )

    private fun buildUserProfileItems(
        profile: UserProfileEntity?,
        settings: AppSettings
    ): List<BackupUserProfileItem> {
        val reminderEnabled = settings.morningReminderEnabled || settings.eveningReminderEnabled
        val reminderTime = buildList {
            if (settings.morningReminderEnabled) add("morning ${settings.morningReminderTime}")
            if (settings.eveningReminderEnabled) add("evening ${settings.eveningReminderTime}")
        }.joinToString("; ")

        return listOf(
            BackupUserProfileItem("name", profile?.name),
            BackupUserProfileItem("age", profile?.age?.toString()),
            BackupUserProfileItem("sex", profile?.gender),
            BackupUserProfileItem("target_sys", profile?.targetSystolic?.toString()),
            BackupUserProfileItem("target_dia", profile?.targetDiastolic?.toString()),
            BackupUserProfileItem("reminder_enabled", reminderEnabled.toString()),
            BackupUserProfileItem("reminder_time", reminderTime.ifBlank { null }),
            BackupUserProfileItem("display_show_target_line", settings.showTrendChart.toString())
        )
    }

    private fun buildMetaItems(
        appName: String,
        appVersion: String,
        exportedAt: String,
        totalRecords: Int,
        diagnostics: BackupExportDiagnostics
    ): List<BackupMetaItem> = listOf(
        BackupMetaItem("app_name", appName),
        BackupMetaItem("app_version", appVersion),
        BackupMetaItem("export_format_version", EXPORT_FORMAT_VERSION.toString()),
        BackupMetaItem("exported_at", exportedAt),
        BackupMetaItem("timezone", zoneId.id),
        BackupMetaItem("total_records", totalRecords.toString()),
        BackupMetaItem("measurement_sessions_count", diagnostics.sessionCount.toString()),
        BackupMetaItem("measurement_readings_count", diagnostics.readingCount.toString()),
        BackupMetaItem("legacy_bp_measurements_count", diagnostics.legacyBpMeasurementCount.toString()),
        BackupMetaItem("legacy_blood_pressure_records_count", diagnostics.legacyBloodPressureRecordCount.toString()),
        BackupMetaItem("source", "room_local_db")
    )

    private fun formatDateTime(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(dateTimeFormatter)
    }

    private fun formatDate(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(dateFormatter)
    }

    private fun formatTime(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter)
    }

    companion object {
        const val EXPORT_FORMAT_VERSION = 1
        const val MAX_EXPORT_READING_GROUPS = 5
    }
}
