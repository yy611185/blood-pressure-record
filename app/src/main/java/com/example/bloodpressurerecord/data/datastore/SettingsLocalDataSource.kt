package com.example.bloodpressurerecord.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SettingsPreferences(
    val largeTextEnabled: Boolean = true
)

class SettingsLocalDataSource(
    private val context: Context
) {
    val settingsFlow: Flow<SettingsPreferences> = context.appDataStore.data.map { prefs ->
        SettingsPreferences(
            largeTextEnabled = prefs[PreferenceKeys.LARGE_TEXT] ?: true
        )
    }

    suspend fun setLargeTextEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.LARGE_TEXT] = enabled
        }
    }
}
