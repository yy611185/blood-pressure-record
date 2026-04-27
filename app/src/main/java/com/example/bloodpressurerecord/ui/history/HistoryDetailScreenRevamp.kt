package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.*

@Composable
fun HistoryDetailScreenRevamp(
    viewModel: HistoryDetailViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onBack()
        }
    }

    if (session == null) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AppTopBar(title = "记录详情", onBack = onBack)
            Text("未找到记录", modifier = Modifier.padding(16.dp))
        }
        return
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("确认删除") },
            text = { Text("是否删除这条测量记录？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }

    val isAbnormal = session.category.contains("高") && !session.category.contains("正常")
    val categoryText = when (session.category) {
        "NORMAL" -> "正常"
        "ELEVATED" -> "正常高值"
        "STAGE1" -> "1级高血压"
        "STAGE2" -> "2级高血压"
        "SEVERE" -> "重度高血压"
        else -> session.category
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = "记录详情", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("测量时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        uiState.measuredAtText,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("自动计算结果", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("平均收缩压", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${session.avgSystolic} mmHg", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("平均舒张压", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${session.avgDiastolic} mmHg", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("平均脉搏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(session.avgPulse?.let { "$it 次/分" } ?: "--", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("分级结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusChip(text = categoryText, isAbnormal = isAbnormal)
                    }
                    if (!session.note.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("备注", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.note, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            session.readings.forEachIndexed { index, reading ->
                DataCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("第 ${index + 1} 组读数", style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("收缩压 / 舒张压", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${reading.systolic} / ${reading.diastolic} mmHg")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("脉搏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(reading.pulse?.let { "$it 次/分" } ?: "--")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AppSecondaryButton(
                text = "编辑记录",
                onClick = { onEdit(sessionId) },
                modifier = Modifier.fillMaxWidth()
            )
            AppDangerButton(
                text = "删除记录",
                onClick = viewModel::requestDelete,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
