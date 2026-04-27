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
import com.example.bloodpressurerecord.data.repository.transfer.ExportRepository
import com.example.bloodpressurerecord.data.repository.transfer.ImportRepository
import com.example.bloodpressurerecord.data.repository.transfer.ImportResult
import com.example.bloodpressurerecord.data.repository.transfer.TransferFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class SettingsRepositoryStable(
    private val context: Context,
    private val appSettingsStore: AppSettingsStore,
    private val userProfileDao: UserProfileDao,
    private val measurementSessionDao: MeasurementSessionDao,
    private val measurementDao: BloodPressureMeasurementDao,
    private val fileStore: TransferFileStore,
    private val exportRepository: ExportRepository,
    private val importRepository: ImportRepository
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
    }

    override suspend fun setMorningReminderTime(value: String) {
        appSettingsStore.setMorningReminderTime(value)
    }

    override suspend fun setEveningReminderEnabled(enabled: Boolean) {
        appSettingsStore.setEveningReminderEnabled(enabled)
    }

    override suspend fun setEveningReminderTime(value: String) {
        appSettingsStore.setEveningReminderTime(value)
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
        measurementSessionDao.deleteAllReadings()
        measurementSessionDao.deleteAllSessions()
        measurementDao.deleteAll()
        userProfileDao.deleteAll()
    }

    override suspend fun exportCsv(): Result<String> = runCatching {
        val result = exportRepository.exportCsv().getOrThrow()
        "${result.format} 导出成功：${result.filePath}\n共导出 ${result.rowCount} 条会话"
    }

    override suspend fun exportXlsx(): Result<String> = runCatching {
        val result = exportRepository.exportXlsx().getOrThrow()
        "${result.format} 导出成功：${result.filePath}\n共导出 ${result.rowCount} 条会话"
    }

    override suspend fun exportBackupXlsxToUri(uri: Uri, fileNameHint: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val payload = BackupExportService(
                sessionDao = measurementSessionDao,
                userProfileDao = userProfileDao,
                appSettingsStore = appSettingsStore
            ).buildPayload(
                appName = "家庭血压记录",
                appVersion = currentAppVersion()
            )
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入所选文件，请重新选择保存位置")

            outputStream.use { stream ->
                BackupFileWriter().writeXlsx(payload, stream)
            }
            "Excel 备份导出成功：$fileNameHint\n共导出 ${payload.measurements.size} 条测量记录\n文件仅保存在本地，不会上传。"
        }
    }

    override suspend fun importCsv(): Result<String> = runCatching {
        formatImportResult(importRepository.importCsv().getOrThrow())
    }

    override suspend fun importXlsx(): Result<String> = runCatching {
        formatImportResult(importRepository.importXlsx().getOrThrow())
    }

    override suspend fun stageImportFromUri(uri: Uri, fileNameHint: String?): Result<String> = runCatching {
        val file = fileStore.stageImportFromUri(uri, fileNameHint).getOrThrow()
        "已读取所选文件：${file.name}"
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

    private fun formatImportResult(result: ImportResult): String {
        val summary = "${result.format} 导入完成：成功 ${result.successRows} 行，失败 ${result.failedRows} 行，共 ${result.totalRows} 行"
        if (result.errors.isEmpty()) return "$summary\n来源：${result.filePath}"
        val details = result.errors.take(5).joinToString("\n") { item ->
            "第 ${item.lineNumber} 行：${item.reason}"
        }
        val remain = result.errors.size - 5
        val tail = if (remain > 0) "\n其余 $remain 行错误请修复后再次导入" else ""
        return "$summary\n来源：${result.filePath}\n$details$tail"
    }

    @Suppress("DEPRECATION")
    private fun currentAppVersion(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
