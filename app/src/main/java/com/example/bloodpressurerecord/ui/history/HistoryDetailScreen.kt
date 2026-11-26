package com.example.bloodpressurerecord.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.domain.calculator.BloodPressureRules
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.ExpandableSection
import com.example.bloodpressurerecord.ui.common.MeasurementTags
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val session = uiState.session
    val bottomInset = pageContentBottomPadding()

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }

    if (session == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(top = statusBarTopPadding())) {
                AppTopBar(title = "记录详情", onBack = onBack)
                // UX-04：初态加载时不闪现“未找到记录”
                if (!uiState.loading) {
                    Text(
                        if (uiState.deleted) "记录已删除" else "未找到记录",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("确认删除") },
            text = { Text("${uiState.measuredAtText} · ${session.avgSystolic}/${session.avgDiastolic}。删除后可在提示条里撤销。") },
            confirmButton = {
                TextButton(enabled = !uiState.isDeleting, onClick = viewModel::confirmDelete) {
                    Text(if (uiState.isDeleting) "正在删除…" else "删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !uiState.isDeleting, onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }

    val isAbnormal = session.containsHighRiskReading || session.category.uppercase() != "NORMAL"
    val categoryText = CategoryPresentation.label(session.category)
    val topBarScroll = rememberHideOnScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTopPadding())
                .nestedScroll(topBarScroll.nestedScrollConnection)
        ) {
            AppTopBar(title = "记录详情", onBack = onBack, hideOnScroll = topBarScroll)
            AnimatedContent(
                targetState = uiState.deleted,
                transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
                label = "history_detail_deleted"
            ) { isDeleted ->
                if (isDeleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimensions.pageHorizontalPadding, vertical = 24.dp)
                    ) {
                        Text("记录已删除，可点击底部「撤销」恢复。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppDimensions.pageHorizontalPadding)
                            .padding(top = 16.dp, bottom = bottomInset),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DataCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${uiState.measuredAtText} · ${session.timePeriod ?: session.scene.replace("晨起", "清晨")}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${session.avgSystolic}/${session.avgDiastolic}",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFeatureSettings = TabularFeatureSettings
                                    ),
                                    fontFamily = NumberFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusChip(
                                        text = categoryText,
                                        isAbnormal = isAbnormal,
                                        status = bloodPressureVisualStatus(session.category, false)
                                    )
                                    if (session.containsHighRiskReading) {
                                        StatusChip(
                                            text = "含高风险读数",
                                            isAbnormal = true,
                                            status = bloodPressureVisualStatus(session.category, true)
                                        )
                                    }
                                }
                                Text(
                                    (if (session.averageStrategy == AverageStrategy.DISCARD_FIRST && session.readings.size >= 2)
                                        "弃用第 1 组，取其余 ${session.readings.size - 1} 组平均"
                                    else "${session.readings.size} 组全部平均") +
                                        (session.avgPulse?.let { " · 脉搏 $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFeatureSettings = TabularFeatureSettings
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DataCard {
                            ExpandableSection(
                                title = "原始读数",
                                summary = "共 ${session.readings.size} 组读数",
                                initiallyExpanded = session.readings.size <= 3,
                                stateKey = "history-readings-$sessionId"
                            ) {
                                DetailReadingRow("组别", "高压", "低压", "脉搏", header = true)
                                session.readings.forEachIndexed { index, reading ->
                                    DetailReadingRow(
                                        label = "第 ${index + 1} 组" + if (session.averageStrategy == AverageStrategy.DISCARD_FIRST && index == 0 && session.readings.size >= 2) "（不计）" else "",
                                        systolic = reading.systolic.toString(),
                                        diastolic = reading.diastolic.toString(),
                                        pulse = reading.pulse?.toString() ?: "—",
                                        highRisk = BloodPressureRules.isHighRisk(reading.systolic, reading.diastolic)
                                    )
                                }
                            }
                        }
                        val (symptomTags, factorTags) = remember(session.symptoms) {
                            MeasurementTags.splitSymptomsAndFactors(session.symptoms)
                        }
                        val supplementaryDetails = buildList {
                            session.symptomNote?.takeIf { it.isNotBlank() }?.let { add("不舒服：$it") }
                            session.factorNote?.takeIf { it.isNotBlank() }?.let { add("可能影响血压：$it") }
                            if (session.timePeriod != null && session.scene.isNotBlank() && session.scene != session.timePeriod) {
                                add("原场景：${session.scene}")
                            }
                            if (symptomTags.isNotEmpty()) add("症状：${symptomTags.joinToString("、")}")
                            if (factorTags.isNotEmpty()) add("影响因素：${factorTags.joinToString("、")}")
                            session.note?.takeIf { it.isNotBlank() }?.let { add("“$it”") }
                        }
                        if (supplementaryDetails.isNotEmpty()) {
                            val supplementarySummary = buildList {
                                if (symptomTags.isNotEmpty()) add("症状：${symptomTags.joinToString("、")}")
                                else if (!session.symptomNote.isNullOrBlank()) add("有不舒服记录")
                                if (factorTags.isNotEmpty()) add("影响因素：${factorTags.joinToString("、")}")
                                else if (!session.factorNote.isNullOrBlank()) add("有影响因素记录")
                                if (session.timePeriod != null && session.scene.isNotBlank() && session.scene != session.timePeriod) {
                                    add("原场景：${session.scene}")
                                }
                                if (!session.note.isNullOrBlank()) add("有备注")
                            }.joinToString(" · ")
                            DataCard {
                                ExpandableSection(
                                    title = "补充情况",
                                    summary = supplementarySummary,
                                    stateKey = "history-supplement-$sessionId"
                                ) {
                                    supplementaryDetails.forEach { Text(it) }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 防误删布局重排：突出主按钮「修改记录」，将「删除」置于低误触次级位置
                        AppPrimaryButton(
                            text = "修改记录",
                            onClick = { onEdit(sessionId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = viewModel::requestDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppDimensions.minimumTouchTarget)
                        ) {
                            Text(
                                "删除这条记录",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (uiState.message.isNotBlank() && uiState.messageIsError) {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailReadingRow(
    label: String,
    systolic: String,
    diastolic: String,
    pulse: String,
    header: Boolean = false,
    highRisk: Boolean = false
) {
    val foreground = when {
        highRisk -> MaterialTheme.colorScheme.onErrorContainer
        header -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium, color = foreground)
        listOf(systolic, diastolic, pulse).forEach { value ->
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                fontFamily = if (header) null else NumberFontFamily,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Bold,
                color = foreground
            )
        }
    }
}
