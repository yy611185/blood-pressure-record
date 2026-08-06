package com.example.bloodpressurerecord.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.data.calendar.MedicationCalendarSync
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

object MedicationReminders {
    val ACTION = "${BuildConfig.APPLICATION_ID}.REMINDER_MEDICATION"
    const val EXTRA_TIME_ID = "medication_time_id"

    /** 与晨晚提醒（71xx/81xx）错开的独立号段。 */
    const val REQUEST_CODE_BASE = 72000
    const val NOTIFICATION_ID_BASE = 82000
}

/** 为每个服药时间点排一个次日自续的精确闹钟（与晨晚提醒同一套回退策略）。 */
class MedicationReminderScheduler(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun apply(
        enabled: Boolean,
        slots: List<MedicationSlot>,
        allTimeIds: Collection<Long> = slots.map { it.timeId }
    ) {
        ReminderNotifications.ensureChannel(context)
        val slotsById = slots.associateBy { it.timeId }
        (allTimeIds + slotsById.keys).distinct().forEach { timeId ->
            val pendingIntent = pendingIntent(timeId)
            val slot = slotsById[timeId]
            if (!enabled || slot == null) {
                alarmManager.cancel(pendingIntent)
                return@forEach
            }
            val triggerAt = ReminderTimeCalculator
                .nextTriggerMillis(slot.timeText, now(), zoneId)
                ?: run {
                    alarmManager.cancel(pendingIntent)
                    return@forEach
                }
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            }
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
    }

    private fun pendingIntent(timeId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(MedicationReminders.ACTION)
            .putExtra(MedicationReminders.EXTRA_TIME_ID, timeId)
        return PendingIntent.getBroadcast(
            context,
            (MedicationReminders.REQUEST_CODE_BASE + timeId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * 服药提醒的统一入口：
 * - [resyncAlarms]：按当前药品与设置重排全部精确闹钟（启动/开机/闹钟触发后自续）。
 * - [resyncAll]：闹钟 + 系统日历日程全量重建（药品或开关变更时调用）。
 *
 * 已删除时间点的旧闹钟会在触发时因查不到记录而自然失效，无需枚举取消。
 */
class MedicationReminderCoordinator(
    private val context: Context,
    private val medicationRepository: MedicationRepository
) {
    private val calendarSync = MedicationCalendarSync(context)

    suspend fun resyncAlarms() {
        val settings = AppSettingsStore(context).settingsFlow.first()
        val slots = medicationRepository.getSlotsForDay(LocalDate.now())
        MedicationReminderScheduler(context).apply(
            enabled = settings.medicationReminderEnabled,
            slots = slots,
            allTimeIds = medicationRepository.getAllTimeIds()
        )
    }

    suspend fun resyncAll() {
        val settings = AppSettingsStore(context).settingsFlow.first()
        val slots = medicationRepository.getSlotsForDay(LocalDate.now())
        MedicationReminderScheduler(context).apply(
            enabled = settings.medicationReminderEnabled,
            slots = slots,
            allTimeIds = medicationRepository.getAllTimeIds()
        )
        calendarSync.rebuild(slots, settings.medicationCalendarSyncEnabled)
    }
}
