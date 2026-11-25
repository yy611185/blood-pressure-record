package com.example.bloodpressurerecord.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.TrendSeriesCalculator
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendYAxis

/**
 * 图表内所有颜色在这里统一取值，绘制函数不再各写各的常量。
 * 收缩压沿用陶土橙（主色），舒张压沿用蓝色（三级容器前景色）；高风险圆环取 scheme.error 适配深浅色。
 */
@Immutable
internal data class TrendChartPalette(
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
internal fun trendChartPalette(): TrendChartPalette {
    val scheme = MaterialTheme.colorScheme
    return TrendChartPalette(
        systolic = scheme.primary,
        diastolic = scheme.onTertiaryContainer,
        axis = scheme.onSurfaceVariant,
        grid = scheme.onSurfaceVariant.copy(alpha = 0.16f),
        targetDim = 0.68f,
        nodeBackground = scheme.surface,
        selectionRing = scheme.primary,
        outlier = scheme.error,
        hint = scheme.onSurfaceVariant
    )
}

internal sealed interface ChartThresholdLine {
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

internal fun DrawScope.drawPulseSeriesLine(
    points: List<TrendPoint>,
    segmentIds: Map<String, Int>,
    scaler: ChartProjection,
    color: Color,
    path: Path
) {
    path.reset()
    var previous: TrendPoint? = null
    points.forEach { point ->
        val pulse = point.pulse ?: return@forEach
        val x = scaler.xOfTime(point.timestamp)
        val y = scaler.yOfValue(pulse)
        val earlier = previous
        if (earlier == null || segmentIds[earlier.id] != segmentIds[point.id]) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        previous = point
    }
    drawPath(path, color, style = Stroke(width = SERIES_LINE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round))
}

internal fun buildThresholdLines(
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

internal fun DrawScope.drawYAxisGrid(
    geometry: ChartGeometry,
    scaler: ChartProjection,
    yAxis: TrendYAxis,
    textMeasurer: TextMeasurer,
    palette: TrendChartPalette,
    dense: Boolean = false
) {
    val labelStyle = TextStyle(color = palette.axis, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val labelHeight = textMeasurer.measure(yAxis.max.toString(), labelStyle, softWrap = false).size.height
    TrendChartMath.yAxisTickValues(
        yAxis, geometry.plotHeight, labelHeight.toFloat(), 7.dp.toPx(), dense
    ).forEach { value ->
        val y = scaler.yOfValue(value)
        drawLine(
            color = palette.grid,
            start = Offset(geometry.left, y),
            end = Offset(geometry.right, y),
            strokeWidth = 1.dp.toPx()
        )

        val label = textMeasurer.measure(
            value.toString(),
            labelStyle,
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
 * 横向阈值线：固定的 140/90 长虚线参考线 + 用户个人目标点状虚线（右端带「目标」微缩标签）。
 */
internal fun DrawScope.drawThresholdLines(
    geometry: ChartGeometry,
    scaler: ChartProjection,
    lines: List<ChartThresholdLine>,
    palette: TrendChartPalette,
    textMeasurer: TextMeasurer? = null
): List<Rect> {
    data class MeasuredTargetTag(
        val layout: TextLayoutResult,
        val spec: TargetLabelSpec
    )
    val targetTags = mutableListOf<MeasuredTargetTag>()

    lines.forEach { line ->
        val isTarget = line is ChartThresholdLine.SystolicTarget || line is ChartThresholdLine.DiastolicTarget
        val color = when (line) {
            ChartThresholdLine.SystolicReference -> palette.systolic.copy(alpha = REFERENCE_LINE_ALPHA)
            ChartThresholdLine.DiastolicReference -> palette.diastolic.copy(alpha = REFERENCE_LINE_ALPHA)
            is ChartThresholdLine.SystolicTarget -> palette.systolic.copy(alpha = palette.targetDim)
            is ChartThresholdLine.DiastolicTarget -> palette.diastolic.copy(alpha = palette.targetDim)
        }
        val y = scaler.yOfValue(line.value)
        val dash = if (isTarget) {
            PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 3.5.dp.toPx()))
        } else {
            PathEffect.dashPathEffect(floatArrayOf(THRESHOLD_DASH_DP.dp.toPx(), THRESHOLD_GAP_DP.dp.toPx()))
        }
        drawLine(
            color = color,
            start = Offset(geometry.left, y),
            end = Offset(geometry.right, y),
            strokeWidth = THRESHOLD_LINE_WIDTH_DP.dp.toPx(),
            pathEffect = dash
        )
        if (isTarget && textMeasurer != null) {
            val tagLayout = textMeasurer.measure(
                text = "目标 ${line.value}",
                style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                softWrap = false
            )
            targetTags += MeasuredTargetTag(
                layout = tagLayout,
                spec = TargetLabelSpec(
                    lineY = y,
                    width = tagLayout.size.width.toFloat(),
                    height = tagLayout.size.height.toFloat()
                )
            )
        }
    }

    if (targetTags.isEmpty()) return emptyList()
    return run {
        val placements = TrendChartMath.resolveTargetLabelPositions(
            specs = targetTags.map { it.spec },
            left = geometry.left,
            right = geometry.right,
            top = geometry.top,
            bottom = geometry.bottom,
            lineGapPx = 2.dp.toPx(),
            labelGapPx = 2.dp.toPx(),
            rightMarginPx = 4.dp.toPx()
        )
        val padH = 2.dp.toPx()
        targetTags.mapIndexed { index, tag ->
            val pos = placements[index]
            val bounds = Rect(pos.x - padH, pos.y,
                pos.x + tag.layout.size.width + padH, pos.y + tag.layout.size.height)
            drawRect(
                color = palette.nodeBackground.copy(alpha = 0.88f),
                topLeft = Offset((pos.x - padH).coerceAtLeast(geometry.left), pos.y),
                size = androidx.compose.ui.geometry.Size(
                    tag.layout.size.width + padH * 2f,
                    tag.layout.size.height.toFloat()
                )
            )
            drawText(tag.layout, topLeft = Offset(pos.x, pos.y))
            bounds
        }
    }
}

internal fun DrawScope.drawSeriesLine(
    points: List<TrendPoint>,
    segmentIds: Map<String, Int>,
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
        if (index == 0 || segmentIds[point.id] != segmentIds[points[index - 1].id]) {
            path.moveTo(offset.x, offset.y)
        } else {
            path.lineTo(offset.x, offset.y)
        }
    }
    drawPath(
        path,
        color,
        style = Stroke(width = SERIES_LINE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round)
    )
}

internal fun DrawScope.drawTimeAxisLabels(
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
    drawLine(
        color = palette.grid,
        start = Offset(geometry.left, geometry.bottom),
        end = Offset(geometry.right, geometry.bottom),
        strokeWidth = 1.dp.toPx()
    )
}

internal fun DrawScope.drawPointNode(
    x: Float,
    y: Float,
    color: Color,
    backgroundColor: Color,
    ringColor: Color,
    selected: Boolean,
    highRisk: Boolean = false,
    highRiskColor: Color = ringColor
) {
    if (!selected) {
        if (highRisk) {
            drawCircle(color = backgroundColor, radius = 3.dp.toPx(), center = Offset(x, y))
            drawCircle(
                color = highRiskColor,
                radius = 3.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(x, y))
        return
    }
    drawCircle(color = ringColor.copy(alpha = 0.18f), radius = 7.dp.toPx(), center = Offset(x, y))
    drawCircle(color = backgroundColor, radius = 4.4.dp.toPx(), center = Offset(x, y))
    drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(x, y))
    drawCircle(
        color = ringColor,
        radius = 4.4.dp.toPx(),
        center = Offset(x, y),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

internal fun valueColor(
    value: Int,
    normalColor: Color,
    outlierColor: Color,
    validRange: IntRange
): Color = if (value in validRange) normalColor else outlierColor

private const val SERIES_LINE_WIDTH_DP = 1.7f
private const val THRESHOLD_LINE_WIDTH_DP = 1.2f
private const val THRESHOLD_DASH_DP = 6f
private const val THRESHOLD_GAP_DP = 5f
private const val REFERENCE_LINE_ALPHA = 0.42f

internal fun DrawScope.drawCrosshair(
    point: TrendPoint,
    scaler: ChartProjection,
    palette: TrendChartPalette,
    textMeasurer: TextMeasurer,
    showSystolic: Boolean,
    showDiastolic: Boolean,
    pulseMode: Boolean = false,
    targetLabelBounds: List<Rect> = emptyList()
) {
    val geometry = scaler.geometry
    val x = scaler.xOfTime(point.timestamp)
    drawLine(
        palette.selectionRing.copy(alpha = 0.6f),
        Offset(x, geometry.top),
        Offset(x, geometry.bottom),
        strokeWidth = 1.dp.toPx()
    )
    val rangeMean = if (point.aggregation != TrendAggregation.RAW) "均" else ""
    val occupied = targetLabelBounds.toMutableList()
    fun valueLine(value: Int, label: String, color: Color, atRight: Boolean) {
        val y = scaler.yOfValue(value)
        drawLine(
            color.copy(alpha = 0.7f),
            Offset(geometry.left, y),
            Offset(geometry.right, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        )
        val layout = textMeasurer.measure(
            "$label$rangeMean $value",
            TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            softWrap = false
        )
        val left = geometry.left + 4.dp.toPx()
        val right = (geometry.right - layout.size.width - 4.dp.toPx()).coerceAtLeast(left)
        val labelY = (y - layout.size.height - 3.dp.toPx()).coerceIn(
            geometry.top,
            (geometry.bottom - layout.size.height).coerceAtLeast(geometry.top)
        )
        val pad = 2.dp.toPx()
        val bottom = (geometry.bottom - layout.size.height).coerceAtLeast(geometry.top)
        val below = (y + 3.dp.toPx()).coerceIn(geometry.top, bottom)
        val primary = if (atRight) right else left
        val opposite = if (atRight) left else right
        val position = listOf(Offset(primary, labelY), Offset(opposite, labelY),
            Offset(primary, below), Offset(opposite, below)).firstOrNull { pos ->
            val bounds = Rect(pos.x - pad, pos.y, pos.x + layout.size.width + pad, pos.y + layout.size.height)
            if (occupied.any { it.overlaps(bounds) }) false else {
                occupied += bounds
                true
            }
        } ?: return
        drawRect(
            palette.nodeBackground.copy(alpha = 0.94f),
            topLeft = Offset(position.x - pad, position.y),
            size = androidx.compose.ui.geometry.Size(layout.size.width + 4.dp.toPx(), layout.size.height.toFloat())
        )
        drawText(layout, topLeft = position)
    }
    if (showSystolic) valueLine(point.systolic, "收", palette.systolic, atRight = false)
    if (showDiastolic) valueLine(point.diastolic, "舒", palette.diastolic, atRight = true)
    if (pulseMode) {
        point.pulse?.let { valueLine(it, "脉搏", palette.systolic, atRight = true) } ?: run {
            val missing = textMeasurer.measure(
                "脉搏 —",
                TextStyle(color = palette.axis, fontSize = 12.sp),
                softWrap = false
            )
            drawText(missing, topLeft = Offset(geometry.right - missing.size.width - 4.dp.toPx(), geometry.top))
        }
    }
}
