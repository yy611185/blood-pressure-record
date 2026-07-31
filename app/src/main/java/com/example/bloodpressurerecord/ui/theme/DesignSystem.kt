package com.example.bloodpressurerecord.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object AppSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
    val xxLarge = 32.dp
}

object AppDimensions {
    /** 页面水平边距（暖阳设计：18dp）。 */
    val pageHorizontalPadding = 18.dp
    /** 卡片内边距（暖阳设计：20dp）。 */
    val cardPadding = 20.dp
    val minimumTouchTarget = 48.dp
    /** 主按钮高度（暖阳设计：60dp，全药丸）。 */
    val primaryButtonHeight = 60.dp
    /** 表单保存按钮高度。 */
    val saveButtonHeight = 58.dp
    val calendarDayMinHeight = 48.dp
    /** 日历日期圆形直径。 */
    val calendarDaySize = 38.dp
    val bottomActionPadding = 16.dp
}

enum class BloodPressureVisualStatus {
    NORMAL,
    LOW,
    ELEVATED,
    HIGH,
    HIGH_RISK
}

data class BloodPressureStatusStyle(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

/**
 * 血压状态色（暖阳设计固定值）：
 * 正常走鼠尾草绿，偏高/高风险走陶土橙，偏低走浅橙提示。
 */
fun BloodPressureVisualStatus.style(colors: ColorScheme): BloodPressureStatusStyle = when (this) {
    BloodPressureVisualStatus.NORMAL -> BloodPressureStatusStyle(
        label = "正常",
        icon = Icons.Default.CheckCircle,
        containerColor = Sage200,
        contentColor = Sage800
    )
    BloodPressureVisualStatus.LOW -> BloodPressureStatusStyle(
        label = "血压偏低",
        icon = Icons.Default.Info,
        containerColor = Terracotta100,
        contentColor = Terracotta700
    )
    BloodPressureVisualStatus.ELEVATED -> BloodPressureStatusStyle(
        label = "正常高值",
        icon = Icons.Default.Info,
        containerColor = Terracotta200,
        contentColor = Terracotta800
    )
    BloodPressureVisualStatus.HIGH -> BloodPressureStatusStyle(
        label = "血压偏高",
        icon = Icons.Default.Warning,
        containerColor = Terracotta200,
        contentColor = Terracotta800
    )
    BloodPressureVisualStatus.HIGH_RISK -> BloodPressureStatusStyle(
        label = "含高风险读数",
        icon = Icons.Default.Error,
        containerColor = Terracotta300,
        contentColor = Terracotta900
    )
}

fun bloodPressureVisualStatus(
    category: String,
    containsHighRiskReading: Boolean
): BloodPressureVisualStatus = when {
    containsHighRiskReading -> BloodPressureVisualStatus.HIGH_RISK
    category.equals("NORMAL", ignoreCase = true) -> BloodPressureVisualStatus.NORMAL
    category.equals("LOW", ignoreCase = true) -> BloodPressureVisualStatus.LOW
    // ELEVATED 为 v5 迁移前的旧命名，与正常高值同级
    category.equals("HIGH_NORMAL", ignoreCase = true) ||
        category.equals("ELEVATED", ignoreCase = true) -> BloodPressureVisualStatus.ELEVATED
    else -> BloodPressureVisualStatus.HIGH
}
