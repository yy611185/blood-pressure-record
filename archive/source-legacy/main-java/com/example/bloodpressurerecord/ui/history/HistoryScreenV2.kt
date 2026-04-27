package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenChart: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("历史记录", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.periodType == HistoryPeriodType.DAY,
                onClick = { viewModel.setPeriodType(HistoryPeriodType.DAY) },
                label = { Text("日") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = uiState.periodType == HistoryPeriodType.WEEK,
                onClick = { viewModel.setPeriodType(HistoryPeriodType.WEEK) },
                label = { Text("周") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = uiState.periodType == HistoryPeriodType.MONTH,
                onClick = { viewModel.setPeriodType(HistoryPeriodType.MONTH) },
                label = { Text("月") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        StatisticCard(uiState = uiState)

        if (uiState.showTrendChart) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChart() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("血压趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("查看最近7天/30天双折线时间序列（按每次测量点）")
                }
            }
        }

        if (uiState.groups.isEmpty()) {
            EmptyStateCard()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.groups.forEach { group ->
                item("header-${group.dateLabel}") {
                    Text(group.dateLabel, style = MaterialTheme.typography.titleMedium)
                }
                items(group.sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(session.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${session.measuredAtText} · ${session.scene}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    session.categoryText,
                                    color = if (
                                        session.categoryText.contains("重度") ||
                                        session.categoryText.contains("2期")
                                    ) Color(0xFFB8141A) else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "平均血压 ${session.avgBloodPressureText} mmHg   平均脉搏 ${session.avgPulseText}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("备注：${session.noteSummary}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticCard(uiState: HistoryUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("统计概览", style = MaterialTheme.typography.titleMedium)
            Text("最近7天平均：${uiState.avg7dSystolic}/${uiState.avg7dDiastolic} mmHg")
            Text("最近30天平均：${uiState.avg30dSystolic}/${uiState.avg30dDiastolic} mmHg")
            Text("本周记录次数：${uiState.weekRecordCount}")
            Text("最近30天高风险次数：${uiState.highRiskCount30d}")
            Text("当前筛选记录数：${uiState.totalCountInPeriod}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("暂无记录", style = MaterialTheme.typography.titleMedium)
            Text("当前范围没有测量数据，先去“测量”页新增一条记录。")
        }
    }
}
