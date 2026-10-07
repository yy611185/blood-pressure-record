package com.example.bloodpressurerecord.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.InlineSessionAction
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.SessionCompletedDialog
import com.example.bloodpressurerecord.ui.common.SessionSupplementFields
import com.example.bloodpressurerecord.ui.common.SessionAverageCard
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
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
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var showSupplement by rememberSaveable { mutableStateOf(false) }
    // Presentation identities survive form-page transitions; drafts remain entirely in the ViewModel.
    val extraReadingMotion = remember(viewModel) { ExtraReadingMotionState(state.extraReadings) }
    extraReadingMotion.reconcile(state.extraReadings)
    val averagePresentation = remember { AveragePresentation() }
    if (state.avgSystolic != null && state.avgDiastolic != null) {
        averagePresentation.reading = Triple(state.avgSystolic, state.avgDiastolic, state.averagedGroupCount)
    }
    val displayedAverage = averagePresentation.reading
    val focusManager = LocalFocusManager.current
    LaunchedEffect(showSupplement) { focusManager.clearFocus() }
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
                    hideOnScroll = topBarScroll,
                    titleContent = {
                        AnimatedContent(
                            targetState = showSupplement,
                            transitionSpec = { AppMotion.formFadeThrough() },
                            label = "sessionFormTitle"
                        ) { supplement ->
                            Text(
                                if (supplement) "补充情况" else "记一次血压",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
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
            AnimatedContent(
                targetState = showSupplement,
                transitionSpec = { AppMotion.formFadeThrough() },
                label = "sessionFormPage"
            ) { supplement ->
                Column(
                    modifier = if (supplement == showSupplement) Modifier else Modifier
                        .clearAndSetSemantics {}
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
                ) {
                    if (!supplement) {
                        Text("测量时间", style = MaterialTheme.typography.titleMedium)
                        MeasurementDateTimePicker(
                            measuredAtText = state.measuredAtText,
                            onMeasuredAtChange = { if (!showSupplement) viewModel.updateMeasuredAtText(it) }
                        )
                        val readings = listOf(state.reading1, state.reading2)
                        Column {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                                readings.forEachIndexed { index, reading ->
                                    MeasurementReadingCard(
                                        index = index,
                                        reading = reading,
                                        removable = false,
                                        onSystolicChange = {
                                            if (!showSupplement) {
                                                when (index) {
                                                    0 -> viewModel.updateReading1Systolic(it)
                                                    1 -> viewModel.updateReading2Systolic(it)
                                                }
                                            }
                                        },
                                        onDiastolicChange = {
                                            if (!showSupplement) {
                                                when (index) {
                                                    0 -> viewModel.updateReading1Diastolic(it)
                                                    1 -> viewModel.updateReading2Diastolic(it)
                                                }
                                            }
                                        },
                                        onPulseChange = {
                                            if (!showSupplement) {
                                                when (index) {
                                                    0 -> viewModel.updateReading1Pulse(it)
                                                    1 -> viewModel.updateReading2Pulse(it)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            extraReadingMotion.entries.forEach { entry ->
                                key(entry.id) {
                                    AnimatedExtraReadingCard(
                                        entry = entry,
                                        reading = entry.reading,
                                        index = entry.displayIndex,
                                        active = !showSupplement && extraReadingMotion.activeIndex(entry) != null,
                                        motion = extraReadingMotion,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                        Column {
                            DashedAddGroupButton(
                                enabled = !showSupplement && readings.size + state.extraReadings.size < MeasurementInputRules.MAX_READING_COUNT,
                                onClick = { if (!showSupplement) viewModel.addNextReadingGroup() }
                            )

                            val averageOffsetPx = with(LocalDensity.current) { 8.dp.toPx() }
                            AnimatedVisibility(
                                visible = state.avgSystolic != null && state.avgDiastolic != null,
                                enter = AverageEnter,
                                exit = AverageExit
                            ) {
                                val averageOffset by transition.animateFloat(
                                    transitionSpec = { AppMotion.floatFast },
                                    label = "sessionAverageOffset"
                                ) { phase -> if (phase == EnterExitState.Visible) 0f else averageOffsetPx }
                                Box(Modifier.padding(top = AppSpacing.large).graphicsLayer { translationY = averageOffset }) {
                                    displayedAverage?.let { (systolic, diastolic, count) ->
                                        SessionAverageCard(systolic, diastolic, count)
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = state.containsHighRiskReading,
                                enter = HighRiskEnter,
                                exit = HighRiskExit
                            ) {
                                Surface(modifier = Modifier.padding(top = AppSpacing.large),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.errorContainer) {
                                    Text("有读数达到高风险范围。请休息后复测，身体不适请及时就医。",
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    } else {
                        SessionSupplementFields(
                            symptomNote = state.symptomNote,
                            factorNote = state.factorNote,
                            onSymptomNoteChange = { if (showSupplement) viewModel.updateSymptomNote(it) },
                            onFactorNoteChange = { if (showSupplement) viewModel.updateFactorNote(it) }
                        )
                    }
                }
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

/** UI-only snapshots keep outgoing values out of the business state and its draft writer. */
private class AveragePresentation {
    var reading: Triple<Int?, Int?, Int>? = null
}

private class ExtraReadingPresentation(
    val id: Long,
    var reading: SessionReadingInputUi,
    var displayIndex: Int,
    initiallyVisible: Boolean
) {
    val visibility = MutableTransitionState(initiallyVisible).apply { targetState = true }
}

/**
 * The ViewModel intentionally stores a plain indexed list. Keep stable UI keys locally, and
 * remove from that indexed list immediately while a non-interactive snapshot finishes its exit.
 */
private class ExtraReadingMotionState(initialReadings: List<SessionReadingInputUi>) {
    private var nextId = 0L
    private var lastReadings = initialReadings
    private val activeEntries = initialReadings.mapIndexed { index, reading ->
        ExtraReadingPresentation(nextId++, reading, index + 2, initiallyVisible = true)
    }.toMutableList()
    val entries = mutableStateListOf<ExtraReadingPresentation>().apply { addAll(activeEntries) }

    fun reconcile(readings: List<SessionReadingInputUi>) {
        if (readings === lastReadings) return
        // Explicit removal has already removed the corresponding key. A reset can only trim
        // the tail; no business state is ever inferred from these presentation snapshots.
        while (activeEntries.size > readings.size) {
            activeEntries.removeAt(activeEntries.lastIndex).visibility.targetState = false
        }
        while (activeEntries.size < readings.size) {
            val index = activeEntries.size
            val entry = ExtraReadingPresentation(nextId++, readings[index], index + 2, initiallyVisible = false)
            activeEntries.add(entry)
            entries.add(entry)
        }
        activeEntries.forEachIndexed { index, entry ->
            entry.reading = readings[index]
            entry.displayIndex = index + 2
        }
        lastReadings = readings
    }

    fun activeIndex(entry: ExtraReadingPresentation): Int? =
        activeEntries.indexOf(entry).takeIf { it >= 0 }

    fun retire(entry: ExtraReadingPresentation, latestReadings: List<SessionReadingInputUi>): Int? {
        val index = activeIndex(entry) ?: return null
        entry.reading = latestReadings.getOrNull(index) ?: entry.reading
        entry.displayIndex = index + 2
        activeEntries.removeAt(index)
        entry.visibility.targetState = false
        return index
    }

    fun forget(entry: ExtraReadingPresentation) {
        if (activeIndex(entry) == null) entries.remove(entry)
    }
}

@Composable
private fun AnimatedExtraReadingCard(
    entry: ExtraReadingPresentation,
    reading: SessionReadingInputUi,
    index: Int,
    active: Boolean,
    motion: ExtraReadingMotionState,
    viewModel: HomeViewModel
) {
    LaunchedEffect(entry) {
        snapshotFlow { entry.visibility.isIdle && !entry.visibility.currentState && !entry.visibility.targetState }
            .first { it }
        motion.forget(entry)
    }
    AnimatedVisibility(
        visibleState = entry.visibility,
        enter = ReadingGroupEnter,
        exit = ReadingGroupExit
    ) {
        Box(
            Modifier.padding(top = AppSpacing.large)
                .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
        ) {
            MeasurementReadingCard(
                index = index,
                reading = reading,
                removable = true,
                enabled = active,
                onSystolicChange = { value ->
                    if (active) motion.activeIndex(entry)?.let { viewModel.updateExtraReadingSystolic(it, value) }
                },
                onDiastolicChange = { value ->
                    if (active) motion.activeIndex(entry)?.let { viewModel.updateExtraReadingDiastolic(it, value) }
                },
                onPulseChange = { value ->
                    if (active) motion.activeIndex(entry)?.let { viewModel.updateExtraReadingPulse(it, value) }
                },
                onRemove = {
                    if (active) motion.retire(entry, viewModel.uiState.value.extraReadings)
                        ?.let(viewModel::removeExtraReading)
                }
            )
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
