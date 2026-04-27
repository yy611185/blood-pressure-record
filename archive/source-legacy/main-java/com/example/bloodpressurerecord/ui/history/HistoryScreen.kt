package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("历史记录", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.periodType == HistoryPeriodType.DAY,
                onClick = { viewModel.setPeriodType(HistoryPeriodType.DAY) },
                label = { Text("日") }
            )
            FilterChip(
                selected = uiState.periodType == HistoryPeriodType.WEEK,
                onClick = { viewModel.setPeriodType(HistoryPeriodType.WEEK) },
                label = { Text("周") }
            )
            FilterChip(
                selected = uiState.periodType == HistoryPeriodType.MONTH,
                onClick = { viewModel.setPeriodType(HistoryPeriodType.MONTH) },
                label = { Text("月") }
            )
        }
        Text("当前区间会话数：${uiState.totalCountInPeriod}", style = MaterialTheme.typography.bodyMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("基础统计", style = MaterialTheme.typography.titleLarge)
                Text("最近7天平均：${uiState.avg7dSystolic}/${uiState.avg7dDiastolic}")
                Text("最近30天平均：${uiState.avg30dSystolic}/${uiState.avg30dDiastolic}")
                Text("本周记录次数：${uiState.weekRecordCount}")
                Text("近30天高风险次数：${uiState.highRiskCount30d}")
            }
        }

        if (uiState.showTrendChart) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("趋势图设置", style = MaterialTheme.typography.titleLarge)
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
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("7天趋势", style = MaterialTheme.typography.titleLarge)
                    TrendChart(points = uiState.trend7d, metricType = uiState.trendMetric)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("30天趋势", style = MaterialTheme.typography.titleLarge)
                    TrendChart(points = uiState.trend30d, metricType = uiState.trendMetric)
                }
            }
        }

        if (uiState.groups.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "当前区间暂无记录。",
                    modifier = Modifier.padding(14.dp)
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.groups.forEach { group ->
                item(key = "header-${group.dateLabel}") {
                    Text(group.dateLabel, style = MaterialTheme.typography.titleLarge)
                }
                items(group.sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(session.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("时间 ${session.measuredAtText}  场景 ${session.scene}")
                            Text("平均血压 ${session.avgBloodPressureText}   平均脉搏 ${session.avgPulseText}")
                            Text("分级 ${session.categoryText}")
                            Text("备注 ${session.noteSummary}")
                        }
                    }
                }
            }
        }
    }
}
