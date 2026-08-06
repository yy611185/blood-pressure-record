package com.example.bloodpressurerecord.ui.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.ui.common.SessionSaveBottomBar
import com.example.bloodpressurerecord.ui.common.StatusChip

/** 按组确认页：可编辑、可疑高亮、确认后保存（复用现有表单组件与 Repository）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanReviewScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var zoomGroup by remember { mutableStateOf<ScanGroup?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    if (state.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("请再次确认数值") },
            text = { Text("本次记录中包含高危血压读数，请再次确认读数无误后再保存。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmHighRiskAndContinue) { Text("确认无误") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("返回核对") }
            }
        )
    }

    if (state.showAbnormalConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAbnormalDialog,
            title = { Text("请再次确认数值") },
            text = { Text(state.abnormalConfirmMessage) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAbnormalAndContinue) { Text("确认无误") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAbnormalDialog) { Text("返回修改") }
            }
        )
    }

    zoomGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { zoomGroup = null },
            title = { Text("第 ${group.groupNumber} 组照片") },
            text = {
                group.thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "识别照片",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { zoomGroup = null }) { Text("关闭") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = "确认保存", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.groups.forEach { group ->
                GroupReviewCard(
                    group = group,
                    onZoom = { zoomGroup = group },
                    onSystolicChange = { viewModel.updateGroup(group.groupNumber, ScanField.SYSTOLIC, it) },
                    onDiastolicChange = { viewModel.updateGroup(group.groupNumber, ScanField.DIASTOLIC, it) },
                    onPulseChange = { viewModel.updateGroup(group.groupNumber, ScanField.PULSE, it) }
                )
            }
            if (state.groups.size < 3) {
                AppSecondaryButton(
                    text = "返回拍照（还可再拍 ${3 - state.groups.size} 组）",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            MeasurementDateTimePicker(
                measuredAtText = state.measuredAtText,
                onMeasuredAtChange = viewModel::updateMeasuredAtText
            )
            SceneSelector(scene = state.scene, onSceneChange = viewModel::updateScene)
            if (state.message.isNotBlank()) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }
        SessionSaveBottomBar(
            canSave = state.canSave,
            disabledReason = state.saveDisabledReason,
            isSaving = state.isSaving,
            buttonText = "确认保存",
            onSave = viewModel::onSaveClicked,
            embedded = true
        )
    }
}

@Composable
private fun GroupReviewCard(
    group: ScanGroup,
    onZoom: () -> Unit,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit
) {
    val warningColor = Color(0xFFB8860B)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (group.isSuspicious) {
            BorderStroke(2.dp, warningColor)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("第 ${group.groupNumber} 组", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                if (group.isSuspicious) {
                    StatusChip(text = "请核对", isAbnormal = true)
                }
                if (group.thumbnail != null) {
                    TextButton(onClick = onZoom) { Text("查看照片") }
                }
            }
            MeasurementReadingCard(
                index = group.groupNumber - 1,
                reading = SessionReadingInputUi(group.systolic, group.diastolic, group.pulse),
                removable = false,
                onSystolicChange = onSystolicChange,
                onDiastolicChange = onDiastolicChange,
                onPulseChange = onPulseChange
            )
            if (group.isSuspicious) {
                Text(
                    "识别可能存在误差，请核对后再保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneSelector(scene: String, onSceneChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("场景", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementTags.scenes.forEach { candidate ->
                FilterChip(
                    selected = candidate == scene,
                    onClick = { onSceneChange(candidate) },
                    label = { Text(candidate) }
                )
            }
        }
    }
}
