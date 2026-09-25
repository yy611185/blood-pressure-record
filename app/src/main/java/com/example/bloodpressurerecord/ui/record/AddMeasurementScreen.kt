package com.example.bloodpressurerecord.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.InlineSessionAction
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.SessionChoiceChip
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.home.Buddy
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory

/** 存储值保持“无症状”不变，仅展示时使用口语化文案。 */
private fun symptomLabel(symptom: String): String =
    if (symptom == "无症状") "没有，挺好的" else symptom

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf(0) }
    // 录入主线为三步：读数 → 情况 → 完成（不再有静坐引导页）。
    var step by rememberSaveable { mutableIntStateOf(0) }
    var savedSystolic by rememberSaveable { mutableStateOf<Int?>(null) }
    var savedDiastolic by rememberSaveable { mutableStateOf<Int?>(null) }
    var savedCategory by rememberSaveable { mutableStateOf("") }
    var savedRisk by rememberSaveable { mutableStateOf(false) }
    var savedPulse by rememberSaveable { mutableStateOf<Int?>(null) }
    var savedGroupCount by rememberSaveable { mutableIntStateOf(0) }
    var savedDiscardedFirst by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        when {
            step == 2 -> onSaved()
            step == 1 -> step = 0
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

    LaunchedEffect(state.saved) {
        if (state.saved) step = 2
    }

    val topBarScroll = rememberHideOnScrollState()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        // 顶部安全区交给 topBar 槽里的 AppTopBar，底部安全区交给滚动内容自己，
        // 避免 Scaffold.innerPadding 与页面 padding 重复叠加。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = when (step) {
                    0 -> "记一次血压"
                    1 -> "测的时候怎么样？"
                    else -> "记好啦"
                },
                onBack = requestBack,
                hideOnScroll = topBarScroll,
                actions = {
                    Row(
                        modifier = Modifier.semantics { contentDescription = "第 ${step + 1} 步，共 3 步" },
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        repeat(3) { index ->
                            val color = when {
                                index == step -> MaterialTheme.colorScheme.primary
                                index < step -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                            androidx.compose.foundation.layout.Box(
                                Modifier.width(if (index == step) 30.dp else 18.dp)
                                    .height(6.dp)
                                    .background(color, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            )
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
            // 读数页的「下一步 · 补充情况」与情况页的「保存这次记录」都作为
            // 页面滚动内容末尾的普通主按钮，不再拥有独立白色 Dock、不随键盘上浮。
            val action: @Composable () -> Unit = {
                InlineSessionAction(
                    canSave = when (step) {
                        0 -> state.canContinueReadings
                        1 -> state.canSave
                        else -> true
                    },
                    disabledReason = when (step) {
                        0 -> state.readingsDisabledReason
                        1 -> state.saveDisabledReason
                        else -> ""
                    },
                    isSaving = state.isSaving,
                    buttonText = when (step) {
                        0 -> "下一步 · 补充情况"
                        1 -> "保存这次记录"
                        else -> "完成"
                    },
                    onSave = {
                        when (step) {
                            0 -> step = 1
                            1 -> {
                                savedSystolic = state.avgSystolic
                                savedDiastolic = state.avgDiastolic
                                savedCategory = state.categoryLabel
                                savedRisk = state.containsHighRiskReading
                                savedPulse = state.avgPulse
                                savedGroupCount = state.averagedGroupCount
                                savedDiscardedFirst = state.discardedFirstReading
                                viewModel.onSaveClicked()
                            }
                            else -> onSaved()
                        }
                    }
                )
            }
            if (step == 0) {
            Text("连续测量，更接近真实血压", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            DashedAddGroupButton(
                enabled = readings.size < MeasurementInputRules.MAX_READING_COUNT,
                onClick = {
                    viewModel.addNextReadingGroup()
                    selectedGroup = readings.size
                }
            )

            if (state.avgSystolic != null && state.avgDiastolic != null) {
                AverageResultCard(
                    groupLabel = if (state.discardedFirstReading) {
                        "不计第一组 · ${state.averagedGroupCount}组平均"
                    } else {
                        "${state.averagedGroupCount}组平均"
                    },
                    avgText = "${state.avgSystolic} / ${state.avgDiastolic}",
                    avgPulse = state.avgPulse,
                    categoryLabel = state.categoryLabel
                )
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
            action()
            }

            if (step == 1) {
            Text("本次平均 ${state.avgSystolic ?: "—"}/${state.avgDiastolic ?: "—"} mmHg",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("什么时候测的？", style = MaterialTheme.typography.titleMedium)
            MeasurementDateTimePicker(
                measuredAtText = state.measuredAtText,
                onMeasuredAtChange = viewModel::updateMeasuredAtText
            )

            Text("在什么情况下测的？", style = MaterialTheme.typography.titleMedium)
            // 时段标签随测量时间自动预选；旧记录的历史标签（如“居家安静”）
            // 不在标准列表时追加显示，保证选中态可见。
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
                    SessionChoiceChip(
                        text = scene,
                        selected = state.scene == scene,
                        onClick = { viewModel.updateScene(scene) }
                    )
                }
            }

            Text("有没有不舒服的地方？", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                MeasurementTags.symptoms.forEach { symptom ->
                    SessionChoiceChip(
                        text = symptomLabel(symptom),
                        selected = symptom in state.selectedSymptoms,
                        onClick = { viewModel.toggleSymptom(symptom) }
                    )
                }
            }

            val dangerSymptoms = state.selectedSymptoms.filter {
                it == "胸闷或胸痛" || it == "视物模糊"
            }
            val highPressure = state.avgSystolic?.let { systolic ->
                state.avgDiastolic?.let { diastolic ->
                    CategoryCalculator.calculate(systolic, diastolic) in
                        setOf(BloodPressureCategory.STAGE2, BloodPressureCategory.STAGE3)
                }
            } == true
            if (dangerSymptoms.isNotEmpty() && (state.containsHighRiskReading || highPressure)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "血压偏高且伴有${dangerSymptoms.joinToString("、")}，请尽快就医或拨打 120。",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

                Text("有没有可能影响血压的情况？", style = MaterialTheme.typography.titleMedium)
                Text(
                    "比如刚喝了咖啡、没睡好，记下来方便对照数值。（可多选）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    MeasurementTags.factors.forEach { factor ->
                        SessionChoiceChip(
                            text = factor,
                            selected = factor in state.selectedFactors,
                            onClick = { viewModel.toggleFactor(factor) }
                        )
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    placeholder = {
                        Text(
                            "想补充点什么？比如「早饭前测的」（选填）",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    minLines = 3
                )
                action()
            }
            if (step == 2) {
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
                    Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Buddy(if (savedRisk) "STAGE3" else savedCategory, Modifier.size(96.dp, 90.dp))
                        Text("记录保存好了", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("${savedSystolic ?: "—"} / ${savedDiastolic ?: "—"}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusChip(savedCategory, isAbnormal = savedCategory != "正常")
                            if (savedRisk) {
                                StatusChip("高风险读数", isAbnormal = true,
                                    status = BloodPressureVisualStatus.HIGH_RISK)
                            }
                        }
                        Text(
                            if (savedRisk) "这次有读数达到 180/120 以上。请先静坐休息后复测；如伴有胸痛、剧烈头痛、视物模糊、说话不清等，请立即就医或拨打 120。"
                            else "今天也辛苦啦",
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    if (savedRisk) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(18.dp)
                                ).padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (savedRisk) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            buildString {
                                append(if (savedDiscardedFirst) "弃第1组 · " else "")
                                append("${savedGroupCount}组平均")
                                savedPulse?.let { append(" · 脉搏 $it 次/分") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                action()
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

@Composable
private fun AverageResultCard(
    groupLabel: String,
    avgText: String,
    avgPulse: Int?,
    categoryLabel: String
) {
    val visualStatus = when (categoryLabel) {
        "正常" -> BloodPressureVisualStatus.NORMAL
        "血压偏低" -> BloodPressureVisualStatus.LOW
        "正常高值" -> BloodPressureVisualStatus.ELEVATED
        else -> BloodPressureVisualStatus.HIGH
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(groupLabel, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(avgText, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                avgPulse?.let { Text("♥ $it", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StatusChip(
                text = categoryLabel,
                isAbnormal = visualStatus != BloodPressureVisualStatus.NORMAL,
                status = visualStatus
            )
        }
    }
}
