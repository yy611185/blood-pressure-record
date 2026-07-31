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
    /** 计算平均值时弃用第一组读数（家庭自测常用做法）。 */
    val discardFirstReading: Boolean = false,
    val lastSuccessfulExportAt: Long? = null,
    /** 服药提醒总开关。默认开启：添加药品后立即生效，符合用户预期。 */
    val medicationReminderEnabled: Boolean = true,
    /** 服药提醒同步写入系统日历（需要日历读写权限）。 */
    val medicationCalendarSyncEnabled: Boolean = false
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
            discardFirstReading = prefs[PreferenceKeys.DISCARD_FIRST_READING] ?: false,
            lastSuccessfulExportAt = prefs[PreferenceKeys.LAST_SUCCESSFUL_EXPORT_AT],
            medicationReminderEnabled = prefs[PreferenceKeys.MEDICATION_REMINDER_ENABLED] ?: true,
            medicationCalendarSyncEnabled =
                prefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED] ?: false
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

    suspend fun setDiscardFirstReading(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.DISCARD_FIRST_READING] = enabled
        }
    }

    suspend fun setLastSuccessfulExportAt(value: Long) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.LAST_SUCCESSFUL_EXPORT_AT] = value
        }
    }

    suspend fun setMedicationReminderEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.MEDICATION_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setMedicationCalendarSyncEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED] = enabled
        }
    }

    /** 清空全部设置（含上次导出时间），恢复为默认值。 */
    suspend fun clearAll() {
        context.appDataStore.edit { prefs -> prefs.clear() }
    }
}
