package com.example.bloodpressurerecord.ui.history

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/** 图表手势状态机：待定 → 横向平移 / 双指缩放 / 长按数据检查。 */
private enum class ChartGestureMode { PENDING, PANNING, ZOOMING, SCRUBBING }


/** 图表手势参数：集中一处，避免阈值散落在循环里。 */
internal class ChartGestureConfig(
    val longPressTimeoutMillis: Long,
    val doubleTapTimeoutMillis: Long,
    val doubleTapMinTimeMillis: Long,
    val touchSlop: Float,
    val tapSlop: Float
) {
    companion object {
        /** 长按进入数据检查：比系统 500ms 更快，接近图表类应用手感。 */
        const val LONG_PRESS_MILLIS = 400L

    }
}


@Composable
internal fun rememberChartGestureConfig(): ChartGestureConfig {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    return remember(density, viewConfiguration) {
        ChartGestureConfig(
            longPressTimeoutMillis = ChartGestureConfig.LONG_PRESS_MILLIS,
            doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
            doubleTapMinTimeMillis = viewConfiguration.doubleTapMinTimeMillis,
            touchSlop = viewConfiguration.touchSlop,
            tapSlop = with(density) { TAP_SLOP_DP.dp.toPx() }
        )
    }
}

/**
 * 图表手势期间禁止父级纵向滚动。
 *
 * Compose 里没有 `requestDisallowInterceptTouchEvent`，对应手段是嵌套滚动：
 * 图表声明接管时，把可用滚动量全部消费掉，父级 `verticalScroll` 就不会启动。
 * [claimed] 由手势状态机写入，因此普通上下滑动（未接管）仍然可以滚动页面。
 */
@Composable
internal fun rememberChartScrollClaim(claimed: () -> Boolean): NestedScrollConnection {
    val currentClaimed by rememberUpdatedState(claimed)
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (currentClaimed()) available else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (currentClaimed()) available else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 手势期间不允许页面惯性滚动，否则松手后页面会接着滑。
                return if (currentClaimed()) available else Velocity.Zero
            }
        }
    }
}

