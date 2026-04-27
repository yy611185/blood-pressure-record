package com.example.bloodpressurerecord.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Measure : AppDestination("measure", "测量", Icons.Outlined.MedicalServices)
    data object History : AppDestination("history", "历史", Icons.Outlined.History)
    data object Settings : AppDestination("settings", "设置", Icons.Outlined.Settings)

    data object DoubleLineChart : AppDestination("history/chart", "趋势", Icons.AutoMirrored.Outlined.ShowChart)
    data object SettingsProfile : AppDestination("settings/profile", "用户资料", Icons.Outlined.Settings)
    data object SettingsReminder : AppDestination("settings/reminder", "提醒设置", Icons.Outlined.Settings)
    data object SettingsDisplay : AppDestination("settings/display", "显示设置", Icons.Outlined.Settings)
    data object SettingsDataManagement : AppDestination("settings/data", "数据管理", Icons.Outlined.Settings)
    data object SettingsInfo : AppDestination("settings/info", "说明", Icons.Outlined.Settings)
    data object SettingsDisclaimer : AppDestination("settings/disclaimer", "免责声明", Icons.Outlined.Settings)

    data object HistoryDetail : AppDestination("history/detail/{sessionId}", "详情", Icons.Outlined.History) {
        fun route(sessionId: String): String = "history/detail/$sessionId"
    }

    data object HistoryEdit : AppDestination("history/edit/{sessionId}", "编辑", Icons.Outlined.History) {
        fun route(sessionId: String): String = "history/edit/$sessionId"
    }
}
