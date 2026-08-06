package com.example.bloodpressurerecord.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.appDataStore by preferencesDataStore(name = "app_preferences")

object PreferenceKeys {
    val LARGE_TEXT = booleanPreferencesKey("large_text")
    val ENABLE_HIGH_RISK_ALERT = booleanPreferencesKey("enable_high_risk_alert")
    val SHOW_TREND_CHART = booleanPreferencesKey("show_trend_chart")
    val MORNING_REMINDER_ENABLED = booleanPreferencesKey("morning_reminder_enabled")
    val MORNING_REMINDER_TIME = stringPreferencesKey("morning_reminder_time")
    val EVENING_REMINDER_ENABLED = booleanPreferencesKey("evening_reminder_enabled")
    val EVENING_REMINDER_TIME = stringPreferencesKey("evening_reminder_time")
    val DISCARD_FIRST_READING = booleanPreferencesKey("discard_first_reading")
    val LAST_SUCCESSFUL_EXPORT_AT = longPreferencesKey("last_successful_export_at")
    val MEDICATION_REMINDER_ENABLED = booleanPreferencesKey("medication_reminder_enabled")
    val MEDICATION_CALENDAR_SYNC_ENABLED = booleanPreferencesKey("medication_calendar_sync_enabled")
    val SAVE_SCAN_PHOTOS = booleanPreferencesKey("save_scan_photos")
    val SCHEDULED_MEDICATION_TIME_IDS = stringPreferencesKey("scheduled_medication_time_ids")
}
