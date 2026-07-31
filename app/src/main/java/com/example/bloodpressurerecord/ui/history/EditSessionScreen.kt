package com.example.bloodpressurerecord.ui.history

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.SessionSaveBottomBar
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSessionScreen(
    viewModel: EditSessionViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.isDirty) showExitDialog = true else onBack()
    }
    BackHandler(onBack = requestBack)

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
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
                Text("本次编辑包含高风险读数。本应用不能替代医疗诊断，如伴有不适请及时寻求医疗帮助。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmHighRiskAndSave) { Text("仍要保存") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("返回修改") }
            }
        )
    }

    val topBarScroll = rememberHideOnScrollState()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        topBar = {
            AppTopBar(title = "编辑测量", onBack = requestBack, hideOnScroll = topBarScroll)
        },
        bottomBar = {
            if (!state.loading) {
                SessionSaveBottomBar(
                    canSave = state.canSave,
                    disabledReason = state.saveDisabledReason,
                    isSaving = state.isSaving,
                    buttonText = "保存修改",
                    onSave = viewModel::onSaveClicked
                )
            }
        }
    ) { padding ->
        if (state.loading) {
            Text("正在加载记录…", modifier = Modifier.padding(padding).padding(AppSpacing.large))
            return@Scaffold
        }
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
            // 旧记录的历史场景标签不在标准时段列表时追加显示，保证选中态可见。
            val sceneOptions = remember(state.scene) {
                if (state.scene in MeasurementTags.scenes) {
                    MeasurementTags.scenes
                } else {
                    MeasurementTags.scenes + state.scene
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                sceneOptions.forEach { scene ->
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

            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text("自动计算结果", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.avgSystolic ?: "--"} / ${state.avgDiastolic ?: "--"} mmHg",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("平均脉搏 ${state.avgPulse ?: "--"} 次/分")
                    StatusChip(state.categoryLabel, isAbnormal = state.categoryLabel != "正常")
                }
            }

            Text("伴随症状", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                MeasurementTags.symptoms.forEach { symptom ->
                    FilterChip(
                        selected = symptom in state.selectedSymptoms,
                        onClick = { viewModel.toggleSymptom(symptom) },
                        label = { Text(symptom) }
                    )
                }
            }

            Text("影响血压的情况", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                MeasurementTags.factors.forEach { factor ->
                    FilterChip(
                        selected = factor in state.selectedFactors,
                        onClick = { viewModel.toggleFactor(factor) },
                        label = { Text(factor) }
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
            if (state.message.isNotBlank() && state.message !in listOf("正在保存…", "编辑已保存。")) {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(AppSpacing.xLarge))
        }
    }
}
