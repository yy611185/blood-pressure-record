package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.datastore.AppSettings
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportPreview
import android.net.Uri
import kotlinx.coroutines.flow.Flow

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
    fun toUserMessage(): String {
        if (!databaseCleared) return "清空失败：本地数据未完整删除，请重试。"
        if (warnings.isNotEmpty()) return "健康记录已删除，但部分设置未能重置。"
        return "全部数据已清空。"
    }
}

interface SettingsRepository {
    fun observeSettings(): Flow<SettingsBundle>

    suspend fun setLargeTextEnabled(enabled: Boolean)

    suspend fun setHighRiskAlertEnabled(enabled: Boolean)

    suspend fun setShowTrendChart(enabled: Boolean)

    suspend fun setDiscardFirstReading(enabled: Boolean)

    suspend fun setMorningReminderEnabled(enabled: Boolean)

    suspend fun setMorningReminderTime(value: String)

    suspend fun setEveningReminderEnabled(enabled: Boolean)

    suspend fun setEveningReminderTime(value: String)

    suspend fun setMedicationReminderEnabled(enabled: Boolean)

    suspend fun setMedicationCalendarSyncEnabled(enabled: Boolean)

    suspend fun setSaveScanPhotosEnabled(enabled: Boolean)

    suspend fun clearScanPhotos(olderThanDays: Int?): Result<String>

    suspend fun refreshReminders()

    suspend fun saveUserProfile(profile: UserProfile)

    suspend fun clearAllData(): Result<ClearAllDataResult>

    suspend fun exportBackupXlsxToUri(uri: Uri, fileNameHint: String): Result<String>

    suspend fun importBackupXlsxFromUri(uri: Uri): Result<String>

    suspend fun previewBackupXlsxFromUri(uri: Uri): Result<BackupImportPreview> =
        Result.failure(UnsupportedOperationException("当前设置仓库不支持备份预览"))

    suspend fun commitBackupImport(
        preview: BackupImportPreview,
        options: BackupImportOptions
    ): Result<String> = Result.failure(
        UnsupportedOperationException("当前设置仓库不支持备份导入提交")
    )
}
