package com.example.bloodpressurerecord.data.repository

import android.net.Uri
import com.example.bloodpressurerecord.data.calendar.MedicationCalendarTarget
import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPlan
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportPreview
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class UserProfile(
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val targetSystolic: Int? = null,
    val targetDiastolic: Int? = null
)

data class SettingsBundle(
    val appSettings: AppSettings = AppSettings(),
    val userProfile: UserProfile = UserProfile()
)

data class ClearAllDataResult(
    val databaseCleared: Boolean,
    val settingsCleared: Boolean,
    val remindersRescheduled: Boolean,
    val widgetRefreshed: Boolean,
    val warnings: List<String> = emptyList()
) {
    val hasIssues: Boolean
        get() = !databaseCleared || !settingsCleared || !remindersRescheduled ||
            !widgetRefreshed || warnings.isNotEmpty()

    fun toUserMessage(): String {
        if (!databaseCleared) return "清空失败：本地数据未完整删除，请重试。"
        if (warnings.isNotEmpty()) return "健康记录已删除，但部分清理未完成：${warnings.joinToString("、")}。"
        return "全部数据已清空。"
    }
}

interface SettingsRepository {
    fun observeSettings(): Flow<SettingsBundle>

    fun observeTrendAggregationPreference(): Flow<TrendAggregation?> = flowOf(null)

    suspend fun setTrendAggregationPreference(aggregation: TrendAggregation) = Unit

    suspend fun setLargeTextEnabled(enabled: Boolean)

    suspend fun setHighRiskAlertEnabled(enabled: Boolean)

    suspend fun setShowTrendChart(enabled: Boolean)

    suspend fun setAppearanceMode(mode: String)

    suspend fun setShowBuddy(enabled: Boolean)

    suspend fun setDiscardFirstReading(enabled: Boolean)

    suspend fun setMorningReminderEnabled(enabled: Boolean)

    suspend fun setMorningReminderTime(value: String)

    suspend fun setMorningReminderSchedule(time: String, enabled: Boolean = true) {
        setMorningReminderTime(time)
        setMorningReminderEnabled(enabled)
    }

    suspend fun setEveningReminderEnabled(enabled: Boolean)

    suspend fun setEveningReminderTime(value: String)

    suspend fun setEveningReminderSchedule(time: String, enabled: Boolean = true) {
        setEveningReminderTime(time)
        setEveningReminderEnabled(enabled)
    }

    suspend fun setMedicationReminderEnabled(enabled: Boolean)

    suspend fun getWritableMedicationCalendars(): List<MedicationCalendarTarget>
    suspend fun setMedicationCalendar(calendarId: Long?, includeDetails: Boolean)

    suspend fun refreshReminders()
    suspend fun refreshMeasurementReminders() = refreshReminders()

    suspend fun saveUserProfile(profile: UserProfile)

    suspend fun clearAllData(): Result<ClearAllDataResult>

    suspend fun exportBackupXlsxToUri(
        uri: Uri,
        fileNameHint: String,
        passphrase: CharArray? = null
    ): Result<String>

    suspend fun prepareBackupExport(): Result<BackupExportPlan> =
        Result.failure(UnsupportedOperationException("当前设置仓库不支持分卷备份"))

    suspend fun exportBackupVolumeToUri(
        plan: BackupExportPlan,
        volumeIndex: Int,
        uri: Uri,
        passphrase: CharArray? = null
    ): Result<Unit> = Result.failure(UnsupportedOperationException("当前设置仓库不支持分卷备份"))

    suspend fun importBackupXlsxFromUri(uri: Uri, passphrase: CharArray? = null): Result<String>

    suspend fun previewBackupXlsxFromUri(
        uri: Uri,
        passphrase: CharArray? = null
    ): Result<BackupImportPreview> =
        Result.failure(UnsupportedOperationException("当前设置仓库不支持备份预览"))

    suspend fun commitBackupImport(
        preview: BackupImportPreview,
        options: BackupImportOptions
    ): Result<String> = Result.failure(
        UnsupportedOperationException("当前设置仓库不支持备份导入提交")
    )
}
