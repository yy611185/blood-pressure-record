package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.bloodpressurerecord.domain.calculator.BloodPressureRules
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import com.example.bloodpressurerecord.ui.common.*

@OptIn(ExperimentalLayoutApi::class)
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
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column {
                AppTopBar(title = "记录详情", onBack = onBack)
                Text(if (uiState.deleted) "记录已删除" else "未找到记录", modifier = Modifier.padding(16.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        return
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("确认删除") },
            text = { Text("${uiState.measuredAtText} · ${session.avgSystolic}/${session.avgDiastolic}。删除后可在提示条里撤销。") },
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
                    Text("${uiState.measuredAtText} · ${session.scene}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${session.avgSystolic}/${session.avgDiastolic}",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = NumberFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusChip(
                            text = categoryText,
                            isAbnormal = isAbnormal,
                            status = bloodPressureVisualStatus(session.category, false)
                        )
                        if (session.containsHighRiskReading) {
                            StatusChip(
                                text = "含高风险读数",
                                isAbnormal = true,
                                status = bloodPressureVisualStatus(session.category, true)
                            )
                        }
                    }
                    Text(
                        (if (session.averageStrategy == AverageStrategy.DISCARD_FIRST && session.readings.size >= 2)
                            "弃用第 1 组，取其余 ${session.readings.size - 1} 组平均"
                        else "${session.readings.size} 组全部平均") +
                            (session.avgPulse?.let { " · 脉搏 $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("原始读数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DetailReadingRow("组", "高压", "低压", "脉搏", header = true)
                    session.readings.forEachIndexed { index, reading ->
                        DetailReadingRow(
                            label = "第${index + 1}组" + if (session.averageStrategy == AverageStrategy.DISCARD_FIRST && index == 0 && session.readings.size >= 2) "（不计）" else "",
                            systolic = reading.systolic.toString(),
                            diastolic = reading.diastolic.toString(),
                            pulse = reading.pulse?.toString() ?: "—",
                            highRisk = BloodPressureRules.isHighRisk(reading.systolic, reading.diastolic)
                        )
                    }
                }
            }
            val (symptomTags, factorTags) = remember(session.symptoms) {
                MeasurementTags.splitSymptomsAndFactors(session.symptoms)
            }
            if (symptomTags.isNotEmpty() || factorTags.isNotEmpty() || !session.note.isNullOrBlank()) {
                DataCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("测量备注", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (symptomTags.isNotEmpty()) Text("症状：${symptomTags.joinToString("、")}")
                        if (factorTags.isNotEmpty()) Text("影响因素：${factorTags.joinToString("、")}")
                        session.note?.takeIf { it.isNotBlank() }?.let { Text("“$it”") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDangerButton("删除", viewModel::requestDelete, Modifier.weight(1f))
                AppSecondaryButton("编辑", { onEdit(sessionId) }, Modifier.weight(1f))
            }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DetailReadingRow(
    label: String,
    systolic: String,
    diastolic: String,
    pulse: String,
    header: Boolean = false,
    highRisk: Boolean = false
) {
    val foreground = when {
        highRisk -> MaterialTheme.colorScheme.onErrorContainer
        header -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (highRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp)
            ).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium, color = foreground)
        listOf(systolic, diastolic, pulse).forEach { value ->
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = if (header) null else NumberFontFamily,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Bold,
                color = foreground
            )
        }
    }
}
