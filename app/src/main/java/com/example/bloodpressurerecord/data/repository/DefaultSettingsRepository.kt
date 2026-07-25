package com.example.bloodpressurerecord.data.repository

import android.content.Context
import android.net.Uri
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.data.repository.backup.BackupExportService
import com.example.bloodpressurerecord.data.repository.backup.BackupFileWriter
import com.example.bloodpressurerecord.data.repository.backup.BackupImportService
import com.example.bloodpressurerecord.data.db.AppDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.example.bloodpressurerecord.reminder.ReminderScheduler

class DefaultSettingsRepository(
    private val context: Context,
    private val appSettingsStore: AppSettingsStore,
    private val database: AppDatabase,
    private val userProfileDao: UserProfileDao,
    private val measurementSessionDao: MeasurementSessionDao,
    private val measurementDao: BloodPressureMeasurementDao
) : SettingsRepository {
    override fun observeSettings(): Flow<SettingsBundle> = combine(
        appSettingsStore.settingsFlow,
        userProfileDao.observeProfile()
    ) { appSettings, profile ->
        SettingsBundle(
            appSettings = appSettings,
            userProfile = profile?.toModel() ?: UserProfile()
        )
    }

    override suspend fun setLargeTextEnabled(enabled: Boolean) {
        appSettingsStore.setLargeTextEnabled(enabled)
    }

    override suspend fun setHighRiskAlertEnabled(enabled: Boolean) {
        appSettingsStore.setHighRiskAlertEnabled(enabled)
    }

    override suspend fun setShowTrendChart(enabled: Boolean) {
        appSettingsStore.setShowTrendChart(enabled)
    }

    override suspend fun setMorningReminderEnabled(enabled: Boolean) {
        appSettingsStore.setMorningReminderEnabled(enabled)
        rescheduleReminders()
    }

    override suspend fun setMorningReminderTime(value: String) {
        appSettingsStore.setMorningReminderTime(value)
        rescheduleReminders()
    }

    override suspend fun setEveningReminderEnabled(enabled: Boolean) {
        appSettingsStore.setEveningReminderEnabled(enabled)
        rescheduleReminders()
    }

    override suspend fun setEveningReminderTime(value: String) {
        appSettingsStore.setEveningReminderTime(value)
        rescheduleReminders()
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.upsert(
            UserProfileEntity(
                id = 1,
                name = profile.name?.takeIf { it.isNotBlank() },
                age = profile.age,
                gender = profile.gender?.takeIf { it.isNotBlank() },
                targetSystolic = profile.targetSystolic,
                targetDiastolic = profile.targetDiastolic,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearAllData(): Result<Unit> = runCatching {
        database.withTransaction {
            measurementSessionDao.deleteAllReadings()
            measurementSessionDao.deleteAllSessions()
            measurementDao.deleteAll()
            if (measurementDao.tableExists("blood_pressure_records") > 0) {
                database.openHelper.writableDatabase.execSQL("DELETE FROM blood_pressure_records")
            }
            userProfileDao.deleteAll()
        }
    }

    override suspend fun exportBackupXlsxToUri(uri: Uri, fileNameHint: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val payload = BackupExportService(
                sessionDao = measurementSessionDao,
                measurementDao = measurementDao,
                userProfileDao = userProfileDao,
                appSettingsStore = appSettingsStore
            ).buildPayload(
                appName = "家庭血压记录",
                appVersion = currentAppVersion()
            )
            
            if (payload.measurements.isEmpty()) {
                error(
                    "未读取到可导出的历史测量记录。\n" +
                        payload.diagnostics.toUserMessage() +
                        "\n请先确认历史页已有保存记录；如果历史页有数据但这里仍为 0，请反馈此诊断信息。"
                )
            }
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入所选文件，请重新选择保存位置")

            outputStream.use { stream ->
                val writer = BackupFileWriter()
                val template = runCatching {
                    context.assets.open(BackupFileWriter.TEMPLATE_ASSET_NAME)
                }.getOrNull()
                
                if (template != null) {
                    template.use { input ->
                        writer.writeXlsx(payload, stream, input)
                    }
                } else {
                    writer.writeXlsx(payload, stream)
                }
            }
            appSettingsStore.setLastSuccessfulExportAt(System.currentTimeMillis())
            "Excel 备份导出成功：$fileNameHint\n共导出 ${payload.measurements.size} 条测量记录\n${payload.diagnostics.toUserMessage()}\n文件仅保存在本地，不会上传。"
        }
    }

    override suspend fun refreshReminders() {
        rescheduleReminders()
    }

    override suspend fun importBackupXlsxFromUri(uri: Uri): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选文件")
            val result = inputStream.use { stream ->
                BackupImportService(
                    database = database,
                    appSettingsStore = appSettingsStore
                ).importXlsx(stream)
            }
            rescheduleReminders()
            result.toUserMessage()
        }
    }

    private fun UserProfileEntity.toModel(): UserProfile {
        return UserProfile(
            name = name,
            age = age,
            gender = gender,
            targetSystolic = targetSystolic,
            targetDiastolic = targetDiastolic
        )
    }

    private suspend fun rescheduleReminders() {
        ReminderScheduler(context).apply(appSettingsStore.settingsFlow.first())
    }

    @Suppress("DEPRECATION")
    private fun currentAppVersion(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
