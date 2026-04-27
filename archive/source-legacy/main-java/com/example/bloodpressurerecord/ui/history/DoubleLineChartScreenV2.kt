package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                title = { Text("血压趋势") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("双折线时间序列图", style = MaterialTheme.typography.titleMedium)
            Text("横轴：日期/时间（每次测量点） · 纵轴：mmHg", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.trendMetric == TrendMetricType.SYSTOLIC,
                    onClick = { viewModel.setTrendMetric(TrendMetricType.SYSTOLIC) },
                    label = { Text("收缩压") }
                )
                FilterChip(
                    selected = uiState.trendMetric == TrendMetricType.DIASTOLIC,
                    onClick = { viewModel.setTrendMetric(TrendMetricType.DIASTOLIC) },
                    label = { Text("舒张压") }
                )
                FilterChip(
                    selected = uiState.trendMetric == TrendMetricType.BOTH,
                    onClick = { viewModel.setTrendMetric(TrendMetricType.BOTH) },
                    label = { Text("双曲线") }
                )
            }

            TrendCard("最近7天（按每次测量点）", uiState.sessions7d, uiState.trendMetric)
            TrendCard("最近30天（按每次测量点）", uiState.sessions30d, uiState.trendMetric)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TrendCard(
    title: String,
    sessions: List<SessionRecord>,
    metric: TrendMetricType
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            when (metric) {
                TrendMetricType.BOTH -> SessionTimeSeriesDualLineChart(sessions = sessions)
                TrendMetricType.SYSTOLIC -> SessionTimeSeriesDualLineChart(
                    sessions = sessions.map { it.copy(avgDiastolic = it.avgSystolic) }
                )
                TrendMetricType.DIASTOLIC -> SessionTimeSeriesDualLineChart(
                    sessions = sessions.map { it.copy(avgSystolic = it.avgDiastolic) }
                )
            }

            if (sessions.isEmpty()) {
                Text("暂无测量记录", style = MaterialTheme.typography.bodySmall)
            } else {
                val first = formatSessionDate(sessions.first().measuredAt)
                val last = formatSessionDate(sessions.last().measuredAt)
                Text("样本区间：$first - $last", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatSessionDate(measuredAt: Long): String {
    return Instant.ofEpochMilli(measuredAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd"))
}
