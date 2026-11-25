package com.example.bloodpressurerecord.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

val LocalAppFontScale = compositionLocalOf { 1f }

/** 系统动画时长缩放为 0 时为 true；预览模式默认 true 以保证首帧落位。 */
val LocalReducedMotion = compositionLocalOf { false }

/** 当前是否处于深色主题，替代各页面通过背景 RGB 猜测深浅色。 */
val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun rememberSystemReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    fun readReduced(): Boolean = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    var reduced by remember(resolver) { mutableStateOf(readReduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReduced()
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
        }
        reduced = readReduced()
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return reduced
}
