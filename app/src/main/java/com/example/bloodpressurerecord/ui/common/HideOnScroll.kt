package com.example.bloodpressurerecord.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppMotion

/**
 * 顶栏“往下浏览时收起、往回滚动时复现”的滚动状态。
 * 手指离开后承接剩余纵向惯性速度弹簧吸附，不再僵硬按半高死线重启。
 */
@Stable
class HideOnScrollState(
    private val reducedMotionProvider: () -> Boolean = { false }
) {
    /** 顶栏完整高度（px），由顶栏测量时写入。 */
    var barHeightPx by mutableFloatStateOf(0f)

    /** 当前收起偏移，范围 [-barHeightPx, 0]，0 表示完全展开。 */
    var offsetPx by mutableFloatStateOf(0f)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (barHeightPx > 0f) {
                offsetPx = (offsetPx + available.y).coerceIn(-barHeightPx, 0f)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(initialVelocity = available.y)
            return Velocity.Zero
        }
    }

    /** 当滚动容器已经回到起点时强制展开，避免标题栏停留在不可见状态。 */
    fun expand() {
        offsetPx = 0f
    }

    private suspend fun settle(initialVelocity: Float) {
        if (barHeightPx <= 0f) return
        val target = when {
            initialVelocity < -400f -> -barHeightPx
            initialVelocity > 400f -> 0f
            offsetPx < -barHeightPx / 2f -> -barHeightPx
            else -> 0f
        }
        if (target != offsetPx) {
            val reduced = reducedMotionProvider()
            if (reduced) {
                offsetPx = target
            } else {
                animate(
                    initialValue = offsetPx,
                    targetValue = target,
                    initialVelocity = initialVelocity.coerceIn(-2400f, 2400f),
                    animationSpec = AppMotion.springSnappy(reducedMotion = false)
                ) { value, _ ->
                    offsetPx = value.coerceIn(-barHeightPx, 0f)
                }
            }
        }
    }
}

@Composable
fun rememberHideOnScrollState(): HideOnScrollState {
    val reducedMotion = LocalReducedMotion.current
    return remember(reducedMotion) { HideOnScrollState(reducedMotionProvider = { reducedMotion }) }
}
