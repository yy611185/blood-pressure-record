package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    onBackToHistory: () -> Unit,
    onEdit: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBackToHistory()
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("确认删除") },
            text = { Text("删除后不可恢复，是否继续？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("会话详情", style = MaterialTheme.typography.headlineMedium)
        if (session == null) {
            Text("未找到该记录。")
            Button(onClick = onBackToHistory, modifier = Modifier.fillMaxWidth()) {
                Text("返回历史")
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("时间 ${uiState.measuredAtText}")
                    Text("场景 ${session.scene}")
                    Text("平均血压 ${session.avgSystolic}/${session.avgDiastolic}")
                    Text("平均脉搏 ${session.avgPulse ?: "--"}")
                    Text("分级 ${session.category}")
                    Text("高风险标记 ${if (session.highRiskAlertTriggered) "是" else "否"}")
                    Text("症状 ${uiState.symptomsText}")
                    Text("备注 ${session.note ?: "无"}")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("本次读数明细", style = MaterialTheme.typography.titleLarge)
                    session.readings.forEach { reading ->
                        Text(
                            "第${reading.orderIndex}组：${reading.systolic}/${reading.diastolic}" +
                                (reading.pulse?.let { "  脉搏 $it" } ?: "")
                        )
                    }
                }
            }

            Button(onClick = { onEdit(session.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("编辑")
            }
            Button(onClick = viewModel::requestDelete, modifier = Modifier.fillMaxWidth()) {
                Text("删除")
            }
        }
        if (uiState.message.isNotBlank()) {
            Text(uiState.message, color = Color(0xFFB71C1C))
        }
    }
}
