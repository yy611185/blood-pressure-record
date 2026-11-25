package com.example.bloodpressurerecord.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.bloodpressurerecord.BuildConfig
import com.example.bloodpressurerecord.data.calendar.MedicationCalendarSync
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.datastore.MedicationAlarmState
import com.example.bloodpressurerecord.data.repository.MedicationRepository
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MedicationReminders {
    val ACTION = "${BuildConfig.APPLICATION_ID}.REMINDER_MEDICATION"
    val ACTION_MARK_TAKEN = "${BuildConfig.APPLICATION_ID}.ACTION_MEDICATION_MARK_TAKEN"
    const val EXTRA_MEDICATION_ID = "medication_id"
    const val EXTRA_TIME_ID = "medication_time_id"
    const val EXTRA_DATE_EPOCH_DAY = "medication_date_epoch_day"
    const val EXTRA_TRIGGER_AT = "medication_trigger_at"
    const val REQUEST_CODE_BASE = 72000
    const val MARK_TAKEN_REQUEST_CODE_BASE = 92000
    const val NOTIFICATION_ID_BASE = 82000

    fun notificationIdFor(timeId: Long): Int = (NOTIFICATION_ID_BASE + timeId).toInt()

    fun notificationTagFor(timeId: Long): String = "medication:$timeId"

    fun markTakenUri(timeId: Long, epochDay: Long): Uri =
        Uri.parse("medication://mark-taken/$timeId/$epochDay")

    fun cancelNotifications(context: Context, timeIds: Iterable<Long>) {
        val manager = NotificationManagerCompat.from(context)
        timeIds.forEach { timeId ->
            val id = notificationIdFor(timeId)
            manager.cancel(notificationTagFor(timeId), id)
            manager.cancel(id) // 更新前遗留的无 tag 通知
        }
    }

    fun cancelMeasurementNotifications(context: Context) {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            ReminderType.entries.forEach { type -> manager.cancel(type.notificationId) }
        }
    }
}

internal class MedicationAlarmDay(date: LocalDate, time: LocalTime, zone: ZoneId) {
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val today = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val tomorrow = date.plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun pending(previous: Long?, now: Long): Long = when {
        previous == today -> today
        previous == tomorrow -> tomorrow
        // 重启时可能已经跨日。旧计划只恢复今天的时段。
        previous != null && previous < start -> today
        today > now -> today
        else -> tomorrow
    }

    fun isDue(previous: Long?, delivered: Long?, legacyPending: Boolean, now: Long): Boolean =
        now >= today && if (delivered == null) legacyPending else previous == today && delivered == today
}

