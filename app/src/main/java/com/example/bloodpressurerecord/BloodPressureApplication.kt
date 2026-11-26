package com.example.bloodpressurerecord

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.example.bloodpressurerecord.data.repository.backup.clearLegacyBackupImportFiles
import com.example.bloodpressurerecord.update.cleanupLegacyUpdateFiles
import com.example.bloodpressurerecord.widget.AppWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BloodPressureApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        registerTimeAndZoneReceiver()
        applicationScope.launch(Dispatchers.IO) {
            cleanupLegacyUpdateFiles(filesDir, cacheDir)
            runCatching { clearLegacyBackupImportFiles(cacheDir) }
        }
        applicationScope.launch {
            runCatching { appContainer.medicationReminderCoordinator.cleanUnconfiguredCalendarEvents() }
        }
        // 自愈：提醒链依赖“上一次广播里排下一次”。个别 ROM 吞掉一次广播后链条会断，
        // 这里在每次进程启动时按当前设置重排，保证提醒最迟在下次打开应用后恢复。
        applicationScope.launch {
            runCatching { appContainer.settingsRepository.refreshMeasurementReminders() }
            runCatching { appContainer.medicationReminderCoordinator.resyncAlarms() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 深浅色切换等配置变更时同步唤醒并刷新活跃与休眠小组件实例。
        if (::appContainer.isInitialized) {
            AppWidgetUpdater.requestUpdate(this)
        }
    }

    private fun registerTimeAndZoneReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (::appContainer.isInitialized) {
                    appContainer.notifySystemTimeOrZoneChanged()
                    AppWidgetUpdater.requestUpdate(this@BloodPressureApplication)
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}
