package com.example.bloodpressurerecord.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MedicationCalendarTarget(
    val id: Long,
    val displayName: String,
    val accountName: String
)

/**
 * 把服药提醒写入系统日历：每个服药时间点一条“每日重复”日程（带日历提醒），
 * 由系统日历应用负责通知/闹钟，与应用内精确闹钟互为双保险。
 *
 * 事件通过 CUSTOM_APP_PACKAGE + 描述标记识别归属，每次同步全量重建，
 * 修改时间、删除药品或关闭开关后不会留下孤儿日程。
 */
class MedicationCalendarSync(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val settingsStore = AppSettingsStore(context)

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadTargets(): List<MedicationCalendarTarget> = withContext(Dispatchers.IO) {
        check(hasPermission()) { "请先授权日历读写权限" }
        cleanUnconfiguredEvents()
        findWritableCalendars()
    }

    /** 旧版开启过同步但未明确选择日历，移除这些旧日程并等待用户重新确认。 */
    suspend fun cleanUnconfiguredEvents() = rebuildMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!settingsStore.settingsFlow.first().medicationCalendarSyncEnabled &&
                settingsStore.calendarEventsMayExist.first() && hasPermission()
            ) {
                findOurEventIds().forEach(::deleteEvent)
                settingsStore.setCalendarEventsMayExist(false)
            }
        }
    }

    suspend fun rebuild(slots: List<MedicationSlot>) = rebuildMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = settingsStore.settingsFlow.first()
            val calendarId = settings.medicationCalendarId
            val mayHaveEvents = settingsStore.calendarEventsMayExist.first()
            if (calendarId == null && !mayHaveEvents) return@withContext
            check(hasPermission()) {
                if (calendarId == null) "请授予日历读写权限以清理旧日程"
                else "请授予日历读写权限以同步服药提醒"
            }
            val existingEventIds = findOurEventIds()
            if (calendarId == null) {
                existingEventIds.forEach(::deleteEvent)
                settingsStore.setCalendarEventsMayExist(false)
                return@withContext
            }
            check(findWritableCalendars().any { it.id == calendarId }) {
                "所选日历已不可写，请重新选择"
            }
            // 先记状态再创建，进程退出或创建失败时仍能重试清理。
            if (slots.isNotEmpty()) settingsStore.setCalendarEventsMayExist(true)
            val createdEventIds = mutableListOf<Long>()
            try {
                slots.forEach { slot ->
                    createdEventIds += insertDailyEvent(calendarId, slot, settings.medicationCalendarIncludeDetails)
                }
                // 新集合完整创建后才移除旧集合，创建阶段失败不会丢失原有提醒。
                existingEventIds.forEach(::deleteEvent)
                settingsStore.setCalendarEventsMayExist(createdEventIds.isNotEmpty())
            } catch (throwable: Throwable) {
                // 只回滚本轮创建的事件，避免把其他合法的本应用日程一并删除。
                createdEventIds.asReversed().forEach { eventId ->
                    runCatching { deleteEvent(eventId) }
                }
                throw throwable
            }
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

    private fun findWritableCalendars(): List<MedicationCalendarTarget> {
        val resolver = context.contentResolver
        val calendars = mutableListOf<MedicationCalendarTarget>()
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
            ),
            null,
            null,
            null
        ) ?: error("无法读取可写日历列表")
        cursor.use {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val access = cursor.getInt(1)
                val visible = cursor.getInt(2) == 1
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR && visible) {
                    calendars += MedicationCalendarTarget(
                        id = id,
                        displayName = cursor.getString(3).orEmpty().ifBlank { "日历 $id" },
                        accountName = cursor.getString(4).orEmpty()
                    )
                }
            }
        }
        return calendars.sortedWith(compareBy({ it.displayName }, { it.accountName }, { it.id }))
    }

    private fun insertDailyEvent(calendarId: Long, slot: MedicationSlot, includeDetails: Boolean): Long {
        val time = LocalTime.parse(slot.timeText)
        val start = LocalDate.now(zoneId).atTime(time).atZone(zoneId).toInstant().toEpochMilli()
        val title = if (includeDetails) "服药提醒：${slot.name} ${slot.dosage}".trimEnd() else "服药提醒"
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
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
        private val rebuildMutex = Mutex()
        const val EVENT_DESCRIPTION = "由「血压记录」App自动创建的服药提醒日程"
    }
}
