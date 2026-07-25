package com.example.bloodpressurerecord.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.bloodpressurerecord.data.datastore.AppSettings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderType(
    val action: String,
    val requestCode: Int,
    val notificationId: Int,
    val title: String
) {
    MORNING("com.example.bloodpressurerecord.REMINDER_MORNING", 7101, 8101, "晨间血压提醒"),
    EVENING("com.example.bloodpressurerecord.REMINDER_EVENING", 7102, 8102, "晚间血压提醒");

    companion object {
        fun fromAction(action: String?): ReminderType? = entries.firstOrNull { it.action == action }
    }
}

class ReminderScheduler(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun apply(settings: AppSettings) {
        ReminderNotifications.ensureChannel(context)
        scheduleOrCancel(
            type = ReminderType.MORNING,
            enabled = settings.morningReminderEnabled,
            timeText = settings.morningReminderTime
        )
        scheduleOrCancel(
            type = ReminderType.EVENING,
            enabled = settings.eveningReminderEnabled,
            timeText = settings.eveningReminderTime
        )
    }

    private fun scheduleOrCancel(type: ReminderType, enabled: Boolean, timeText: String) {
        val pendingIntent = pendingIntent(type)
        if (!enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val triggerAt = ReminderTimeCalculator.nextTriggerMillis(timeText, now(), zoneId) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    private fun pendingIntent(type: ReminderType): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(type.action)
        return PendingIntent.getBroadcast(
            context,
            type.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object ReminderTimeCalculator {
    fun nextTriggerMillis(
        timeText: String,
        current: LocalDateTime,
        zoneId: ZoneId
    ): Long? {
        val time = runCatching { LocalTime.parse(timeText) }.getOrNull() ?: return null
        var trigger = current.toLocalDate().atTime(time)
        if (!trigger.isAfter(current)) trigger = trigger.plusDays(1)
        return trigger.atZone(zoneId).toInstant().toEpochMilli()
    }
}

object ReminderNotifications {
    const val CHANNEL_ID = "blood_pressure_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "血压测量提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "晨间和晚间血压测量提醒"
            }
        )
    }
}
