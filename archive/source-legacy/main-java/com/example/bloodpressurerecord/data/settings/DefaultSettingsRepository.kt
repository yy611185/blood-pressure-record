package com.example.bloodpressurerecord.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class DefaultSettingsRepository(
    private val context: Context
) : SettingsRepository {

    override val settingsFlow: Flow<AppSettings> =
        context.dataStore.data.map { preferences ->
            AppSettings(
                isLargeTextEnabled = preferences[LARGE_TEXT_KEY] ?: true,
                useSystemFilePicker = preferences[USE_SYSTEM_PICKER_KEY] ?: false
            )
        }

    override suspend fun setLargeTextEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LARGE_TEXT_KEY] = enabled }
    }

    override suspend fun setUseSystemFilePicker(enabled: Boolean) {
        context.dataStore.edit { it[USE_SYSTEM_PICKER_KEY] = enabled }
    }

    private companion object {
        val LARGE_TEXT_KEY = booleanPreferencesKey("large_text")
        val USE_SYSTEM_PICKER_KEY = booleanPreferencesKey("use_system_picker")
    }
}
