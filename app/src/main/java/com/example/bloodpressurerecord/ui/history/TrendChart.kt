package com.example.bloodpressurerecord.ui.history

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private data class SelectedChartPoint(
    val point: TrendPoint,
    val systolicSelected: Boolean
)

@Composable
fun SessionTimeSeriesDualLineChart(
    series: TrendSeries,
    modifier: Modifier = Modifier,
    targetSystolic: Int? = null,
    targetDiastolic: Int? = null,
    showSystolic: Boolean = true,
    showDiastolic: Boolean = true,
    emptyTitle: String = "暂无趋势数据",
    averageLabel: String = "平均",
    onPointActivated: (TrendPoint) -> Unit = {}
) {
    val points = series.points
    if (points.isEmpty()) {
        TrendEmptyState(title = emptyTitle, modifier = modifier)
        return
    }

    val density = LocalDensity.current
    val zoneId = remember { ZoneId.systemDefault() }
    val textMeasurer = rememberTextMeasurer()
    val viewport = remember(series.range, series.rangeStart, series.rangeEnd) {
        TrendTimeViewportState()
    }
    var selected by remember(points) { mutableStateOf<SelectedChartPoint?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    val sysPath = remember { Path() }
    val diaPath = remember { Path() }

    LaunchedEffect(series.range, series.rangeStart, series.rangeEnd) {
        viewport.reset(series.rangeStart, series.rangeEnd)
        selected = null
        isInteracting = false
    }

    val visiblePoints by remember(points) {
        derivedStateOf {
            TrendChartMath.visiblePoints(points, viewport.startMillis, viewport.endMillis)
        }
    }
    val maxDrawPoints by remember {
        derivedStateOf {
            (canvasSize.width / 2).coerceIn(MIN_DRAW_POINTS, MAX_DRAW_POINTS)
        }
    }
    val drawnPoints by remember {
        derivedStateOf {
            TrendChartMath.sampleShared(visiblePoints, maxDrawPoints)
        }
    }
    val axisTicks by remember {
        derivedStateOf {
            TrendChartMath.timeTicks(
                startMillis = viewport.startMillis,
                endMillis = viewport.endMillis,
                zoneId = zoneId
            )
        }
    }
    val geometry = remember(canvasSize, density) {
        ChartGeometry.create(canvasSize, density.density)
    }
    val selectedPosition = remember(
        selected,
        geometry,
        viewport.startMillis,
        viewport.endMillis,
        series.yAxis
    ) {
        selected?.let {
            val scaler = ChartScaler(
                geometry = geometry,
                startMillis = viewport.startMillis,
                endMillis = viewport.endMillis,
                yAxis = series.yAxis
            )
            Offset(
                x = scaler.xOf(it.point.timestamp),
                y = scaler.yOf(if (it.systolicSelected) it.point.systolic else it.point.diastolic)
            )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "$averageLabel 收缩压 ${series.averageSystolic ?: "--"} mmHg",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "$averageLabel 舒张压 ${series.averageDiastolic ?: "--"} mmHg",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem("收缩压", SYS_COLOR)
            LegendItem("舒张压", DIA_COLOR)
            Text(
                text = if (series.range == com.example.bloodpressurerecord.domain.model.TrendRange.ALL) {
                    "每日平均"
                } else {
                    "每次测量"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .background(Color.White, RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val currentGeometry = ChartGeometry.create(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    density = density.density
                )
                val scaler = ChartScaler(
                    geometry = currentGeometry,
                    startMillis = viewport.startMillis,
                    endMillis = viewport.endMillis,
                    yAxis = series.yAxis
                )
                drawYAxisGrid(currentGeometry, scaler, series.yAxis, textMeasurer)
                drawReferenceLines(currentGeometry, scaler, series.yAxis, textMeasurer)
                targetSystolic?.takeIf { it in series.yAxis.min..series.yAxis.max }?.let {
                    drawTargetLine(scaler.yOf(it), currentGeometry, "目标收缩压 $it", SYS_COLOR, textMeasurer)
                }
                targetDiastolic?.takeIf { it in series.yAxis.min..series.yAxis.max }?.let {
                    drawTargetLine(scaler.yOf(it), currentGeometry, "目标舒张压 $it", DIA_COLOR, textMeasurer)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points, series.range) {
                        var lastTapAt = 0L
                        var lastTapPosition = Offset(-10_000f, -10_000f)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var transformed = false
                            var accumulatedMovement = 0f
                            val tapPosition = down.position

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                accumulatedMovement += pan.getDistance()
                                if (!transformed &&
                                    (abs(zoom - 1f) > 0.005f || accumulatedMovement > viewConfiguration.touchSlop)
                                ) {
                                    transformed = true
                                    isInteracting = true
                                    selected = null
                                }

                                if (transformed) {
                                    val currentGeometry = ChartGeometry.create(size, density.density)
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
                            }

                            if (transformed) {
                                isInteracting = false
                            } else {
                                val now = SystemClock.uptimeMillis()
                                val isDoubleTap =
                                    now - lastTapAt in viewConfiguration.doubleTapMinTimeMillis..
                                        viewConfiguration.doubleTapTimeoutMillis &&
                                        (tapPosition - lastTapPosition).getDistance() <=
                                        viewConfiguration.touchSlop * 2f
                                if (isDoubleTap) {
                                    viewport.reset(series.rangeStart, series.rangeEnd)
                                    selected = null
                                    lastTapAt = 0L
                                } else {
                                    selected = selectNearestPoint(
                                        tap = tapPosition,
                                        points = visiblePoints,
                                        geometry = ChartGeometry.create(size, density.density),
                                        viewport = viewport,
                                        yAxis = series.yAxis,
                                        showSystolic = showSystolic,
                                        showDiastolic = showDiastolic,
                                        hitRadiusPx = with(density) { HIT_RADIUS.toPx() }
                                    )
                                    selected?.point?.let(onPointActivated)
                                    lastTapAt = now
                                    lastTapPosition = tapPosition
                                }
                            }
                        }
                    }
            ) {
                val currentGeometry = ChartGeometry.create(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    density = density.density
                )
                val scaler = ChartScaler(
                    geometry = currentGeometry,
                    startMillis = viewport.startMillis,
                    endMillis = viewport.endMillis,
                    yAxis = series.yAxis
                )

                selected?.let { selectedPoint ->
                    if (!isInteracting &&
                        selectedPoint.point.timestamp in viewport.startMillis..viewport.endMillis
                    ) {
                        val x = scaler.xOf(selectedPoint.point.timestamp)
                        drawLine(
                            color = Color(0x8894A3B8),
                            start = Offset(x, currentGeometry.top),
                            end = Offset(x, currentGeometry.bottom),
                            strokeWidth = 1.4f
                        )
                    }
                }

                if (showSystolic) {
                    drawSeriesLine(drawnPoints, scaler, SYS_COLOR, systolic = true, path = sysPath)
                }
                if (showDiastolic) {
                    drawSeriesLine(drawnPoints, scaler, DIA_COLOR, systolic = false, path = diaPath)
                }

                val pointSpacing = (currentGeometry.right - currentGeometry.left) /
                    visiblePoints.size.coerceAtLeast(1)
                if (!isInteracting && pointSpacing >= MIN_NODE_SPACING_PX) {
                    visiblePoints.forEach { point ->
                        val x = scaler.xOf(point.timestamp)
                        val isSelected = selected?.point?.id == point.id
                        if (showSystolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOf(point.systolic),
                                color = valueColor(point.systolic, SYS_COLOR),
                                selected = isSelected && selected?.systolicSelected == true
                            )
                        }
                        if (showDiastolic) {
                            drawPointNode(
                                x = x,
                                y = scaler.yOf(point.diastolic),
                                color = valueColor(point.diastolic, DIA_COLOR),
                                selected = isSelected && selected?.systolicSelected == false
                            )
                        }
                    }
                } else if (!isInteracting) {
                    selected?.let { selectedPoint ->
                        val point = selectedPoint.point
                        drawPointNode(
                            x = scaler.xOf(point.timestamp),
                            y = scaler.yOf(
                                if (selectedPoint.systolicSelected) point.systolic else point.diastolic
                            ),
                            color = if (selectedPoint.systolicSelected) {
                                valueColor(point.systolic, SYS_COLOR)
                            } else {
                                valueColor(point.diastolic, DIA_COLOR)
                            },
                            selected = true
                        )
                    }
                }

                if (!isInteracting) {
                    drawTimeAxisLabels(axisTicks, scaler, currentGeometry, textMeasurer)
                }
            }

            if (!isInteracting) {
                ChartTooltip(
                    selected = selected,
                    selectedPosition = selectedPosition,
                    canvasSize = canvasSize,
                    density = density
                )
            }
        }

        Text(
            if (series.range == com.example.bloodpressurerecord.domain.model.TrendRange.ALL) {
                "轻触每日节点查看当天原始记录"
            } else {
                "轻触节点查看详细数值"
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
    }
}

private fun selectNearestPoint(
    tap: Offset,
    points: List<TrendPoint>,
    geometry: ChartGeometry,
    viewport: TrendTimeViewportState,
    yAxis: TrendYAxis,
    showSystolic: Boolean,
    showDiastolic: Boolean,
    hitRadiusPx: Float
): SelectedChartPoint? {
    val tapTime = TrendChartMath.timeAtX(
        tap.x,
        geometry.left,
        geometry.right,
        viewport.startMillis,
        viewport.endMillis
    )
    val point = TrendChartMath.nearestPoint(points, tapTime) ?: return null
    val scaler = ChartScaler(geometry, viewport.startMillis, viewport.endMillis, yAxis)
    val x = scaler.xOf(point.timestamp)
    val sysDistance = if (showSystolic) {
        (tap - Offset(x, scaler.yOf(point.systolic))).getDistance()
    } else {
        Float.MAX_VALUE
    }
    val diaDistance = if (showDiastolic) {
        (tap - Offset(x, scaler.yOf(point.diastolic))).getDistance()
    } else {
        Float.MAX_VALUE
    }
    val best = minOf(sysDistance, diaDistance)
    return if (best <= hitRadiusPx) {
        SelectedChartPoint(point = point, systolicSelected = sysDistance <= diaDistance)
    } else {
        null
    }
}

@Composable
private fun ChartTooltip(
    selected: SelectedChartPoint?,
    selectedPosition: Offset?,
    canvasSize: IntSize,
    density: androidx.compose.ui.unit.Density
) {
    val point = selected?.point ?: return
    val position = selectedPosition ?: return
    Card(
        modifier = Modifier.offset {
            val width = with(density) { 184.dp.toPx() }.roundToInt()
            val maxX = (canvasSize.width - width).coerceAtLeast(8)
            IntOffset(
                x = (position.x - width / 2f).roundToInt().coerceIn(8, maxX),
                y = (position.y - 126f).roundToInt().coerceAtLeast(8)
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatFullDate(point.timestamp), fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            if (point.aggregation == TrendAggregation.RAW) {
                Text(formatFullTime(point.timestamp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            } else {
                Text(
                    "当日 ${point.recordCount} 次测量平均",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
            TooltipValue("收缩压", "${point.systolic} mmHg", valueColor(point.systolic, SYS_COLOR))
            TooltipValue("舒张压", "${point.diastolic} mmHg", valueColor(point.diastolic, DIA_COLOR))
            Text("脉搏 ${point.pulse?.toString() ?: "--"} bpm", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            Text("分级 ${point.category.toChineseCategoryLabel()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun TooltipValue(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(7.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text("$label $value", style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F172A))
    }
}

private val CHART_HEIGHT = 280.dp
private val HIT_RADIUS = 32.dp
private val SYS_COLOR = Color(0xFF3B82F6)
private val DIA_COLOR = Color(0xFF2DD4BF)
private val GRID_COLOR = Color(0xFFE2E8F0)
private val AXIS_COLOR = Color(0xFF94A3B8)
private val REFERENCE_COLOR = Color(0xFFF59E0B)
private val OUTLIER_COLOR = Color(0xFFDC2626)
private const val MIN_DRAW_POINTS = 60
private const val MAX_DRAW_POINTS = 900
private const val MIN_NODE_SPACING_PX = 12f

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text(text, color = Color(0xFF334155), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrendEmptyState(title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("保持每天固定时间记录，积累记录后即可查看趋势。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
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
        fun create(size: IntSize, density: Float): ChartGeometry {
            val width = size.width.toFloat().coerceAtLeast(1f)
            val height = size.height.toFloat().coerceAtLeast(1f)
            return ChartGeometry(
                width = width,
                height = height,
                left = 50f * density,
                right = (width - 24f * density).coerceAtLeast(51f * density),
                top = 18f * density,
                bottom = (height - 44f * density).coerceAtLeast(19f * density)
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
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    var value = ((yAxis.min + yAxis.tickStep - 1) / yAxis.tickStep) * yAxis.tickStep
    while (value <= yAxis.max) {
        val y = scaler.yOf(value)
        drawLine(
            color = GRID_COLOR,
            start = Offset(geometry.left, y),
            end = Offset(geometry.right, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
        )
        drawText(
            textMeasurer = textMeasurer,
            text = value.toString(),
            topLeft = Offset(8f, y - 8f),
            style = TextStyle(color = AXIS_COLOR, fontSize = 10.sp)
        )
        value += yAxis.tickStep
    }
}

private fun DrawScope.drawReferenceLines(
    geometry: ChartGeometry,
    scaler: ChartScaler,
    yAxis: TrendYAxis,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    listOf(140, 90)
        .filter { it in yAxis.min..yAxis.max }
        .forEach { ref ->
            val y = scaler.yOf(ref)
            drawLine(
                color = REFERENCE_COLOR.copy(alpha = 0.75f),
                start = Offset(geometry.left, y),
                end = Offset(geometry.right, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )
            drawText(
                textMeasurer = textMeasurer,
                text = ref.toString(),
                topLeft = Offset(geometry.right + 3f, y - 8f),
                style = TextStyle(color = REFERENCE_COLOR, fontSize = 9.sp)
            )
        }
}

private fun DrawScope.drawTargetLine(
    y: Float,
    geometry: ChartGeometry,
    label: String,
    color: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    drawLine(
        color = color.copy(alpha = 0.42f),
        start = Offset(geometry.left, y),
        end = Offset(geometry.right, y),
        strokeWidth = 1.4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
    )
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(geometry.left + 8f, y - 18f),
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
    if (points.isEmpty()) return
    path.reset()
    points.forEachIndexed { index, point ->
        val offset = Offset(
            scaler.xOf(point.timestamp),
            scaler.yOf(if (systolic) point.systolic else point.diastolic)
        )
        if (index == 0) {
            path.moveTo(offset.x, offset.y)
        } else {
            path.lineTo(offset.x, offset.y)
        }
    }
    drawPath(path, color, style = Stroke(width = 3f))
}

private fun DrawScope.drawTimeAxisLabels(
    ticks: List<TrendTimeTick>,
    scaler: ChartScaler,
    geometry: ChartGeometry,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    ticks.forEach { tick ->
        val x = scaler.xOf(tick.timestamp)
        val primaryStyle = TextStyle(color = AXIS_COLOR, fontSize = 10.sp)
        val primaryLayout = textMeasurer.measure(tick.primary, primaryStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = tick.primary,
            topLeft = Offset(x - primaryLayout.size.width / 2f, geometry.bottom + 10f),
            style = primaryStyle
        )
        tick.secondary?.let { secondary ->
            val secondaryStyle = TextStyle(color = AXIS_COLOR.copy(alpha = 0.72f), fontSize = 9.sp)
            val secondaryLayout = textMeasurer.measure(secondary, secondaryStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = secondary,
                topLeft = Offset(x - secondaryLayout.size.width / 2f, geometry.bottom + 23f),
                style = secondaryStyle
            )
        }
    }
}

private fun DrawScope.drawPointNode(
    x: Float,
    y: Float,
    color: Color,
    selected: Boolean
) {
    val radius = if (selected) 6.6f else 4.8f
    drawCircle(color = Color.White, radius = radius, center = Offset(x, y))
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(x, y),
        style = Stroke(width = if (selected) 2.8f else 2f)
    )
}

private fun valueColor(value: Int, normalColor: Color): Color {
    return if (value in TrendSeriesCalculator.CHART_SAFE_MIN..TrendSeriesCalculator.CHART_SAFE_MAX) {
        normalColor
    } else {
        OUTLIER_COLOR
    }
}

private fun formatFullDate(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

private fun formatFullTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun String.toChineseCategoryLabel(): String = when (uppercase()) {
    "NORMAL" -> "正常"
    "ELEVATED" -> "偏高"
    "STAGE1" -> "1期偏高"
    "STAGE2" -> "2期偏高"
    "SEVERE" -> "重度偏高"
    else -> this
}
