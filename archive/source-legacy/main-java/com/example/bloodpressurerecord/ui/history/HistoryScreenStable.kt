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
    onOpenDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("History", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = uiState.periodType == HistoryPeriodType.DAY, onClick = { viewModel.setPeriodType(HistoryPeriodType.DAY) }, label = { Text("Day") })
            FilterChip(selected = uiState.periodType == HistoryPeriodType.WEEK, onClick = { viewModel.setPeriodType(HistoryPeriodType.WEEK) }, label = { Text("Week") })
            FilterChip(selected = uiState.periodType == HistoryPeriodType.MONTH, onClick = { viewModel.setPeriodType(HistoryPeriodType.MONTH) }, label = { Text("Month") })
        }
        Text("Count in period: ${uiState.totalCountInPeriod}")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Statistics", style = MaterialTheme.typography.titleMedium)
                Text("7d avg: ${uiState.avg7dSystolic}/${uiState.avg7dDiastolic}")
                Text("30d avg: ${uiState.avg30dSystolic}/${uiState.avg30dDiastolic}")
                Text("This week records: ${uiState.weekRecordCount}")
                Text("30d high risk: ${uiState.highRiskCount30d}")
            }
        }

        if (uiState.showTrendChart) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Trend metric", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = uiState.trendMetric == TrendMetricType.SYSTOLIC, onClick = { viewModel.setTrendMetric(TrendMetricType.SYSTOLIC) }, label = { Text("SBP") })
                        FilterChip(selected = uiState.trendMetric == TrendMetricType.DIASTOLIC, onClick = { viewModel.setTrendMetric(TrendMetricType.DIASTOLIC) }, label = { Text("DBP") })
                        FilterChip(selected = uiState.trendMetric == TrendMetricType.BOTH, onClick = { viewModel.setTrendMetric(TrendMetricType.BOTH) }, label = { Text("Both") })
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("7-day trend", style = MaterialTheme.typography.titleMedium)
                    TrendChart(points = uiState.trend7d, metricType = uiState.trendMetric)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("30-day trend", style = MaterialTheme.typography.titleMedium)
                    TrendChart(points = uiState.trend30d, metricType = uiState.trendMetric)
                }
            }
        }

        if (uiState.groups.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("No records in current range.", modifier = Modifier.padding(12.dp))
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
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(session.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Time ${session.measuredAtText}  Scene ${session.scene}")
                            Text("Avg BP ${session.avgBloodPressureText}  Pulse ${session.avgPulseText}")
                            Text("Category ${session.categoryText}")
                            Text("Note ${session.noteSummary}")
                        }
                    }
                }
            }
        }
    }
}
