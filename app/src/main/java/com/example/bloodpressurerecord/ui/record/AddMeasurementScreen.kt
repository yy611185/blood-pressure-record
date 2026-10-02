package com.example.bloodpressurerecord.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.InlineSessionAction
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.SessionCompletedDialog
import com.example.bloodpressurerecord.ui.common.SessionSupplementFields
import com.example.bloodpressurerecord.ui.common.SessionAverageCard
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var showSupplement by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        when {
            state.saved -> { viewModel.closeSavedSession(); onSaved() }
            showSupplement -> showSupplement = false
            state.isDirty -> showExitDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestBack)

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

    if (state.saved) {
        SessionCompletedDialog(
            avgSystolic = state.avgSystolic,
            avgDiastolic = state.avgDiastolic,
            measuredAtText = state.measuredAtText,
            timePeriod = state.scene,
            deadlineMillis = state.completionDeadlineMillis,
            onEdit = { viewModel.editSavedSession(); showSupplement = false },
            onClose = { viewModel.closeSavedSession(); onSaved() }
        )
    }

    val topBarScroll = rememberHideOnScrollState()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        // 顶部安全区交给 topBar 槽里的 AppTopBar，底部安全区交给滚动内容自己，
        // 避免 Scaffold.innerPadding 与页面 padding 重复叠加。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            // 本页 Scaffold 把 contentWindowInsets 归零，AppTopBar 自身不含状态栏安全区，
            // 因此必须在外层补上：整块顶栏（含滚动隐藏的外层留白）都待在系统状态栏之下。
            // 只在记录页外层加，不动全局 AppTopBar，避免其他已自行处理安全区的页面重复 inset。
            Box(modifier = Modifier.padding(top = statusBarTopPadding())) {
                AppTopBar(
                    title = if (showSupplement) "补充情况" else "记一次血压",
                    onBack = requestBack,
                    hideOnScroll = topBarScroll
                )
            }
        }
    ) { padding ->
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
            if (!showSupplement) {
                Text("测量时间", style = MaterialTheme.typography.titleMedium)
                MeasurementDateTimePicker(
                    measuredAtText = state.measuredAtText,
                    onMeasuredAtChange = viewModel::updateMeasuredAtText
                )
                val readings = listOf(state.reading1, state.reading2) + state.extraReadings
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
                ) {
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
                }
                DashedAddGroupButton(
                    enabled = readings.size < MeasurementInputRules.MAX_READING_COUNT,
                    onClick = viewModel::addNextReadingGroup
                )

                if (state.avgSystolic != null && state.avgDiastolic != null) {
                    SessionAverageCard(state.avgSystolic, state.avgDiastolic, state.averagedGroupCount)
                }
                if (state.containsHighRiskReading) {
                    Surface(shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer) {
                        Text("有读数达到高风险范围。请休息后复测，身体不适请及时就医。",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (showSupplement) {
                SessionSupplementFields(
                    symptomNote = state.symptomNote,
                    factorNote = state.factorNote,
                    onSymptomNoteChange = viewModel::updateSymptomNote,
                    onFactorNoteChange = viewModel::updateFactorNote
                )
            }
            InlineSessionAction(
                canSave = state.canSave && !state.saved,
                disabledReason = state.saveDisabledReason,
                isSaving = state.isSaving,
                buttonText = "保存记录",
                onSave = viewModel::onSaveClicked
            )
            if (!showSupplement) {
                TextButton(onClick = { showSupplement = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("补充情况（选填）")
                }
            }
            if (state.formMessage.isNotBlank() && !state.isSaving && !state.saved) {
                Text(
                    state.formMessage,
                    color = if (state.formMessageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DashedAddGroupButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val dashColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
                drawRoundRect(
                    color = dashColor,
                    style = stroke,
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "再加一组",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
