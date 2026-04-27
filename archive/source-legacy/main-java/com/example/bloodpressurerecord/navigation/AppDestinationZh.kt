package com.example.bloodpressurerecord.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Measure : AppDestination("measure", "新增测量", Icons.Outlined.MedicalServices)
    data object History : AppDestination("history", "历史", Icons.Outlined.History)
    data object Settings : AppDestination("settings", "设置", Icons.Outlined.Settings)
    data object DoubleLineChart : AppDestination("history/chart", "图表", Icons.Outlined.History)

    data object HistoryDetail : AppDestination("history/detail/{sessionId}", "详情", Icons.Outlined.History) {
        fun route(sessionId: String): String = "history/detail/$sessionId"
    }

    data object HistoryEdit : AppDestination("history/edit/{sessionId}", "编辑", Icons.Outlined.History) {
        fun route(sessionId: String): String = "history/edit/$sessionId"
    }

    data object DoubleLineChart : AppDestination("history/double_line_chart", "双折线", Icons.Outlined.ShowChart)
}
