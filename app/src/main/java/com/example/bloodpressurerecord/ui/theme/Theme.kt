package com.example.bloodpressurerecord.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 「暖阳打卡」浅色主题：奶油底 + 陶土橙 / 鼠尾草绿。
private val LightColors = lightColorScheme(
    primary = Terracotta500,
    onPrimary = OnTerracotta,
    primaryContainer = Terracotta200,
    onPrimaryContainer = Terracotta800,
    secondary = Sage500,
    onSecondary = Sage100,
    secondaryContainer = Sage200,
    onSecondaryContainer = Sage800,
    tertiary = Terracotta400,
    onTertiary = Terracotta900,
    tertiaryContainer = Terracotta200,
    onTertiaryContainer = Terracotta800,
    error = WarmError,
    onError = WarmOnError,
    errorContainer = WarmErrorContainer,
    onErrorContainer = WarmOnErrorContainer,
    background = WarmBackground,
    onBackground = WarmText,
    surface = WarmSurface,
    onSurface = WarmText,
    surfaceVariant = WarmSurfaceSoft,
    onSurfaceVariant = WarmTextMuted,
    outline = WarmDivider,
    outlineVariant = WarmDivider,
    surfaceContainerLow = WarmSurfaceSoft,
    surfaceContainerHighest = WarmNeutral200
)

// 设计稿仅定义浅色；深色为按同一暖色 ramp 推导的最佳匹配。
private val DarkColors = darkColorScheme(
    primary = Terracotta400,
    onPrimary = Terracotta900,
    primaryContainer = Terracotta800,
    onPrimaryContainer = Terracotta200,
    secondary = Sage400,
    onSecondary = Sage900,
    secondaryContainer = Sage800,
    onSecondaryContainer = Sage200,
    tertiary = Terracotta300,
    onTertiary = Terracotta900,
    tertiaryContainer = Terracotta800,
    onTertiaryContainer = Terracotta200,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201E1D),
    onBackground = Color(0xFFF5EAD8),
    surface = WarmNeutral900,
    onSurface = Color(0xFFF5EAD8),
    surfaceVariant = Color(0xFF474238),
    onSurfaceVariant = WarmTextDisabled,
    outline = Color(0x52F5EAD8),
    outlineVariant = Color(0x29F5EAD8),
    surfaceContainerLow = Color(0xFF474238),
    surfaceContainerHighest = Color(0xFF474238)
)

// 超圆角体系：输入框 20，卡片 28，按钮/chip/导航全药丸。
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(20.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(999.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BloodPressureRecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
