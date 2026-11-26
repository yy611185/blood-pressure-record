package com.example.bloodpressurerecord.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DashedAddGroupButton
import com.example.bloodpressurerecord.ui.common.ExpandableSupplementCard
import com.example.bloodpressurerecord.ui.common.InlineSessionAction
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.SessionAverageCard
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.rememberTouchExplorationEnabled
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.record.AnimatedExtraReadingCard
import com.example.bloodpressurerecord.ui.record.ExtraReadingMotionState
import com.example.bloodpressurerecord.ui.record.rememberExtraReadingUndo
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing

/**
 * 与 AddMeasurementScreen 100% 同构的纵向多组编辑表单（C-01 / UX-08 / UX-09）：
 * - 纵向平铺全部读数组 + 虚线「再加一组」+ 实时平均卡 + 实时高风险横幅
 * - 原地折叠「补充情况（选填）」并条件开放旧版 note「原备注」编辑框
 * - 保存后直接回到原记录详情页并由全局 Snackbar 轻提示「修改已保存」
 */
@Composable
fun EditSessionScreen(
    viewModel: EditSessionViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    canConsumeSave: Boolean = true,
    isCurrentEntry: () -> Boolean = { true }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val touchExploration = rememberTouchExplorationEnabled()
    var showExitDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val extraReadingUndo = rememberExtraReadingUndo(snackbarHostState, viewModel::restoreExtraReading)
    val formEditable = !state.isSaving && !state.saved

    val extraReadingMotion = remember(viewModel) { ExtraReadingMotionState(state.extraReadings) }
    extraReadingMotion.reconcile(state.extraReadings, reducedMotion)

    val group1SysFocus = remember { FocusRequester() }
    val group2SysFocus = remember { FocusRequester() }
    // 仅在显式点击「再加一组」时记录待聚焦的新组序号，加载多组旧记录绝不抢焦点。
    var pendingFocusExtraId by remember { mutableStateOf<Long?>(null) }

    // D-01 & UX-06：保存修改后单次消费完成态并无阻断直返原详情页
    LaunchedEffect(state.saved, canConsumeSave) {
        if (state.saved && canConsumeSave && isCurrentEntry() && viewModel.consumeSavedSession()) {
            onSaved()
        }
    }

    val requestBack = {
        when {
            !isCurrentEntry() || viewModel.uiState.value.isSaving -> Unit
            viewModel.uiState.value.saved -> {
                if (isCurrentEntry() && viewModel.consumeSavedSession()) {
                    onSaved()
                }
            }
            viewModel.uiState.value.isDirty -> showExitDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestBack)

    if (showExitDialog && !state.isSaving) {
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
                TextButton(enabled = !state.isSaving, onClick = viewModel::confirmAbnormalAndContinue) {
                    Text("确认无误")
                }
            },
            dismissButton = {
                TextButton(enabled = !state.isSaving, onClick = viewModel::dismissAbnormalDialog) {
                    Text("返回修改")
                }
            }
        )
    }
    if (state.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("检测到高风险读数") },
            text = {
                Text(
                    text = stringResource(R.string.high_risk_reading_guidance) + "本应用不能替代医疗诊断。",
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(enabled = !state.isSaving, onClick = viewModel::confirmHighRiskAndSave) {
                    Text("保存记录（供医生参考）")
                }
            },
            dismissButton = {
                TextButton(enabled = !state.isSaving, onClick = viewModel::dismissHighRiskDialog) {
                    Text("返回检查数值")
                }
            }
        )
    }

    val topBarScroll = rememberHideOnScrollState()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        },
        topBar = {
            Box(modifier = Modifier.padding(top = statusBarTopPadding())) {
                AppTopBar(
                    title = "修改读数",
                    onBack = requestBack,
                    hideOnScroll = topBarScroll
                )
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.loading,
            transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
            label = "edit_session_loading"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                        .padding(AppSpacing.large)
                ) {
                    Text("正在加载记录…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val activeExtras = extraReadingMotion.activeEntriesSnapshot()
                val firstExtraFocus = activeExtras.firstOrNull()?.systolicFocusRequester
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
                    Text("测量时间", style = MaterialTheme.typography.titleMedium)
                    MeasurementDateTimePicker(
                        measuredAtText = state.measuredAtText,
                        onMeasuredAtChange = viewModel::updateMeasuredAtText,
                        enabled = formEditable
                    )

                    Column {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                            MeasurementReadingCard(
                                index = 0,
                                reading = state.reading1,
                                removable = false,
                                enabled = formEditable,
                                onSystolicChange = viewModel::updateReading1Systolic,
                                onDiastolicChange = viewModel::updateReading1Diastolic,
                                onPulseChange = viewModel::updateReading1Pulse,
                                systolicFocusRequester = group1SysFocus,
                                pulseImeAction = ImeAction.Next,
                                onAdvanceNextGroup = { runCatching { group2SysFocus.requestFocus() } }
                            )
                            MeasurementReadingCard(
                                index = 1,
                                reading = state.reading2,
                                removable = false,
                                enabled = formEditable,
                                onSystolicChange = viewModel::updateReading2Systolic,
                                onDiastolicChange = viewModel::updateReading2Diastolic,
                                onPulseChange = viewModel::updateReading2Pulse,
                                systolicFocusRequester = group2SysFocus,
                                pulseImeAction = if (firstExtraFocus != null) ImeAction.Next else ImeAction.Done,
                                onAdvanceNextGroup = firstExtraFocus?.let { nextReq ->
                                    { runCatching { nextReq.requestFocus() } }
                                }
                            )
                        }
                        extraReadingMotion.entries.forEach { entry ->
                            key(entry.id) {
                                val activeIdx = extraReadingMotion.activeIndex(entry)
                                val nextActiveEntry = if (activeIdx != null && activeIdx + 1 < activeExtras.size) {
                                    activeExtras[activeIdx + 1]
                                } else null
                                AnimatedExtraReadingCard(
                                    entry = entry,
                                    reading = entry.reading,
                                    index = entry.displayIndex,
                                    active = activeIdx != null,
                                    editable = formEditable,
                                    motion = extraReadingMotion,
                                    systolicFocusRequester = entry.systolicFocusRequester,
                                    nextGroupFocusRequester = nextActiveEntry?.systolicFocusRequester,
                                    onSystolicPlaced = {
                                        if (activeIdx != null && pendingFocusExtraId == entry.id &&
                                            (touchExploration || runCatching { entry.systolicFocusRequester.requestFocus() }.isSuccess)
                                        ) {
                                            pendingFocusExtraId = null
                                        }
                                    },
                                    onSystolicChange = { viewModel.updateExtraReadingSystolic(entry.id, it) },
                                    onDiastolicChange = { viewModel.updateExtraReadingDiastolic(entry.id, it) },
                                    onPulseChange = { viewModel.updateExtraReadingPulse(entry.id, it) },
                                    onRemove = {
                                        viewModel.removeExtraReading(entry.id)?.let { deleted ->
                                            if (pendingFocusExtraId == entry.id) pendingFocusExtraId = null
                                            extraReadingUndo.offer(deleted)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Column {
                        DashedAddGroupButton(
                            enabled = formEditable && 2 + state.extraReadings.size < MeasurementInputRules.MAX_READING_COUNT,
                            onClick = {
                                val beforeCount = viewModel.uiState.value.extraReadings.size
                                viewModel.addNextReadingGroup()
                                if (!touchExploration && viewModel.uiState.value.extraReadings.size > beforeCount) {
                                    pendingFocusExtraId = viewModel.uiState.value.extraReadings.last().editorId
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = state.avgSystolic != null && state.avgDiastolic != null,
                            enter = AppMotion.expandVisibilityEnter(reducedMotion),
                            exit = AppMotion.expandVisibilityExit(reducedMotion)
                        ) {
                            Box(Modifier.padding(top = AppSpacing.large)) {
                                SessionAverageCard(
                                    systolic = state.avgSystolic,
                                    diastolic = state.avgDiastolic,
                                    measurementCount = state.averagedGroupCount,
                                    avgPulse = state.avgPulse,
                                    categoryLabel = state.categoryLabel
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = state.containsHighRiskReading,
                            enter = AppMotion.expandVisibilityEnter(reducedMotion),
                            exit = AppMotion.expandVisibilityExit(reducedMotion)
                        ) {
                            Surface(
                                modifier = Modifier.padding(top = AppSpacing.large),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    stringResource(R.string.high_risk_reading_guidance),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    ExpandableSupplementCard(
                        symptomNote = state.symptomNote,
                        factorNote = state.factorNote,
                        onSymptomNoteChange = viewModel::updateSymptomNote,
                        onFactorNoteChange = viewModel::updateFactorNote,
                        legacyNote = state.note,
                        showLegacyNoteField = state.hasLegacyNote,
                        onLegacyNoteChange = viewModel::updateNote,
                        enabled = formEditable,
                        initiallyExpanded = state.symptomNote.isNotBlank() ||
                            state.factorNote.isNotBlank() ||
                            state.note.isNotBlank(),
                        stateKey = "edit_session_supplement"
                    )

                    AnimatedVisibility(
                        visible = state.message.isNotBlank() && state.message !in listOf("正在保存…", "编辑已保存。"),
                        enter = AppMotion.expandVisibilityEnter(reducedMotion),
                        exit = AppMotion.expandVisibilityExit(reducedMotion)
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    InlineSessionAction(
                        canSave = state.canSave && !state.saved,
                        disabledReason = state.saveDisabledReason,
                        isSaving = state.isSaving,
                        buttonText = "保存修改",
                        onSave = viewModel::onSaveClicked
                    )
                }
            }
        }
    }
}
