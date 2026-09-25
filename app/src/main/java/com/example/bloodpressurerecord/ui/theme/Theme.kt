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

// UIredesign 根目录原型：奶白背景、白色卡片、珊瑚橙操作。
private val LightColors = lightColorScheme(
    primary = Terracotta500,
    onPrimary = OnTerracotta,
    primaryContainer = Terracotta200,
    onPrimaryContainer = Terracotta700,
    secondary = Sage500,
    onSecondary = Sage100,
    secondaryContainer = Sage200,
    onSecondaryContainer = Sage700,
    tertiary = Terracotta400,
    onTertiary = Terracotta900,
    tertiaryContainer = Color(0xFFCAEDFF),
    onTertiaryContainer = Color(0xFF02578B),
    error = WarmError,
    onError = WarmOnError,
    errorContainer = WarmErrorContainer,
    onErrorContainer = WarmOnErrorContainer,
    background = WarmBackground,
    onBackground = WarmText,
    surface = WarmSurface,
    onSurface = WarmText,
    surfaceVariant = WarmSurfaceSoft,
    onSurfaceVariant = WarmTextBody,
    outline = WarmDivider,
    outlineVariant = WarmDivider,
    surfaceContainerLow = WarmSurfaceSoft,
    surfaceContainer = WarmSurfaceSoft,
    surfaceContainerHigh = WarmNeutral200,
    surfaceContainerHighest = WarmSurfaceSoft
)

// 原稿 .phone.dark 独立深色表面。
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
    tertiaryContainer = Color(0xFF1A3B55),
    onTertiaryContainer = Color(0xFF8BD2FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1512),
    onBackground = Color(0xFFF2EEE7),
    surface = WarmNeutral900,
    onSurface = Color(0xFFF2EEE7),
    surfaceVariant = Color(0xFF2D2823),
    onSurfaceVariant = Color(0xFFB6B0A9),
    outline = Color(0xFF7F7973),
    outlineVariant = Color(0xFF3A342F),
    surfaceContainerLow = Color(0xFF2D2823),
    surfaceContainer = Color(0xFF2D2823),
    surfaceContainerHigh = Color(0xFF38312C),
    surfaceContainerHighest = Color(0xFF2D2823)
)

// 原稿卡片 26、输入 16、按钮 20；胶囊由对应控件显式指定。
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
