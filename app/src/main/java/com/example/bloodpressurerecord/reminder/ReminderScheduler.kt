package com.example.bloodpressurerecord.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bloodpressurerecord.BuildConfig
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
    MORNING("${BuildConfig.APPLICATION_ID}.REMINDER_MORNING", 7101, 8101, "晨间血压提醒"),
    EVENING("${BuildConfig.APPLICATION_ID}.REMINDER_EVENING", 7102, 8102, "晚间血压提醒");

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

    fun cancelAll() {
        ReminderType.entries.forEach { type ->
            alarmManager.cancel(pendingIntent(type))
        }
    }

    private fun scheduleOrCancel(type: ReminderType, enabled: Boolean, timeText: String) {
        val pendingIntent = pendingIntent(type)
        if (!enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val triggerAt = ReminderTimeCalculator.nextTriggerMillis(timeText, now(), zoneId)
            ?: run {
                // 时间被改坏时也要清理旧闹钟，不能让已失效的配置继续触发。
                alarmManager.cancel(pendingIntent)
                return
            }
        // 血压提醒对时间敏感：优先使用精确闹钟；用户在系统里撤销精确闹钟权限时
        // 回退到非精确闹钟，保证提醒仍然会到达。
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
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

enum class ReminderAuthorizationStatus {
    GRANTED,
    RUNTIME_PERMISSION_REQUIRED,
    SYSTEM_DISABLED
}

object ReminderAuthorization {
    fun status(context: Context): ReminderAuthorizationStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ReminderAuthorizationStatus.RUNTIME_PERMISSION_REQUIRED
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return ReminderAuthorizationStatus.SYSTEM_DISABLED
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return ReminderAuthorizationStatus.SYSTEM_DISABLED
        }
        return ReminderAuthorizationStatus.GRANTED
    }

    fun settingsIntent(context: Context): Intent {
        return Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
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
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "健康提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "血压测量和服药提醒"
            }
        )
    }
}
