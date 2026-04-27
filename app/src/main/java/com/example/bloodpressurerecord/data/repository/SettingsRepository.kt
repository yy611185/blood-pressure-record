package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.datastore.AppSettings
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

interface SettingsRepository {
    fun observeSettings(): Flow<SettingsBundle>

    suspend fun setLargeTextEnabled(enabled: Boolean)

    suspend fun setHighRiskAlertEnabled(enabled: Boolean)

    suspend fun setShowTrendChart(enabled: Boolean)

    suspend fun setMorningReminderEnabled(enabled: Boolean)

    suspend fun setMorningReminderTime(value: String)

    suspend fun setEveningReminderEnabled(enabled: Boolean)

    suspend fun setEveningReminderTime(value: String)

    suspend fun saveUserProfile(profile: UserProfile)

    suspend fun clearAllData(): Result<Unit>

    suspend fun exportBackupXlsxToUri(uri: Uri, fileNameHint: String): Result<String>
}
