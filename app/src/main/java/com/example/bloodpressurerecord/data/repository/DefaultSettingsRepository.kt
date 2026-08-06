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
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportPreview
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
    private val measurementDao: BloodPressureMeasurementDao,
    /** 服药提醒重排回调（闹钟 + 日历），由 AppContainer 注入避免直接依赖。 */
    private val medicationResync: (suspend () -> Unit)? = null,
    /** 导入、清空等批量变更后刷新桌面小部件。 */
    private val onDataChanged: (() -> Unit)? = null
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

    override suspend fun setDiscardFirstReading(enabled: Boolean) {
        appSettingsStore.setDiscardFirstReading(enabled)
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

    override suspend fun setMedicationReminderEnabled(enabled: Boolean) {
        val previous = appSettingsStore.settingsFlow.first().medicationReminderEnabled
        appSettingsStore.setMedicationReminderEnabled(enabled)
        try {
            medicationResync?.invoke()
        } catch (throwable: Throwable) {
            appSettingsStore.setMedicationReminderEnabled(previous)
            runCatching { medicationResync?.invoke() }
            throw throwable
        }
    }

    override suspend fun setMedicationCalendarSyncEnabled(enabled: Boolean) {
        val previous = appSettingsStore.settingsFlow.first().medicationCalendarSyncEnabled
        appSettingsStore.setMedicationCalendarSyncEnabled(enabled)
        try {
            medicationResync?.invoke()
        } catch (throwable: Throwable) {
            appSettingsStore.setMedicationCalendarSyncEnabled(previous)
            runCatching { medicationResync?.invoke() }
            throw throwable
        }
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

    override suspend fun clearAllData(): Result<ClearAllDataResult> {
        var databaseCleared = false
        try {
            database.withTransaction {
                measurementSessionDao.deleteAllReadings()
                measurementSessionDao.deleteAllSessions()
                measurementDao.deleteAll()
                if (measurementDao.tableExists("blood_pressure_records") > 0) {
                    database.openHelper.writableDatabase.execSQL("DELETE FROM blood_pressure_records")
                }
                userProfileDao.deleteAll()
                database.medicationDao().deleteAllLogs()
                database.medicationDao().deleteAllTimes()
                database.medicationDao().deleteAllMedications()
            }
            databaseCleared = true
        } catch (throwable: Throwable) {
            return Result.success(
                ClearAllDataResult(
                    databaseCleared = false,
                    settingsCleared = false,
                    remindersRescheduled = false,
                    widgetRefreshed = false,
                    warnings = listOf("数据库清理失败：${throwable.message ?: "未知错误"}")
                )
            )
        }

        val warnings = mutableListOf<String>()
        var settingsCleared = false
        var remindersRescheduled = true
        var widgetRefreshed = true

        // 数据库已清理后，后续 DataStore/提醒/小部件失败都要如实返回，
        // 不能把结果伪装成“数据没有删除”。
        try {
            // 在清空 DataStore 前先用持久化的旧时间点 id 取消药物闹钟，
            // 否则清空设置会丢失取消旧 PendingIntent 所需的 id。
            medicationResync?.invoke()
        } catch (throwable: Throwable) {
            remindersRescheduled = false
            warnings += "服药提醒同步失败"
        }
        try {
            appSettingsStore.clearAll()
            settingsCleared = true
        } catch (throwable: Throwable) {
            warnings += "设置重置失败"
        }
        try {
            rescheduleReminders()
        } catch (throwable: Throwable) {
            remindersRescheduled = false
            warnings += "血压提醒同步失败"
        }
        try {
            onDataChanged?.invoke()
        } catch (throwable: Throwable) {
            widgetRefreshed = false
            warnings += "桌面小部件刷新失败"
        }
        return Result.success(
            ClearAllDataResult(
                databaseCleared = databaseCleared,
                settingsCleared = settingsCleared,
                remindersRescheduled = remindersRescheduled,
                widgetRefreshed = widgetRefreshed,
                warnings = warnings
            )
        )
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
            "Excel 备份导出成功：$fileNameHint\n共导出 ${payload.measurements.size} 条测量记录\n${payload.diagnostics.toUserMessage()}\n文件已交给你选择的保存位置。"
        }
    }

    override suspend fun refreshReminders() {
        rescheduleReminders()
        medicationResync?.invoke()
    }

    override suspend fun importBackupXlsxFromUri(uri: Uri): Result<String> = runCatching {
        val preview = previewBackupXlsxFromUri(uri).getOrThrow()
        commitBackupImport(
            preview,
            BackupImportOptions(
                importMeasurements = true,
                restoreUserProfile = true,
                restoreDisplaySettings = true,
                restoreReminderSettings = true
            )
        ).getOrThrow()
    }

    override suspend fun previewBackupXlsxFromUri(uri: Uri): Result<BackupImportPreview> = runCatching {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选文件")
            inputStream.use { stream ->
                BackupImportService(
                    database = database,
                    appSettingsStore = appSettingsStore
                ).previewXlsx(stream)
            }
        }
    }

    override suspend fun commitBackupImport(
        preview: BackupImportPreview,
        options: BackupImportOptions
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val result = BackupImportService(
                database = database,
                appSettingsStore = appSettingsStore
            ).commitImport(preview, options)
            if (options.restoreReminderSettings) rescheduleReminders()
            onDataChanged?.invoke()
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
