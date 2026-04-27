package com.example.bloodpressurerecord.data.settings

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val isLargeTextEnabled: Boolean = true,
    val useSystemFilePicker: Boolean = false
)

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>

    suspend fun setLargeTextEnabled(enabled: Boolean)

    suspend fun setUseSystemFilePicker(enabled: Boolean)
}
