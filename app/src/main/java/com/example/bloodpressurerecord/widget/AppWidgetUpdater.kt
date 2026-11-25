package com.example.bloodpressurerecord.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 数据或配置变化后刷新桌面小部件：
 * - 递增 [refreshSignal] 让正在运行的 Glance 会话实时重订阅并更新组合；
 * - 调用 [updateAll] 唤醒未运行的桌面组件实例；无小部件实例时为空操作。
 */
object AppWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _refreshSignal = MutableStateFlow(0L)
    val refreshSignal: StateFlow<Long> = _refreshSignal.asStateFlow()

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { updateNow(appContext) }
        }
    }

    suspend fun updateNow(context: Context) {
        _refreshSignal.update { it + 1L }
        BloodPressureWidget().updateAll(context.applicationContext)
    }
}
