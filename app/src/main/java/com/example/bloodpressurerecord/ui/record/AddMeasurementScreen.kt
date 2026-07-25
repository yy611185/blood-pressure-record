package com.example.bloodpressurerecord.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.SessionSaveBottomBar
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing

private val measurementScenes = listOf("晨起", "睡前", "居家安静", "运动后", "其他")
private val measurementSymptoms = listOf("无症状", "头痛", "头晕", "心悸", "胸闷或胸痛", "视物模糊", "其他")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.isDirty) showExitDialog = true else onBack()
    }
    BackHandler(onBack = requestBack)

    if (showExitDialog) {
        UnsavedChangesDialog(
            onContinueEditing = { showExitDialog = false },
            onSaveDraft = {
                showExitDialog = false
                onBack()
            },
            onDiscard = {
                viewModel.discardDraft()
                showExitDialog = false
                onBack()
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
    if (state.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("包含高风险读数") },
            text = {
                Text("检测到高风险读数。请在安静状态下复测，若伴有不适应及时寻求医疗帮助。本应用不能替代医疗诊断。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmHighRiskAndSave) { Text("仍要保存") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("返回修改") }
            }
        )
    }

    LaunchedEffect(state.formMessage) {
        if (state.formMessage == "保存成功。") onSaved()
    }

    Scaffold(
        topBar = { AppTopBar(title = "新增测量", onBack = requestBack) },
        bottomBar = {
            SessionSaveBottomBar(
                canSave = state.canSave,
                disabledReason = state.saveDisabledReason,
                isSaving = state.isSaving,
                buttonText = "保存记录",
                onSave = viewModel::onSaveClicked
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = AppDimensions.pageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            Text("测量日期和时间", style = MaterialTheme.typography.titleMedium)
            MeasurementDateTimePicker(
                measuredAtText = state.measuredAtText,
                onMeasuredAtChange = viewModel::updateMeasuredAtText
            )

            Text("测量场景", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                measurementScenes.forEach { scene ->
                    FilterChip(
                        selected = state.scene == scene,
                        onClick = { viewModel.updateScene(scene) },
                        label = { Text(scene) }
                    )
                }
            }

            val readings = listOf(state.reading1, state.reading2) + state.extraReadings
            readings.forEachIndexed { index, reading ->
                MeasurementReadingCard(
                    index = index,
                    reading = reading,
                    removable = index >= 2,
                    onSystolicChange = {
                        when (index) {
                            0 -> viewModel.updateReading1Systolic(it)
                            1 -> viewModel.updateReading2Systolic(it)
                            else -> viewModel.updateExtraReadingSystolic(index - 2, it)
                        }
                    },
                    onDiastolicChange = {
                        when (index) {
                            0 -> viewModel.updateReading1Diastolic(it)
                            1 -> viewModel.updateReading2Diastolic(it)
                            else -> viewModel.updateExtraReadingDiastolic(index - 2, it)
                        }
                    },
                    onPulseChange = {
                        when (index) {
                            0 -> viewModel.updateReading1Pulse(it)
                            1 -> viewModel.updateReading2Pulse(it)
                            else -> viewModel.updateExtraReadingPulse(index - 2, it)
                        }
                    },
                    onRemove = { viewModel.removeExtraReading(index - 2) }
                )
            }
            TextButton(
                onClick = viewModel::addNextReadingGroup,
                enabled = readings.size < 10,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("添加一组")
            }

            if (state.avgSystolic != null && state.avgDiastolic != null) {
                DataCard {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text("自动计算结果", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.avgSystolic} / ${state.avgDiastolic} mmHg",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("平均脉搏 ${state.avgPulse ?: "--"} 次/分")
                        StatusChip(
                            text = state.categoryLabel,
                            isAbnormal = state.categoryLabel != "正常"
                        )
                    }
                }
            }

            Text("伴随症状", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                measurementSymptoms.forEach { symptom ->
                    FilterChip(
                        selected = symptom in state.selectedSymptoms,
                        onClick = { viewModel.toggleSymptom(symptom) },
                        label = { Text(symptom) }
                    )
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注或“其他”补充说明") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            if (state.formMessage.isNotBlank() &&
                state.formMessage !in listOf("保存成功。", "正在保存...")
            ) {
                Text(
                    state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(AppSpacing.xLarge))
        }
    }
}
