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
    val pageHorizontalPadding = 16.dp
    val minimumTouchTarget = 48.dp
    val primaryButtonHeight = 56.dp
    val calendarDayMinHeight = 48.dp
    val bottomActionPadding = 16.dp
}

enum class BloodPressureVisualStatus {
    NORMAL,
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

fun BloodPressureVisualStatus.style(colors: ColorScheme): BloodPressureStatusStyle = when (this) {
    BloodPressureVisualStatus.NORMAL -> BloodPressureStatusStyle(
        label = "正常",
        icon = Icons.Default.CheckCircle,
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer
    )
    BloodPressureVisualStatus.ELEVATED -> BloodPressureStatusStyle(
        label = "偏高",
        icon = Icons.Default.Info,
        containerColor = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer
    )
    BloodPressureVisualStatus.HIGH -> BloodPressureStatusStyle(
        label = "血压偏高",
        icon = Icons.Default.Warning,
        containerColor = colors.errorContainer,
        contentColor = colors.onErrorContainer
    )
    BloodPressureVisualStatus.HIGH_RISK -> BloodPressureStatusStyle(
        label = "含高风险读数",
        icon = Icons.Default.Error,
        containerColor = colors.errorContainer,
        contentColor = colors.onErrorContainer
    )
}

fun bloodPressureVisualStatus(
    category: String,
    containsHighRiskReading: Boolean
): BloodPressureVisualStatus = when {
    containsHighRiskReading -> BloodPressureVisualStatus.HIGH_RISK
    category.equals("NORMAL", ignoreCase = true) -> BloodPressureVisualStatus.NORMAL
    category.equals("ELEVATED", ignoreCase = true) -> BloodPressureVisualStatus.ELEVATED
    else -> BloodPressureVisualStatus.HIGH
}

