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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
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

/** 图表手势状态机：待定 → 平移缩放 / 长按十字指针 / 点按。 */
private enum class ChartGestureMode { PENDING, TRANSFORM, CROSSHAIR }

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
    val reference: Color,
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
        reference = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        targetDim = 0.42f,
        nodeBackground = scheme.surface,
        selectionRing = scheme.primary,
        outlier = WarmError,
        hint = scheme.onSurfaceVariant
    )
}

@Composable
fun SessionTimeSeriesDualLineChart(
    series: TrendSeries,
    selectedPoint: TrendPoint?,
    onPointSelected: (TrendPoint?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    val sysPath = remember { Path() }
    val diaPath = remember { Path() }
    val haptics = LocalHapticFeedback.current

    // 默认视野：聚焦真实有数据的区间。范围切换或跨零点才重置，
    // 同一天内新增记录不会把用户已经缩放/平移过的视野拉回去。
    LaunchedEffect(series.range, series.rangeStart, series.rangeEnd) {
        val (defaultStart, defaultEnd) = TrendChartMath.defaultViewport(
            points = points,
            range = series.range,
            windowStart = series.rangeStart,
            windowEndInclusive = series.rangeEnd
        )
        viewport.reset(defaultStart, defaultEnd)
        isInteracting = false
    }

    val viewportStart = viewport.startMillis
    val viewportEnd = viewport.endMillis
    val visiblePoints = remember(points, viewportStart, viewportEnd) {
        TrendChartMath.visiblePoints(points, viewportStart, viewportEnd)
    }
    val maxDrawPoints = remember(canvasSize.width) {
        (canvasSize.width / 2).coerceIn(MIN_DRAW_POINTS, MAX_DRAW_POINTS)
    }
    val renderPoints = remember(visiblePoints, maxDrawPoints) {
        TrendChartMath.sampleShared(visiblePoints, maxDrawPoints)
    }
    val maxTicks = remember(canvasSize.width, density) {
        TrendChartMath.maxTickCount((canvasSize.width / density.density - 74f).roundToInt())
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
    val axisLabelLayouts = axisTicks.map { tick ->
        textMeasurer.measure(
            text = listOfNotNull(tick.primary, tick.secondary).joinToString("\n"),
            style = axisLabelStyle,
            softWrap = false
        )
    }
    val axisBottomPadding = with(density) {
        // 始终预留两行，缩放切换小时/日期刻度时绘图区和手势坐标保持不变。
        textMeasurer.measure("00:00\n00-00", axisLabelStyle, softWrap = false).size.height +
            16.dp.toPx()
    }
    val referenceLabels = buildReferenceLabels(
        yAxis = series.yAxis,
        showSystolic = showSystolic,
        showDiastolic = showDiastolic
    ).map { reference ->
        RenderedReferenceLabel(
            value = reference.value,
            layout = textMeasurer.measure(
                reference.label,
                TextStyle(color = palette.reference, fontSize = 10.sp),
                softWrap = false
            )
        )
    }
    val targetSystolicLabel = targetSystolic
        ?.takeIf { showSystolic && it in series.yAxis.min..series.yAxis.max }
        ?.let { "目标收缩压 $it" }
    val targetDiastolicLabel = targetDiastolic
        ?.takeIf { showDiastolic && it in series.yAxis.min..series.yAxis.max }
        ?.let { "目标舒张压 $it" }
    val chartDescription = remember(series, showSystolic, showDiastolic) {
        buildChartDescription(series, showSystolic, showDiastolic)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .background(palette.nodeBackground, RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = chartDescription }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val currentGeometry = ChartGeometry.create(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    density = density.density,
                    bottomPadding = axisBottomPadding
                )
                val scaler = ChartScaler(
                    geometry = currentGeometry,
                    startMillis = viewport.startMillis,
                    endMillis = viewport.endMillis,
                    yAxis = series.yAxis
                )
                drawYAxisGrid(
                    currentGeometry, scaler, series.yAxis, textMeasurer, palette
                )
                drawReferenceLines(
                    geometry = currentGeometry,
                    scaler = scaler,
                    yAxis = series.yAxis,
                    showSystolic = showSystolic,
                    showDiastolic = showDiastolic,
                    labels = referenceLabels,
                    palette = palette
                )
                targetSystolicLabel?.let { label ->
                    drawTargetLine(
                        y = scaler.yOf(targetSystolic),
                        geometry = currentGeometry,
                        label = label,
                        color = palette.systolic.copy(alpha = palette.targetDim),
                        textMeasurer = textMeasurer
                    )
                }
                targetDiastolicLabel?.let { label ->
                    drawTargetLine(
                        y = scaler.yOf(targetDiastolic),
                        geometry = currentGeometry,
                        label = label,
                        color = palette.diastolic.copy(alpha = palette.targetDim),
                        textMeasurer = textMeasurer
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points, series.range, density, axisBottomPadding) {
                        var lastTapAt = 0L
                        var lastTapPosition = Offset(-10_000f, -10_000f)

                        // 按 x 坐标吸附到最近的可见数据点：单击图表任意位置都能选中，
                        // 不再要求必须点中折线或圆点。
                        fun snapToNearestX(x: Float) {
                            val currentGeometry =
                                ChartGeometry.create(size, density.density, axisBottomPadding)
                            val clampedX = x.coerceIn(currentGeometry.left, currentGeometry.right)
                            val time = TrendChartMath.timeAtX(
                                x = clampedX,
                                left = currentGeometry.left,
                                right = currentGeometry.right,
                                start = viewport.startMillis,
                                end = viewport.endMillis
                            )
                            val visible = TrendChartMath.visiblePoints(
                                points,
                                viewport.startMillis,
                                viewport.endMillis
                            )
                            TrendChartMath.nearestPoint(visible, time)?.let { onPointSelected(it, true) }
                        }

                        fun applyTransform(
                            event: androidx.compose.ui.input.pointer.PointerEvent,
                            zoom: Float,
                            pan: Offset
                        ) {
                            val currentGeometry =
                                ChartGeometry.create(size, density.density, axisBottomPadding)
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val focusMillis = TrendChartMath.timeAtX(
                                x = centroid.x,
                                left = currentGeometry.left,
                                right = currentGeometry.right,
                                start = viewport.startMillis,
                                end = viewport.endMillis
                            )
                            viewport.zoomBy(
                                zoomChange = zoom,
                                focusMillis = focusMillis,
                                seriesStart = series.rangeStart,
                                seriesEnd = series.rangeEnd,
                                minSpanMillis = TrendChartMath.minViewportSpan(series.range)
                            )
                            val deltaMillis = (
                                -pan.x /
                                    (currentGeometry.right - currentGeometry.left).coerceAtLeast(1f) *
                                    viewport.spanMillis
                                ).roundToLong()
                            viewport.panBy(deltaMillis, series.rangeStart, series.rangeEnd)
                            event.changes.forEach { it.consume() }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var mode = ChartGestureMode.PENDING
                            var accumulatedMovement = 0f
                            val tapPosition = down.position
                            val downAt = SystemClock.uptimeMillis()
                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                            while (true) {
                                val event = if (mode == ChartGestureMode.PENDING) {
                                    val remaining =
                                        longPressTimeout - (SystemClock.uptimeMillis() - downAt)
                                    if (remaining <= 0) {
                                        null
                                    } else {
                                        withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                    }
                                } else {
                                    awaitPointerEvent()
                                }

                                if (event == null) {
                                    // 长按达时：唤出十字指针（股票 App 式查看）。
                                    mode = ChartGestureMode.CROSSHAIR
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    snapToNearestX(tapPosition.x)
                                    continue
                                }

                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                when (mode) {
                                    ChartGestureMode.PENDING -> {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        accumulatedMovement += pan.getDistance()
                                        if (event.changes.size > 1 ||
                                            abs(zoom - 1f) > 0.005f ||
                                            accumulatedMovement > viewConfiguration.touchSlop
                                        ) {
                                            mode = ChartGestureMode.TRANSFORM
                                            isInteracting = true
                                            applyTransform(event, zoom, pan)
                                        }
                                    }

                                    ChartGestureMode.TRANSFORM -> {
                                        applyTransform(
                                            event,
                                            event.calculateZoom(),
                                            event.calculatePan()
                                        )
                                    }

                                    ChartGestureMode.CROSSHAIR -> {
                                        snapToNearestX(pressed.first().position.x)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }

                            when (mode) {
                                ChartGestureMode.TRANSFORM -> isInteracting = false
                                // 松手后保留最后吸附的数据点，方便逐点读数。
                                ChartGestureMode.CROSSHAIR -> Unit
                                ChartGestureMode.PENDING -> {
                                    val now = SystemClock.uptimeMillis()
                                    val isDoubleTap =
                                        now - lastTapAt in viewConfiguration.doubleTapMinTimeMillis..
                                            viewConfiguration.doubleTapTimeoutMillis &&
                                            (tapPosition - lastTapPosition).getDistance() <=
                                            viewConfiguration.touchSlop * 2f
                                    if (isDoubleTap) {
                                        viewport.reset(
                                            viewport.defaultStartMillis,
                                            viewport.defaultEndMillis
                                        )
                                        onPointSelected(null, false)
                                        lastTapAt = 0L
                                    } else {
                                        val currentGeometry = ChartGeometry.create(
                                            size, density.density, axisBottomPadding
                                        )
                                        val tapTime = TrendChartMath.timeAtX(
                                            x = tapPosition.x,
                                            left = currentGeometry.left,
                                            right = currentGeometry.right,
                                            start = viewport.startMillis,
                                            end = viewport.endMillis
                                        )
                                        val tapped = TrendChartMath.nearestPoint(
                                            TrendChartMath.visiblePoints(
                                                points,
                                                viewport.startMillis,
                                                viewport.endMillis
                                            ),
                                            tapTime
                                        )
                                        onPointSelected(tapped, tapped != null)
                                        lastTapAt = now
                                        lastTapPosition = tapPosition
                                    }
                                }
                            }
                        }
                    }
            ) {
                val currentGeometry = ChartGeometry.create(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    density = density.density,
                    bottomPadding = axisBottomPadding
                )
                val scaler = ChartScaler(
                    geometry = currentGeometry,
                    startMillis = viewport.startMillis,
                    endMillis = viewport.endMillis,
                    yAxis = series.yAxis
                )

                if (!isInteracting && showSystolic) {
                    drawSeriesLine(renderPoints, scaler, palette.systolic, systolic = true, path = sysPath)
                }
                if (!isInteracting && showDiastolic) {
                    drawSeriesLine(renderPoints, scaler, palette.diastolic, systolic = false, path = diaPath)
                }

                val selectedVisible = selectedPoint?.takeIf { point ->
                    point.timestamp in viewport.startMillis..viewport.endMillis
                }
                selectedVisible?.let { point ->
                    val x = scaler.xOf(point.timestamp)
                    // 选中辅助线：细实线 + 顶端刻度，弱于数据折线。
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

                if (!isInteracting) {
                    val nodeSpacing = (currentGeometry.right - currentGeometry.left) /
                        renderPoints.size.coerceAtLeast(1)
                    // 节点少时才画小节点；密集时只突出选中点，避免满屏空心圆。
                    val showAllNodes = renderPoints.size <= MAX_NODE_POINTS &&
                        nodeSpacing >= MIN_NODE_SPACING_PX
                    renderPoints.forEach { point ->
                        val isPointSelected = selectedPoint?.id == point.id
                        if (!showAllNodes && !isPointSelected) return@forEach
                        val x = scaler.xOf(point.timestamp)
                        if (showSystolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOf(point.systolic),
                                color = valueColor(point.systolic, palette.systolic, palette.outlier),
                                backgroundColor = palette.nodeBackground,
                                ringColor = palette.selectionRing,
                                selected = isPointSelected
                            )
                        }
                        if (showDiastolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOf(point.diastolic),
                                color = valueColor(point.diastolic, palette.diastolic, palette.outlier),
                                backgroundColor = palette.nodeBackground,
                                ringColor = palette.selectionRing,
                                selected = isPointSelected
                            )
                        }
                    }
                }

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

private data class ReferenceLabel(val value: Int, val label: String)

/** 参考线数值 + 已测量好的标签文本。 */
private data class RenderedReferenceLabel(val value: Int, val layout: TextLayoutResult)

/** 只显示与当前可见指标相关的固定参考线。 */
private fun buildReferenceLabels(
    yAxis: TrendYAxis,
    showSystolic: Boolean,
    showDiastolic: Boolean
): List<ReferenceLabel> = buildList {
    if (showSystolic && TrendSeriesCalculator.REFERENCE_SYSTOLIC in yAxis.min..yAxis.max) {
        add(
            ReferenceLabel(
                value = TrendSeriesCalculator.REFERENCE_SYSTOLIC,
                label = "收缩压参考 ${TrendSeriesCalculator.REFERENCE_SYSTOLIC}"
            )
        )
    }
    if (showDiastolic && TrendSeriesCalculator.REFERENCE_DIASTOLIC in yAxis.min..yAxis.max) {
        add(
            ReferenceLabel(
                value = TrendSeriesCalculator.REFERENCE_DIASTOLIC,
                label = "舒张压参考 ${TrendSeriesCalculator.REFERENCE_DIASTOLIC}"
            )
        )
    }
}

private fun buildHint(series: TrendSeries): String {
    val daily = series.aggregation == TrendAggregation.DAILY
    val detail = if (daily) {
        "每日平均；选中节点后可查看当日原始记录"
    } else {
        "每次测量"
    }
    return "单击任意位置自动吸附最近数据点，长按滑动连续查看，双指缩放，双击恢复默认视野 · $detail"
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
        "样本区间 $first 至 $last。可双击恢复默认视野，长按滑动逐点读数。"
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

private data class ChartGeometry(
    val width: Float,
    val height: Float,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float
) {
    companion object {
        fun create(size: IntSize, density: Float, bottomPadding: Float): ChartGeometry {
            val width = size.width.toFloat().coerceAtLeast(1f)
            val height = size.height.toFloat().coerceAtLeast(1f)
            return ChartGeometry(
                width = width,
                height = height,
                left = 38f * density,
                right = (width - 14f * density).coerceAtLeast(39f * density),
                top = 20f * density,
                bottom = (height - bottomPadding).coerceAtLeast(21f * density)
            )
        }
    }
}

private class ChartScaler(
    private val geometry: ChartGeometry,
    private val startMillis: Long,
    private val endMillis: Long,
    private val yAxis: TrendYAxis
) {
    fun xOf(timestamp: Long): Float {
        return TrendChartMath.xOfTime(
            timestamp = timestamp,
            left = geometry.left,
            right = geometry.right,
            start = startMillis,
            end = endMillis
        )
    }

    fun yOf(value: Int): Float {
        val range = (yAxis.max - yAxis.min).coerceAtLeast(1)
        val safeValue = value.coerceIn(yAxis.min, yAxis.max)
        val ratio = (safeValue - yAxis.min).toFloat() / range.toFloat()
        return geometry.bottom - ratio * (geometry.bottom - geometry.top)
    }
}

private fun DrawScope.drawYAxisGrid(
    geometry: ChartGeometry,
    scaler: ChartScaler,
    yAxis: TrendYAxis,
    textMeasurer: TextMeasurer,
    palette: TrendChartPalette
) {
    // 只有 5–7 条主刻度（tickStep 由 TrendSeriesCalculator 的漂亮步长决定）。
    TrendSeriesCalculator.tickValues(yAxis).forEach { value ->
        val y = scaler.yOf(value)
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

private fun DrawScope.drawReferenceLines(
    geometry: ChartGeometry,
    scaler: ChartScaler,
    yAxis: TrendYAxis,
    showSystolic: Boolean,
    showDiastolic: Boolean,
    labels: List<RenderedReferenceLabel>,
    palette: TrendChartPalette
) {
    val visibleReferences = buildReferenceLabels(yAxis, showSystolic, showDiastolic)
        .mapNotNull { reference ->
            val layout = labels.firstOrNull { it.value == reference.value }?.layout
                ?: return@mapNotNull null
            reference.value to layout
        }
    // 两条固定参考线在极端跨度下可能贴近，标签需要上下错开避免叠字。
    var previousLabelTop = Float.NEGATIVE_INFINITY
    visibleReferences.forEach { (value, layout) ->
        val y = scaler.yOf(value)
        drawLine(
            color = palette.reference,
            start = Offset(geometry.left, y),
            end = Offset(geometry.right, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        )

        // 标签固定在右端：既不压住左侧目标线文字，也不打断折线起点。
        val left = (geometry.right - 8.dp.toPx() - layout.size.width)
            .coerceAtLeast(geometry.left)
        val overlapOffset = if (y - layout.size.height / 2f - previousLabelTop <
            layout.size.height + 2.dp.toPx()
        ) {
            layout.size.height + 2.dp.toPx()
        } else {
            0f
        }
        val top = (y - layout.size.height / 2f + overlapOffset)
            .coerceIn(geometry.top - 6.dp.toPx(), (geometry.bottom - layout.size.height).coerceAtLeast(0f))
        previousLabelTop = top
        // 半透明衬底把标签与折线、节点隔开，保证文字可读。
        drawRoundRect(
            color = palette.nodeBackground.copy(alpha = 0.86f),
            topLeft = Offset(left - 4.dp.toPx(), top - 1.dp.toPx()),
            size = Size(
                layout.size.width + 8.dp.toPx(),
                layout.size.height + 2.dp.toPx()
            ),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawText(textLayoutResult = layout, topLeft = Offset(left, top))
    }
}

private fun DrawScope.drawTargetLine(
    y: Float,
    geometry: ChartGeometry,
    label: String,
    color: Color,
    textMeasurer: TextMeasurer
) {
    drawLine(
        color = color,
        start = Offset(geometry.left, y),
        end = Offset(geometry.right, y),
        strokeWidth = 1.2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
    )
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(geometry.left + 6f, y - 17f),
        style = TextStyle(color = color, fontSize = 10.sp)
    )
}

private fun DrawScope.drawSeriesLine(
    points: List<TrendPoint>,
    scaler: ChartScaler,
    color: Color,
    systolic: Boolean,
    path: Path
) {
    path.reset()
    if (points.size < 2) return
    points.forEachIndexed { index, point ->
        val offset = Offset(
            scaler.xOf(point.timestamp),
            scaler.yOf(if (systolic) point.systolic else point.diastolic)
        )
        // 大于约 2 天的缺测保持断线，不跨缺口直连。
        if (index == 0 || point.timestamp - points[index - 1].timestamp > GAP_MILLIS) {
            path.moveTo(offset.x, offset.y)
        } else {
            val previous = points[index - 1]
            val previousX = scaler.xOf(previous.timestamp)
            val previousY = scaler.yOf(if (systolic) previous.systolic else previous.diastolic)
            val controlX = (previousX + offset.x) / 2f
            path.cubicTo(controlX, previousY, controlX, offset.y, offset.x, offset.y)
        }
    }
    drawPath(
        path,
        color,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawTimeAxisLabels(
    ticks: List<TrendTimeTick>,
    layouts: List<TextLayoutResult>,
    scaler: ChartScaler,
    geometry: ChartGeometry,
    palette: TrendChartPalette
) {
    val centers = ticks.map { scaler.xOf(it.timestamp) }
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

private val CHART_HEIGHT = 252.dp
private const val MIN_DRAW_POINTS = 60
private const val MAX_DRAW_POINTS = 900
private const val MIN_NODE_SPACING_PX = 14f
private const val MAX_NODE_POINTS = 60
private const val GAP_MILLIS = 2L * 24L * 60L * 60L * 1000L
