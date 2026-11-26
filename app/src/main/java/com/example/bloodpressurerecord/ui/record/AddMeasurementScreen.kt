package com.example.bloodpressurerecord.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.bloodpressurerecord.ui.common.RemovedExtraReading
import com.example.bloodpressurerecord.ui.common.RestoreExtraReadingResult
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.rememberTouchExplorationEnabled
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

private val ReadingGroupEnter = fadeIn(AppMotion.floatNormal) + expandVertically(AppMotion.sizeNormal)
private val ReadingGroupExit = fadeOut(AppMotion.floatNormal) + shrinkVertically(AppMotion.sizeNormal)
private val AverageEnter = fadeIn(AppMotion.floatFast)
private val AverageExit = fadeOut(AppMotion.floatFast)
private val HighRiskEnter = fadeIn(AppMotion.floatFast) + expandVertically(AppMotion.sizeFast)
private val HighRiskExit = fadeOut(AppMotion.floatFast) + shrinkVertically(AppMotion.sizeFast)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSavedFeedback: ((sessionId: String, avgSys: Int?, avgDia: Int?, categoryLabel: String) -> Unit)? = null,
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

    val averagePresentation = remember { AveragePresentation() }
    if (state.avgSystolic != null && state.avgDiastolic != null) {
        averagePresentation.reading = AverageSnapshot(
            state.avgSystolic,
            state.avgDiastolic,
            state.avgPulse,
            state.averagedGroupCount,
            state.categoryLabel
        )
    }
    val displayedAverage = averagePresentation.reading

    // 焦点链：第 1 组高压 → 低压 → 脉搏 → 第 2 组高压 → … → 新增组高压
    val group1SysFocus = remember { FocusRequester() }
    val group2SysFocus = remember { FocusRequester() }
    var initialFocusPending by remember {
        mutableStateOf(!touchExploration && !state.restoredFromDraft && state.reading1.systolic.isEmpty())
    }
    var pendingFocusExtraId by remember { mutableStateOf<Long?>(null) }

    // D-01 方案 A：保存成功后原子消费实际提交结果，无阻断直返 + 底部可交互轻提示
    LaunchedEffect(state.saved, canConsumeSave) {
        if (state.saved && canConsumeSave && isCurrentEntry()) {
            val saved = viewModel.consumeSavedSession() ?: return@LaunchedEffect
            onSavedFeedback?.invoke(
                saved.sessionId,
                saved.avgSystolic,
                saved.avgDiastolic,
                saved.categoryLabel
            )
            onSaved()
        }
    }

    val requestBack = {
        when {
            !isCurrentEntry() || viewModel.uiState.value.isSaving -> Unit
            viewModel.uiState.value.saved -> {
                val saved = if (isCurrentEntry()) viewModel.consumeSavedSession() else null
                if (saved != null) {
                    onSavedFeedback?.invoke(
                        saved.sessionId,
                        saved.avgSystolic,
                        saved.avgDiastolic,
                        saved.categoryLabel
                    )
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
                    title = "记一次血压",
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
            // UX-20: 跨天草稿恢复提示置顶并附带「用当前时间」与「清空重记」入口
            AnimatedVisibility(
                visible = state.restoredFromDraft,
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                val draftTimeLabel = remember(state.measuredAtText) {
                    DateTimeInputFormatter.parse(state.measuredAtText)
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                        ?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                        ?: state.measuredAtText
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                    ) {
                        Text(
                            "已恢复 $draftTimeLabel 未保存的草稿",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            TextButton(enabled = formEditable, onClick = viewModel::refreshMeasuredAtToNow) {
                                Text("用当前时间")
                            }
                            TextButton(enabled = formEditable, onClick = viewModel::discardDraft) {
                                Text("清空重记")
                            }
                        }
                    }
                }
            }

            Text("测量时间", style = MaterialTheme.typography.titleMedium)
            MeasurementDateTimePicker(
                measuredAtText = state.measuredAtText,
                onMeasuredAtChange = viewModel::updateMeasuredAtText,
                enabled = formEditable
            )

            val activeExtras = extraReadingMotion.activeEntriesSnapshot()
            val firstExtraFocus = activeExtras.firstOrNull()?.systolicFocusRequester

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
                        onSystolicPlaced = {
                            if (initialFocusPending) {
                                initialFocusPending = false
                                if (!touchExploration && !state.restoredFromDraft && state.reading1.systolic.isEmpty()) {
                                    runCatching { group1SysFocus.requestFocus() }
                                }
                            }
                        },
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

                val averageOffsetPx = with(LocalDensity.current) { 8.dp.toPx() }
                AnimatedVisibility(
                    visible = state.avgSystolic != null && state.avgDiastolic != null,
                    enter = if (reducedMotion) EnterTransition.None else AverageEnter,
                    exit = if (reducedMotion) ExitTransition.None else AverageExit
                ) {
                    val averageOffsetState = transition.animateFloat(
                        transitionSpec = { if (reducedMotion) AppMotion.springPress(true) else AppMotion.floatFast },
                        label = "sessionAverageOffset"
                    ) { phase -> if (phase == EnterExitState.Visible || reducedMotion) 0f else averageOffsetPx }
                    Box(
                        Modifier
                            .padding(top = AppSpacing.large)
                            .graphicsLayer {
                                translationY = if (reducedMotion) 0f else averageOffsetState.value
                            }
                    ) {
                        displayedAverage?.let { avg ->
                            SessionAverageCard(
                                systolic = avg.systolic,
                                diastolic = avg.diastolic,
                                measurementCount = avg.count,
                                avgPulse = avg.pulse,
                                categoryLabel = avg.categoryLabel
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = state.containsHighRiskReading,
                    enter = if (reducedMotion) EnterTransition.None else HighRiskEnter,
                    exit = if (reducedMotion) ExitTransition.None else HighRiskExit
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

            // D-02 方案 A：原地折叠展开「补充情况（选填）」+ 常用词一键点选胶囊
            ExpandableSupplementCard(
                symptomNote = state.symptomNote,
                factorNote = state.factorNote,
                onSymptomNoteChange = viewModel::updateSymptomNote,
                onFactorNoteChange = viewModel::updateFactorNote,
                enabled = formEditable,
                initiallyExpanded = state.symptomNote.isNotBlank() || state.factorNote.isNotBlank(),
                stateKey = "add_measurement_supplement"
            )

            InlineSessionAction(
                canSave = state.canSave && !state.saved,
                disabledReason = state.saveDisabledReason,
                isSaving = state.isSaving,
                buttonText = "保存记录",
                onSave = viewModel::onSaveClicked
            )

            AnimatedVisibility(
                visible = state.formMessage.isNotBlank() && !state.isSaving && !state.saved,
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
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

private data class AverageSnapshot(
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
    val count: Int,
    val categoryLabel: String
)

private class AveragePresentation {
    var reading: AverageSnapshot? = null
}

internal class ExtraReadingPresentation(
    val id: Long,
    var reading: SessionReadingInputUi,
    var displayIndex: Int,
    initiallyVisible: Boolean
) {
    val visibility = MutableTransitionState(initiallyVisible).apply { targetState = true }
    val systolicFocusRequester = FocusRequester()
}

internal class ExtraReadingMotionState(initialReadings: List<SessionReadingInputUi>) {
    private var lastReadings = initialReadings
    private var activeEntries = initialReadings.mapIndexed { index, reading ->
        ExtraReadingPresentation(reading.editorId, reading, index + 2, initiallyVisible = true)
    }
    val entries = mutableStateListOf<ExtraReadingPresentation>().apply { addAll(activeEntries) }

    fun activeEntriesSnapshot(): List<ExtraReadingPresentation> = activeEntries

    fun reconcile(readings: List<SessionReadingInputUi>, reducedMotion: Boolean = false) {
        if (readings !== lastReadings) {
            val previousById = entries.associateBy { it.id }
            activeEntries = readings.mapIndexed { index, reading ->
                val entry = previousById[reading.editorId] ?: ExtraReadingPresentation(
                    reading.editorId, reading, index + 2, initiallyVisible = reducedMotion
                )
                entry.reading = reading
                entry.displayIndex = index + 2
                entry.visibility.targetState = true
                entry
            }
            val activeIds = readings.mapTo(hashSetOf()) { it.editorId }
            val ordered = activeEntries.toMutableList()
            if (!reducedMotion) {
                entries.forEachIndexed { index, entry ->
                    if (entry.id !in activeIds) {
                        entry.visibility.targetState = false
                        ordered.add(index.coerceAtMost(ordered.size), entry)
                    }
                }
            }
            if (ordered != entries) {
                entries.clear()
                entries.addAll(ordered)
            }
            lastReadings = readings
        } else if (reducedMotion) {
            entries.removeAll { it !in activeEntries }
        }
    }

    fun activeIndex(entry: ExtraReadingPresentation): Int? =
        activeEntries.indexOf(entry).takeIf { it >= 0 }

    fun forget(entry: ExtraReadingPresentation) {
        if (activeIndex(entry) == null) entries.remove(entry)
    }
}

internal class ExtraReadingUndo {
    private var queue by mutableStateOf(emptyList<RemovedExtraReading>())
    val current: RemovedExtraReading? get() = queue.firstOrNull()
    var retry: RestoreExtraReadingResult? by mutableStateOf(null)
        private set

    fun offer(removed: RemovedExtraReading) {
        queue = if (retry != null && queue.isNotEmpty()) {
            listOf(queue.first(), removed) + queue.drop(1)
        } else {
            listOf(removed)
        }
    }

    fun finish(removed: RemovedExtraReading, result: RestoreExtraReadingResult?) {
        if (current != removed) return
        retry = result?.takeUnless { it == RestoreExtraReadingResult.RESTORED }
        if (retry == null) queue = queue.drop(1)
    }
}

@Composable
internal fun rememberExtraReadingUndo(
    snackbarHostState: SnackbarHostState,
    onRestore: (RemovedExtraReading) -> RestoreExtraReadingResult
): ExtraReadingUndo {
    val undo = remember(snackbarHostState) { ExtraReadingUndo() }
    val removed = undo.current
    LaunchedEffect(removed) {
        removed ?: return@LaunchedEffect
        while (undo.current == removed) {
            val retry = undo.retry
            val message = when (retry) {
                RestoreExtraReadingResult.AT_LIMIT ->
                    "已达 ${MeasurementInputRules.MAX_READING_COUNT} 组读数上限，请先删除一组后再恢复"
                RestoreExtraReadingResult.BUSY -> "正在保存，可在保存完成后重试撤销"
                else -> "已移除第 ${removed.index + 3} 组读数"
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤销",
                withDismissAction = retry != null,
                duration = if (retry == null) SnackbarDuration.Long else SnackbarDuration.Indefinite
            )
            undo.finish(removed, if (result == SnackbarResult.ActionPerformed) onRestore(removed) else null)
        }
    }
    return undo
}

@Composable
internal fun AnimatedExtraReadingCard(
    entry: ExtraReadingPresentation,
    reading: SessionReadingInputUi,
    index: Int,
    active: Boolean,
    editable: Boolean = true,
    motion: ExtraReadingMotionState,
    systolicFocusRequester: FocusRequester?,
    nextGroupFocusRequester: FocusRequester?,
    onSystolicPlaced: (() -> Unit)? = null,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val content: @Composable () -> Unit = {
        val cardEnabled = active && editable
        Box(
            Modifier
                .padding(top = AppSpacing.large)
                .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
        ) {
            MeasurementReadingCard(
                index = index,
                reading = reading,
                removable = true,
                enabled = cardEnabled,
                systolicFocusRequester = systolicFocusRequester,
                onSystolicPlaced = onSystolicPlaced,
                pulseImeAction = if (nextGroupFocusRequester != null) ImeAction.Next else ImeAction.Done,
                onAdvanceNextGroup = nextGroupFocusRequester?.let { nextReq ->
                    { runCatching { nextReq.requestFocus() } }
                },
                onSystolicChange = { value -> if (cardEnabled) onSystolicChange(value) },
                onDiastolicChange = { value -> if (cardEnabled) onDiastolicChange(value) },
                onPulseChange = { value -> if (cardEnabled) onPulseChange(value) },
                onRemove = { if (cardEnabled) onRemove() }
            )
        }
    }
    if (reducedMotion) {
        if (active) content()
        return
    }
    LaunchedEffect(entry) {
        snapshotFlow { entry.visibility.isIdle && !entry.visibility.currentState && !entry.visibility.targetState }
            .first { it }
        motion.forget(entry)
    }
    AnimatedVisibility(
        visibleState = entry.visibility,
        enter = ReadingGroupEnter,
        exit = ReadingGroupExit
    ) { content() }
}
