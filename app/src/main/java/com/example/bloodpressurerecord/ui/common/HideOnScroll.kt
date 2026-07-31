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

/**
 * 顶栏“往下浏览时收起、往回滚动时复现”的滚动状态。
 *
 * 用法：页面把 [nestedScrollConnection] 挂在滚动容器的父级
 * （Scaffold 或根 Column 的 `Modifier.nestedScroll(...)`），
 * 再把本状态传给 [AppTopBar]，顶栏即可跟手收起/展开；
 * 手指离开后自动吸附到全显或全隐，不会停在半截。
 */
@Stable
class HideOnScrollState {
    /** 顶栏完整高度（px），由顶栏测量时写入。 */
    var barHeightPx by mutableFloatStateOf(0f)

    /** 当前收起偏移，范围 [-barHeightPx, 0]，0 表示完全展开。 */
    var offsetPx by mutableFloatStateOf(0f)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (barHeightPx > 0f) {
                offsetPx = (offsetPx + available.y).coerceIn(-barHeightPx, 0f)
            }
            // 不消费滚动量：内容与顶栏同步移动，视觉上是标准的 enterAlways 行为。
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** 当滚动容器已经回到起点时强制展开，避免标题栏停留在不可见状态。 */
    fun expand() {
        offsetPx = 0f
    }

    private suspend fun settle() {
        if (barHeightPx <= 0f) return
        val target = if (offsetPx < -barHeightPx / 2f) -barHeightPx else 0f
        if (target != offsetPx) {
            animate(initialValue = offsetPx, targetValue = target) { value, _ ->
                offsetPx = value
            }
        }
    }
}

@Composable
fun rememberHideOnScrollState(): HideOnScrollState = remember { HideOnScrollState() }
