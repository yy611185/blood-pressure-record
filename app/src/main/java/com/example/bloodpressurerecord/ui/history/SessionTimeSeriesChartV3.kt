package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.bloodpressurerecord.data.repository.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private data class SelectedChartPoint(
    val index: Int,
    val systolicSelected: Boolean
)

@Composable
fun SessionTimeSeriesDualLineChart(
    sessions: List<SessionRecord>,
    modifier: Modifier = Modifier,
    targetSystolic: Int? = null,
    targetDiastolic: Int? = null,
    showSystolic: Boolean = true,
    showDiastolic: Boolean = true,
    emptyTitle: String = "暂无趋势数据",
    averageLabel: String = "平均",
    defaultVisibleCount: Int = 30
) {
    val points = remember(sessions) {
        sessions.sortedBy { it.measuredAt }.map(::trendChartPointFromSession)
    }
    if (points.isEmpty()) {
        TrendEmptyState(title = emptyTitle, modifier = modifier)
        return
    }

    val avgSystolic = points.map { it.systolic }.average().roundToInt()
    val avgDiastolic = points.map { it.diastolic }.average().roundToInt()
    val viewport = remember(points) { TrendChartViewportState() }
    var selected by remember(points) { mutableStateOf<SelectedChartPoint?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val sysPath = remember { Path() }
    val diaPath = remember { Path() }

    LaunchedEffect(points, defaultVisibleCount) {
        viewport.reset(points.size, defaultVisibleCount)
        selected = null
        isInteracting = false
        interactionTick = 0
    }

    LaunchedEffect(interactionTick) {
        if (isInteracting) {
            delay(110)
            isInteracting = false
        }
    }

    val visibleStart by remember { derivedStateOf { viewport.visibleStart() } }
    val visibleEnd by remember { derivedStateOf { viewport.visibleEnd() } }
    val visibleIndexed by remember(points) {
        derivedStateOf { TrendChartMath.visiblePoints(points, visibleStart, visibleEnd) }
    }
    val visiblePoints by remember {
        derivedStateOf { visibleIndexed.map { it.value } }
    }
    val singleDayMode by remember(points) {
        derivedStateOf { TrendChartMath.isSingleDayMode(points, visibleStart, visibleEnd) }
    }
    val yRange by remember {
        derivedStateOf { TrendChartMath.yRange(visiblePoints, showSystolic, showDiastolic) }
    }
    val axisLabels by remember(points) {
        derivedStateOf { TrendChartMath.axisLabels(points, visibleStart, visibleEnd, singleDayMode) }
    }
    val maxDrawnPoints by remember {
        derivedStateOf { if (isInteracting) MAX_INTERACTION_LINE_POINTS else MAX_DRAWN_LINE_POINTS }
    }
    val drawnIndexed by remember {
        derivedStateOf { visibleIndexed.sampleForDrawing(maxDrawnPoints) }
    }
    val selectedPosition = remember(selected, canvasSize, visibleStart, visibleEnd, yRange) {
        selected?.let { selectedPoint ->
            if (canvasSize.width <= 0 || canvasSize.height <= 0) return@let null
            val geometry = ChartGeometry(canvasSize.width.toFloat(), canvasSize.height.toFloat())
            val scaler = ChartScaler(geometry, visibleStart, visibleEnd, yRange)
            val point = points.getOrNull(selectedPoint.index) ?: return@let null
            Offset(
                x = scaler.xOfIndex(selectedPoint.index),
                y = scaler.yOf(if (selectedPoint.systolicSelected) point.systolic else point.diastolic)
            )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${averageLabel}收缩压 $avgSystolic mmHg", style = MaterialTheme.typography.bodySmall)
            Text("${averageLabel}舒张压 $avgDiastolic mmHg", style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem("收缩压", SYS_COLOR)
            LegendItem("舒张压", DIA_COLOR)
            Text(
                text = if (singleDayMode) "单日细节" else "趋势概览",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .background(Color.White, RoundedCornerShape(18.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(points, viewport.visibleSpan, viewport.centerIndex, showSystolic, showDiastolic) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val geometry = ChartGeometry(size.width.toFloat(), size.height.toFloat())
                            val focusIndex = TrendChartMath.targetIndexFromX(
                                x = centroid.x,
                                left = geometry.left,
                                right = geometry.right,
                                visibleStart = viewport.visibleStart(),
                                visibleEnd = viewport.visibleEnd()
                            )
                            viewport.zoomBy(zoom, focusIndex, points.size)
                            val plotWidth = (geometry.right - geometry.left).coerceAtLeast(1f)
                            viewport.panBy((-pan.x / plotWidth) * viewport.visibleSpan, points.size)
                            if (!isInteracting) isInteracting = true
                            interactionTick += 1
                        }
                    }
                    .pointerInput(points, viewport.visibleSpan, viewport.centerIndex, showSystolic, showDiastolic) {
                        detectTapGestures(
                            onDoubleTap = {
                                viewport.reset(points.size, defaultVisibleCount)
                                selected = null
                            },
                            onTap = { tap ->
                                val geometry = ChartGeometry(size.width.toFloat(), size.height.toFloat())
                                val currentVisible = TrendChartMath.visiblePoints(points, viewport.visibleStart(), viewport.visibleEnd())
                                val currentYRange = TrendChartMath.yRange(currentVisible.map { it.value }, showSystolic, showDiastolic)
                                val scaler = ChartScaler(geometry, viewport.visibleStart(), viewport.visibleEnd(), currentYRange)
                                selected = currentVisible.mapNotNull { indexed ->
                                    val point = indexed.value
                                    val x = scaler.xOfIndex(indexed.index)
                                    val sysDistance = if (showSystolic) (tap - Offset(x, scaler.yOf(point.systolic))).getDistance() else Float.MAX_VALUE
                                    val diaDistance = if (showDiastolic) (tap - Offset(x, scaler.yOf(point.diastolic))).getDistance() else Float.MAX_VALUE
                                    val best = min(sysDistance, diaDistance)
                                    if (best <= HIT_RADIUS_PX) SelectedChartPoint(indexed.index, sysDistance <= diaDistance) to best else null
                                }.minByOrNull { it.second }?.first
                            }
                        )
                    }
            ) {
                val geometry = ChartGeometry(size.width, size.height)
                val scaler = ChartScaler(geometry, visibleStart, visibleEnd, yRange)

                drawYAxisGrid(geometry, scaler, yRange, textMeasurer, showLabels = !isInteracting)
                if (!isInteracting) {
                    drawReferenceLines(geometry, scaler, yRange, textMeasurer)
                    targetSystolic?.takeIf { it in yRange.min..yRange.max }?.let {
                        drawTargetLine(scaler.yOf(it), geometry, "目标收缩压 $it", SYS_COLOR, textMeasurer)
                    }
                    targetDiastolic?.takeIf { it in yRange.min..yRange.max }?.let {
                        drawTargetLine(scaler.yOf(it), geometry, "目标舒张压 $it", DIA_COLOR, textMeasurer)
                    }
                }

                selected?.let { selectedPoint ->
                    if (!isInteracting && selectedPoint.index.toFloat() in visibleStart..visibleEnd) {
                        val x = scaler.xOfIndex(selectedPoint.index)
                        drawLine(
                            color = Color(0x8894A3B8),
                            start = Offset(x, geometry.top),
                            end = Offset(x, geometry.bottom),
                            strokeWidth = 1.4f
                        )
                    }
                }

                if (showSystolic) drawSeriesLine(drawnIndexed, scaler, SYS_COLOR, systolic = true, path = sysPath)
                if (showDiastolic) drawSeriesLine(drawnIndexed, scaler, DIA_COLOR, systolic = false, path = diaPath)

                if (!isInteracting || visibleIndexed.size <= 28) {
                    visibleIndexed.forEach { indexed ->
                        val point = indexed.value
                        val x = scaler.xOfIndex(indexed.index)
                        val isSelected = selected?.index == indexed.index
                        if (showSystolic) {
                            drawPointNode(x, scaler.yOf(point.systolic), SYS_COLOR, isSelected && selected?.systolicSelected == true)
                        }
                        if (showDiastolic) {
                            drawPointNode(x, scaler.yOf(point.diastolic), DIA_COLOR, isSelected && selected?.systolicSelected == false)
                        }
                    }
                }

                if (!isInteracting) {
                    drawAxisLabels(axisLabels, scaler, geometry, textMeasurer)
                }
            }

            if (!isInteracting) {
                ChartTooltip(
                    selected = selected,
                    points = points,
                    selectedPosition = selectedPosition,
                    canvasSize = canvasSize,
                    density = density
                )
            }
        }

        Text(
            "轻触图表节点查看详细数值",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun ChartTooltip(
    selected: SelectedChartPoint?,
    points: List<TrendChartPoint>,
    selectedPosition: Offset?,
    canvasSize: IntSize,
    density: androidx.compose.ui.unit.Density
) {
    val point = selected?.let { points.getOrNull(it.index) } ?: return
    val position = selectedPosition ?: return
    Card(
        modifier = Modifier.offset {
            val maxX = (canvasSize.width - with(density) { 178.dp.toPx() }.roundToInt()).coerceAtLeast(8)
            IntOffset(
                x = position.x.roundToInt().coerceIn(8, maxX),
                y = (position.y - 118f).roundToInt().coerceAtLeast(8)
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatFullDate(point.timestamp), fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(formatFullTime(point.timestamp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            TooltipValue("收缩压", "${point.systolic} mmHg", SYS_COLOR)
            TooltipValue("舒张压", "${point.diastolic} mmHg", DIA_COLOR)
            Text("脉搏 ${point.pulse?.toString() ?: "--"} bpm", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            Text("分级 ${point.level.toChineseCategoryLabel()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
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

private val CHART_HEIGHT = 256.dp
private val SYS_COLOR = Color(0xFF3B82F6)
private val DIA_COLOR = Color(0xFF2DD4BF)
private val GRID_COLOR = Color(0xFFE2E8F0)
private val AXIS_COLOR = Color(0xFF94A3B8)
private val REFERENCE_COLOR = Color(0xFFFDBA74)
private const val MAX_DRAWN_LINE_POINTS = 90
private const val MAX_INTERACTION_LINE_POINTS = 48
private const val HIT_RADIUS_PX = 30f

private fun List<IndexedValue<TrendChartPoint>>.sampleForDrawing(maxPoints: Int): List<IndexedValue<TrendChartPoint>> {
    if (size <= maxPoints) return this
    val step = ceil(size / maxPoints.toFloat()).toInt().coerceAtLeast(1)
    return filterIndexed { index, _ -> index == 0 || index == lastIndex || index % step == 0 }
}

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
            Text("保持每天固定时间记录，积累两条以上记录后即可查看趋势。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
        }
    }
}

private data class ChartGeometry(
    val width: Float,
    val height: Float,
    val left: Float = 42f,
    val right: Float = width - 10f,
    val top: Float = 16f,
    val bottom: Float = height - 38f
)

private class ChartScaler(
    private val geometry: ChartGeometry,
    private val visibleStart: Float,
    private val visibleEnd: Float,
    private val yRange: TrendChartYRange
) {
    fun xOfIndex(index: Int): Float {
        val span = (visibleEnd - visibleStart).coerceAtLeast(0.001f)
        val ratio = ((index - visibleStart) / span).coerceIn(-0.2f, 1.2f)
        return geometry.left + ratio * (geometry.right - geometry.left)
    }

    fun yOf(value: Int): Float {
        val range = (yRange.max - yRange.min).coerceAtLeast(1)
        val ratio = (value - yRange.min).toFloat() / range.toFloat()
        return geometry.bottom - ratio * (geometry.bottom - geometry.top)
    }
}

private fun DrawScope.drawYAxisGrid(
    geometry: ChartGeometry,
    scaler: ChartScaler,
    yRange: TrendChartYRange,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    showLabels: Boolean
) {
    val step = when {
        yRange.max - yRange.min <= 35 -> 10
        yRange.max - yRange.min <= 70 -> 15
        else -> 20
    }
    val first = ((yRange.min + step - 1) / step) * step
    generateSequence(first) { it + step }
        .takeWhile { it <= yRange.max }
        .forEach { value ->
            val y = scaler.yOf(value)
            drawLine(
                color = GRID_COLOR,
                start = Offset(geometry.left, y),
                end = Offset(geometry.right, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
            )
            if (showLabels) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = value.toString(),
                    topLeft = Offset(8f, y - 8f),
                    style = TextStyle(color = AXIS_COLOR, fontSize = 10.sp)
                )
            }
        }
}

private fun DrawScope.drawReferenceLines(
    geometry: ChartGeometry,
    scaler: ChartScaler,
    yRange: TrendChartYRange,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    listOf(140, 90)
        .filter { it in yRange.min..yRange.max }
        .forEach { ref ->
            val y = scaler.yOf(ref)
            drawLine(
                color = REFERENCE_COLOR,
                start = Offset(geometry.left, y),
                end = Offset(geometry.right, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
            )
            drawText(
                textMeasurer = textMeasurer,
                text = ref.toString(),
                topLeft = Offset(geometry.right - 22f, y - 11f),
                style = TextStyle(color = REFERENCE_COLOR, fontSize = 10.sp)
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
        color = color.copy(alpha = 0.45f),
        start = Offset(geometry.left, y),
        end = Offset(geometry.right, y),
        strokeWidth = 1.4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
    )
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(geometry.left + 8f, y - 18f),
        style = TextStyle(color = color, fontSize = 10.sp)
    )
}

private fun DrawScope.drawSeriesLine(
    points: List<IndexedValue<TrendChartPoint>>,
    scaler: ChartScaler,
    color: Color,
    systolic: Boolean,
    path: Path
) {
    if (points.isEmpty()) return
    path.reset()
    val offsets = points.map { indexed ->
        val point = indexed.value
        Offset(scaler.xOfIndex(indexed.index), scaler.yOf(if (systolic) point.systolic else point.diastolic))
    }
    offsets.forEachIndexed { index, offset ->
        if (index == 0) {
            path.moveTo(offset.x, offset.y)
        } else {
            val previous = offsets[index - 1]
            val midX = (previous.x + offset.x) / 2f
            path.cubicTo(midX, previous.y, midX, offset.y, offset.x, offset.y)
        }
    }
    drawPath(path, color, style = Stroke(width = 3f))
}

private fun DrawScope.drawAxisLabels(
    labels: List<TrendAxisLabel>,
    scaler: ChartScaler,
    geometry: ChartGeometry,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    labels.forEach { label ->
        val x = scaler.xOfIndex(label.index)
        drawText(
            textMeasurer = textMeasurer,
            text = label.primary,
            topLeft = Offset((x - 16f).coerceIn(geometry.left - 8f, geometry.right - 30f), geometry.bottom + 10f),
            style = TextStyle(color = AXIS_COLOR, fontSize = 10.sp)
        )
        label.secondary?.let {
            drawText(
                textMeasurer = textMeasurer,
                text = it,
                topLeft = Offset((x - 16f).coerceIn(geometry.left - 8f, geometry.right - 30f), geometry.bottom + 22f),
                style = TextStyle(color = AXIS_COLOR.copy(alpha = 0.72f), fontSize = 9.sp)
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
    drawCircle(color = Color.White, radius = if (selected) 6.6f else 4.8f, center = Offset(x, y))
    drawCircle(
        color = color,
        radius = if (selected) 6.6f else 4.8f,
        center = Offset(x, y),
        style = Stroke(width = if (selected) 2.8f else 2f)
    )
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
