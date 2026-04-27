package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.domain.model.TrendDayPoint

@Composable
fun TrendChart(
    points: List<TrendDayPoint>,
    metricType: TrendMetricType,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Text("数据不足，暂时无法绘制趋势图。", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val values = when (metricType) {
        TrendMetricType.SYSTOLIC -> points.map { it.avgSystolic }
        TrendMetricType.DIASTOLIC -> points.map { it.avgDiastolic }
        TrendMetricType.BOTH -> points.flatMap { listOf(it.avgSystolic, it.avgDiastolic) }
    }
    val minValue = values.minOrNull() ?: 0
    val maxValue = (values.maxOrNull() ?: 0).let { if (it == minValue) it + 1 else it }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val left = 14f
            val right = size.width - 14f
            val top = 12f
            val bottom = size.height - 12f
            val width = right - left
            val height = bottom - top
            val stepX = width / (points.size - 1).toFloat()

            drawLine(
                color = Color(0xFFB0BEC5),
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = 2f
            )

            fun yOf(value: Int): Float {
                val ratio = (value - minValue).toFloat() / (maxValue - minValue).toFloat()
                return bottom - ratio * height
            }

            fun drawSeries(color: Color, selector: (TrendDayPoint) -> Int) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = left + index * stepX
                    val y = yOf(selector(point))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = color)
                points.forEachIndexed { index, point ->
                    val x = left + index * stepX
                    val y = yOf(selector(point))
                    drawCircle(color = color, radius = 4f, center = Offset(x, y))
                }
            }

            if (metricType == TrendMetricType.SYSTOLIC || metricType == TrendMetricType.BOTH) {
                drawSeries(Color(0xFFB71C1C)) { it.avgSystolic }
            }
            if (metricType == TrendMetricType.DIASTOLIC || metricType == TrendMetricType.BOTH) {
                drawSeries(Color(0xFF1565C0)) { it.avgDiastolic }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().dateKey, style = MaterialTheme.typography.bodySmall)
            Text(points.last().dateKey, style = MaterialTheme.typography.bodySmall)
        }
    }
}
