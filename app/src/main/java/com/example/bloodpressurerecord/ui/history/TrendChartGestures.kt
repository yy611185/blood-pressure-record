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

private enum class ChartGestureMode { PENDING, PANNING, ZOOMING, SCRUBBING }

internal class ChartGestureConfig(
    val longPressTimeoutMillis: Long,
    val doubleTapTimeoutMillis: Long,
    val doubleTapMinTimeMillis: Long,
    val touchSlop: Float,
    val tapSlop: Float
) {
    companion object {
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
    axisLeftPadding: Float = 38f * densityScale,
    axisTopPadding: Float = 20f * densityScale,
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
        densityScale, axisBottomPadding, axisLeftPadding, axisTopPadding, minSpanRatio
    ) {
        var lastTapAt = 0L
        var lastTapPosition = Offset(-10_000f, -10_000f)

        fun currentProjection(): ChartProjection = ChartProjection(
            geometry = ChartGeometry.create(
                size = size,
                density = densityScale,
                bottomPadding = axisBottomPadding,
                leftPadding = axisLeftPadding,
                topPadding = axisTopPadding
            ),
            domainStart = domainStart,
            domainEnd = domainEnd,
            startRatio = viewport.startRatio,
            endRatio = viewport.endRatio,
            yAxis = yAxis()
        )

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
            // 再按已选中的节点可取消固定十字线
            if (tapped != null && tapped.id == selectedPoint()?.id) {
                onPointSelected(null)
            } else {
                onPointSelected(tapped)
            }
        }

        fun resetViewport() {
            if (controller != null) {
                controller.reset()
                onPointSelected(null)
            } else {
                viewport.resetToDefault()
                onPointSelected(null)
            }
        }

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
                // 手指按下瞬间立即打断按钮触发的在途视窗过渡，确保手势 0ms 直接跟手
                viewport.cancelAnimation()
                var mode = ChartGestureMode.PENDING
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
                                dragIntent == ChartDragIntent.PAN -> {
                                    mode = ChartGestureMode.PANNING
                                    setClaimScroll(true)
                                    applyTransform(event, 1f, Offset(displacement.x, 0f))
                                }
                                dragIntent == ChartDragIntent.YIELD -> {
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
                        sawMultiplePointers &&
                            singlePointerMovement <= gestureConfig.touchSlop ->
                            scrubTo(down.position.x)

                        !sawMultiplePointers &&
                            singlePointerMovement <= gestureConfig.tapSlop ->
                            resolveTap(down.position, down.uptimeMillis)
                    }
                }
                setClaimScroll(false)
            }
        } finally {
            setClaimScroll(false)
        }
    }

private const val TAP_SLOP_DP = 8f
