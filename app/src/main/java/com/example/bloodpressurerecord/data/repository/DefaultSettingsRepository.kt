package com.example.bloodpressurerecord.data.repository

import android.content.Context
import android.net.Uri
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.data.repository.backup.BackupCrypto
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
import java.io.ByteArrayOutputStream
import com.example.bloodpressurerecord.ui.common.FileSessionDraftRepository

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
        FileSessionDraftRepository(context).clearAll().onFailure {
            warnings += "测量草稿清理失败"
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

    override suspend fun exportBackupXlsxToUri(
        uri: Uri,
        fileNameHint: String,
        passphrase: CharArray?
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val payload = BackupExportService(
                sessionDao = measurementSessionDao,
                measurementDao = measurementDao,
                userProfileDao = userProfileDao,
                appSettingsStore = appSettingsStore,
                medicationDao = database.medicationDao()
            ).buildPayload(
                appName = "家庭血压记录",
                appVersion = currentAppVersion()
            )

            // 先在内存中生成 xlsx，再按需加密后一次性写出，避免把明文临时落盘。
            val xlsxBytes = ByteArrayOutputStream().use { buffer ->
                val writer = BackupFileWriter()
                val template = runCatching {
                    context.assets.open(BackupFileWriter.TEMPLATE_ASSET_NAME)
                }.getOrNull()

                if (template != null) {
                    template.use { input ->
                        writer.writeXlsx(payload, buffer, input)
                    }
                } else {
                    writer.writeXlsx(payload, buffer)
                }
                buffer.toByteArray()
            }

            val exportBytes = if (passphrase != null) {
                BackupCrypto.encrypt(xlsxBytes, passphrase)
            } else {
                xlsxBytes
            }
            require(exportBytes.size <= com.example.bloodpressurerecord.data.repository.backup.BackupImportLimits.MAX_FILE_BYTES) {
                "生成的备份超过 10 MB，无法保证可恢复；请减少范围后分批导出。"
            }
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入所选文件，请重新选择保存位置")

            outputStream.use { stream ->
                stream.write(exportBytes)
                stream.flush()
            }
            appSettingsStore.setLastSuccessfulExportAt(System.currentTimeMillis())
            if (passphrase != null) {
                "加密备份导出成功：$fileNameHint\n共导出 ${payload.measurements.size} 条测量记录\n" +
                    payload.diagnostics.toUserMessage() + "\n包含药品 ${payload.medications.size} 种、" +
                    "时间点 ${payload.medicationTimes.size} 个、服药打卡 ${payload.medicationLogs.size} 条。" +
                    "\n文件已用口令加密（.bpx），导入时需要相同口令；请妥善保管口令。"
            } else {
                "Excel 备份导出成功：$fileNameHint\n共导出 ${payload.measurements.size} 条测量记录\n" +
                    payload.diagnostics.toUserMessage() + "\n包含药品 ${payload.medications.size} 种、" +
                    "时间点 ${payload.medicationTimes.size} 个、服药打卡 ${payload.medicationLogs.size} 条。" +
                    "\n文件已交给你选择的保存位置。"
            }
        }
    }

    override suspend fun refreshReminders() {
        rescheduleReminders()
        medicationResync?.invoke()
    }

    override suspend fun importBackupXlsxFromUri(
        uri: Uri,
        passphrase: CharArray?
    ): Result<String> = runCatching {
        val preview = previewBackupXlsxFromUri(uri, passphrase).getOrThrow()
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

    override suspend fun previewBackupXlsxFromUri(
        uri: Uri,
        passphrase: CharArray?
    ): Result<BackupImportPreview> = runCatching {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选文件")
            inputStream.use { stream ->
                BackupImportService(
                    database = database,
                    appSettingsStore = appSettingsStore
                ).previewXlsx(stream, passphrase)
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
            val followUpWarnings = mutableListOf<String>()
            if (options.restoreReminderSettings) {
                runCatching { rescheduleReminders() }
                    .onFailure { followUpWarnings += "血压提醒待重试" }
            }
            runCatching { medicationResync?.invoke() }
                .onFailure { followUpWarnings += "服药提醒与日历待重试" }
            runCatching { onDataChanged?.invoke() }
                .onFailure { followUpWarnings += "桌面小部件待刷新" }
            buildString {
                append(result.toUserMessage())
                if (followUpWarnings.isNotEmpty()) {
                    append(" 数据已恢复；")
                    append(followUpWarnings.joinToString("、"))
                    append("。")
                }
            }
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
