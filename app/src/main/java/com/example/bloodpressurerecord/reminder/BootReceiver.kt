package com.example.bloodpressurerecord.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bloodpressurerecord.BloodPressureApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val container = (appContext as BloodPressureApplication).appContainer
                container.settingsRepository.refreshMeasurementReminders()
                container.medicationReminderCoordinator.resyncAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
