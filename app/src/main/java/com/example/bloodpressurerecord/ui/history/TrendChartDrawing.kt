package com.example.bloodpressurerecord.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
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
import com.example.bloodpressurerecord.ui.theme.WarmError

/**
 * 图表内所有颜色在这里统一取值，绘制函数不再各写各的常量。
 * 收缩压沿用陶土橙（主色），舒张压沿用蓝色（三级容器前景色）。
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


/**
 * 图表横向阈值线：固定的收缩压 140 / 舒张压 90 参考线，以及用户自己设置的
 * 目标线。两者取值可能相同，因此用密封类型区分语义，不再按数值去重。
 * 参考线只画虚线，不带任何说明文字（数值由 Y 轴刻度表达）。
 */
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

/**
 * 需要绘制的横向阈值线：与当前可见指标相关、且落在 Y 轴范围内的固定参考线
 * 与用户目标线。参考线只有虚线，不再生成任何说明文字。
 */
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
 * 横向阈值线：固定的 90/140 参考线 + 用户目标线。
 *
 * 参考线只保留「Y 轴刻度 + 水平细虚线」两种表达，不再写「收缩压参考 140」
 * 这类文字——文字会抢占趋势曲线的视觉焦点，数值本身 Y 轴刻度已经说明。
 * 虚线比普通网格稍明显，但仍弱于数据折线。
 */
internal fun DrawScope.drawThresholdLines(
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
        // 缺测依据原始连续段，采样新增的时间间隔不作为断线依据。
        if (index == 0 || segmentIds[point.id] != segmentIds[points[index - 1].id]) {
            path.moveTo(offset.x, offset.y)
        } else {
            path.lineTo(offset.x, offset.y)
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
    // 轴线本身保持极轻，避免和网格抢焦点。
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

internal fun valueColor(
    value: Int,
    normalColor: Color,
    outlierColor: Color,
    validRange: IntRange
): Color {
    return if (value in validRange) {
        normalColor
    } else {
        outlierColor
    }
}


private const val SERIES_LINE_WIDTH_DP = 2f
private const val THRESHOLD_LINE_WIDTH_DP = 1.2f
private const val THRESHOLD_DASH_DP = 6f
private const val THRESHOLD_GAP_DP = 5f
private const val REFERENCE_LINE_ALPHA = 0.42f
/** 十字线只显示选中节点的实测值或聚合均值，缺失脉搏不借邻值。 */
internal fun DrawScope.drawCrosshair(
    point: TrendPoint,
    scaler: ChartProjection,
    palette: TrendChartPalette,
    textMeasurer: TextMeasurer,
    showSystolic: Boolean,
    showDiastolic: Boolean,
    pulseMode: Boolean = false
) {
    val geometry = scaler.geometry
    val x = scaler.xOfTime(point.timestamp)
    drawLine(
        palette.selectionRing.copy(alpha = 0.6f),
        Offset(x, geometry.top), Offset(x, geometry.bottom), strokeWidth = 1.dp.toPx()
    )
    val rangeMean = if (point.aggregation == TrendAggregation.DAILY_RANGE) "均" else ""
    fun valueLine(value: Int, label: String, color: Color, atRight: Boolean) {
        val y = scaler.yOfValue(value)
        drawLine(
            color.copy(alpha = 0.7f), Offset(geometry.left, y), Offset(geometry.right, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        )
        val layout = textMeasurer.measure(
            "$label$rangeMean $value",
            TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            softWrap = false
        )
        val labelX = if (atRight) geometry.right - layout.size.width - 4.dp.toPx() else geometry.left + 4.dp.toPx()
        val labelY = (y - layout.size.height - 3.dp.toPx()).coerceIn(
            geometry.top, (geometry.bottom - layout.size.height).coerceAtLeast(geometry.top)
        )
        drawRect(
            palette.nodeBackground.copy(alpha = 0.94f),
            topLeft = Offset(labelX - 2.dp.toPx(), labelY),
            size = androidx.compose.ui.geometry.Size(layout.size.width + 4.dp.toPx(), layout.size.height.toFloat())
        )
        drawText(layout, topLeft = Offset(labelX, labelY))
    }
    if (showSystolic) valueLine(point.systolic, "收", palette.systolic, atRight = false)
    if (showDiastolic) valueLine(point.diastolic, "舒", palette.diastolic, atRight = true)
    if (pulseMode) {
        point.pulse?.let { valueLine(it, "脉搏", palette.systolic, atRight = true) } ?: run {
            val missing = textMeasurer.measure(
                "脉搏 —", TextStyle(color = palette.axis, fontSize = 12.sp), softWrap = false
            )
            drawText(missing, topLeft = Offset(geometry.right - missing.size.width - 4.dp.toPx(), geometry.top))
        }
    }
}
