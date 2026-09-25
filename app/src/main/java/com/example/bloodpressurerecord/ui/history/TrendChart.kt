package com.example.bloodpressurerecord.ui.history

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import com.example.bloodpressurerecord.domain.model.displayLabel
import com.example.bloodpressurerecord.ui.theme.WarmError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.withTimeoutOrNull

/** 图表手势状态机：待定 → 横向平移 / 双指缩放 / 长按数据检查。 */
private enum class ChartGestureMode { PENDING, PANNING, ZOOMING, SCRUBBING }

/**
 * 图表内所有颜色在这里统一取值，绘制函数不再各写各的常量。
 * 收缩压沿用陶土橙（主色），舒张压沿用蓝色（三级容器前景色）。
 */
@Immutable
private data class TrendChartPalette(
    val systolic: Color,
    val diastolic: Color,
    val axis: Color,
    val grid: Color,
    val targetDim: Float,
    val nodeBackground: Color,
    val selectionRing: Color,
    val outlier: Color,
    val hint: Color
)

@Composable
private fun trendChartPalette(): TrendChartPalette {
    val scheme = MaterialTheme.colorScheme
    // 颜色来源：主色 = 收缩压，三级容器前景色 = 舒张压；其余中性色从同一套
    // 主题派生。旧文件里未被引用的 SYS_/DIA_/GRID_/AXIS_/REFERENCE_ 常量已删除。
    return TrendChartPalette(
        systolic = scheme.primary,
        diastolic = scheme.onTertiaryContainer,
        axis = scheme.onSurfaceVariant,
        grid = scheme.onSurfaceVariant.copy(alpha = 0.16f),
        targetDim = 0.42f,
        nodeBackground = scheme.surface,
        selectionRing = scheme.primary,
        outlier = WarmError,
        hint = scheme.onSurfaceVariant
    )
}

/** 图表手势参数：集中一处，避免阈值散落在循环里。 */
private class ChartGestureConfig(
    val longPressTimeoutMillis: Long,
    val doubleTapTimeoutMillis: Long,
    val doubleTapMinTimeMillis: Long,
    val touchSlop: Float,
    val tapSlop: Float
) {
    companion object {
        /** 长按进入数据检查：比系统 500ms 更快，接近图表类应用手感。 */
        const val LONG_PRESS_MILLIS = 400L

        /** 长按期间允许的抖动；超过即判定为拖动而不是长按。 */
        const val LONG_PRESS_SLOP_DP = 8f
    }
}

/**
 * 图表横向阈值线：固定的收缩压 140 / 舒张压 90 参考线，以及用户自己设置的
 * 目标线。两者取值可能相同，因此用密封类型区分语义，不再按数值去重。
 * 参考线只画虚线，不带任何说明文字（数值由 Y 轴刻度表达）。
 */
private sealed interface ChartThresholdLine {
    val value: Int

    data object SystolicReference : ChartThresholdLine {
        override val value: Int = TrendSeriesCalculator.REFERENCE_SYSTOLIC
    }

    data object DiastolicReference : ChartThresholdLine {
        override val value: Int = TrendSeriesCalculator.REFERENCE_DIASTOLIC
    }

    data class SystolicTarget(override val value: Int) : ChartThresholdLine

    data class DiastolicTarget(override val value: Int) : ChartThresholdLine
}

/**
 * 趋势图的复位入口。
 *
 * 「双击图表」和操作区里的「恢复」按钮共用同一个复位信号：两条路径各写一套
 * 重置逻辑迟早会漏掉某项状态（缩放、平移、视窗、Inspect、选中高亮），
 * 因此这里只保留一个版本计数，由图表内部统一消费。
 */
class TrendChartController {
    internal var resetToken by mutableStateOf(0)
        private set

    fun reset() {
        resetToken++
    }
}

