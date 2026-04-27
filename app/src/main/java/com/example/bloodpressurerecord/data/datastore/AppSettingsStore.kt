package com.example.bloodpressurerecord.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val largeTextEnabled: Boolean = true,
    val highRiskAlertEnabled: Boolean = true,
    val showTrendChart: Boolean = true,
    val morningReminderEnabled: Boolean = false,
    val morningReminderTime: String = "07:30",
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderTime: String = "21:00",
    val defaultScene: String = "居家安静"
)

class AppSettingsStore(
    private val context: Context
) {
    val settingsFlow: Flow<AppSettings> = context.appDataStore.data.map { prefs ->
        AppSettings(
            largeTextEnabled = prefs[PreferenceKeys.LARGE_TEXT] ?: true,
            highRiskAlertEnabled = prefs[PreferenceKeys.ENABLE_HIGH_RISK_ALERT] ?: true,
            showTrendChart = prefs[PreferenceKeys.SHOW_TREND_CHART] ?: true,
            morningReminderEnabled = prefs[PreferenceKeys.MORNING_REMINDER_ENABLED] ?: false,
            morningReminderTime = prefs[PreferenceKeys.MORNING_REMINDER_TIME] ?: "07:30",
            eveningReminderEnabled = prefs[PreferenceKeys.EVENING_REMINDER_ENABLED] ?: false,
            eveningReminderTime = prefs[PreferenceKeys.EVENING_REMINDER_TIME] ?: "21:00",
            defaultScene = prefs[PreferenceKeys.DEFAULT_SCENE] ?: "居家安静"
        )
    }

    suspend fun setLargeTextEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.LARGE_TEXT] = enabled
        }
    }

    suspend fun setHighRiskAlertEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.ENABLE_HIGH_RISK_ALERT] = enabled
        }
    }

    suspend fun setShowTrendChart(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.SHOW_TREND_CHART] = enabled
        }
    }

    suspend fun setMorningReminderEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.MORNING_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setMorningReminderTime(value: String) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.MORNING_REMINDER_TIME] = value
        }
    }

    suspend fun setEveningReminderEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.EVENING_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setEveningReminderTime(value: String) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.EVENING_REMINDER_TIME] = value
        }
    }

    suspend fun setDefaultScene(scene: String) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.DEFAULT_SCENE] = scene
        }
    }
}
