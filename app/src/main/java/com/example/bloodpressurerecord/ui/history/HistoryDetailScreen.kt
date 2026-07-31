package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.*

@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session = uiState.session
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            when (
                snackbarHostState.showSnackbar(
                    message = "记录已删除",
                    actionLabel = "撤销",
                    duration = SnackbarDuration.Long
                )
            ) {
                SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                SnackbarResult.Dismissed -> onBack()
            }
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

    val isAbnormal = session.containsHighRiskReading || session.category.uppercase() != "NORMAL"
    val categoryText = CategoryPresentation.label(session.category)

    val topBarScroll = rememberHideOnScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarScroll.nestedScrollConnection)
        ) {
            AppTopBar(title = "记录详情", onBack = onBack, hideOnScroll = topBarScroll)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("高风险状态", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusChip(
                            text = if (session.containsHighRiskReading) "包含高风险读数" else "未检出",
                            isAbnormal = session.containsHighRiskReading
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("测量场景", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(session.scene, style = MaterialTheme.typography.bodyLarge)
                    }
                    val (symptomTags, factorTags) = remember(session.symptoms) {
                        MeasurementTags.splitSymptomsAndFactors(session.symptoms)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("伴随症状", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            symptomTags.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (factorTags.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("影响因素", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(factorTags.joinToString("、"), style = MaterialTheme.typography.bodyLarge)
                        }
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
