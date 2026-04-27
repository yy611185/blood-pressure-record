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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.data.repository.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun SessionTimeSeriesDualLineChart(
    sessions: List<SessionRecord>,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Text("当前范围暂无数据，无法绘制时间序列图。", style = MaterialTheme.typography.bodyMedium)
        return
    }
    if (sessions.size == 1) {
        val one = sessions.first()
        Text(
            "仅 1 条记录：${formatDateTime(one.measuredAt)}  " +
                "收缩压 ${one.avgSystolic} / 舒张压 ${one.avgDiastolic} mmHg",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    val points = sessions.sortedBy { it.measuredAt }
    val textMeasurer = rememberTextMeasurer()
    val refs = listOf(80, 90, 120, 130, 140)
    val minY = minOf(points.minOf { it.avgDiastolic }, refs.min()) - 8
    val maxY = maxOf(points.maxOf { it.avgSystolic }, refs.max()) + 8
    val sysColor = Color(0xFF005DAC)
    val diaColor = Color(0xFF6D8EC1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        val left = 76f
        val right = size.width - 24f
        val top = 16f
        val bottom = size.height - 46f
        val chartWidth = right - left
        val chartHeight = bottom - top

        val minTime = points.first().measuredAt.toDouble()
        val maxTime = points.last().measuredAt.toDouble()
        val timeSpan = (maxTime - minTime).takeIf { abs(it) > 0.5 } ?: 1.0

        fun xOf(time: Long): Float {
            val ratio = ((time - minTime) / timeSpan).toFloat()
            return left + ratio * chartWidth
        }

        fun yOf(value: Int): Float {
            val ratio = (value - minY).toFloat() / (maxY - minY).toFloat()
            return bottom - ratio * chartHeight
        }

        refs.forEach { ref ->
            val y = yOf(ref)
            drawLine(
                color = Color(0xFFD4DAE3),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.4f
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "$ref",
                topLeft = Offset(10f, y - 10f),
                style = TextStyle(color = Color(0xFF616A78), fontSize = TextUnit(10f, TextUnitType.Sp))
            )
        }

        val sysPath = Path()
        val diaPath = Path()
        points.forEachIndexed { idx, p ->
            val x = xOf(p.measuredAt)
            val ys = yOf(p.avgSystolic)
            val yd = yOf(p.avgDiastolic)
            if (idx == 0) {
                sysPath.moveTo(x, ys)
                diaPath.moveTo(x, yd)
            } else {
                sysPath.lineTo(x, ys)
                diaPath.lineTo(x, yd)
            }
        }
        drawPath(path = sysPath, color = sysColor, style = Stroke(width = 3.2f))
        drawPath(path = diaPath, color = diaColor, style = Stroke(width = 3.2f))

        points.forEach { p ->
            val x = xOf(p.measuredAt)
            drawCircle(color = sysColor, radius = 4.6f, center = Offset(x, yOf(p.avgSystolic)))
            drawCircle(color = diaColor, radius = 4.6f, center = Offset(x, yOf(p.avgDiastolic)))
            drawLine(
                color = Color(0xFFB9C0CC),
                start = Offset(x, bottom),
                end = Offset(x, bottom + 5f),
                strokeWidth = 1f
            )
        }

        val last = points.last()
        drawText(
            textMeasurer = textMeasurer,
            text = "收缩压",
            topLeft = Offset((xOf(last.measuredAt) + 8f).coerceAtMost(right - 44f), yOf(last.avgSystolic) - 16f),
            style = TextStyle(color = sysColor, fontSize = TextUnit(10f, TextUnitType.Sp))
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "舒张压",
            topLeft = Offset((xOf(last.measuredAt) + 8f).coerceAtMost(right - 44f), yOf(last.avgDiastolic) - 16f),
            style = TextStyle(color = diaColor, fontSize = TextUnit(10f, TextUnitType.Sp))
        )

        val firstLabel = formatDateTime(points.first().measuredAt)
        val midLabel = formatDateTime(points[points.lastIndex / 2].measuredAt)
        val lastLabel = formatDateTime(points.last().measuredAt)
        drawText(
            textMeasurer = textMeasurer,
            text = firstLabel,
            topLeft = Offset(left - 4f, bottom + 8f),
            style = TextStyle(color = Color(0xFF616A78), fontSize = TextUnit(9f, TextUnitType.Sp))
        )
        drawText(
            textMeasurer = textMeasurer,
            text = midLabel,
            topLeft = Offset((left + chartWidth / 2f) - 30f, bottom + 8f),
            style = TextStyle(color = Color(0xFF616A78), fontSize = TextUnit(9f, TextUnitType.Sp))
        )
        drawText(
            textMeasurer = textMeasurer,
            text = lastLabel,
            topLeft = Offset(right - 60f, bottom + 8f),
            style = TextStyle(color = Color(0xFF616A78), fontSize = TextUnit(9f, TextUnitType.Sp))
        )

        drawText(
            textMeasurer = textMeasurer,
            text = "mmHg",
            topLeft = Offset(10f, top - 4f),
            style = TextStyle(color = Color(0xFF616A78), fontSize = TextUnit(10f, TextUnitType.Sp))
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("● 收缩压", color = sysColor, style = MaterialTheme.typography.bodySmall)
        Text("● 舒张压", color = diaColor, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDateTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}