internal fun Modifier.trendChartPointerInput(
    points: List<TrendPoint>,
    range: TrendRange,
    domainStart: Long,
    domainEnd: Long,
    viewport: TrendTimeViewportState,
    densityScale: Float,
    axisBottomPadding: Float,
    yAxis: () -> TrendYAxis,
    selectedPoint: () -> TrendPoint?,
    onPointSelected: (TrendPoint?) -> Unit,
    controller: TrendChartController?,
    gestureConfig: ChartGestureConfig,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    showSystolic: Boolean,
    showDiastolic: Boolean,
    pulseMode: Boolean,
    nodeTouchRadiusPx: Float,
    minSpanRatio: Double,
    setClaimScroll: (Boolean) -> Unit
): Modifier = this
                .pointerInput(
                    points, range, domainStart, domainEnd, showSystolic, showDiastolic, pulseMode,
                    densityScale, axisBottomPadding, minSpanRatio
                ) {
                    var lastTapAt = 0L
                    var lastTapPosition = Offset(-10_000f, -10_000f)

                    fun currentProjection(): ChartProjection = ChartProjection(
                        geometry = ChartGeometry.create(size, densityScale, axisBottomPadding),
                        domainStart = domainStart,
                        domainEnd = domainEnd,
                        startRatio = viewport.startRatio,
                        endRatio = viewport.endRatio,
                        yAxis = yAxis()
                    )

                    /** 当前严格落在可视窗口内的点：命中测试与长按吸附只看用户真正看到的部分。 */
                    fun visibleNow(): List<TrendPoint> {
                        val start = viewport.startMillis()
                        val end = viewport.endMillis()
                        return TrendChartMath.visiblePoints(
                            points = points,
                            startInclusive = start,
                            endInclusive = end,
                            alreadySorted = true
                        ).filter { it.timestamp in start..end }
                    }

                    /** 长按/拖动：按手指 X 找到最近数据点并连续切换。 */
                    fun scrubTo(x: Float) {
                        val current = currentProjection()
                        val clampedX = x.coerceIn(current.geometry.left, current.geometry.right)
                        val nearest = TrendChartMath.nearestPoint(
                            visibleNow(),
                            current.timeAtX(clampedX)
                        ) ?: return
                        if (nearest.id != selectedPoint()?.id) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onPointSelected(nearest)
                    }

                    /**
                     * 单击：优先命中节点热区（不要求点中视觉圆点），
                     * 未命中则退化到「按 X 最近的可见点」。
                     *
                     * 单击只负责**选中**：当天明细改由操作区的「明细」按钮打开，
                     * 因此这里不再需要跨帧等待双击窗口的延迟任务。
                     */
                    fun handleTap(position: Offset) {
                        val current = currentProjection()
                        val tapped = if (pulseMode) TrendChartMath.hitTestPulse(
                            projection = current,
                            visible = visibleNow(),
                            x = position.x,
                            y = position.y,
                            touchRadiusPx = nodeTouchRadiusPx
                        ) else TrendChartMath.hitTest(
                            projection = current,
                            visible = visibleNow(),
                            x = position.x,
                            y = position.y,
                            showSystolic = showSystolic,
                            showDiastolic = showDiastolic,
                            touchRadiusPx = nodeTouchRadiusPx
                        )
                        onPointSelected(tapped)
                    }

                    /**
                     * 双击复位：与操作区「恢复」按钮共用控制器上的同一个复位信号，
                     * 缩放 / 平移 / 视窗 / Inspect / 选中高亮一次性回到默认。
                     */
                    fun resetViewport() {
                        if (controller != null) {
                            controller.reset()
                            onPointSelected(null)
                        } else {
                            viewport.resetToDefault()
                            onPointSelected(null)
                        }
                    }

                    /** 单击 / 双击分流：双击复位视窗并撤销这次单击。 */
                    fun resolveTap(position: Offset, tapUptimeMillis: Long) {
                        val isDoubleTap = lastTapAt != 0L &&
                            tapUptimeMillis - lastTapAt in
                            gestureConfig.doubleTapMinTimeMillis..gestureConfig.doubleTapTimeoutMillis &&
                            (position - lastTapPosition).getDistance() <= gestureConfig.touchSlop
                        if (isDoubleTap) {
                            lastTapAt = 0L
                            resetViewport()
                        } else {
                            lastTapAt = tapUptimeMillis
                            lastTapPosition = position
                            handleTap(position)
                        }
                    }

                    fun applyTransform(event: PointerEvent, zoomChange: Float, pan: Offset) {
                        val current = currentProjection()
                        if (event.changes.size > 1 && abs(zoomChange - 1f) > 0.001f) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val positionInPlot =
                                ((centroid.x - current.geometry.left) / current.geometry.plotWidth)
                                    .toDouble()
                            val focusRatio = positionInPlot.coerceIn(0.0, 1.0)
                            viewport.zoomBy(
                                zoomChange = zoomChange,
                                focusRatio = focusRatio,
                                minSpanRatio = minSpanRatio
                            )
                        }
                        if (pan.x != 0f) {
                            val deltaRatio = -pan.x.toDouble() / current.geometry.plotWidth *
                                viewport.spanRatio
                            viewport.panBy(deltaRatio)
                        }
                        event.changes.forEach { it.consume() }
                    }

                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var mode = ChartGestureMode.PENDING
                            // 仅统计单指移动：多指手势（捏合）的位移不应参与点按判定。
                            var singlePointerMovement = 0f
                            var sawMultiplePointers = false
                            val downAt = SystemClock.uptimeMillis()

                            while (true) {
                                val event = if (mode == ChartGestureMode.PENDING) {
                                    val remaining =
                                        gestureConfig.longPressTimeoutMillis -
                                            (SystemClock.uptimeMillis() - downAt)
                                    if (remaining > 0) {
                                        withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                    } else {
                                        null
                                    }
                                } else {
                                    awaitPointerEvent()
                                }

                                if (event == null) {
                                    // 长按达时且按住的是一根手指（没有移动）：
                                    // 进入数据检查模式。有明显移动就不抢，交还页面。
                                    if (singlePointerMovement > gestureConfig.touchSlop) break
                                    mode = ChartGestureMode.SCRUBBING
                                    setClaimScroll(true)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scrubTo(down.position.x)
                                    continue
                                }

                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                val pointerCount = event.changes.count { it.pressed }
                                if (pointerCount > 1) sawMultiplePointers = true
                                if (pointerCount == 1 &&
                                    (mode == ChartGestureMode.PENDING ||
                                        mode == ChartGestureMode.SCRUBBING)
                                ) {
                                    singlePointerMovement += event.calculatePan().getDistance()
                                }

                                when (mode) {
                                    ChartGestureMode.PENDING -> {
                                        val displacement = pressed.first().position - down.position
                                        val zoomChange = event.calculateZoom()
                                        val dragIntent = TrendChartMath.dragIntent(
                                            displacement.x, displacement.y, gestureConfig.touchSlop,
                                            viewport.zoom > TrendChartMath.PAN_ZOOM_THRESHOLD
                                        )
                                        when {
                                            pointerCount > 1 || abs(zoomChange - 1f) > 0.005f -> {
                                                mode = ChartGestureMode.ZOOMING
                                                setClaimScroll(true)
                                                applyTransform(event, zoomChange, Offset.Zero)
                                            }
                                            // 只有放大过才平移；未放大时横向拖动无意义，
                                            // 不接管手势，页面纵向滚动照常。
                                            dragIntent == ChartDragIntent.PAN -> {
                                                mode = ChartGestureMode.PANNING
                                                setClaimScroll(true)
                                                applyTransform(event, 1f, Offset(displacement.x, 0f))
                                            }
                                            dragIntent == ChartDragIntent.YIELD -> {
                                                // 纵向拖动：不接管手势，交还给页面滚动。
                                                break
                                            }
                                        }
                                    }

                                    ChartGestureMode.PANNING -> {
                                        applyTransform(event, 1f, event.calculatePan())
                                    }

                                    ChartGestureMode.ZOOMING -> {
                                        applyTransform(
                                            event,
                                            event.calculateZoom(),
                                            event.calculatePan()
                                        )
                                    }

                                    ChartGestureMode.SCRUBBING -> {
                                        scrubTo(pressed.first().position.x)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }

                            if (mode == ChartGestureMode.PENDING) {
                                when {
                                    // 双指轻点：按手势起点吸附一次，方便快速定位。
                                    sawMultiplePointers &&
                                        singlePointerMovement <= gestureConfig.touchSlop ->
                                        scrubTo(down.position.x)

                                    // 单指轻点：选中 / 打开当日明细（见 resolveTap）。
                                    !sawMultiplePointers &&
                                        singlePointerMovement <= gestureConfig.tapSlop ->
                                        resolveTap(down.position, down.uptimeMillis)
                                }
                            }
                            setClaimScroll(false)
                        }
                    } finally {
                        // pointerInput 的 key 随数据变化时协程会取消；及时释放父级滚动。
                        setClaimScroll(false)
                    }
                }


private const val TAP_SLOP_DP = 8f
