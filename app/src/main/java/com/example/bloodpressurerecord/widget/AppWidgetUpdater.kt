package com.example.bloodpressurerecord.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 数据变化后刷新桌面小部件；无小部件实例时是空操作，失败静默忽略。 */
object AppWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { BloodPressureWidget().updateAll(appContext) }
        }
    }
}
