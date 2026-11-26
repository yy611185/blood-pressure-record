package com.example.bloodpressurerecord.reminder

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.MainActivity
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.navigation.AppDestination
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MedicationReminders.ACTION) {
            handleMedicationReminder(context, intent)
            return
        }
        if (intent.action == MedicationReminders.ACTION_MARK_TAKEN) {
            handleMedicationMarkTaken(context, intent)
            return
        }
        val type = ReminderType.fromAction(intent.action) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val settings = AppSettingsStore(appContext).settingsFlow.first()
                val typeStillEnabled = when (type) {
                    ReminderType.MORNING -> settings.morningReminderEnabled
                    ReminderType.EVENING -> settings.eveningReminderEnabled
                }
                ReminderNotifications.ensureChannel(appContext)
                if (typeStillEnabled &&
                    ReminderAuthorization.status(appContext) == ReminderAuthorizationStatus.GRANTED
                ) {
                    showNotification(appContext, type)
                }
                (appContext as BloodPressureApplication).appContainer.settingsRepository.refreshMeasurementReminders()
            } catch (_: Throwable) {
                // 广播后台任务不能因异常终止宿主进程。
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMedicationReminder(context: Context, intent: Intent) {
        val timeId = intent.getLongExtra(MedicationReminders.EXTRA_TIME_ID, -1L)
        if (timeId < 0) return
        val triggerAt = intent.getLongExtra(MedicationReminders.EXTRA_TRIGGER_AT, -1L).takeIf { it >= 0 }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val application = appContext as? BloodPressureApplication ?: return@launch
                application.appContainer.medicationReminderCoordinator.receive(timeId, triggerAt) { slot, date ->
                    ReminderNotifications.ensureChannel(appContext)
                    if (ReminderAuthorization.status(appContext) == ReminderAuthorizationStatus.GRANTED) {
                        showMedicationNotification(
                            context = appContext,
                            medicationId = slot.medicationId,
                            timeId = timeId,
                            dateEpochDay = date.toEpochDay()
                        )
                    }
                }
            } catch (_: Throwable) {
                // 忽略异常以避免后台广播杀死进程。
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMedicationMarkTaken(context: Context, intent: Intent) {
        // 兼容升级前遗留的旧通知「已打卡」广播：由于通知正文隐藏药名，不再在后台直接写入打卡，
        // 而是核对计划状态；已打卡或失效则收起旧通知，仍待确认则替换为可打开应用核对的新通知。
        val medicationId = intent.getLongExtra(MedicationReminders.EXTRA_MEDICATION_ID, -1L)
        val timeId = intent.getLongExtra(MedicationReminders.EXTRA_TIME_ID, -1L)
        if (medicationId < 0 || timeId < 0) return
        val epochDay = intent.getLongExtra(
            MedicationReminders.EXTRA_DATE_EPOCH_DAY,
            LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        )
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val application = appContext as? BloodPressureApplication ?: return@launch
                application.appContainer.medicationReminderCoordinator.reviewLegacyAction(
                    medicationId, timeId, epochDay, legacyUntagged = intent.data == null
                ) {
                    ReminderNotifications.ensureChannel(appContext)
                    showMedicationNotification(appContext, medicationId, timeId, epochDay)
                }
            } catch (_: Throwable) {
                // 数据库或通知异常不得导致进程崩溃。
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showMedicationNotification(
        context: Context,
        medicationId: Long,
        timeId: Long,
        dateEpochDay: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = buildMedicationNotification(
            context = context,
            medicationId = medicationId,
            timeId = timeId,
            dateEpochDay = dateEpochDay
        )
        val manager = NotificationManagerCompat.from(context)
        val notificationId = MedicationReminders.notificationIdFor(timeId)
        manager.cancel(notificationId)
        manager.notify(
            MedicationReminders.notificationTagFor(timeId),
            notificationId,
            notification
        )
    }

    internal companion object {
        const val MEDICATION_PUBLIC_TEXT = "你有一条定时用药提醒，请在应用内查看。"
        const val MEDICATION_PRIVATE_TEXT = "你有一条定时用药提醒，点按打开应用核对药品并确认打卡。"
        const val MEDICATION_ACTION_REVIEW = "核对并打卡"

        fun buildMedicationNotification(
            context: Context,
            medicationId: Long,
            timeId: Long,
            dateEpochDay: Long
        ): Notification {
            val openConfirmationIntent = PendingIntent.getActivity(
                context,
                (MedicationReminders.REQUEST_CODE_BASE + timeId).toInt(),
                MedicationReminders.createOpenConfirmationIntent(
                    context = context,
                    medicationId = medicationId,
                    timeId = timeId,
                    dateEpochDay = dateEpochDay
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val publicNotification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle("服药提醒")
                .setContentText(MEDICATION_PUBLIC_TEXT)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            return NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle("服药提醒")
                .setContentText(MEDICATION_PRIVATE_TEXT)
                .setContentIntent(openConfirmationIntent)
                .addExtras(Bundle().apply {
                    putLong(MedicationReminders.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
                })
                .addAction(0, MEDICATION_ACTION_REVIEW, openConfirmationIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        }
    }

    private fun showNotification(context: Context, type: ReminderType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            type.requestCode,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_DESTINATION, AppDestination.AddMeasurement.route)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            type.notificationId,
            NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(type.title)
                .setContentText("请先静坐休息 5 分钟，再测量并记录血压。")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }
}
