package com.example.bloodpressurerecord.reminder

import android.Manifest
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
                            dateEpochDay = date.toEpochDay(),
                            name = slot.name,
                            dosage = slot.dosage
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
                val markResult = application.appContainer.medicationRepository.markTakenFromReminder(
                    medicationId = medicationId,
                    timeId = timeId,
                    date = LocalDate.ofEpochDay(epochDay)
                )
                // 仅当写入成功（含重复打卡幂等）或原药品/时段已删除失效时收起通知；
                // 数据库临时写入失败时保留通知，使通知状态与未落盘状态严格一致。
                if (markResult.isSuccess) {
                    application.appContainer.medicationReminderCoordinator.dismissTakenNotification(
                        timeId, epochDay, legacyAction = intent.data == null
                    )
                }
            } catch (_: Throwable) {
                // 数据库异常不得导致进程崩溃，且保留通知供后续重试。
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showMedicationNotification(
        context: Context,
        medicationId: Long,
        timeId: Long,
        dateEpochDay: Long,
        name: String,
        dosage: String
    ) {
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
            (MedicationReminders.REQUEST_CODE_BASE + timeId).toInt(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markTakenIntent = PendingIntent.getBroadcast(
            context,
            (MedicationReminders.MARK_TAKEN_REQUEST_CODE_BASE + timeId).toInt(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = MedicationReminders.ACTION_MARK_TAKEN
                data = MedicationReminders.markTakenUri(timeId, dateEpochDay)
                putExtra(MedicationReminders.EXTRA_MEDICATION_ID, medicationId)
                putExtra(MedicationReminders.EXTRA_TIME_ID, timeId)
                putExtra(MedicationReminders.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val dosageText = if (dosage.isBlank()) "" else "（$dosage）"
        val manager = NotificationManagerCompat.from(context)
        val notificationId = MedicationReminders.notificationIdFor(timeId)
        manager.cancel(notificationId)
        manager.notify(
            MedicationReminders.notificationTagFor(timeId),
            notificationId,
            NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle("服药提醒")
                .setContentText("该吃「$name」$dosageText 了，吃完可直接点“已服用”。")
                .setContentIntent(contentIntent)
                .addExtras(Bundle().apply {
                    putLong(MedicationReminders.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
                })
                .addAction(0, "已服用", markTakenIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
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
                .setContentText("请在安静休息后测量并记录血压。")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }
}