@Composable
private fun rememberChartGestureConfig(): ChartGestureConfig {
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
private fun rememberChartScrollClaim(claimed: () -> Boolean): NestedScrollConnection {
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

@Composable
fun SessionTimeSeriesDualLineChart(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    onPointSelected: (TrendPoint?) -> Unit,
    modifier: Modifier = Modifier,
    controller: TrendChartController? = null,
    targetSystolic: Int? = null,
    targetDiastolic: Int? = null,
    showSystolic: Boolean = true,
    showDiastolic: Boolean = true,
    emptyTitle: String = "暂无趋势数据"
) {
    val points = series.points
    if (points.isEmpty()) {
        TrendEmptyState(title = emptyTitle, modifier = modifier)
        return
    }

    val density = LocalDensity.current
    val palette = trendChartPalette()
    val zoneId = remember { ZoneId.systemDefault() }
    val textMeasurer = rememberTextMeasurer()
    val viewport = remember(series.range) { TrendTimeViewportState() }
    val gestureConfig = rememberChartGestureConfig()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var claimScroll by remember { mutableStateOf(false) }
    val sysPath = remember { Path() }
    val diaPath = remember { Path() }
    val haptics = LocalHapticFeedback.current

    // 数据域：7/30 天是自然周期窗口，「全部」是首条记录到当前时刻。
    // 视窗夹取在域内，因此平移天然不会拖出真实数据。
    val domainStart = series.rangeStart
    val domainEnd = series.rangeEnd
    val domainSpanMillis = (domainEnd - domainStart).coerceAtLeast(1L)

    // 复位计数：图表内部只认这一套重置逻辑，范围切换、双击复位与「恢复」按钮
    // 都走同一个副作用，避免出现多份互不一致的重置代码。
    val activeResetToken = controller?.resetToken ?: 0

    // 默认视野：聚焦真实有数据的区间。范围切换或跨零点才重置，
    // 同一天内新增记录不会把用户已经缩放/平移过的视野拉回去。
    LaunchedEffect(series, domainStart, domainEnd, activeResetToken) {
        val (defaultStart, defaultEnd) = TrendChartMath.defaultViewport(
            points = series.points,
            range = series.range,
            windowStart = domainStart,
            windowEndInclusive = domainEnd
        )
        viewport.reset(domainStart, domainEnd, defaultStart, defaultEnd)
        claimScroll = false
        // 视窗回到默认时，Inspect / 手工选中的节点、竖向指示线与高亮一并撤销。
        if (activeResetToken != 0) {
            onPointSelected(null)
        }
    }

    val viewportStart = viewport.startMillis()
    val viewportEnd = viewport.endMillis()
    val visiblePoints = remember(points, viewportStart, viewportEnd) {
        TrendChartMath.visiblePoints(
            points = points,
            startInclusive = viewportStart,
            endInclusive = viewportEnd
        )
    }
    // 严格落在可视窗口内的点：节点密度、命中测试与竖向指示线取它，
    // 避免把窗口外的点在边界处钳成一条竖直的点列。
    val viewportPoints = remember(visiblePoints, viewportStart, viewportEnd) {
        visiblePoints.filter { it.timestamp in viewportStart..viewportEnd }
    }
    // Y 轴随可视数据自适应：初始取该范围自身的轴，之后只由「可视点 + 上一帧轴」
    // 决定（带滞回，避免拖动时逐帧抖动），始终不裁切可视数据。
    // 用 range 作 key，范围切换时回到该范围的初始轴。
    var yAxis by remember(series.range) { mutableStateOf(series.yAxis) }
    LaunchedEffect(visiblePoints) {
        if (visiblePoints.isNotEmpty()) {
            yAxis = TrendChartMath.stableYAxis(yAxis, visiblePoints)
        }
    }
    // 手势回调不在组合作用域内，用 rememberUpdatedState 读取最新的 Y 轴与选中点，
    // 否则 pointerInput 协程会一直用首次组合时的值做命中测试 / 触觉节流。
    val latestYAxis by rememberUpdatedState(yAxis)
    val latestSelectedPoint by rememberUpdatedState(selectedPoint)
    val maxDrawPoints = remember(canvasSize.width) {
        (canvasSize.width / 2).coerceIn(MIN_DRAW_POINTS, MAX_DRAW_POINTS)
    }
    // 只降低绘制密度，不删除真实数据：极值点全部保留。
    val renderPoints = remember(visiblePoints, maxDrawPoints) {
        TrendChartMath.sampleShared(visiblePoints, maxDrawPoints)
    }
    val maxTicks = remember(canvasSize.width, density) {
        val plotWidthPx = if (canvasSize.width > 0) {
            (canvasSize.width - 52f * density.density).coerceAtLeast(40f)
        } else {
            0f
        }
        TrendChartMath.maxTickCount((plotWidthPx / density.density).roundToInt())
    }
    val axisTicks = remember(viewportStart, viewportEnd, maxTicks, zoneId) {
        TrendChartMath.timeTicks(
            startMillis = viewportStart,
            endMillis = viewportEnd,
            zoneId = zoneId,
            maxTicks = maxTicks
        )
    }
    // 日期与时间作为一个完整文本块测量，行高、底部留白和横向避让使用同一尺寸。
    val axisLabelStyle = TextStyle(color = palette.axis, fontSize = 12.sp, lineHeight = 16.sp)
    val axisLabelLayouts = remember(axisTicks, axisLabelStyle) {
        axisTicks.map { tick ->
            textMeasurer.measure(
                text = listOfNotNull(tick.primary, tick.secondary).joinToString("\n"),
                style = axisLabelStyle,
                softWrap = false
            )
        }
    }
    // 始终预留两行，缩放切换小时/日期刻度时绘图区和手势坐标保持不变。
    val axisBottomPadding = remember(axisLabelStyle, density) {
        with(density) {
            textMeasurer.measure("00:00\n00-00", axisLabelStyle, softWrap = false).size.height +
                16.dp.toPx()
        }
    }
    val targetSystolicLabel = targetSystolic
        ?.takeIf { showSystolic && it in yAxis.min..yAxis.max }
        ?.let { "目标收缩压 $it" }
    val targetDiastolicLabel = targetDiastolic
        ?.takeIf { showDiastolic && it in yAxis.min..yAxis.max }
        ?.let { "目标舒张压 $it" }
    val chartDescription = remember(series, showSystolic, showDiastolic) {
        buildChartDescription(series, showSystolic, showDiastolic)
    }
    val scrollClaim = rememberChartScrollClaim { claimScroll }

    val geometry = remember(canvasSize, density, axisBottomPadding) {
        ChartGeometry.create(
            size = canvasSize,
            density = density.density,
            bottomPadding = axisBottomPadding
        )
    }
    val nodeTouchRadiusPx = remember(density) {
        with(density) { NODE_TOUCH_RADIUS.dp.toPx() }
    }
    val minSpanRatio = remember(series.range, domainSpanMillis, geometry.plotWidth) {
        TrendChartMath.minSpanRatio(series.range, domainSpanMillis, geometry.plotWidth)
    }
    // 视窗内容版本：用来区分「视窗自己动了」和「选中的点换了」。
    val viewportRevision = viewport.revision
    // 「上一条 / 下一条」这类外部选点必须可见：用户看不到自己点的是哪里，
    // 若节点在窗口外，顶部数据卡换了、图上却什么都没有。
    // 只有【选点 id 变化】且【视窗没有跟着动】时才调整视窗：
    // - 图表内部的触摸选点本来就落在窗口内，不会触发；
    // - 用户自己在缩放/平移时视窗版本会变，这里绝不插手，否则选中一个点之后
    //   就再也拖不动图表了。
    var lastCenteredId by remember(series.range) { mutableStateOf<String?>(null) }
    var lastCenteredRevision by remember(series.range) { mutableIntStateOf(NEVER_CENTERED) }
    LaunchedEffect(selectedPoint?.id, viewportRevision) {
        val point = selectedPoint ?: run {
            lastCenteredId = null
            return@LaunchedEffect
        }
        val isNewSelection = point.id != lastCenteredId
        val viewportMovedSince = viewportRevision != lastCenteredRevision
        if (isNewSelection && viewportMovedSince &&
            point.timestamp !in viewportStart..viewportEnd
        ) {
            viewport.centerOnRatio(point.timestamp)
            lastCenteredRevision = viewport.revision
        }
        lastCenteredId = point.id
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .background(palette.nodeBackground, RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = chartDescription }
                .nestedScroll(scrollClaim)
                .pointerInput(points, series.range, domainStart, domainEnd) {
                    var lastTapAt = 0L
                    var lastTapPosition = Offset(-10_000f, -10_000f)

                    fun currentProjection(): ChartProjection = ChartProjection(
                        geometry = ChartGeometry.create(size, density.density, axisBottomPadding),
                        domainStart = domainStart,
                        domainEnd = domainEnd,
                        startRatio = viewport.startRatio,
                        endRatio = viewport.endRatio,
                        yAxis = latestYAxis
                    )

                    /** 当前严格落在可视窗口内的点：命中测试与长按吸附只看用户真正看到的部分。 */
                    fun visibleNow(): List<TrendPoint> {
                        val start = viewport.startMillis()
                        val end = viewport.endMillis()
                        return TrendChartMath.visiblePoints(
                            points = points,
                            startInclusive = start,
                            endInclusive = end
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
                        if (nearest.id != latestSelectedPoint?.id) {
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
                        val tapped = TrendChartMath.hitTest(
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
                        } else {
                            viewport.resetToDefault()
                            onPointSelected(null)
                        }
                    }

                    /** 单击 / 双击分流：双击复位视窗并撤销这次单击。 */
                    fun resolveTap(position: Offset) {
                        val now = SystemClock.uptimeMillis()
                        val isDoubleTap = lastTapAt != 0L &&
                            now - lastTapAt in
                            gestureConfig.doubleTapMinTimeMillis..gestureConfig.doubleTapTimeoutMillis &&
                            (position - lastTapPosition).getDistance() <= gestureConfig.touchSlop
                        if (isDoubleTap) {
                            lastTapAt = 0L
                            resetViewport()
                        } else {
                            lastTapAt = now
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

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startedWithMultiplePointers = down.pressed
                        var mode = ChartGestureMode.PENDING
                        // 仅统计单指移动：多指手势（捏合）的位移不应参与点按判定。
                        var singlePointerMovement = 0f
                        var sawMultiplePointers = startedWithMultiplePointers
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
                                claimScroll = true
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
                                    val pan = event.calculatePan()
                                    val zoomChange = event.calculateZoom()
                                    when {
                                        pointerCount > 1 || abs(zoomChange - 1f) > 0.005f -> {
                                            mode = ChartGestureMode.ZOOMING
                                            claimScroll = true
                                            applyTransform(event, zoomChange, Offset.Zero)
                                        }
                                        // 只有放大过才平移；未放大时横向拖动无意义，
                                        // 不接管手势，页面纵向滚动照常。
                                        abs(pan.x) > abs(pan.y) &&
                                            abs(pan.x) > gestureConfig.touchSlop &&
                                            viewport.zoom > TrendChartMath.PAN_ZOOM_THRESHOLD -> {
                                            mode = ChartGestureMode.PANNING
                                            claimScroll = true
                                            applyTransform(event, 1f, pan)
                                        }
                                        singlePointerMovement > gestureConfig.touchSlop -> {
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
                                    resolveTap(down.position)
                            }
                        }
                        claimScroll = false
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val currentGeometry = ChartGeometry.create(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    density = density.density,
                    bottomPadding = axisBottomPadding
                )
                val scaler = ChartProjection(
                    geometry = currentGeometry,
                    domainStart = domainStart,
                    domainEnd = domainEnd,
                    startRatio = viewport.startRatio,
                    endRatio = viewport.endRatio,
                    yAxis = yAxis
                )
                val thresholdLines = buildThresholdLines(
                    yAxis = yAxis,
                    targetSystolic = targetSystolic,
                    targetDiastolic = targetDiastolic,
                    showSystolic = showSystolic,
                    showDiastolic = showDiastolic
                )

                drawYAxisGrid(currentGeometry, scaler, yAxis, textMeasurer, palette)

                // —— 数据层：严格裁剪在真实 Plot Area 内 ——
                // 时间映射（ChartProjection.xOfTime）为了保证缩放/平移时曲线在边界
                // 处仍然连续，会把窗口外的点钳到 [-5%, 105%]：放大后横向浏览时，
                // 这些点（以及它们的节点、竖向指示线）会画到坐标轴文字所在的留白里，
                // 看起来就是「折线越过了图表左右边界」。裁剪放在这里统一解决，
                // 不靠删数据或隐藏边界点规避。
                // 轴文字不在此范围内：Y 轴刻度画在 left 左侧、X 轴日期画在 bottom
                // 下方，它们必须保持完整可见。
                clipRect(
                    left = currentGeometry.left,
                    top = currentGeometry.top,
                    right = currentGeometry.right,
                    bottom = currentGeometry.bottom
                ) {
                    drawThresholdLines(
                        geometry = currentGeometry,
                        scaler = scaler,
                        lines = thresholdLines,
                        palette = palette
                    )

                    // 手势期间照常绘制曲线，缩放/拖动有实时反馈。
                    if (showSystolic) {
                        drawSeriesLine(renderPoints, scaler, palette.systolic, systolic = true, path = sysPath)
                    }
                    if (showDiastolic) {
                        drawSeriesLine(renderPoints, scaler, palette.diastolic, systolic = false, path = diaPath)
                    }

                    val selectedVisible = selectedPoint?.takeIf { point ->
                        point.timestamp in viewportStart..viewportEnd
                    }
                    selectedVisible?.let { point ->
                        val x = scaler.xOfTime(point.timestamp)
                        // 竖向指示线：细实线 + 顶端刻度，弱于数据折线。
                        drawLine(
                            color = palette.selectionRing.copy(alpha = 0.5f),
                            start = Offset(x, currentGeometry.top),
                            end = Offset(x, currentGeometry.bottom),
                            strokeWidth = 1.2f
                        )
                        drawLine(
                            color = palette.selectionRing.copy(alpha = 0.75f),
                            start = Offset(x, currentGeometry.top),
                            end = Offset(x, currentGeometry.top + 6f),
                            strokeWidth = 2f
                        )
                    }

                    // 节点密度自适应：数据密时默认只保留选中/吸附节点，放大后逐步显示；
                    // 判定用真实可视点距，且不删除任何数据点（折线仍由全部点构成）。
                    val nodeSpacing = currentGeometry.plotWidth /
                        viewportPoints.size.coerceAtLeast(1)
                    val showAllNodes = viewportPoints.size <= MAX_NODE_POINTS &&
                        nodeSpacing >= MIN_NODE_SPACING_PX
                    renderPoints.forEach { point ->
                        val isPointSelected = selectedPoint?.id == point.id
                        if (!showAllNodes && !isPointSelected) return@forEach
                        val x = scaler.xOfTime(point.timestamp)
                        if (showSystolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOfValue(point.systolic),
                                color = valueColor(point.systolic, palette.systolic, palette.outlier),
                                backgroundColor = palette.nodeBackground,
                                ringColor = palette.selectionRing,
                                selected = isPointSelected
                            )
                        }
                        if (showDiastolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOfValue(point.diastolic),
                                color = valueColor(point.diastolic, palette.diastolic, palette.outlier),
                                backgroundColor = palette.nodeBackground,
                                ringColor = palette.selectionRing,
                                selected = isPointSelected
                            )
                        }
                    }
                }

                // 时间轴文字：位于 Plot Area 下方，不能被数据层裁剪连坐。
                drawTimeAxisLabels(axisTicks, axisLabelLayouts, scaler, currentGeometry, palette)
            }
        }

        Text(
            text = buildHint(series),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.hint
        )
    }
}

/**
 * 需要绘制的横向阈值线：与当前可见指标相关、且落在 Y 轴范围内的固定参考线
 * 与用户目标线。参考线只有虚线，不再生成任何说明文字。
 */
private fun buildThresholdLines(
    yAxis: TrendYAxis,
    targetSystolic: Int?,
    targetDiastolic: Int?,
    showSystolic: Boolean,
    showDiastolic: Boolean
): List<ChartThresholdLine> = buildList {
    if (showSystolic && TrendSeriesCalculator.REFERENCE_SYSTOLIC in yAxis.min..yAxis.max) {
        add(ChartThresholdLine.SystolicReference)
    }
    if (showDiastolic && TrendSeriesCalculator.REFERENCE_DIASTOLIC in yAxis.min..yAxis.max) {
        add(ChartThresholdLine.DiastolicReference)
    }
    targetSystolic
        ?.takeIf { showSystolic && it in yAxis.min..yAxis.max }
        ?.let { add(ChartThresholdLine.SystolicTarget(it)) }
    targetDiastolic
        ?.takeIf { showDiastolic && it in yAxis.min..yAxis.max }
        ?.let { add(ChartThresholdLine.DiastolicTarget(it)) }
}

private fun buildHint(series: TrendSeries): String {
    val granularity = if (series.aggregation == TrendAggregation.DAILY) "每日平均" else "每次测量"
    return "双指缩放，放大后单指拖动，双击复位；长按滑动逐点查看 · $granularity"
}

private fun buildChartDescription(
    series: TrendSeries,
    showSystolic: Boolean,
    showDiastolic: Boolean
): String {
    val metric = when {
        showSystolic && showDiastolic -> "收缩压与舒张压"
        showSystolic -> "收缩压"
        else -> "舒张压"
    }
    val granularity = series.aggregation.displayLabel()
    val first = series.firstMeasuredAt?.let(::formatReadoutDateTime) ?: "--"
    val last = series.lastMeasuredAt?.let(::formatReadoutDateTime) ?: "--"
    return "血压趋势折线图，$metric，$granularity，共 ${series.points.size} 个数据点，" +
        "样本区间 $first 至 $last。可双指缩放、放大后单指拖动、双击恢复默认视野，长按滑动逐点读数。"
}

private fun formatReadoutDateTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

@Composable
private fun TrendEmptyState(title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "保持每天固定时间记录，积累记录后即可查看趋势。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawYAxisGrid(
    geometry: ChartGeometry,
    scaler: ChartProjection,
    yAxis: TrendYAxis,
    textMeasurer: TextMeasurer,
    palette: TrendChartPalette
) {
    // 主刻度间隔由自适应逻辑给出（优先 10 mmHg），条数控制在 5–10 条。
    TrendSeriesCalculator.tickValues(yAxis).forEach { value ->
        val y = scaler.yOfValue(value)
        drawLine(
            color = palette.grid,
            start = Offset(geometry.left, y),
            end = Offset(geometry.right, y),
            strokeWidth = 1.dp.toPx()
        )

        val label = textMeasurer.measure(
            value.toString(),
            TextStyle(color = palette.axis, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            softWrap = false
        )
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                (geometry.left - 6.dp.toPx() - label.size.width).coerceAtLeast(0f),
                y - label.size.height / 2f
            )
        )
    }
}

/**
 * 横向阈值线：固定的 90/140 参考线 + 用户目标线。
 *
 * 参考线只保留「Y 轴刻度 + 水平细虚线」两种表达，不再写「收缩压参考 140」
 * 这类文字——文字会抢占趋势曲线的视觉焦点，数值本身 Y 轴刻度已经说明。
 * 虚线比普通网格稍明显，但仍弱于数据折线。
 */
private fun DrawScope.drawThresholdLines(
    geometry: ChartGeometry,
    scaler: ChartProjection,
    lines: List<ChartThresholdLine>,
    palette: TrendChartPalette
) {
    lines.forEach { line ->
        val color = when (line) {
            ChartThresholdLine.SystolicReference -> palette.systolic.copy(alpha = REFERENCE_LINE_ALPHA)
            ChartThresholdLine.DiastolicReference -> palette.diastolic.copy(alpha = REFERENCE_LINE_ALPHA)
            is ChartThresholdLine.SystolicTarget -> palette.systolic.copy(alpha = palette.targetDim)
            is ChartThresholdLine.DiastolicTarget -> palette.diastolic.copy(alpha = palette.targetDim)
        }
        drawLine(
            color = color,
            start = Offset(geometry.left, scaler.yOfValue(line.value)),
            end = Offset(geometry.right, scaler.yOfValue(line.value)),
            strokeWidth = THRESHOLD_LINE_WIDTH_DP.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(THRESHOLD_DASH_DP.dp.toPx(), THRESHOLD_GAP_DP.dp.toPx())
            )
        )
    }
}

private fun DrawScope.drawSeriesLine(
    points: List<TrendPoint>,
    scaler: ChartProjection,
    color: Color,
    systolic: Boolean,
    path: Path
) {
    path.reset()
    if (points.size < 2) return
    points.forEachIndexed { index, point ->
        val offset = Offset(
            scaler.xOfTime(point.timestamp),
            scaler.yOfValue(if (systolic) point.systolic else point.diastolic)
        )
        // 大于约 2 天的缺测保持断线，不跨缺口直连。
        if (index == 0 || point.timestamp - points[index - 1].timestamp > GAP_MILLIS) {
            path.moveTo(offset.x, offset.y)
        } else {
            val previous = points[index - 1]
            val previousX = scaler.xOfTime(previous.timestamp)
            val previousY = scaler.yOfValue(if (systolic) previous.systolic else previous.diastolic)
            // 控制点取两端 X 的中点、Y 取端点值：曲线只在两点之间过渡，
            // 不会像普通样条那样过冲，因此不会凭空造出不存在的峰值或谷值。
            val controlX = (previousX + offset.x) / 2f
            path.cubicTo(controlX, previousY, controlX, offset.y, offset.x, offset.y)
        }
    }
    drawPath(
        path,
        color,
        // 血压数据是离散测量：2dp 的抗锯齿圆头折线在 2x/3x 屏上仍然清晰，
        // 又不会像旧的 3dp 那样压住网格与参考线。选中态不靠加粗整条折线表达，
        // 而是由选中节点的圆点 + 外圈高亮承担。
        style = Stroke(width = SERIES_LINE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawTimeAxisLabels(
    ticks: List<TrendTimeTick>,
    layouts: List<TextLayoutResult>,
    scaler: ChartProjection,
    geometry: ChartGeometry,
    palette: TrendChartPalette
) {
    if (ticks.size != layouts.size || ticks.isEmpty()) return
    val centers = ticks.map { scaler.xOfTime(it.timestamp) }
    val visibleIndices = TrendChartMath.nonOverlappingTickIndices(
        centers = centers,
        widths = layouts.map { it.size.width.toFloat() },
        left = geometry.left,
        right = geometry.right,
        minimumGap = 8.dp.toPx()
    )
    visibleIndices.forEach { index ->
        val primaryLayout = layouts[index]
        val centerX = centers[index]
        val labelX = (centerX - primaryLayout.size.width / 2f)
            .coerceIn(
                geometry.left,
                (geometry.right - primaryLayout.size.width).coerceAtLeast(geometry.left)
            )
        drawText(
            textLayoutResult = primaryLayout,
            topLeft = Offset(labelX, geometry.bottom + 8.dp.toPx())
        )
    }
    if (visibleIndices.isEmpty()) return
    // 轴线本身保持极轻，避免和网格抢焦点。
    drawLine(
        color = palette.grid,
        start = Offset(geometry.left, geometry.bottom),
        end = Offset(geometry.right, geometry.bottom),
        strokeWidth = 1.dp.toPx()
    )
}

private fun DrawScope.drawPointNode(
    x: Float,
    y: Float,
    color: Color,
    backgroundColor: Color,
    ringColor: Color,
    selected: Boolean
) {
    if (!selected) {
        drawCircle(color = color, radius = 3.2f, center = Offset(x, y))
        return
    }
    // 选中点：外圈光晕 + 实心节点 + 主题色描边，明显区别于普通点。
    drawCircle(color = ringColor.copy(alpha = 0.18f), radius = 11f, center = Offset(x, y))
    drawCircle(color = backgroundColor, radius = 6.4f, center = Offset(x, y))
    drawCircle(color = color, radius = 4.6f, center = Offset(x, y))
    drawCircle(
        color = ringColor,
        radius = 6.4f,
        center = Offset(x, y),
        style = Stroke(width = 2f)
    )
}

private fun valueColor(value: Int, normalColor: Color, outlierColor: Color): Color {
    return if (value in TrendSeriesCalculator.CHART_SAFE_MIN..TrendSeriesCalculator.CHART_SAFE_MAX) {
        normalColor
    } else {
        outlierColor
    }
}

/** 触点命中节点的矩形热区半径：不要求精确点中视觉圆点。 */
private const val NODE_TOUCH_RADIUS = 22

/** 单击判定允许的最大移动（点按抖动容忍）。 */
private const val TAP_SLOP_DP = 8f

/** 收缩压/舒张压折线宽度：2dp 兼顾精细度与高 DPI 屏的可读性。 */
private const val SERIES_LINE_WIDTH_DP = 2f

/** 尚未因外部选点调整过视窗时的哨兵版本号（真实版本号从 0 开始）。 */
private const val NEVER_CENTERED = -1

/** 90/140 参考线：细虚线，比普通网格稍明显，但不抢趋势曲线的焦点。 */
private const val THRESHOLD_LINE_WIDTH_DP = 1.2f
private const val THRESHOLD_DASH_DP = 6f
private const val THRESHOLD_GAP_DP = 5f
private const val REFERENCE_LINE_ALPHA = 0.42f

private val CHART_HEIGHT = 252.dp
private const val MIN_DRAW_POINTS = 60
private const val MAX_DRAW_POINTS = 900
private const val MIN_NODE_SPACING_PX = 14f
private const val MAX_NODE_POINTS = 80
private const val GAP_MILLIS = 2L * 24L * 60L * 60L * 1000L
