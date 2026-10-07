package com.example.bloodpressurerecord.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/** Short, finite UI feedback. Compose's frame clock respects system animator duration scale. */
object AppMotion {
    const val Micro = 120
    const val Fast = 160
    const val Normal = 180
    const val Medium = 220
    const val Dock = 260
    const val Press = 100
    const val FadeExit = 60
    const val FadeEnter = 100
    const val FormExit = 90
    const val FormEnter = 130

    val floatMicro = tween<Float>(Micro, easing = FastOutSlowInEasing)
    val floatFast = tween<Float>(Fast, easing = FastOutSlowInEasing)
    val floatNormal = tween<Float>(Normal, easing = FastOutSlowInEasing)
    val floatDock = tween<Float>(Dock, easing = FastOutSlowInEasing)
    val floatPress = tween<Float>(Press, easing = FastOutSlowInEasing)
    val floatDockTabsEnter = tween<Float>(Press, delayMillis = Fast, easing = FastOutSlowInEasing)
    val colorMicro = tween<Color>(Micro, easing = FastOutSlowInEasing)
    val colorFast = tween<Color>(Fast, easing = FastOutSlowInEasing)
    val colorNormal = tween<Color>(Normal, easing = FastOutSlowInEasing)
    val sizeNormal = tween<IntSize>(Normal, easing = FastOutSlowInEasing)
    val sizeFast = tween<IntSize>(Fast, easing = FastOutSlowInEasing)

    private val fadeExit = tween<Float>(FadeExit, easing = FastOutSlowInEasing)
    private val fadeEnter = tween<Float>(FadeEnter, FadeExit, FastOutSlowInEasing)
    private val microExit = tween<Float>(Micro / 3, easing = FastOutSlowInEasing)
    private val microEnter = tween<Float>(Micro * 2 / 3, Micro / 3, FastOutSlowInEasing)
    private val formExit = tween<Float>(FormExit, easing = FastOutSlowInEasing)
    private val formEnter = tween<Float>(FormEnter, FormExit, FastOutSlowInEasing)

    /** No SizeTransform: outgoing/incoming content is never continuously remeasured by a tween. */
    fun fadeThrough(): ContentTransform = ContentTransform(fadeIn(fadeEnter), fadeOut(fadeExit), sizeTransform = null)
    fun microFadeThrough(): ContentTransform = ContentTransform(fadeIn(microEnter), fadeOut(microExit), sizeTransform = null)
    fun formFadeThrough(): ContentTransform = ContentTransform(fadeIn(formEnter), fadeOut(formExit), sizeTransform = null)
}
