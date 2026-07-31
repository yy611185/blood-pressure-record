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
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext null
            // 删除失败时不能继续新增，否则每次同步都会产生重复日程。
            deleteOurEvents()
            if (!enabled) return@withContext 0
            val calendarId = findWritableCalendarId() ?: return@withContext null
            var created = 0
            try {
                slots.forEach { slot ->
                    insertDailyEvent(calendarId, slot)
                    created += 1
                }
            } catch (throwable: Throwable) {
                // 尽力回滚本轮已创建的日程，避免留下半套配置。
                runCatching { deleteOurEvents() }
                throw throwable
            }
            created
        }

    private fun deleteOurEvents() {
        val resolver = context.contentResolver
        val selection = "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?) OR " +
            "(${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.DESCRIPTION} = ?)"
        val args = arrayOf(context.packageName, "服药提醒：%", EVENT_DESCRIPTION)
        val ids = mutableListOf<Long>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        }
        ids.forEach { id ->
            resolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                null,
                null
            )
        }
    }

    private fun findWritableCalendarId(): Long? {
        val resolver = context.contentResolver
        var fallback: Long? = null
        resolver.query(
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
        )?.use { cursor ->
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

    private fun insertDailyEvent(calendarId: Long, slot: MedicationSlot) {
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
        val eventId = ContentUris.parseId(eventUri)
        context.contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 0)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
        )
    }

    companion object {
        const val EVENT_DESCRIPTION = "由「血压记录」App自动创建的服药提醒日程"
    }
}
