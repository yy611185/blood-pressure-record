package com.example.bloodpressurerecord.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.MainActivity
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import java.time.LocalDate
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
                ReminderScheduler(appContext).apply(settings)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMedicationReminder(context: Context, intent: Intent) {
        val timeId = intent.getLongExtra(MedicationReminders.EXTRA_TIME_ID, -1L)
        if (timeId < 0) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val application = appContext as? BloodPressureApplication ?: return@launch
                val settings = AppSettingsStore(appContext).settingsFlow.first()
                val slot = application.appContainer.medicationRepository
                    .getSlotsForDay(LocalDate.now())
                    .firstOrNull { it.timeId == timeId }
                ReminderNotifications.ensureChannel(appContext)
                // 时间点已被删除/停用时静默跳过，旧闹钟自然失效。
                if (slot != null &&
                    settings.medicationReminderEnabled &&
                    ReminderAuthorization.status(appContext) == ReminderAuthorizationStatus.GRANTED
                ) {
                    showMedicationNotification(appContext, timeId, slot.name, slot.dosage)
                }
                application.appContainer.medicationReminderCoordinator.resyncAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showMedicationNotification(
        context: Context,
        timeId: Long,
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
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dosageText = if (dosage.isBlank()) "" else "（$dosage）"
        NotificationManagerCompat.from(context).notify(
            (MedicationReminders.NOTIFICATION_ID_BASE + timeId).toInt(),
            NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle("服药提醒")
                .setContentText("该吃「$name」$dosageText 了，吃完记得在首页打个勾。")
                .setContentIntent(contentIntent)
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
            Intent(context, MainActivity::class.java),
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
