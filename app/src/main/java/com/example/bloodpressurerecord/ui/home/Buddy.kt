package com.example.bloodpressurerecord.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppMotion

/** 供资料页复用的友好状态小压；category 可传中文分级或仓库 enum 名。 */
@Composable
fun Buddy(
    category: String,
    modifier: Modifier = Modifier,
    animateIdle: Boolean = true
) {
    val key = when {
        category == "正常" -> "NORMAL"
        category.contains("偏低") -> "LOW"
        category.contains("正常高值") -> "HIGH_NORMAL"
        category.contains("1级") -> "STAGE1"
        category.contains("2级") -> "STAGE2"
        category.contains("3级") -> "STAGE3"
        else -> category
    }
    Buddy(gradeLook(key), modifier = modifier, animateIdle = animateIdle)
}

/**
 * 根目录 bp-common.jsx 的小压几何外形，按 100×94 坐标重绘。
 * 动画值仅在 graphicsLayer 与 Canvas 绘制阶段读取，避免每帧触发组合；
 * 动态开启减少动态效果时立即取消在途动画并同帧归零。
 */
@Composable
internal fun Buddy(
    look: GradeLook,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    animateIdle: Boolean = true
) {
    val reducedMotion = LocalReducedMotion.current
    val motionEnabled = !reducedMotion
    val idleMotionEnabled = motionEnabled && animateIdle
    val density = LocalDensity.current

    val bobState: State<Float>?
    val blinkState: State<Float>?
    if (idleMotionEnabled) {
        val transition = rememberInfiniteTransition(label = "小压呼吸")
        bobState = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "轻浮动"
        )
        blinkState = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(keyframes {
                durationMillis = 5000
                0f at 0
                0f at 4650
                1f at 4750
                0f at 4900
                0f at 5000
            }),
            label = "眨眼"
        )
    } else {
        bobState = null
        blinkState = null
    }

    val tapOffsetDp = remember { Animatable(0f) }
    val tapScaleX = remember { Animatable(1f) }
    val tapScaleY = remember { Animatable(1f) }
    var taps by remember { mutableIntStateOf(0) }
    var handledTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps, motionEnabled) {
        if (!motionEnabled) {
            handledTaps = taps
            tapOffsetDp.snapTo(0f)
            tapScaleX.snapTo(1f)
            tapScaleY.snapTo(1f)
            return@LaunchedEffect
        }
        if (taps > handledTaps) {
            handledTaps = taps
            tapScaleX.snapTo(1.08f)
            tapScaleY.snapTo(0.92f)
            tapOffsetDp.snapTo(0f)
            tapScaleX.animateTo(0.96f, tween(110, easing = AppMotion.SmoothOutEasing))
            tapScaleY.animateTo(1.06f, tween(110, easing = AppMotion.SmoothOutEasing))
            tapOffsetDp.animateTo(-8f, tween(140, easing = AppMotion.SmoothOutEasing))
            tapOffsetDp.animateTo(0f, AppMotion.springCheck(reducedMotion = false))
            tapScaleX.animateTo(1f, AppMotion.springCheck(reducedMotion = false))
            tapScaleY.animateTo(1f, AppMotion.springCheck(reducedMotion = false))
        }
    }

    val bodyColorState = animateColorAsState(
        targetValue = look.body,
        animationSpec = AppMotion.colorSpec(reducedMotion, AppMotion.Medium),
        label = "buddy_body_color"
    )
    val midColorState = animateColorAsState(
        targetValue = look.mid,
        animationSpec = AppMotion.colorSpec(reducedMotion, AppMotion.Medium),
        label = "buddy_mid_color"
    )

    val bodyPath = remember { Path() }
    val leftLeafPath = remember { Path() }
    val rightLeafPath = remember { Path() }
    val mouthPath = remember { Path() }

    Canvas(
        modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics { contentDescription = "小压，点一下换一句话" }
                        .clickable {
                            if (motionEnabled) {
                                taps += 1
                            }
                            onClick()
                        }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                if (!motionEnabled) {
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                } else {
                    val bob = bobState?.value ?: 0f
                    translationY = with(density) { (tapOffsetDp.value - bob * 3f).dp.toPx() }
                    scaleX = tapScaleX.value
                    scaleY = tapScaleY.value
                }
            }
    ) {
        val bodyColor = if (reducedMotion) look.body else bodyColorState.value
        val midColor = if (reducedMotion) look.mid else midColorState.value
        val blink = if (idleMotionEnabled) (blinkState?.value ?: 0f) else 0f
        val sx = size.width / 100f
        val sy = size.height / 94f
        fun point(x: Float, y: Float) = Offset(x * sx, y * sy)
        drawOval(Color(0x332A211C), topLeft = point(20f, 86f), size = Size(60f * sx, 7f * sy))
        bodyPath.rewind()
        bodyPath.apply {
            moveTo(50f * sx, 6f * sy)
            cubicTo(79f * sx, 0f, 101f * sx, 24f * sy, 99f * sx, 50f * sy)
            cubicTo(97f * sx, 77f * sy, 78f * sx, 88f * sy, 51f * sx, 88f * sy)
            cubicTo(22f * sx, 89f * sy, 0f, 75f * sy, 1f * sx, 50f * sy)
            cubicTo(1f * sx, 25f * sy, 19f * sx, 7f * sy, 50f * sx, 6f * sy)
            close()
        }
        drawPath(bodyPath, bodyColor)
        drawOval(midColor.copy(alpha = 0.23f), topLeft = point(30f, 68f), size = Size(55f * sx, 15f * sy))
        drawLine(Color(0xFF5E9A69), point(51f, 9f), point(52f, 0f), 3f * sx)
        leftLeafPath.rewind()
        leftLeafPath.apply {
            moveTo(51f * sx, 5f * sy)
            quadraticTo(36f * sx, -8f * sy, 36f * sx, 2f * sy)
            quadraticTo(43f * sx, 7f * sy, 51f * sx, 5f * sy)
        }
        rightLeafPath.rewind()
        rightLeafPath.apply {
            moveTo(52f * sx, 4f * sy)
            quadraticTo(67f * sx, -8f * sy, 66f * sx, 2f * sy)
            quadraticTo(60f * sx, 7f * sy, 52f * sx, 4f * sy)
        }
        drawPath(leftLeafPath, Color(0xFF79BF83))
        drawPath(rightLeafPath, Color(0xFF79BF83))
        drawOval(Color(0x77E78D89), topLeft = point(15f, 50f), size = Size(16f * sx, 9f * sy))
        drawOval(Color(0x77E78D89), topLeft = point(69f, 50f), size = Size(16f * sx, 9f * sy))
        val ink = Color(0xFF2A211C)
        if (look.mood == "sleepy") {
            drawLine(ink, point(31f, 43f), point(41f, 43f), 3f * sx)
            drawLine(ink, point(59f, 43f), point(69f, 43f), 3f * sx)
        } else {
            val eyeHeight = 14f * (1f - blink * 0.8f)
            val eyeTop = 33f + (14f - eyeHeight) / 2f
            drawOval(ink, topLeft = point(30f, eyeTop), size = Size(10f * sx, eyeHeight * sy))
            drawOval(ink, topLeft = point(60f, eyeTop), size = Size(10f * sx, eyeHeight * sy))
            drawCircle(Color.White, 2f * sx, point(34f, 37f))
            drawCircle(Color.White, 2f * sx, point(64f, 37f))
        }
        when (look.mood) {
            "meh" -> drawLine(ink, point(43f, 60f), point(57f, 60f), 2.5f * sx)
            "sleepy" -> drawCircle(ink, 5f * sx, point(50f, 61f), style = Stroke(2.5f * sx))
            "worried" -> {
                mouthPath.rewind()
                mouthPath.apply {
                    moveTo(41f * sx, 67f * sy)
                    quadraticTo(50f * sx, 53f * sy, 59f * sx, 67f * sy)
                }
                drawPath(mouthPath, ink, style = Stroke(2.8f * sx))
            }
            else -> {
                mouthPath.rewind()
                mouthPath.apply {
                    moveTo(39f * sx, 57f * sy)
                    quadraticTo(50f * sx, if (look.mood == "happy") 75f * sy else 69f * sy, 61f * sx, 57f * sy)
                }
                drawPath(mouthPath, ink, style = Stroke(2.8f * sx))
            }
        }
    }
}
