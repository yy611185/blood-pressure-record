package com.example.bloodpressurerecord.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import com.example.bloodpressurerecord.data.calendar.MedicationCalendarSync
import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.dao.BloodPressureMeasurementDao
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.dao.UserProfileDao
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.data.repository.backup.BackupCrypto
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPayload
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPlan
import com.example.bloodpressurerecord.data.repository.backup.BackupExportService
import com.example.bloodpressurerecord.data.repository.backup.BackupFileReader
import com.example.bloodpressurerecord.data.repository.backup.BackupFileWriter
import com.example.bloodpressurerecord.data.repository.backup.BackupImportLimits
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportPreview
import com.example.bloodpressurerecord.data.repository.backup.BackupImportService
import com.example.bloodpressurerecord.data.repository.backup.clearLegacyBackupImportFiles
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.export.report.ReportCache
import com.example.bloodpressurerecord.export.report.ReportShare
import com.example.bloodpressurerecord.reminder.MedicationReminders
import com.example.bloodpressurerecord.reminder.ReminderScheduler
import com.example.bloodpressurerecord.reminder.ReminderType
import com.example.bloodpressurerecord.ui.common.FileSessionDraftRepository
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val onDataChanged: (suspend () -> Unit)? = null,
    /** 应用内提醒开关只影响闹钟，不依赖可选的日历授权。 */
    private val medicationAlarmResync: (suspend () -> Unit)? = null,
    private val reportCache: ReportCache = ReportCache(File(context.cacheDir, "reports")),
    private val deletionUndoManager: DeletionUndoManager? = null,
    private val cancelRemovedMedicationNotifications: (suspend (Collection<Long>) -> Unit)? = null,
    private val cleanMedicationCalendar: (suspend () -> Unit)? = null,
    private val cancelMeasurementNotifications: () -> Unit = {
        MedicationReminders.cancelMeasurementNotifications(context)
    },
    private val applyMeasurementReminders: (AppSettings) -> Unit = {
        ReminderScheduler(context).apply(it)
    }
) : SettingsRepository {
    private val calendarSettingsMutex = Mutex()
    private val reminderSettingsMutex = Mutex()

    override fun observeTrendAggregationPreference(): Flow<TrendAggregation?> =
        appSettingsStore.trendAggregationPreference

    override suspend fun setTrendAggregationPreference(aggregation: TrendAggregation) {
        appSettingsStore.setTrendAggregationPreference(aggregation)
    }

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

    override suspend fun setAppearanceMode(mode: String) {
        appSettingsStore.setAppearanceMode(mode)
    }

    override suspend fun setShowBuddy(enabled: Boolean) {
        appSettingsStore.setShowBuddy(enabled)
    }

    override suspend fun setDiscardFirstReading(enabled: Boolean) {
        appSettingsStore.setDiscardFirstReading(enabled)
    }

    override suspend fun setMorningReminderEnabled(enabled: Boolean) {
        updateMorningScheduleInternal(enabled = enabled, time = null)
    }

    override suspend fun setMorningReminderTime(value: String) {
        updateMorningScheduleInternal(enabled = null, time = value)
    }

    override suspend fun setMorningReminderSchedule(time: String, enabled: Boolean) {
        updateMorningScheduleInternal(enabled = enabled, time = time)
    }

    override suspend fun setEveningReminderEnabled(enabled: Boolean) {
        updateEveningScheduleInternal(enabled = enabled, time = null)
    }

    override suspend fun setEveningReminderTime(value: String) {
        updateEveningScheduleInternal(enabled = null, time = value)
    }

    override suspend fun setEveningReminderSchedule(time: String, enabled: Boolean) {
        updateEveningScheduleInternal(enabled = enabled, time = time)
    }

    private suspend fun updateMorningScheduleInternal(enabled: Boolean?, time: String?) {
        reminderSettingsMutex.withLock {
            withContext(NonCancellable) {
                val previous = appSettingsStore.settingsFlow.first()
                appSettingsStore.updateMorningReminder(enabled = enabled, time = time)
                try {
                    rescheduleReminders()
                } catch (throwable: Throwable) {
                    appSettingsStore.updateMorningReminder(
                        enabled = previous.morningReminderEnabled,
                        time = previous.morningReminderTime
                    )
                    runCatching { rescheduleReminders() }
                    throw throwable
                }
                if (enabled == false) {
                    NotificationManagerCompat.from(context).cancel(ReminderType.MORNING.notificationId)
                }
            }
        }
    }

    private suspend fun updateEveningScheduleInternal(enabled: Boolean?, time: String?) {
        reminderSettingsMutex.withLock {
            withContext(NonCancellable) {
                val previous = appSettingsStore.settingsFlow.first()
                appSettingsStore.updateEveningReminder(enabled = enabled, time = time)
                try {
                    rescheduleReminders()
                } catch (throwable: Throwable) {
                    appSettingsStore.updateEveningReminder(
                        enabled = previous.eveningReminderEnabled,
                        time = previous.eveningReminderTime
                    )
                    runCatching { rescheduleReminders() }
                    throw throwable
                }
                if (enabled == false) {
                    NotificationManagerCompat.from(context).cancel(ReminderType.EVENING.notificationId)
                }
            }
        }
    }

    override suspend fun setMedicationReminderEnabled(enabled: Boolean) {
        val previous = appSettingsStore.settingsFlow.first().medicationReminderEnabled
        appSettingsStore.setMedicationReminderEnabled(enabled)
        try {
            medicationAlarmResync?.invoke()
        } catch (throwable: Throwable) {
            appSettingsStore.setMedicationReminderEnabled(previous)
            runCatching { medicationAlarmResync?.invoke() }
            throw throwable
        }
    }

    override suspend fun getWritableMedicationCalendars() =
        MedicationCalendarSync(context).loadTargets()

    override suspend fun setMedicationCalendar(calendarId: Long?, includeDetails: Boolean) {
        calendarSettingsMutex.withLock {
            val previous = appSettingsStore.settingsFlow.first()
            appSettingsStore.setMedicationCalendar(calendarId, includeDetails)
            try {
                if (calendarId == null && cleanMedicationCalendar != null) {
                    cleanMedicationCalendar.invoke()
                } else {
                    medicationResync?.invoke()
                }
            } catch (throwable: Throwable) {
                // 关闭同步后即使暂时无法清理旧日程，也不能恢复原来的写入授权。
                if (calendarId != null) {
                    withContext(NonCancellable) {
                        appSettingsStore.setMedicationCalendar(
                            previous.medicationCalendarId, previous.medicationCalendarIncludeDetails
                        )
                        runCatching { medicationResync?.invoke() }
                    }
                }
                throw throwable
            }
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

    override suspend fun clearAllData(): Result<ClearAllDataResult> =
        deletionUndoManager?.clearData(::clearDataLocked) ?: clearDataLocked {}

    private suspend fun clearDataLocked(onDatabaseCommitted: () -> Unit): Result<ClearAllDataResult> =
        reminderSettingsMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) { clearData(onDatabaseCommitted) }
        }

    private suspend fun clearData(onDatabaseCommitted: () -> Unit): Result<ClearAllDataResult> {
        val warnings = mutableListOf<String>()
        val databaseTimeIds = runCatching { database.medicationDao().getAllTimeIds() }
            .onFailure { warnings += "未能读取数据库中的服药提醒时段" }
            .getOrDefault(emptyList())
        val storedTimeIds = runCatching {
            val alarmState = appSettingsStore.medicationAlarms.first()
            alarmState.triggers.keys + alarmState.legacyIds
        }.onFailure { warnings += "未能读取已登记的服药提醒" }.getOrDefault(emptySet())
        val knownMedicationTimeIds = (databaseTimeIds + storedTimeIds).toSet()

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
        onDatabaseCommitted()

        runCatching {
            if (knownMedicationTimeIds.isNotEmpty()) {
                val cancel = cancelRemovedMedicationNotifications
                if (cancel != null) cancel(knownMedicationTimeIds)
                else MedicationReminders.cancelNotifications(context, knownMedicationTimeIds)
            }
            cancelMeasurementNotifications()
        }.onFailure {
            warnings += "已发出的提醒通知清理失败"
        }

        var settingsCleared = false
        var remindersRescheduled = true
        var widgetRefreshed = true

        // 数据库已清理后，后续 DataStore/提醒/小部件失败都要如实返回，
        // 不能把结果伪装成“数据没有删除”。
        try {
            // 在清空 DataStore 前先用持久化的旧时间点 id 取消药物闹钟，
            // 否则清空设置会丢失取消旧 PendingIntent 所需的 id。
            (medicationAlarmResync ?: medicationResync)?.invoke()
        } catch (_: Throwable) {
            remindersRescheduled = false
            warnings += "服药提醒同步失败"
        }
        try {
            appSettingsStore.clearAll()
            settingsCleared = true
        } catch (_: Throwable) {
            warnings += "设置重置失败"
        }
        if (settingsCleared) {
            runCatching { cleanMedicationCalendar?.invoke() }.onFailure {
                warnings += "旧服药日程待清理"
            }
        }
        runCatching { clearLegacyBackupImportFiles(context.cacheDir) }.onFailure {
            warnings += "旧备份导入临时文件清理失败"
        }
        FileSessionDraftRepository(context).clearAll().onFailure {
            warnings += "测量草稿清理失败"
        }
        try {
            withContext(Dispatchers.IO) {
                reportCache.clearAll { file ->
                    context.revokeUriPermission(
                        ReportShare.reportUri(context, file), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        } catch (_: Throwable) {
            warnings += "医生报告缓存清理失败"
        }
        try {
            rescheduleReminders()
        } catch (_: Throwable) {
            remindersRescheduled = false
            warnings += "血压提醒同步失败"
        }
        try {
            onDataChanged?.invoke()
        } catch (_: Throwable) {
            widgetRefreshed = false
            warnings += "桌面小部件刷新失败"
        }
        return Result.success(
            ClearAllDataResult(
                databaseCleared = true,
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

            writeBackupPayload(uri, payload, passphrase)
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

    override suspend fun prepareBackupExport(): Result<BackupExportPlan> = runCatching {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                BackupExportService(
                    sessionDao = measurementSessionDao,
                    measurementDao = measurementDao,
                    userProfileDao = userProfileDao,
                    appSettingsStore = appSettingsStore,
                    medicationDao = database.medicationDao()
                ).buildExportPlan("家庭血压记录", currentAppVersion())
            }
        }
    }

    override suspend fun exportBackupVolumeToUri(
        plan: BackupExportPlan,
        volumeIndex: Int,
        uri: Uri,
        passphrase: CharArray?
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            writeBackupPayload(uri, plan.volumes[volumeIndex], passphrase)
            if (plan.markVolumeSaved(volumeIndex)) {
                appSettingsStore.setLastSuccessfulExportAt(System.currentTimeMillis())
            }
        }
    }

    private fun writeBackupPayload(uri: Uri, payload: BackupExportPayload, passphrase: CharArray?) {
        require(payload.measurements.size <= BackupImportLimits.MAX_RECORDS)
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

        BackupFileReader().validateExportArchive(xlsxBytes)
        val exportBytes = if (passphrase != null) {
            BackupCrypto.encrypt(xlsxBytes, passphrase)
        } else {
            xlsxBytes
        }
        require(exportBytes.size <= BackupImportLimits.MAX_FILE_BYTES) {
            "加密后的备份卷超过 10 MB，无法保存为可恢复的备份。"
        }
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("无法写入所选文件，请重新选择保存位置")

        outputStream.use { stream ->
            stream.write(exportBytes)
            stream.flush()
        }
    }

    override suspend fun refreshMeasurementReminders() {
        reminderSettingsMutex.withLock { rescheduleReminders() }
    }

    override suspend fun refreshReminders() {
        refreshMeasurementReminders()
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
                restoreReminderSettings = true,
                restoreMedications = true
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
        val manager = deletionUndoManager
        if (manager == null) {
            commitBackupImportLocked(preview, options) {}
        } else {
            manager.importBackup(
                importedSessionIds = if (options.importMeasurements) preview.importedSessionIds else emptySet(),
                restoredMedications = options.restoreMedications
            ) { onDatabaseCommitted ->
                commitBackupImportLocked(preview, options, onDatabaseCommitted)
            }
        }
    }

    private suspend fun commitBackupImportLocked(
        preview: BackupImportPreview,
        options: BackupImportOptions,
        onDatabaseCommitted: () -> Unit
    ): String = reminderSettingsMutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val result = BackupImportService(
                database = database,
                appSettingsStore = appSettingsStore
            ).commitImport(preview, options, onDatabaseCommitted)
            val followUpWarnings = mutableListOf<String>()
            if (options.restoreReminderSettings) {
                runCatching { rescheduleReminders() }
                    .onFailure { followUpWarnings += "血压提醒待重试" }
            }
            if (options.restoreMedications) {
                runCatching { medicationResync?.invoke() }
                    .onFailure { followUpWarnings += "服药提醒与日历待重试" }
            } else if (options.restoreReminderSettings && preview.hasMedicationReminderSetting) {
                runCatching { medicationAlarmResync?.invoke() }
                    .onFailure { followUpWarnings += "服药提醒待重试" }
            }
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
        applyMeasurementReminders(appSettingsStore.settingsFlow.first())
    }

    @Suppress("DEPRECATION")
    private fun currentAppVersion(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
