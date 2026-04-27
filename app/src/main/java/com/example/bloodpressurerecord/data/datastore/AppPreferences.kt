package com.example.bloodpressurerecord.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val DEFAULT_SCENE = stringPreferencesKey("default_scene")
}
