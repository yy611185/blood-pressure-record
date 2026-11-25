package com.example.bloodpressurerecord.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * 全应用唯一动效语言：
 * - 空间位移、缩放、滑块、折叠与跟手吸附统一使用保速临界阻尼弹簧（可随时打断转向）；
 * - 透明度与颜色变化统一使用平滑光学减速曲线；
 * - 开启系统减少动态效果（ANIMATOR_DURATION_SCALE == 0）时自动退化为 0ms 同帧落位。
 */
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

    val SmoothOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val SmoothInEasing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

    // —— 物理弹簧族 ——
    fun <T> springPress(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)

    fun <T> springSnappy(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.94f, stiffness = 520f)

    fun <T> springGentle(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 340f)

    fun <T> springCheck(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.78f, stiffness = 480f)

    fun sizeSpec(reducedMotion: Boolean = false): FiniteAnimationSpec<IntSize> =
        if (reducedMotion) snap() else sizeNormal

    // —— 光学曲线族（兼容既有调用点） ——
    val floatMicro = tween<Float>(Micro, easing = SmoothOutEasing)
    val floatFast = tween<Float>(Fast, easing = SmoothOutEasing)
    val floatNormal = tween<Float>(Normal, easing = SmoothOutEasing)
    val floatDock = tween<Float>(Dock, easing = SmoothOutEasing)
    val floatPress = tween<Float>(Press, easing = SmoothOutEasing)
    val floatDockTabsEnter = tween<Float>(Press, delayMillis = Fast, easing = SmoothOutEasing)
    val colorMicro = tween<Color>(Micro, easing = SmoothOutEasing)
    val colorFast = tween<Color>(Fast, easing = SmoothOutEasing)
    val colorNormal = tween<Color>(Normal, easing = SmoothOutEasing)
    val sizeNormal = tween<IntSize>(Normal, easing = FastOutSlowInEasing)
    val sizeFast = tween<IntSize>(Fast, easing = FastOutSlowInEasing)

    fun colorThemeSpec(reducedMotion: Boolean): AnimationSpec<Color> =
        if (reducedMotion) snap() else tween(200, easing = SmoothOutEasing)

    fun colorSpec(reducedMotion: Boolean, durationMillis: Int = Fast): AnimationSpec<Color> =
        if (reducedMotion) snap() else tween(durationMillis, easing = SmoothOutEasing)

    private val fadeExit = tween<Float>(FadeExit, easing = SmoothInEasing)
    private val fadeEnter = tween<Float>(FadeEnter, FadeExit, SmoothOutEasing)
    private val microExit = tween<Float>(Micro / 3, easing = SmoothInEasing)
    private val microEnter = tween<Float>(Micro * 2 / 3, Micro / 3, SmoothOutEasing)
    private val formExit = tween<Float>(FormExit, easing = SmoothInEasing)
    private val formEnter = tween<Float>(FormEnter, FormExit, SmoothOutEasing)

    /** No SizeTransform: outgoing/incoming content is never continuously remeasured by a tween. */
    fun fadeThrough(reducedMotion: Boolean = false): ContentTransform = if (reducedMotion) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    } else {
        ContentTransform(fadeIn(fadeEnter), fadeOut(fadeExit), sizeTransform = null)
    }

    fun microFadeThrough(reducedMotion: Boolean = false): ContentTransform = if (reducedMotion) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    } else {
        ContentTransform(fadeIn(microEnter), fadeOut(microExit), sizeTransform = null)
    }

    fun formFadeThrough(reducedMotion: Boolean = false): ContentTransform = if (reducedMotion) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    } else {
        ContentTransform(fadeIn(formEnter), fadeOut(formExit), sizeTransform = null)
    }

    /** 带空间方向感的水平切换：供月历翻页、表单切换、报告配置↔预览共用。 */
    fun directionalSlideFade(
        forward: Boolean,
        distancePx: Int = 48,
        reducedMotion: Boolean = false
    ): ContentTransform {
        if (reducedMotion) {
            return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
        }
        val sign = if (forward) 1 else -1
        return ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(Medium, easing = SmoothOutEasing),
                initialOffsetX = { sign * distancePx }
            ) + fadeIn(tween(Fast, delayMillis = 30, easing = SmoothOutEasing)),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(Fast, easing = SmoothInEasing),
                targetOffsetX = { -sign * (distancePx / 2) }
            ) + fadeOut(tween(110, easing = SmoothInEasing)),
            sizeTransform = null
        )
    }

    /** 数值微滚过渡：供打卡计数、均值数字等更新时使用。 */
    fun verticalNumericRoll(
        increase: Boolean = true,
        reducedMotion: Boolean = false
    ): ContentTransform {
        if (reducedMotion) {
            return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
        }
        val sign = if (increase) 1 else -1
        return ContentTransform(
            targetContentEnter = slideInVertically(
                animationSpec = tween(Normal, easing = SmoothOutEasing),
                initialOffsetY = { sign * (it / 3) }
            ) + fadeIn(tween(Fast, easing = SmoothOutEasing)),
            initialContentExit = slideOutVertically(
                animationSpec = tween(Micro, easing = SmoothInEasing),
                targetOffsetY = { -sign * (it / 3) }
            ) + fadeOut(tween(Micro, easing = SmoothInEasing)),
            sizeTransform = null
        )
    }

    fun expandVisibilityEnter(reducedMotion: Boolean): EnterTransition = if (reducedMotion) {
        EnterTransition.None
    } else {
        expandVertically(
            animationSpec = tween(Normal, easing = SmoothOutEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(tween(Fast, easing = SmoothOutEasing))
    }

    fun expandVisibilityExit(reducedMotion: Boolean): ExitTransition = if (reducedMotion) {
        ExitTransition.None
    } else {
        shrinkVertically(
            animationSpec = tween(Fast, easing = SmoothInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(tween(Micro, easing = SmoothInEasing))
    }
}