private class MedicationReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun ensure(timeId: Long, trigger: Long) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        cancel(find(timeId, trigger, exact = !exact))
        // 同一时段已在等待系统投递时，保留原来的非精确闹钟窗口。
        if (find(timeId, trigger, exact) != null) return
        val operation = requireNotNull(operation(timeId, trigger, exact, flags = 0))
        try {
            if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        } catch (error: Exception) {
            operation.cancel()
            throw error
        }
    }

    fun cancel(timeId: Long, trigger: Long) {
        cancel(find(timeId, trigger, exact = true))
        cancel(find(timeId, trigger, exact = false))
    }

    fun cancelLegacy(timeId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(MedicationReminders.ACTION)
        cancel(PendingIntent.getBroadcast(context, (MedicationReminders.REQUEST_CODE_BASE + timeId).toInt(),
            intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun cancel(token: PendingIntent?) {
        if (token == null) return
        alarmManager.cancel(token)
        token.cancel()
    }

    private fun find(timeId: Long, trigger: Long, exact: Boolean): PendingIntent? =
        operation(timeId, trigger, exact, PendingIntent.FLAG_NO_CREATE)

    private fun operation(timeId: Long, trigger: Long, exact: Boolean, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(MedicationReminders.ACTION)
            .setData(Uri.parse("medication://reminder/$timeId/$trigger/$exact"))
            .putExtra(MedicationReminders.EXTRA_TIME_ID, timeId)
            .putExtra(MedicationReminders.EXTRA_TRIGGER_AT, trigger)
        return PendingIntent.getBroadcast(context, 0, intent,
            flags or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class MedicationReminderCoordinator(
    private val context: Context,
    private val medicationRepository: MedicationRepository
) {
    private val calendarSync = MedicationCalendarSync(context)
    private val appSettingsStore = AppSettingsStore(context)
    private val scheduler = MedicationReminderScheduler(context)
    private val alarmMutex = Mutex()

    suspend fun cleanUnconfiguredCalendarEvents() = calendarSync.cleanUnconfiguredEvents()

    suspend fun resyncAlarms() {
        synchronize()
    }

    suspend fun resyncAll() {
        calendarSync.rebuild(synchronize())
    }

    // 与 receive 串行。删除先提交 Room，再在该边界取消通知。
    suspend fun cancelRemovedNotifications(timeIds: Collection<Long>) = alarmMutex.withLock {
        MedicationReminders.cancelNotifications(context, timeIds)
    }

    suspend fun dismissTakenNotification(timeId: Long, epochDay: Long, legacyAction: Boolean) =
        alarmMutex.withLock {
            val manager = context.getSystemService(NotificationManager::class.java)
            val id = MedicationReminders.notificationIdFor(timeId)
            if (legacyAction) {
                manager.cancel(id)
            } else {
                val tag = MedicationReminders.notificationTagFor(timeId)
                val active = manager.activeNotifications.firstOrNull { it.id == id && it.tag == tag }
                val activeDay = active?.notification?.extras?.getLong(
                    MedicationReminders.EXTRA_DATE_EPOCH_DAY,
                    Long.MIN_VALUE
                )
                if (activeDay == epochDay) manager.cancel(tag, id)
            }
        }

    private suspend fun synchronize(): List<MedicationSlot> = alarmMutex.withLock {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val enabled = appSettingsStore.settingsFlow.first().medicationReminderEnabled
        val slots = medicationRepository.getSlotsForDay(date)
        val old = appSettingsStore.medicationAlarms.first()
        val triggers = buildMap {
            if (enabled) for (slot in slots) {
                val day = slot.alarmDay(date, zone) ?: continue
                put(slot.timeId, day.pending(old.triggers[slot.timeId], now))
            }
        }
        val next = MedicationAlarmState(triggers, old.legacyIds.intersect(triggers.keys))
        val removedIds = (old.triggers.keys + old.legacyIds) - triggers.keys
        if (removedIds.isNotEmpty()) {
            MedicationReminders.cancelNotifications(context, removedIds)
        }
        if (!enabled) {
            val allKnownIds = runCatching { medicationRepository.getAllTimeIds() }.getOrDefault(emptyList())
            if (allKnownIds.isNotEmpty()) {
                MedicationReminders.cancelNotifications(context, allKnownIds)
            }
        }
        for ((id, trigger) in old.triggers) {
            if (triggers[id] != trigger) scheduler.cancel(id, trigger)
        }
        (old.legacyIds - next.legacyIds).forEach(scheduler::cancelLegacy)
        if (old != next) appSettingsStore.setMedicationAlarms(next)
        ReminderNotifications.ensureChannel(context)
        triggers.forEach { (id, trigger) -> scheduler.ensure(id, trigger) }
        slots
    }

    /** 旧版本广播没有预定时刻，仅在迁移标记仍存在时接受一次。 */
    suspend fun receive(timeId: Long, delivered: Long?, notify: (MedicationSlot, LocalDate) -> Unit) {
        alarmMutex.withLock {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val old = appSettingsStore.medicationAlarms.first()
            val previous = old.triggers[timeId]
            val legacy = timeId in old.legacyIds
            if ((delivered == null && !legacy) || (delivered != null && delivered != previous)) return
            val enabled = appSettingsStore.settingsFlow.first().medicationReminderEnabled
            val slot = medicationRepository.getSlotsForDay(date).firstOrNull { it.timeId == timeId }
            val day = slot?.alarmDay(date, zone)
            val due = enabled && day != null && day.isDue(previous, delivered, legacy, now)
            val nextTrigger = when {
                !enabled || day == null -> null
                due -> day.tomorrow
                else -> day.pending(previous, now)
            }
            if (previous != null && previous != nextTrigger) scheduler.cancel(timeId, previous)
            if (legacy) scheduler.cancelLegacy(timeId)
            val triggers = old.triggers.toMutableMap()
            if (nextTrigger == null) triggers.remove(timeId) else triggers[timeId] = nextTrigger
            val next = MedicationAlarmState(triggers, old.legacyIds - timeId)
            if (old != next) appSettingsStore.setMedicationAlarms(next)
            try {
                if (due && !slot.taken) notify(slot, date)
            } finally {
                if (nextTrigger != null) scheduler.ensure(timeId, nextTrigger)
            }
        }
    }

    private fun MedicationSlot.alarmDay(date: LocalDate, zone: ZoneId): MedicationAlarmDay? =
        runCatching { LocalTime.parse(timeText) }.getOrNull()?.let { MedicationAlarmDay(date, it, zone) }
}
