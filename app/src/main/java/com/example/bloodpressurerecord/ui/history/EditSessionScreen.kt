package com.example.bloodpressurerecord.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.InlineSessionAction
import com.example.bloodpressurerecord.ui.common.SessionChoiceChip
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSessionScreen(
    viewModel: EditSessionViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf(0) }
    var step by rememberSaveable { mutableIntStateOf(1) }
    val requestBack = {
        when {
            step == 3 -> onSaved()
            step == 2 -> step = 1
            state.isDirty -> showExitDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestBack)

    LaunchedEffect(state.saved) {
        if (state.saved) step = 3
    }
    if (showExitDialog) {
        UnsavedChangesDialog(
            onContinueEditing = { showExitDialog = false },
            onSaveDraft = {
                showExitDialog = false
                viewModel.saveDraft(onBack)
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
        // 顶部安全区由 topBar 槽承担；底部安全区由滚动内容自己负责。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            // 与新增记录页一致：contentWindowInsets 归零后，顶栏外层的状态栏安全区
            // 必须自己补，否则标题、返回按钮会与系统状态栏图标重叠。
            Box(modifier = Modifier.padding(top = statusBarTopPadding())) {
                AppTopBar(title = when (step) {
                    1 -> "修改读数"
                    2 -> "测量情况"
                    else -> "已更新"
                }, onBack = requestBack, hideOnScroll = topBarScroll)
            }
        }
    ) { padding ->
        if (state.loading) {
            // 顶部安全区已由 topBar 槽承担，这里只保留页面内边距。
            Text("正在加载记录…", modifier = Modifier.padding(padding).padding(AppSpacing.large))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = AppDimensions.pageHorizontalPadding)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = AppDimensions.pageBottomGap),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            if (step == 1) {
            Text("测量数据", style = MaterialTheme.typography.titleMedium)
            val readings = listOf(state.reading1, state.reading2) + state.extraReadings
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                readings.forEachIndexed { index, reading ->
                    val complete = reading.systolic.isNotBlank() && reading.diastolic.isNotBlank()
                    SessionChoiceChip(
                        text = "第 ${index + 1} 组${if (complete) " ✓" else ""}",
                        selected = index == selectedGroup.coerceIn(0, readings.lastIndex),
                        onClick = { selectedGroup = index }
                    )
                }
            }
            val index = selectedGroup.coerceIn(0, readings.lastIndex)
            val reading = readings[index]
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
                onRemove = {
                    viewModel.removeExtraReading(index - 2)
                    selectedGroup = (index - 1).coerceAtLeast(0)
                }
            )
            TextButton(
                onClick = {
                    viewModel.addNextReadingGroup()
                    selectedGroup = readings.size
                },
                enabled = readings.size < MeasurementInputRules.MAX_READING_COUNT,
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
            }

            if (step == 2) {
            Text("本次平均 ${state.avgSystolic ?: "—"}/${state.avgDiastolic ?: "—"} mmHg",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("测量日期和时间", style = MaterialTheme.typography.titleMedium)
            MeasurementDateTimePicker(
                measuredAtText = state.measuredAtText,
                onMeasuredAtChange = viewModel::updateMeasuredAtText
            )

            Text("测量场景", style = MaterialTheme.typography.titleMedium)
            // 历史场景标签保留在选项中，确保旧记录可继续编辑。
            val sceneOptions = remember(state.scene) {
                if (state.scene in MeasurementTags.scenes) MeasurementTags.scenes
                else MeasurementTags.scenes + state.scene
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                sceneOptions.forEach { scene ->
                    SessionChoiceChip(
                        text = scene,
                        selected = state.scene == scene,
                        onClick = { viewModel.updateScene(scene) }
                    )
                }
            }

            Text("伴随症状", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                MeasurementTags.symptoms.forEach { symptom ->
                    SessionChoiceChip(symptom, symptom in state.selectedSymptoms) {
                        viewModel.toggleSymptom(symptom)
                    }
                }
            }

            Text("影响血压的情况", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                MeasurementTags.factors.forEach { factor ->
                    SessionChoiceChip(factor, factor in state.selectedFactors) {
                        viewModel.toggleFactor(factor)
                    }
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注或“其他”补充说明") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            }
            if (step == 3) {
                DataCard {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                        Text("记录已更新", style = MaterialTheme.typography.titleLarge)
                        Text("${state.avgSystolic ?: "—"} / ${state.avgDiastolic ?: "—"}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary)
                        StatusChip(state.categoryLabel, isAbnormal = state.categoryLabel != "正常")
                    }
                }
            }
            if (state.message.isNotBlank() && state.message !in listOf("正在保存…", "编辑已保存。")) {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // 与新增页一致：主按钮是页面滚动内容末尾的普通按钮，不再固定悬浮、不随键盘上浮。
            InlineSessionAction(
                canSave = if (step == 3) true else state.canSave,
                disabledReason = if (step == 3) "" else state.saveDisabledReason,
                isSaving = state.isSaving,
                buttonText = when (step) {
                    1 -> "下一步 · 测量情况"
                    2 -> "保存修改"
                    else -> "完成"
                },
                onSave = {
                    when (step) {
                        1 -> step = 2
                        2 -> viewModel.onSaveClicked()
                        else -> onSaved()
                    }
                }
            )
        }
    }
}
