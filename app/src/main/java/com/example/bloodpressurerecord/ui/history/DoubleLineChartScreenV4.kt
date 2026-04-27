package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.ui.common.AppBackButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TrendRangePreset(
    val label: String,
    val title: String,
    val defaultVisibleCount: Int
) {
    DAYS_7("7天", "最近7天", 7),
    DAYS_30("30天", "最近30天", 30),
    ALL("全部", "全部记录", 30)
}

@Composable
fun DoubleLineChartScreenZh(
    viewModel: HistoryViewModel,
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var rangePreset by remember { mutableStateOf(TrendRangePreset.DAYS_30) }
    val chartSessions = when (rangePreset) {
        TrendRangePreset.DAYS_7 -> uiState.sessions7d
        TrendRangePreset.DAYS_30 -> uiState.sessions30d
        TrendRangePreset.ALL -> uiState.sessionsAll
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                AppBackButton(onClick = onBack)
            }
            Text(
                text = "血压趋势分析",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp)
            )
        }
        Text(
            "双指缩放、拖动平移，双击图表恢复默认视图；轻触节点查看完整数值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrendCard(
            title = rangePreset.title,
            sessions = chartSessions,
            metric = uiState.trendMetric,
            targetSystolic = uiState.targetSystolic,
            targetDiastolic = uiState.targetDiastolic,
            defaultVisibleCount = rangePreset.defaultVisibleCount,
            rangePreset = rangePreset,
            onRangeChange = { rangePreset = it },
            onMetricChange = viewModel::setTrendMetric
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TrendCard(
    title: String,
    sessions: List<SessionRecord>,
    metric: TrendMetricType,
    targetSystolic: Int?,
    targetDiastolic: Int?,
    defaultVisibleCount: Int,
    rangePreset: TrendRangePreset,
    onRangeChange: (TrendRangePreset) -> Unit,
    onMetricChange: (TrendMetricType) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SegmentedControl(
                items = TrendRangePreset.entries,
                selected = rangePreset,
                label = { it.label },
                onSelected = onRangeChange
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (sessions.isNotEmpty()) {
                    Text("${sessions.size} 次测量", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
            }
            SegmentedControl(
                items = listOf(TrendMetricType.SYSTOLIC, TrendMetricType.DIASTOLIC, TrendMetricType.BOTH),
                selected = metric,
                label = {
                    when (it) {
                        TrendMetricType.SYSTOLIC -> "收缩压"
                        TrendMetricType.DIASTOLIC -> "舒张压"
                        TrendMetricType.BOTH -> "双曲线"
                    }
                },
                onSelected = onMetricChange
            )
            SessionTimeSeriesDualLineChart(
                sessions = sessions,
                targetSystolic = targetSystolic,
                targetDiastolic = targetDiastolic,
                showSystolic = metric != TrendMetricType.DIASTOLIC,
                showDiastolic = metric != TrendMetricType.SYSTOLIC,
                emptyTitle = "$title 暂无数据",
                averageLabel = "${title}平均",
                defaultVisibleCount = defaultVisibleCount
            )
            if (sessions.isNotEmpty()) {
                val first = formatSessionDate(sessions.minOf { it.measuredAt })
                val last = formatSessionDate(sessions.maxOf { it.measuredAt })
                Text("样本区间：$first - $last", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun <T> SegmentedControl(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x80E2E8F0), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(14.dp), clip = false)
                    .background(if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onSelected(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

private fun formatSessionDate(measuredAt: Long): String {
    return Instant.ofEpochMilli(measuredAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd"))
}
