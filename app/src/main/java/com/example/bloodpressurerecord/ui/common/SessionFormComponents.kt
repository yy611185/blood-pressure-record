package com.example.bloodpressurerecord.ui.common

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.calculator.MeasurementInputRules
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        runCatching {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        }.getOrNull()
    }
    var enabled by remember(manager) {
        mutableStateOf(manager != null && manager.isEnabled && manager.isTouchExplorationEnabled)
    }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val update = {
            enabled = manager.isEnabled && manager.isTouchExplorationEnabled
        }
        val a11yListener = AccessibilityManager.AccessibilityStateChangeListener { update() }
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { update() }
        manager.addAccessibilityStateChangeListener(a11yListener)
        manager.addTouchExplorationStateChangeListener(touchListener)
        update()
        onDispose {
            manager.removeAccessibilityStateChangeListener(a11yListener)
            manager.removeTouchExplorationStateChangeListener(touchListener)
        }
    }
    return enabled
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDateTimePicker(
    measuredAtText: String,
    onMeasuredAtChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val epoch = DateTimeInputFormatter.parse(measuredAtText, zoneId) ?: System.currentTimeMillis()
    val localDateTime = Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDateTime()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var dateTimeError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            showDatePicker = false
            showTimePicker = false
        }
    }

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
                onClick = { if (enabled) showDatePicker = true },
                enabled = enabled,
                modifier = buttonModifier
            )
        }
        val timeButton: @Composable (Modifier) -> Unit = { buttonModifier ->
            DateTimePillButton(
                icon = Icons.Default.Schedule,
                text = localDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                onClick = { if (enabled) showTimePicker = true },
                enabled = enabled,
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
fun DateTimePillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.97f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, shape, enabled = enabled)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularFeatureSettings),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasurementReadingCard(
    index: Int,
    reading: SessionReadingInputUi,
    removable: Boolean,
    onSystolicChange: (String) -> Unit,
    onDiastolicChange: (String) -> Unit,
    onPulseChange: (String) -> Unit,
    onRemove: () -> Unit = {},
    enabled: Boolean = true,
    systolicFocusRequester: FocusRequester? = null,
    diastolicFocusRequester: FocusRequester? = null,
    pulseFocusRequester: FocusRequester? = null,
    onSystolicPlaced: (() -> Unit)? = null,
    pulseImeAction: ImeAction = ImeAction.Done,
    onAdvanceNextGroup: (() -> Unit)? = null
) {
    val reducedMotion = LocalReducedMotion.current
    val touchExploration = rememberTouchExplorationEnabled()
    val localDiaFocus = remember { FocusRequester() }
    val localPulseFocus = remember { FocusRequester() }
    val diaRequester = diastolicFocusRequester ?: localDiaFocus
    val pulseRequester = pulseFocusRequester ?: localPulseFocus

    val systolic = reading.systolic.toIntOrNull()
    val diastolic = reading.diastolic.toIntOrNull()
    val pulseVal = reading.pulse.toIntOrNull()
    val systolicRangeError = systolic != null && systolic !in MeasurementInputRules.SYSTOLIC_RANGE
    val diastolicRangeError = diastolic != null && diastolic !in MeasurementInputRules.DIASTOLIC_RANGE
    val pulseRangeError = pulseVal != null && pulseVal !in MeasurementInputRules.PULSE_RANGE
    val relationError = systolic != null && diastolic != null && diastolic >= systolic
    val fieldErrorText = when {
        systolicRangeError -> "高压须在 ${MeasurementInputRules.SYSTOLIC_RANGE.first}–${MeasurementInputRules.SYSTOLIC_RANGE.last} mmHg 之间"
        diastolicRangeError -> "低压须在 ${MeasurementInputRules.DIASTOLIC_RANGE.first}–${MeasurementInputRules.DIASTOLIC_RANGE.last} mmHg 之间"
        relationError -> "高压应大于低压，请检查后重试"
        pulseRangeError -> "脉搏须在 ${MeasurementInputRules.PULSE_RANGE.first}–${MeasurementInputRules.PULSE_RANGE.last} 次/分之间"
        else -> null
    }
    val showGroupResult = systolic != null && diastolic != null && fieldErrorText == null

    // AUD-28：保持无卡片包裹的满宽三栏录入布局；仅在有删除按钮时维持 48dp 最小热区，无删除按钮时收紧组标题虚高贴合输入框。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (removable) Modifier.heightIn(min = AppDimensions.minimumTouchTarget)
                    else Modifier.padding(top = 2.dp)
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "第 ${index + 1} 组",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (removable) {
                val deleteInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onRemove,
                    enabled = enabled,
                    interactionSource = deleteInteraction,
                    modifier = Modifier.pressableScale(deleteInteraction, enabled = enabled, pressedScale = 0.9f)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除第${index + 1}组读数")
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val stacked = maxWidth.value / fontScale < MinRowWidthForThreeFields
            val numberSize = (MaxNumberFieldFontSize / fontScale)
                .coerceIn(MinNumberFieldFontSize, MaxNumberFieldFontSize).sp
            val pulseLabel = if (!stacked && maxWidth < 340.dp) "脉搏(选填)" else "脉搏（选填）"
            val systolicLabel = if (stacked) "高压（收缩压）" else "高压"
            val diastolicLabel = if (stacked) "低压（舒张压）" else "低压"

            val systolicField: @Composable (Modifier) -> Unit = { fieldModifier ->
                CompactNumberField(
                    value = reading.systolic,
                    onValueChange = onSystolicChange,
                    autoAdvanceMaxTwoDigitPrefix = SessionFormLogic.SYSTOLIC_AUTO_ADVANCE_PREFIX,
                    onAutoAdvance = if (!touchExploration) {
                        { runCatching { diaRequester.requestFocus() } }
                    } else null,
                    onFieldPlaced = onSystolicPlaced,
                    label = systolicLabel,
                    unit = "mmHg",
                    accessibleLabel = "第 ${index + 1} 组高压（收缩压）",
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { runCatching { diaRequester.requestFocus() } }),
                    isError = systolicRangeError,
                    numberSize = numberSize,
                    enabled = enabled,
                    modifier = fieldModifier.then(
                        if (systolicFocusRequester != null) Modifier.focusRequester(systolicFocusRequester) else Modifier
                    )
                )
            }
            val diastolicField: @Composable (Modifier) -> Unit = { fieldModifier ->
                CompactNumberField(
                    value = reading.diastolic,
                    onValueChange = onDiastolicChange,
                    autoAdvanceMaxTwoDigitPrefix = SessionFormLogic.DIASTOLIC_AUTO_ADVANCE_PREFIX,
                    onAutoAdvance = if (!touchExploration) {
                        { runCatching { pulseRequester.requestFocus() } }
                    } else null,
                    label = diastolicLabel,
                    unit = "mmHg",
                    accessibleLabel = "第 ${index + 1} 组低压（舒张压）",
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { runCatching { pulseRequester.requestFocus() } }),
                    isError = relationError || diastolicRangeError,
                    numberSize = numberSize,
                    enabled = enabled,
                    modifier = fieldModifier.focusRequester(diaRequester)
                )
            }
            val pulseField: @Composable (Modifier) -> Unit = { fieldModifier ->
                CompactNumberField(
                    value = reading.pulse,
                    onValueChange = onPulseChange,
                    autoAdvanceMaxTwoDigitPrefix = SessionFormLogic.PULSE_AUTO_ADVANCE_PREFIX,
                    onAutoAdvance = if (!touchExploration && onAdvanceNextGroup != null) {
                        onAdvanceNextGroup
                    } else null,
                    label = pulseLabel,
                    unit = "次/分",
                    accessibleLabel = "第 ${index + 1} 组脉搏（选填）",
                    imeAction = pulseImeAction,
                    keyboardActions = KeyboardActions(
                        onNext = { onAdvanceNextGroup?.invoke() }
                    ),
                    isError = pulseRangeError,
                    numberSize = numberSize,
                    enabled = enabled,
                    modifier = fieldModifier.focusRequester(pulseRequester)
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
        AnimatedVisibility(
            visible = showGroupResult,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            if (systolic != null && diastolic != null && !relationError) {
                val category = CategoryCalculator.calculate(systolic, diastolic)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    Text(
                        "本组：$systolic / $diastolic mmHg",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularFeatureSettings),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    StatusChip(
                        text = CategoryPresentation.label(category.name),
                        isAbnormal = category != BloodPressureCategory.NORMAL,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = fieldErrorText != null,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            fieldErrorText?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 紧凑数值输入栏：标签在上、数字居中、单位在下。
 * 提升未聚焦描边对比度至 >=3:1（UX-10），获焦时同步点亮上方标签并启用等宽数字。
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
    autoAdvanceMaxTwoDigitPrefix: Int? = null,
    onAutoAdvance: (() -> Unit)? = null,
    onFieldPlaced: (() -> Unit)? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val targetLabelColor = when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.onSurfaceVariant
    }
    val labelColor = appMotionColor(
        targetValue = targetLabelColor,
        reducedMotion = reducedMotion,
        label = "number_field_label_color"
    )

    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val textFieldValue = if (textFieldValueState.text == value) {
        textFieldValueState
    } else {
        TextFieldValue(
            text = value,
            selection = TextRange(
                textFieldValueState.selection.start.coerceIn(0, value.length),
                textFieldValueState.selection.end.coerceIn(0, value.length)
            )
        )
    }
    SideEffect {
        if (textFieldValue.text != textFieldValueState.text ||
            textFieldValue.selection != textFieldValueState.selection ||
            textFieldValue.composition != textFieldValueState.composition
        ) {
            textFieldValueState = textFieldValue
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedTextField(
            value = textFieldValue,
            enabled = enabled,
            interactionSource = interactionSource,
            onValueChange = { nextValue ->
                if (!enabled) return@OutlinedTextField
                val nextText = nextValue.text
                if (nextText.all(Char::isDigit) && nextText.length <= 3) {
                    val prevValue = textFieldValueState
                    textFieldValueState = nextValue
                    if (nextText != prevValue.text) {
                        onValueChange(nextText)
                        if (autoAdvanceMaxTwoDigitPrefix != null &&
                            onAutoAdvance != null &&
                            SessionFormLogic.shouldAutoAdvanceField(
                                previousText = prevValue.text,
                                previousSelectionStart = prevValue.selection.min,
                                previousSelectionEnd = prevValue.selection.max,
                                nextText = nextText,
                                nextSelectionStart = nextValue.selection.min,
                                nextSelectionEnd = nextValue.selection.max,
                                maxTwoDigitPrefix = autoAdvanceMaxTwoDigitPrefix,
                                hasComposition = nextValue.composition != null
                            )
                        ) {
                            onAutoAdvance()
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            isError = isError,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            textStyle = TextStyle(
                fontSize = numberSize,
                lineHeight = (numberSize.value * 1.16f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.onSurface,
                fontFeatureSettings = TabularFeatureSettings
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline,
                errorBorderColor = colors.error,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface.copy(alpha = 0.6f),
                errorContainerColor = colors.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimensions.numberFieldHeight)
                .then(
                    if (onFieldPlaced != null) {
                        Modifier.onPlaced { onFieldPlaced() }
                    } else {
                        Modifier
                    }
                )
                .semantics { contentDescription = accessibleLabel }
        )
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private const val MaxNumberFieldFontSize = 38f
private const val MinNumberFieldFontSize = 32f
private const val MinRowWidthForThreeFields = 300f

@Composable
fun SessionChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val reducedMotion = LocalReducedMotion.current
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val targetContainer = when {
        !enabled && selected -> colors.onSurface.copy(alpha = 0.12f)
        !enabled -> colors.surface.copy(alpha = 0.6f)
        selected -> colors.primaryContainer
        else -> colors.surface
    }
    val targetLabel = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val targetBorder = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        selected -> colors.primary
        else -> colors.outlineVariant
    }
    val containerColor = appMotionColor(targetContainer, reducedMotion, "choice_chip_bg", AppMotion.Micro)
    val labelColor = appMotionColor(targetLabel, reducedMotion, "choice_chip_fg", AppMotion.Micro)
    val borderColor = appMotionColor(targetBorder, reducedMotion, "choice_chip_border", AppMotion.Micro)

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick
            )
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.96f)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .focusRing(interactionSource, shape, enabled = enabled)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

/** 共享虚线「再加一组」按钮（新增页与编辑页 100% 同构复用），弹性最小高度防大字裁切。 */
@Composable
fun DashedAddGroupButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val targetAlpha = if (enabled) 1f else 0.42f
    val contentAlpha = appMotionFloat(
        targetValue = targetAlpha,
        reducedMotion = reducedMotion,
        spec = AppMotion.springPress(reducedMotion = false),
        label = "dashed_add_alpha"
    )
    val dashColor = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
    val pillShape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.98f)
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
            .clip(pillShape)
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, pillShape, enabled = enabled)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = dashColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "添加一组读数",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = dashColor
        )
    }
}

/**
 * 表单主操作按钮：禁用原因使用 AnimatedVisibility 展开收起，
 * 避免输满最后一位数字瞬间按钮突然上跳 ~24dp；高度采用 heightIn 防极端大字裁切。
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
    val reducedMotion = LocalReducedMotion.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
    ) {
        AnimatedVisibility(
            visible = !canSave && disabledReason.isNotBlank(),
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Text(
                disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        AppPrimaryButton(
            text = if (isSaving) "正在保存…" else buttonText,
            onClick = onSave,
            enabled = canSave && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimensions.saveButtonHeight)
        ) {
            AnimatedContent(
                targetState = isSaving,
                transitionSpec = { AppMotion.microFadeThrough(reducedMotion) },
                label = "sessionSaveContent"
            ) { saving ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    if (saving && !reducedMotion) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        if (saving) "正在保存…" else buttonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(
    onContinueEditing: () -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit
) {
    AppThreeActionDialog(
        title = "保留未保存内容？",
        onDismissRequest = onContinueEditing,
        primaryText = "继续编辑",
        onPrimaryClick = onContinueEditing,
        secondaryText = "保存草稿",
        onSecondaryClick = onSaveDraft,
        destructiveText = "放弃",
        onDestructiveClick = onDiscard
    ) {
        Text(
            "你可以继续编辑、保存草稿后退出，或放弃本次修改。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val QuickSymptomTags = listOf("头晕", "头痛", "胸闷", "心慌", "乏力")
private val QuickFactorTags = listOf("没睡好", "刚运动", "漏服药", "情绪紧张", "饮酒或浓茶")

/**
 * 原地折叠展开的「补充情况（选填）」卡片（D-02 方案 A）：
 * 无需整页切走，支持常用词胶囊一键填入/再点收回，并保留自由输入及编辑页旧版 note 兼容入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableSupplementCard(
    symptomNote: String,
    factorNote: String,
    onSymptomNoteChange: (String) -> Unit,
    onFactorNoteChange: (String) -> Unit,
    legacyNote: String = "",
    showLegacyNoteField: Boolean = false,
    onLegacyNoteChange: ((String) -> Unit)? = null,
    enabled: Boolean = true,
    initiallyExpanded: Boolean = false,
    stateKey: String = "supplement_card"
) {
    val filledSummary = remember(symptomNote, factorNote, legacyNote) {
        listOf(symptomNote.trim(), factorNote.trim(), legacyNote.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifBlank { "可点选头晕、没睡好、漏服药等，或自由输入" }
    }
    DataCard {
        ExpandableSection(
            title = if (symptomNote.isNotBlank() || factorNote.isNotBlank() || legacyNote.isNotBlank()) {
                "补充情况（已填写）"
            } else {
                "补充情况（选填）"
            },
            summary = filledSummary,
            initiallyExpanded = initiallyExpanded,
            stateKey = stateKey
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                Text(
                    "有没有不舒服？点一下填入，再点收回",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                ) {
                    QuickSymptomTags.forEach { tag ->
                        SessionChoiceChip(
                            text = tag,
                            selected = SessionFormLogic.containsQuickNoteTag(symptomNote, tag),
                            enabled = enabled,
                            onClick = { onSymptomNoteChange(SessionFormLogic.toggleQuickNoteTag(symptomNote, tag)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = symptomNote,
                    onValueChange = onSymptomNoteChange,
                    enabled = enabled,
                    label = { Text("有没有不舒服？") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    minLines = 2
                )
                Text(
                    "有没有可能影响血压的情况？",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                ) {
                    QuickFactorTags.forEach { tag ->
                        SessionChoiceChip(
                            text = tag,
                            selected = SessionFormLogic.containsQuickNoteTag(factorNote, tag),
                            enabled = enabled,
                            onClick = { onFactorNoteChange(SessionFormLogic.toggleQuickNoteTag(factorNote, tag)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = factorNote,
                    onValueChange = onFactorNoteChange,
                    enabled = enabled,
                    label = { Text("有没有可能影响血压的情况？") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    minLines = 2
                )
                if (showLegacyNoteField && onLegacyNoteChange != null) {
                    OutlinedTextField(
                        value = legacyNote,
                        onValueChange = onLegacyNoteChange,
                        enabled = enabled,
                        label = { Text("原备注") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        minLines = 2
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionAverageCard(
    systolic: Int?,
    diastolic: Int?,
    measurementCount: Int,
    avgPulse: Int? = null,
    categoryLabel: String? = null
) {
    val resolvedCategory = categoryLabel ?: if (systolic != null && diastolic != null) {
        CategoryPresentation.label(CategoryCalculator.calculate(systolic, diastolic).name)
    } else {
        null
    }
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "本次平均（共 $measurementCount 组）",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularFeatureSettings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (resolvedCategory != null && resolvedCategory != "待计算") {
                    StatusChip(
                        text = resolvedCategory,
                        isAbnormal = resolvedCategory != "正常"
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
            ) {
                Text(
                    "${systolic ?: "—"} / ${diastolic ?: "—"} mmHg",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                    fontFamily = NumberFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (avgPulse != null) {
                    Text(
                        "脉搏 $avgPulse 次/分",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                        fontFamily = NumberFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}
