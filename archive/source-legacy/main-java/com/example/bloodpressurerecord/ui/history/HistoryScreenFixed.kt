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
    onOpenDetail: (String) -> Unit,
    onOpenChart: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("历史记录", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = uiState.periodType == HistoryPeriodType.DAY, onClick = { viewModel.setPeriodType(HistoryPeriodType.DAY) }, label = { Text("日") })
            FilterChip(selected = uiState.periodType == HistoryPeriodType.WEEK, onClick = { viewModel.setPeriodType(HistoryPeriodType.WEEK) }, label = { Text("周") })
            FilterChip(selected = uiState.periodType == HistoryPeriodType.MONTH, onClick = { viewModel.setPeriodType(HistoryPeriodType.MONTH) }, label = { Text("月") })
        }
        Text("当前范围记录数：${uiState.totalCountInPeriod}")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("统计概览", style = MaterialTheme.typography.titleMedium)
                Text("最近 7 天平均：${uiState.avg7dSystolic}/${uiState.avg7dDiastolic}")
                Text("最近 30 天平均：${uiState.avg30dSystolic}/${uiState.avg30dDiastolic}")
                Text("本周记录次数：${uiState.weekRecordCount}")
                Text("最近 30 天高风险次数：${uiState.highRiskCount30d}")
            }
        }

        if (uiState.showTrendChart) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChart() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "查看 7 天 / 30 天双折线时间序列图",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (uiState.groups.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("当前范围暂无记录。", modifier = Modifier.padding(12.dp))
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.groups.forEach { group ->
                item("header-${group.dateLabel}") {
                    Text(group.dateLabel, style = MaterialTheme.typography.titleMedium)
                }
                items(group.sessions, key = { it.id }) { session ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(session.id) }) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("时间 ${session.measuredAtText}  场景 ${session.scene}")
                            Text("平均血压 ${session.avgBloodPressureText}  平均脉搏 ${session.avgPulseText}")
                            Text("分级 ${session.categoryText}")
                            Text("备注 ${session.noteSummary}")
                        }
                    }
                }
            }
        }
    }
}

