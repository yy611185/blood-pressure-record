package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDateTimePicker(
    measuredAtText: String,
    onMeasuredAtChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val epoch = DateTimeInputFormatter.parse(measuredAtText, zoneId) ?: System.currentTimeMillis()
    val localDateTime = Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDateTime()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var dateTimeError by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val selectedMillis = localDateTime.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            val text = date.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                                " " + localDateTime.toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            val next = DateTimeInputFormatter.parse(text, zoneId)
                            if (next == null) {
                                dateTimeError = "所选本地时间无效，请重新选择。"
                            } else {
                                dateTimeError = null
                                onMeasuredAtChange(DateTimeInputFormatter.format(next, zoneId))
                            }
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(
                state = dateState,
                title = {
                    Text(
                        "选择测量日期",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }

    if (showTimePicker) {
        WheelTimePickerDialog(
            title = "选择测量时间",
            initialHour = localDateTime.hour,
            initialMinute = localDateTime.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val time = LocalTime.of(hour, minute)
                val text = localDateTime.toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE) +
                    " " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
                val next = DateTimeInputFormatter.parse(text, zoneId)
                if (next == null) {
                    dateTimeError = "所选本地时间无效，请重新选择。"
                } else {
                    dateTimeError = null
                    onMeasuredAtChange(DateTimeInputFormatter.format(next, zoneId))
                }
                showTimePicker = false
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp
        val dateButton: @Composable (Modifier) -> Unit = { buttonModifier ->
            DateTimePillButton(
                icon = Icons.Default.CalendarMonth,
                text = localDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                onClick = { showDatePicker = true },
                modifier = buttonModifier
            )
        }
        val timeButton: @Composable (Modifier) -> Unit = { buttonModifier ->
            DateTimePillButton(
                icon = Icons.Default.Schedule,
                text = localDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                onClick = { showTimePicker = true },
                modifier = buttonModifier
            )
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                dateButton(Modifier.fillMaxWidth())
                timeButton(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                dateButton(Modifier.weight(1.8f))
                timeButton(Modifier.weight(1f))
            }
        }
    }
    dateTimeError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DateTimePillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun MeasurementReadingCard(
    index: Int,
    reading: SessionReadingInputUi,
    removable: Boolean,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit,
    onRemove: () -> Unit = {}
) {
    val systolic = reading.systolic.toIntOrNull()
    val diastolic = reading.diastolic.toIntOrNull()
    val relationError = systolic != null && diastolic != null && diastolic >= systolic

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (removable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除第${index + 1}组读数")
                    }
                }
            }
            // 三项等权紧凑布局：普通手机宽度一行三栏；窄屏或大字体自动退化为两行。
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                val stacked = maxWidth.value / fontScale < MinRowWidthForThreeFields
                val numberSize = (MaxNumberFieldFontSize / fontScale)
                    .coerceIn(MinNumberFieldFontSize, MaxNumberFieldFontSize).sp
                val systolicField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    CompactNumberField(
                        value = reading.systolic,
                        onValueChange = onSystolicChange,
                        label = "高压",
                        unit = "mmHg",
                        accessibleLabel = "第 ${index + 1} 组收缩压（高压）",
                        imeAction = ImeAction.Next,
                        numberSize = numberSize,
                        modifier = fieldModifier
                    )
                }
                val diastolicField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    CompactNumberField(
                        value = reading.diastolic,
                        onValueChange = onDiastolicChange,
                        label = "低压",
                        unit = "mmHg",
                        accessibleLabel = "第 ${index + 1} 组舒张压（低压）",
                        imeAction = ImeAction.Next,
                        isError = relationError,
                        numberSize = numberSize,
                        modifier = fieldModifier
                    )
                }
                val pulseField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    CompactNumberField(
                        value = reading.pulse,
                        onValueChange = onPulseChange,
                        label = "脉搏",
                        unit = "次/分",
                        accessibleLabel = "第 ${index + 1} 组脉搏（选填）",
                        imeAction = ImeAction.Done,
                        numberSize = numberSize,
                        modifier = fieldModifier
                    )
                }
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            systolicField(Modifier.weight(1f))
                            diastolicField(Modifier.weight(1f))
                        }
                        pulseField(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        systolicField(Modifier.weight(1f))
                        diastolicField(Modifier.weight(1f))
                        pulseField(Modifier.weight(1f))
                    }
                }
            }
            // 本组结果保持在输入区附近，压缩视觉后依然一眼可读。
            // 只有数值有效（低压 < 高压）时才给出分级，避免出现乐观的错误提示。
            if (systolic != null && diastolic != null && !relationError) {
                val category = CategoryCalculator.calculate(systolic, diastolic)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    Text(
                        "本组：$systolic / $diastolic mmHg",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(
                        text = CategoryPresentation.label(category.name),
                        isAbnormal = category != BloodPressureCategory.NORMAL
                    )
                }
            }
            if (relationError) {
                Text(
                    "低压要小于高压，检查一下再保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (index == 0) {
                Text("建议连续测两次，间隔 1–2 分钟。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

/**
 * 紧凑数值输入栏：标签在上、数字居中、单位在下。
 *
 * 数字字号按 fontScale 反向折算（sp 会再乘一次 fontScale），
 * 因此大字模式下不会溢出或截断，同时保留 ≥48dp 的触摸区域、
 * IME Next/Done、数字与长度限制以及无障碍标签。
 */
@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    accessibleLabel: String,
    imeAction: ImeAction,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                if (next.all(Char::isDigit) && next.length <= 3) onValueChange(next)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            isError = isError,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            textStyle = TextStyle(
                fontSize = numberSize,
                lineHeight = numberSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.onSurface
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                errorContainerColor = colors.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimensions.numberFieldHeight)
                .semantics { contentDescription = accessibleLabel },
            placeholder = {
                Text(
                    "—",
                    fontSize = numberSize,
                    lineHeight = numberSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        )
        Text(
            unit,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 单栏数字字号上限（普通机型）。 */
private const val MaxNumberFieldFontSize = 48f
/** 大字体/窄屏下允许的最小字号。 */
private const val MinNumberFieldFontSize = 32f
/** 一行放下三栏所需的最小可用宽度（dp，已按 fontScale 折算）。 */
private const val MinRowWidthForThreeFields = 300f

/**
 * 表单里的可多选/单选标签。
 *
 * 选中态使用暖色 `primaryContainer` / `onPrimaryContainer`（不再出现突兀的深色块），
 * 未选中态是白色 surface + 暖色描边，整体保持暖阳设计语言。
 */
@Composable
fun SessionChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

/**
 * 表单主操作按钮的“页面内”形态：普通滚动内容的一部分。
 *
 * 刻意**不带**白色 Dock 底色、不固定悬浮、不加 `navigationBarsPadding()` /
 * `imePadding()`：键盘弹出时按钮随内容滚动，不会上浮覆盖正在输入的字段。
 * 底部系统安全区由宿主页面的滚动内容统一负责。
 */
@Composable
fun InlineSessionAction(
    canSave: Boolean,
    disabledReason: String,
    isSaving: Boolean,
    buttonText: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
    ) {
        if (!canSave && disabledReason.isNotBlank()) {
            Text(
                disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppPrimaryButton(
            text = if (isSaving) "正在保存…" else buttonText,
            onClick = onSave,
            enabled = canSave && !isSaving,
            modifier = Modifier.fillMaxWidth().height(AppDimensions.saveButtonHeight)
        )
    }
}

/**
 * 分步表单的固定底栏形态（编辑测量页仍在用）。
 *
 * 只负责底部安全区与背景，**不再使用 `imePadding()`**：底栏一旦随键盘长高，
 * 会被 Scaffold 计入 `innerPadding.bottom` 并把整个表单顶上去。
 */
@Composable
fun SessionSaveBottomBar(
    canSave: Boolean,
    disabledReason: String,
    isSaving: Boolean,
    buttonText: String,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .navigationBarsPadding()
            .padding(
                horizontal = AppSpacing.medium,
                vertical = AppSpacing.medium
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
    ) {
        if (!canSave && disabledReason.isNotBlank()) {
            Text(
                disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppPrimaryButton(
            text = if (isSaving) "正在保存…" else buttonText,
            onClick = onSave,
            enabled = canSave && !isSaving,
            modifier = Modifier.fillMaxWidth().height(AppDimensions.saveButtonHeight)
        )
    }
}

@Composable
fun UnsavedChangesDialog(
    onContinueEditing: () -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueEditing,
        title = { Text("保留未保存内容？") },
        text = { Text("你可以继续编辑、保存草稿后退出，或放弃本次修改。") },
        confirmButton = {
            TextButton(onClick = onContinueEditing) { Text("继续编辑") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSaveDraft) { Text("保存草稿") }
                TextButton(onClick = onDiscard) { Text("放弃") }
            }
        }
    )
}
