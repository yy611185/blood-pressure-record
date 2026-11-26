package com.example.bloodpressurerecord.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val largeTextEnabled: Boolean = true,
    val highRiskAlertEnabled: Boolean = true,
    val showTrendChart: Boolean = true,
    /** system / light / dark，默认跟随系统。 */
    val appearanceMode: String = "system",
    val showBuddy: Boolean = true,
    val morningReminderEnabled: Boolean = false,
    val morningReminderTime: String = "07:30",
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderTime: String = "21:00",
    /** 计算平均值时弃用第一组读数（家庭自测常用做法）。 */
    val discardFirstReading: Boolean = false,
    val lastSuccessfulExportAt: Long? = null,
    /** 服药提醒总开关。默认开启：添加药品后立即生效，符合用户预期。 */
    val medicationReminderEnabled: Boolean = true,
    val medicationCalendarId: Long? = null,
    val medicationCalendarIncludeDetails: Boolean = false,
    val medicationCalendarCleanupPending: Boolean = false
) {
    val medicationCalendarSyncEnabled: Boolean get() = medicationCalendarId != null
}

data class MedicationAlarmState(
    val triggers: Map<Long, Long>,
    val legacyIds: Set<Long>
)

class AppSettingsStore private constructor(
    private val preferences: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.appDataStore)

    companion object {
        internal fun using(preferences: DataStore<Preferences>) = AppSettingsStore(preferences)
    }
    /** 本机趋势显示偏好，不加入健康数据与备份设置格式。null 表示尚未主动选择。 */
    val trendAggregationPreference: Flow<TrendAggregation?> = preferences.data.map { prefs ->
        when (prefs[PreferenceKeys.TREND_AGGREGATION]) {
            TrendAggregation.RAW.name -> TrendAggregation.RAW
            TrendAggregation.DAILY.name -> TrendAggregation.DAILY
            else -> null
        }
    }

    suspend fun setTrendAggregationPreference(aggregation: TrendAggregation) {
        require(aggregation == TrendAggregation.RAW || aggregation == TrendAggregation.DAILY)
        preferences.edit { it[PreferenceKeys.TREND_AGGREGATION] = aggregation.name }
    }

    val settingsFlow: Flow<AppSettings> = preferences.data.map { prefs ->
        AppSettings(
            largeTextEnabled = prefs[PreferenceKeys.LARGE_TEXT] ?: true,
            highRiskAlertEnabled = prefs[PreferenceKeys.ENABLE_HIGH_RISK_ALERT] ?: true,
            showTrendChart = prefs[PreferenceKeys.SHOW_TREND_CHART] ?: true,
            appearanceMode = prefs[PreferenceKeys.APPEARANCE_MODE]
                ?.takeIf { it in setOf("system", "light", "dark") } ?: "system",
            showBuddy = prefs[PreferenceKeys.SHOW_BUDDY] ?: true,
            morningReminderEnabled = prefs[PreferenceKeys.MORNING_REMINDER_ENABLED] ?: false,
            morningReminderTime = prefs[PreferenceKeys.MORNING_REMINDER_TIME] ?: "07:30",
            eveningReminderEnabled = prefs[PreferenceKeys.EVENING_REMINDER_ENABLED] ?: false,
            eveningReminderTime = prefs[PreferenceKeys.EVENING_REMINDER_TIME] ?: "21:00",
            discardFirstReading = prefs[PreferenceKeys.DISCARD_FIRST_READING] ?: false,
            lastSuccessfulExportAt = prefs[PreferenceKeys.LAST_SUCCESSFUL_EXPORT_AT],
            medicationReminderEnabled = prefs[PreferenceKeys.MEDICATION_REMINDER_ENABLED] ?: true,
            medicationCalendarId = prefs[PreferenceKeys.MEDICATION_CALENDAR_ID],
            medicationCalendarIncludeDetails =
                prefs[PreferenceKeys.MEDICATION_CALENDAR_INCLUDE_DETAILS] ?: false,
            medicationCalendarCleanupPending = prefs[PreferenceKeys.MEDICATION_CALENDAR_ID] == null &&
                eventsMayExist(prefs)
        )
    }

    suspend fun setLargeTextEnabled(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.LARGE_TEXT] = enabled
        }
    }

    suspend fun setHighRiskAlertEnabled(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.ENABLE_HIGH_RISK_ALERT] = enabled
        }
    }

    suspend fun setShowTrendChart(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.SHOW_TREND_CHART] = enabled
        }
    }

    suspend fun setAppearanceMode(mode: String) {
        require(mode in setOf("system", "light", "dark"))
        preferences.edit { it[PreferenceKeys.APPEARANCE_MODE] = mode }
    }

    suspend fun setShowBuddy(enabled: Boolean) {
        preferences.edit { it[PreferenceKeys.SHOW_BUDDY] = enabled }
    }

    suspend fun setMorningReminderEnabled(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.MORNING_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setMorningReminderTime(value: String) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.MORNING_REMINDER_TIME] = value
        }
    }

    /** 原子写入晨间提醒的时间与启用状态，避免分两次 edit 留下中间状态。 */
    suspend fun updateMorningReminder(enabled: Boolean? = null, time: String? = null) {
        preferences.edit { prefs ->
            enabled?.let { prefs[PreferenceKeys.MORNING_REMINDER_ENABLED] = it }
            time?.let { prefs[PreferenceKeys.MORNING_REMINDER_TIME] = it }
        }
    }

    suspend fun setEveningReminderEnabled(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.EVENING_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setEveningReminderTime(value: String) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.EVENING_REMINDER_TIME] = value
        }
    }

    /** 原子写入晚间提醒的时间与启用状态，避免分两次 edit 留下中间状态。 */
    suspend fun updateEveningReminder(enabled: Boolean? = null, time: String? = null) {
        preferences.edit { prefs ->
            enabled?.let { prefs[PreferenceKeys.EVENING_REMINDER_ENABLED] = it }
            time?.let { prefs[PreferenceKeys.EVENING_REMINDER_TIME] = it }
        }
    }

    suspend fun setDiscardFirstReading(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.DISCARD_FIRST_READING] = enabled
        }
    }

    suspend fun setLastSuccessfulExportAt(value: Long) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.LAST_SUCCESSFUL_EXPORT_AT] = value
        }
    }

    suspend fun setMedicationReminderEnabled(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.MEDICATION_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setMedicationCalendar(calendarId: Long?, includeDetails: Boolean = false) {
        preferences.edit { prefs ->
            val hadLegacySyncEnabled = prefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED] == true
            prefs -= PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED
            if (hadLegacySyncEnabled &&
                prefs[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST] == null
            ) {
                prefs[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST] = true
            }
            if (calendarId == null) {
                prefs -= PreferenceKeys.MEDICATION_CALENDAR_ID
                prefs -= PreferenceKeys.MEDICATION_CALENDAR_INCLUDE_DETAILS
            } else {
                prefs[PreferenceKeys.MEDICATION_CALENDAR_ID] = calendarId
                prefs[PreferenceKeys.MEDICATION_CALENDAR_INCLUDE_DETAILS] = includeDetails
            }
        }
    }

    val medicationAlarms: Flow<MedicationAlarmState> = preferences.data.map { prefs ->
        val triggers = buildMap {
            for (entry in prefs[PreferenceKeys.MEDICATION_ALARM_TRIGGERS].orEmpty()) {
                val id = entry.substringBefore('@').toLongOrNull() ?: continue
                val trigger = entry.substringAfter('@', "").toLongOrNull() ?: continue
                put(id, trigger)
            }
        }
        val legacyIds = prefs[PreferenceKeys.SCHEDULED_MEDICATION_TIME_IDS]
            .orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        MedicationAlarmState(triggers, legacyIds)
    }

    /** 创建前记录，清理成功后清除；兼容旧版本已经开启日历同步的用户。 */
    val calendarEventsMayExist: Flow<Boolean> = preferences.data.map(::eventsMayExist)

    private fun eventsMayExist(prefs: Preferences): Boolean =
        prefs[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST]
            ?: (prefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED] ?: false)

    suspend fun setCalendarEventsMayExist(value: Boolean) {
        preferences.edit { it[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST] = value }
    }

    suspend fun setMedicationAlarms(state: MedicationAlarmState) {
        preferences.edit { prefs ->
            prefs[PreferenceKeys.MEDICATION_ALARM_TRIGGERS] =
                state.triggers.mapTo(mutableSetOf()) { (id, trigger) -> "$id@$trigger" }
            if (state.legacyIds.isEmpty()) prefs -= PreferenceKeys.SCHEDULED_MEDICATION_TIME_IDS
            else prefs[PreferenceKeys.SCHEDULED_MEDICATION_TIME_IDS] = state.legacyIds.joinToString(",")
        }
    }

    /**
     * 从备份一次性恢复用户选择的显示/提醒设置，避免逐项 edit 造成半套设置落盘。
     * 仅写入格式和值都经过导入校验的字段。
     */
    suspend fun restoreFromBackup(
        values: Map<String, String>,
        restoreDisplaySettings: Boolean,
        restoreReminderSettings: Boolean
    ) {
        preferences.edit { prefs ->
            if (restoreDisplaySettings) {
                values["large_text_enabled"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.LARGE_TEXT] = it
                }
                values["high_risk_alert_enabled"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.ENABLE_HIGH_RISK_ALERT] = it
                }
                values["discard_first_reading"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.DISCARD_FIRST_READING] = it
                }
                (values["show_trend_chart"] ?: values["display_show_target_line"])
                    ?.toBooleanStrictOrNull()?.let {
                        prefs[PreferenceKeys.SHOW_TREND_CHART] = it
                    }
                values["appearance_mode"]
                    ?.takeIf { it in setOf("system", "light", "dark") }
                    ?.let { prefs[PreferenceKeys.APPEARANCE_MODE] = it }
                values["show_buddy"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.SHOW_BUDDY] = it
                }
            }
            if (restoreReminderSettings) {
                values["morning_reminder_time"]?.takeIf(::isValidTime)?.let {
                    prefs[PreferenceKeys.MORNING_REMINDER_TIME] = it
                }
                values["evening_reminder_time"]?.takeIf(::isValidTime)?.let {
                    prefs[PreferenceKeys.EVENING_REMINDER_TIME] = it
                }
                values["morning_reminder_enabled"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.MORNING_REMINDER_ENABLED] = it
                }
                values["evening_reminder_enabled"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.EVENING_REMINDER_ENABLED] = it
                }
                values["medication_reminder_enabled"]?.toBooleanStrictOrNull()?.let {
                    prefs[PreferenceKeys.MEDICATION_REMINDER_ENABLED] = it
                }
            }
        }
    }

    private fun isValidTime(value: String): Boolean {
        return Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(value)
    }

    /** 清空全部设置（含上次导出时间），恢复为默认值。 */
    suspend fun clearAll() {
        preferences.edit { prefs ->
            val pendingCalendarCleanup = eventsMayExist(prefs)
            prefs.clear()
            // 权限撤销时清空数据仍需保留后续清理外部日程的能力。
            if (pendingCalendarCleanup) prefs[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST] = true
        }
    }
}
