package com.example.bloodpressurerecord.ui.report

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.domain.report.ReportPolicy
import com.example.bloodpressurerecord.domain.report.ReportTemplate
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.DateTimePillButton
import com.example.bloodpressurerecord.ui.common.focusRing
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.pressableScale
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    val container = (context.applicationContext as BloodPressureApplication).appContainer
    LaunchedEffect(viewModel) {
        viewModel.onScreenEntered()
    }

    AnimatedContent(
        targetState = state.artifact != null,
        transitionSpec = {
            AppMotion.directionalSlideFade(
                forward = targetState,
                distancePx = 48,
                reducedMotion = reducedMotion
            )
        },
        label = "report_step_transition"
    ) { showingPreview ->
        if (showingPreview) {
            ReportPreviewScreen(state, viewModel, container.reportCache, onBack)
        } else {
            ReportOptionsScreen(
                state = state,
                viewModel = viewModel,
                onBack = onBack,
                hasProfileIdentity = state.hasProfileIdentity
            )
        }
    }
}

@Composable
fun ReportOptionsScreen(
    state: ReportUiState,
    viewModel: ReportViewModel,
    onBack: () -> Unit,
    hasProfileIdentity: Boolean = true
) {
    val reducedMotion = LocalReducedMotion.current
    var showCustomDates by remember { mutableStateOf(false) }
    if (showCustomDates) {
        CustomPeriodDialog(state, viewModel) { showCustomDates = false }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTopPadding())
    ) {
        AppTopBar(title = "导出医生报告", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = pageContentBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DataCard {
                Column {
                    Text("报告模板", style = MaterialTheme.typography.titleLarge)
                    ReportTemplate.entries.forEach { template ->
                        val subtitle = when (template) {
                            ReportTemplate.GENERAL -> "适合复诊回顾近 7 / 30 / 90 天或自定义时间段"
                            ReportTemplate.HOME_SEVEN_DAY -> "按早晚分列近 7 天记录，便于医生评估一周血压波动"
                        }
                        PeriodChoice(
                            label = template.title,
                            subtitle = subtitle,
                            selected = state.options.template == template,
                            enabled = !state.isGenerating
                        ) {
                            viewModel.selectTemplate(template)
                        }
                    }
                }
            }
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("统计周期", style = MaterialTheme.typography.titleLarge)
                    val periods = if (state.options.template == ReportTemplate.HOME_SEVEN_DAY) listOf(7) else listOf(7, 30, 90)
                    periods.forEach { days ->
                        PeriodChoice("近 $days 天", state.periodPresetDays == days, !state.isGenerating) {
                            viewModel.selectRecentPeriod(days)
                        }
                    }
                    PeriodChoice("自定义", state.periodPresetDays == null, !state.isGenerating) {
                        showCustomDates = true
                    }
                    Text("${state.period.startDate} 至 ${state.period.endDate}")
                    Text(
                        "摘要、逐日明细及补充记录将生成在同一份 PDF 中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // D-03 方案 A：隐私开关前置至首屏卡片，默认不勾选姓名年龄且每次重进重置，点击生成预览一步直达
            DataCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("报告隐私内容", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "仅作用于本次报告，下次进入默认隐藏姓名和年龄。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReportOptionSwitchRow(
                        label = "包含姓名与年龄",
                        subtitle = if (!hasProfileIdentity) "未填写个人资料" else null,
                        checked = state.options.includeIdentity && hasProfileIdentity,
                        enabled = !state.isGenerating && hasProfileIdentity,
                        onCheckedChange = viewModel::setIncludeIdentity
                    )
                    ReportOptionSwitchRow(
                        label = "包含备注",
                        subtitle = null,
                        checked = state.options.includeNotes,
                        enabled = !state.isGenerating,
                        onCheckedChange = viewModel::setIncludeNotes
                    )
                    ReportOptionSwitchRow(
                        label = "包含药品",
                        subtitle = null,
                        checked = state.options.includeMedications,
                        enabled = !state.isGenerating,
                        onCheckedChange = viewModel::setIncludeMedications
                    )
                }
            }
            AnimatedVisibility(
                visible = state.options.template == ReportTemplate.HOME_SEVEN_DAY,
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                Text(
                    ReportPolicy.SEVEN_DAY_RULES,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "报告在本机生成。现有记录未区分测量场景，分级与参考线采用诊室标准；用药仅显示现有药品信息和保留的打卡次数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(
                visible = state.isGenerating,
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                if (reducedMotion) Text("正在生成报告…") else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AppPrimaryButton(
                text = if (state.isGenerating) "正在生成报告…" else "生成预览",
                onClick = viewModel::confirmAndGenerate,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(
                visible = state.message.isNotEmpty(),
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ReportOptionSwitchRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.985f)
            .clip(rowShape)
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, rowShape, enabled = enabled)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
private fun PeriodChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected, enabled, Role.RadioButton, onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(vertical = 10.dp)) {
            Text(label)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 日历点选自定义周期弹窗（UX-17 & 模块五）：
 * - 彻底取消手打 yyyy-MM-dd，通过 DatePickerDialog 点选且自动禁选未来日期
 * - 7 日监测模板仅选开始日期，系统自动算出第 7 天终点
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPeriodDialog(state: ReportUiState, viewModel: ReportViewModel, onDismiss: () -> Unit) {
    val today = state.today
    val isSevenDay = state.options.template == ReportTemplate.HOME_SEVEN_DAY
    val maxStartDate = if (isSevenDay) today.minusDays(6) else today
    var startDate by remember {
        mutableStateOf(
            if (isSevenDay && state.period.startDate.isAfter(maxStartDate)) maxStartDate else state.period.startDate
        )
    }
    var endDate by remember {
        mutableStateOf(
            if (isSevenDay) startDate.plusDays(6) else state.period.endDate.coerceAtMost(today)
        )
    }
    LaunchedEffect(today, isSevenDay) {
        if (startDate.isAfter(maxStartDate)) {
            startDate = maxStartDate
        }
        endDate = if (isSevenDay) {
            startDate.plusDays(6)
        } else {
            endDate.coerceAtMost(today).coerceAtLeast(startDate)
        }
    }
    var pickingTarget by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }

    if (pickingTarget != null) key(pickingTarget, maxStartDate, today) {
        val pickingStart = pickingTarget == "start"
        val initialDate = if (pickingStart) startDate else endDate
        val maxAllowed = if (pickingStart) maxStartDate else today
        val maxMillis = maxAllowed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickingTarget = null },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("reportDatePickerConfirm"),
                    enabled = pickerState.selectedDateMillis?.let { it <= maxMillis } == true,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            if (pickingStart) {
                                startDate = picked
                                if (isSevenDay) {
                                    endDate = picked.plusDays(6)
                                } else if (picked.isAfter(endDate)) {
                                    endDate = picked
                                }
                            } else {
                                endDate = picked
                                if (picked.isBefore(startDate)) {
                                    startDate = picked
                                }
                            }
                            error = ""
                        }
                        pickingTarget = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingTarget = null }) { Text("取消") }
            }
        ) {
            DatePicker(
                state = pickerState,
                modifier = Modifier.testTag("reportDatePicker"),
                title = {
                    Text(
                        if (pickingStart) "选择开始日期" else "选择结束日期",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }

    val dayCount = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

    if (pickingTarget == null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义统计周期") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (isSevenDay) {
                        "选择开始日期后，系统会自动算出连续 7 天的结束日期。"
                    } else {
                        "点选开始和结束日期（包含当天），最多 ${ReportPolicy.MAX_PERIOD_DAYS} 天。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("开始日期", style = MaterialTheme.typography.labelMedium)
                DateTimePillButton(
                    icon = Icons.Default.CalendarMonth,
                    text = startDate.format(formatter),
                    onClick = { pickingTarget = "start" }
                )
                if (isSevenDay) {
                    Text(
                        "结束日期：${endDate.format(formatter)}（共 7 天）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("结束日期", style = MaterialTheme.typography.labelMedium)
                    DateTimePillButton(
                        icon = Icons.Default.CalendarMonth,
                        text = endDate.format(formatter),
                        onClick = { pickingTarget = "end" }
                    )
                    Text(
                        "共 $dayCount 天（最多 ${ReportPolicy.MAX_PERIOD_DAYS} 天）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dayCount in 1..ReportPolicy.MAX_PERIOD_DAYS) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.testTag("reportCustomPeriodConfirm"), onClick = {
                if (viewModel.selectCustomPeriod(startDate, endDate)) {
                    onDismiss()
                } else {
                    error = viewModel.uiState.value.message
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
