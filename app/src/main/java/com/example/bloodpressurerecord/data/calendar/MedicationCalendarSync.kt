package com.example.bloodpressurerecord.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 把服药提醒写入系统日历：每个服药时间点一条“每日重复”日程（带日历提醒），
 * 由系统日历应用负责通知/闹钟，与应用内精确闹钟互为双保险。
 *
 * 事件通过 CUSTOM_APP_PACKAGE + 描述标记识别归属，每次同步先删旧后建新（全量重建），
 * 修改时间、删除药品或关闭开关后不会留下孤儿日程。
 */
class MedicationCalendarSync(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val rebuildMutex = Mutex()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 全量重建本应用创建的服药日程。
     * @param slots 当前启用的全部服药时间点（taken 字段在此无意义）
     * @param enabled 日历同步开关；关闭时仅清理已有日程
     * @return 成功创建的日程数；无权限或无可写日历时返回 null
     */
    suspend fun rebuild(slots: List<MedicationSlot>, enabled: Boolean): Int? =
        rebuildMutex.withLock {
            withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext null
            val existingEventIds = findOurEventIds()
            if (!enabled) {
                existingEventIds.forEach(::deleteEvent)
                return@withContext 0
            }
            // 先确认目标日历可写，再改动旧日程；没有可写日历时保留现状。
            val calendarId = findWritableCalendarId() ?: return@withContext null
            var created = 0
            val createdEventIds = mutableListOf<Long>()
            try {
                slots.forEach { slot ->
                    createdEventIds += insertDailyEvent(calendarId, slot)
                    created += 1
                }
                // 新集合完整创建后才移除旧集合，创建阶段失败不会丢失原有提醒。
                existingEventIds.forEach(::deleteEvent)
            } catch (throwable: Throwable) {
                // 只回滚本轮创建的事件，避免把其他合法的本应用日程一并删除。
                createdEventIds.asReversed().forEach { eventId ->
                    runCatching { deleteEvent(eventId) }
                }
                throw throwable
            }
            created
            }
        }

    private fun findOurEventIds(): List<Long> {
        val resolver = context.contentResolver
        val selection = "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND " +
            "${CalendarContract.Events.DESCRIPTION} = ?"
        val args = arrayOf(context.packageName, EVENT_DESCRIPTION)
        val ids = mutableListOf<Long>()
        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null
        ) ?: error("无法读取本应用的日历日程")
        cursor.use {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        }
        return ids
    }

    private fun deleteEvent(eventId: Long) {
        val deleted = context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null,
            null
        )
        check(deleted == 1) { "无法删除本应用的日历日程 $eventId" }
    }

    private fun findWritableCalendarId(): Long? {
        val resolver = context.contentResolver
        var fallback: Long? = null
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.VISIBLE
            ),
            null,
            null,
            null
        ) ?: error("无法读取可写日历列表")
        cursor.use {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val access = cursor.getInt(1)
                val isPrimary = cursor.getInt(2) == 1
                val visible = cursor.getInt(3) == 1
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR && visible) {
                    if (isPrimary) return id
                    if (fallback == null) fallback = id
                }
            }
        }
        return fallback
    }

    private fun insertDailyEvent(calendarId: Long, slot: MedicationSlot): Long {
        val time = LocalTime.parse(slot.timeText)
        val start = LocalDate.now(zoneId).atTime(time).atZone(zoneId).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "服药提醒：${slot.name} ${slot.dosage}")
            put(CalendarContract.Events.DESCRIPTION, EVENT_DESCRIPTION)
            put(CalendarContract.Events.DTSTART, start)
            // 重复日程用 DURATION（RFC5545），不能用 DTEND。
            put(CalendarContract.Events.DURATION, "PT10M")
            put(CalendarContract.Events.RRULE, "FREQ=DAILY")
            put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("无法写入日历日程")
        val eventId = runCatching { ContentUris.parseId(eventUri) }.getOrElse { throwable ->
            val deleted = context.contentResolver.delete(eventUri, null, null)
            check(deleted == 1) { "无法回滚无效的日历日程" }
            throw IllegalStateException("日历日程返回了无效 URI", throwable)
        }
        try {
            val reminderUri = context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 0)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
            )
            if (reminderUri == null) error("无法为日历日程写入提醒")
        } catch (throwable: Throwable) {
            runCatching { deleteEvent(eventId) }
            throw throwable
        }
        return eventId
    }

    companion object {
        const val EVENT_DESCRIPTION = "由「血压记录」App自动创建的服药提醒日程"
    }
}
