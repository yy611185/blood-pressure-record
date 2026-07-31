package com.example.bloodpressurerecord

import android.app.Application
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BloodPressureApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        // 自愈：提醒链依赖“上一次广播里排下一次”。个别 ROM 吞掉一次广播后链条会断，
        // 这里在每次进程启动时按当前设置重排，保证提醒最迟在下次打开应用后恢复。
        applicationScope.launch {
            runCatching {
                val settings = AppSettingsStore(this@BloodPressureApplication).settingsFlow.first()
                ReminderScheduler(this@BloodPressureApplication).apply(settings)
                appContainer.medicationReminderCoordinator.resyncAlarms()
            }
        }
    }
}
