package com.example.bloodpressurerecord.ui.record

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.MeasurementDateTimePicker
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.MeasurementReadingCard
import com.example.bloodpressurerecord.ui.common.SessionSaveBottomBar
import com.example.bloodpressurerecord.ui.common.UnsavedChangesDialog
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.Sage200
import com.example.bloodpressurerecord.ui.theme.Sage900
import com.example.bloodpressurerecord.ui.theme.Terracotta700
import com.example.bloodpressurerecord.ui.theme.TerracottaDashed

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
    val context = LocalContext.current
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

    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "保存好了，今天也辛苦啦", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }

    val topBarScroll = rememberHideOnScrollState()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "记一次血压", onBack = requestBack, hideOnScroll = topBarScroll)
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
            Text("什么时候测的？", style = MaterialTheme.typography.titleMedium)
            MeasurementDateTimePicker(
                measuredAtText = state.measuredAtText,
                onMeasuredAtChange = viewModel::updateMeasuredAtText
            )

            Text("测量数据", style = MaterialTheme.typography.titleMedium)
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
            DashedAddGroupButton(
                enabled = readings.size < 10,
                onClick = viewModel::addNextReadingGroup
            )

            if (state.avgSystolic != null && state.avgDiastolic != null) {
                AverageResultCard(
                    groupLabel = "${readings.count { it.systolic.isNotBlank() && it.diastolic.isNotBlank() }}组平均",
                    avgText = "${state.avgSystolic} / ${state.avgDiastolic}",
                    avgPulse = state.avgPulse,
                    categoryLabel = state.categoryLabel
                )
            }

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
                    WarmChip(
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
                    WarmChip(
                        text = symptomLabel(symptom),
                        selected = symptom in state.selectedSymptoms,
                        onClick = { viewModel.toggleSymptom(symptom) }
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
                    WarmChip(
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
            SessionSaveBottomBar(
                canSave = state.canSave,
                disabledReason = state.saveDisabledReason,
                isSaving = state.isSaving,
                buttonText = "保存这次记录",
                onSave = viewModel::onSaveClicked,
                embedded = true
            )
        }
    }
}

@Composable
private fun WarmChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        shape = MaterialTheme.shapes.large,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = if (selected) {
            null
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = MaterialTheme.colorScheme.outline
            )
        }
    )
}

@Composable
private fun DashedAddGroupButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val dashColor = TerracottaDashed
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
            tint = Terracotta700,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "再加一组",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Terracotta700
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Sage200),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppDimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
        ) {
            Text(
                groupLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Sage900
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    avgText,
                    fontSize = 32.sp,
                    color = Sage900
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "mmHg",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sage900,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                avgPulse?.let {
                    Spacer(Modifier.width(AppSpacing.medium))
                    Text(
                        "脉搏 $it 次/分",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sage900,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Text(
                averageComment(categoryLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = Sage900
            )
        }
    }
}

private fun averageComment(categoryLabel: String): String = when {
    categoryLabel == "正常" -> "数值很平稳，记得保持规律作息。"
    categoryLabel == "正常高值" -> "比理想值稍高一点，注意休息，隔几分钟再看看。"
    categoryLabel == "血压偏低" -> "数值偏低，如有头晕乏力请坐下休息。"
    categoryLabel.contains("高血压") -> "数值偏高，休息几分钟再复测一次会更放心。"
    else -> "已按最新读数自动计算平均值。"
}
