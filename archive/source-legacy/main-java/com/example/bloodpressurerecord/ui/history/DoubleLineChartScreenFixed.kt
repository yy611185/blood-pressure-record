package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleLineChartScreenZh(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("双折线时间序列图") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("最近 7 天", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    DoubleLineChart(sessions = uiState.sessions7d)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("最近 30 天", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    DoubleLineChart(sessions = uiState.sessions30d)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFFB71C1C), modifier = Modifier.size(16.dp)) {}
                    Text("收缩压（高压）", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF1565C0), modifier = Modifier.size(16.dp)) {}
                    Text("舒张压（低压）", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DoubleLineChart(sessions: List<SessionRecord>) {
    if (sessions.isEmpty()) {
        Text("当前范围暂无记录，无法绘制图表。", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val sorted = sessions.sortedBy { it.measuredAt }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = 10.dp, bottom = 10.dp)
    ) {
        val left = 80f
        val right = size.width - 60f
        val top = 20f
        val bottom = size.height - 40f
        val width = right - left
        val height = bottom - top

        val minValue = 40
        val maxValue = 200

        fun yOf(value: Int): Float {
            val ratio = (value.coerceIn(minValue, maxValue) - minValue).toFloat() / (maxValue - minValue).toFloat()
            return bottom - ratio * height
        }

        val refs = listOf(80, 90, 120, 130, 140)
        refs.forEach { ref ->
            drawLine(
                color = Color.LightGray,
                start = Offset(left, yOf(ref)),
                end = Offset(right, yOf(ref)),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "$ref",
                topLeft = Offset(5f, yOf(ref) - 20f),
                style = TextStyle(color = Color.Gray, fontSize = TextUnit(12f, TextUnitType.Sp))
            )
        }

        drawLine(color = Color.Gray, start = Offset(left, bottom), end = Offset(right, bottom), strokeWidth = 2f)
        drawLine(color = Color.Gray, start = Offset(left, top), end = Offset(left, bottom), strokeWidth = 2f)

        if (sorted.size == 1) {
            val x = left + width / 2f
            val record = sorted.first()
            drawCircle(color = Color(0xFFB71C1C), radius = 6f, center = Offset(x, yOf(record.avgSystolic)))
            drawCircle(color = Color(0xFF1565C0), radius = 6f, center = Offset(x, yOf(record.avgDiastolic)))
        } else {
            val stepX = width / (sorted.size - 1).toFloat()
            val sysPath = Path()
            val diaPath = Path()

            sorted.forEachIndexed { index, record ->
                val x = left + index * stepX
                val sysY = yOf(record.avgSystolic)
                val diaY = yOf(record.avgDiastolic)

                if (index == 0) {
                    sysPath.moveTo(x, sysY)
                    diaPath.moveTo(x, diaY)
                } else {
                    sysPath.lineTo(x, sysY)
                    diaPath.lineTo(x, diaY)
                }
            }

            drawPath(path = sysPath, color = Color(0xFFB71C1C), style = Stroke(width = 4f))
            drawPath(path = diaPath, color = Color(0xFF1565C0), style = Stroke(width = 4f))

            sorted.forEachIndexed { index, record ->
                val x = left + index * stepX
                drawCircle(color = Color(0xFFB71C1C), radius = 6f, center = Offset(x, yOf(record.avgSystolic)))
                drawCircle(color = Color(0xFF1565C0), radius = 6f, center = Offset(x, yOf(record.avgDiastolic)))
            }

            val lastIndex = sorted.lastIndex
            val lastX = left + lastIndex * stepX
            drawText(
                textMeasurer = textMeasurer,
                text = "高压",
                topLeft = Offset(lastX + 5f, yOf(sorted.last().avgSystolic) - 20f),
                style = TextStyle(color = Color(0xFFB71C1C), fontSize = TextUnit(10f, TextUnitType.Sp))
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "低压",
                topLeft = Offset(lastX + 5f, yOf(sorted.last().avgDiastolic) - 20f),
                style = TextStyle(color = Color(0xFF1565C0), fontSize = TextUnit(10f, TextUnitType.Sp))
            )
        }

        val formatter = DateTimeFormatter.ofPattern("MM/dd")
        val zone = ZoneId.systemDefault()
        val firstDate = Instant.ofEpochMilli(sorted.first().measuredAt).atZone(zone).format(formatter)
        val lastDate = Instant.ofEpochMilli(sorted.last().measuredAt).atZone(zone).format(formatter)
        drawText(
            textMeasurer = textMeasurer,
            text = firstDate,
            topLeft = Offset(left, bottom + 5f),
            style = TextStyle(color = Color.Gray, fontSize = TextUnit(10f, TextUnitType.Sp))
        )
        if (sorted.size > 1) {
            drawText(
                textMeasurer = textMeasurer,
                text = lastDate,
                topLeft = Offset(right - 40f, bottom + 5f),
                style = TextStyle(color = Color.Gray, fontSize = TextUnit(10f, TextUnitType.Sp))
            )
        }
    }
}

