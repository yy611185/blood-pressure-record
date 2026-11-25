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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.97f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
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
    val relationError = systolic != null && diastolic != null && diastolic >= systolic
    val showGroupResult = systolic != null && diastolic != null && !relationError

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
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

            val systolicField: @Composable (Modifier) -> Unit = { fieldModifier ->
                CompactNumberField(
                    value = reading.systolic,
                    onValueChange = onSystolicChange,
                    autoAdvanceMaxTwoDigitPrefix = SessionFormLogic.SYSTOLIC_AUTO_ADVANCE_PREFIX,
                    onAutoAdvance = if (!touchExploration) {
                        { runCatching { diaRequester.requestFocus() } }
                    } else null,
                    onFieldPlaced = onSystolicPlaced,
                    label = "高压",
                    unit = "mmHg",
                    accessibleLabel = "第 ${index + 1} 组收缩压（高压）",
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { runCatching { diaRequester.requestFocus() } }),
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
                    label = "低压",
                    unit = "mmHg",
                    accessibleLabel = "第 ${index + 1} 组舒张压（低压）",
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { runCatching { pulseRequester.requestFocus() } }),
                    isError = relationError,
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
                        .clip(MaterialTheme.shapes.large)
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
            visible = relationError,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Text(
                "低压要小于高压，检查一下再保存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
    val animatedLabelColor by animateColorAsState(
        targetValue = targetLabelColor,
        animationSpec = AppMotion.colorSpec(reducedMotion),
        label = "number_field_label_color"
    )
    val labelColor = if (reducedMotion) targetLabelColor else animatedLabelColor
    // 浅色下 #C2B8AC 与白底/纸底对比度显著增强，深色下直接使用 colors.outline (#7F7973)
    val unfocusedBorder = if (colors.background.red < 0.5f) {
        colors.outline
    } else {
        androidx.compose.ui.graphics.Color(0xFFB3A99D)
    }

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
            shape = MaterialTheme.shapes.large,
            textStyle = TextStyle(
                fontSize = numberSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.onSurface,
                fontFeatureSettings = TabularFeatureSettings
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = unfocusedBorder,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
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
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
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
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
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
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.96f)
    )
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
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = AppMotion.springPress(reducedMotion),
        label = "dashed_add_alpha"
    )
    val contentAlpha = if (reducedMotion) targetAlpha else animatedAlpha
    val dashColor = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
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
            "再加一组",
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
                    shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(20.dp),
                    minLines = 2
                )
                if (showLegacyNoteField && onLegacyNoteChange != null) {
                    OutlinedTextField(
                        value = legacyNote,
                        onValueChange = onLegacyNoteChange,
                        enabled = enabled,
                        label = { Text("原备注") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        minLines = 2
                    )
                }
            }
        }
    }
}

/** 保留供兼容或独立使用。 */
@Composable
fun SessionSupplementFields(
    symptomNote: String,
    factorNote: String,
    onSymptomNoteChange: (String) -> Unit,
    onFactorNoteChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        OutlinedTextField(
            value = symptomNote,
            onValueChange = onSymptomNoteChange,
            label = { Text("有没有不舒服？") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            minLines = 3
        )
        OutlinedTextField(
            value = factorNote,
            onValueChange = onFactorNoteChange,
            label = { Text("有没有可能影响血压的情况？") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            minLines = 3
        )
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
                    "本次平均（$measurementCount 组）",
                    style = MaterialTheme.typography.labelLarge,
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (avgPulse != null) {
                    Text(
                        "脉搏 $avgPulse 次/分",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

/** 保留并完善完成弹窗契约（带分级芯片、完成徽章与柔和倒计时进度条），供测试与可选模式使用。 */
@Composable
fun SessionCompletedDialog(
    avgSystolic: Int?,
    avgDiastolic: Int?,
    measuredAtText: String,
    timePeriod: String,
    deadlineMillis: Long?,
    onEdit: () -> Unit,
    onClose: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val currentOnClose by rememberUpdatedState(onClose)
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var handled by remember(deadlineMillis) { mutableStateOf(false) }
    var remainingSeconds by remember(deadlineMillis) { mutableIntStateOf(5) }
    var progress by remember(deadlineMillis) { mutableFloatStateOf(1f) }
    val close = {
        if (!handled) {
            handled = true
            countdownJob?.cancel()
            onClose()
        }
    }
    val edit = {
        if (!handled) {
            handled = true
            countdownJob?.cancel()
            onEdit()
        }
    }
    LaunchedEffect(deadlineMillis) {
        countdownJob = currentCoroutineContext()[Job]
        deadlineMillis?.let { deadline ->
            val totalMillis = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
            while (!handled) {
                val left = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                remainingSeconds = ceil(left / 1000.0).toInt().coerceAtLeast(1)
                progress = (left.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
                if (left <= 0L) {
                    handled = true
                    currentOnClose()
                    break
                }
                delay(if (reducedMotion) minOf(left, 1000L) else minOf(left, 60L))
            }
        }
    }
    val timestamp = DateTimeInputFormatter.parse(measuredAtText)
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val timestampLabel = if (timestamp?.toLocalDate() == LocalDate.now()) {
        "今天 ${timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } else measuredAtText
    val categoryLabel = if (avgSystolic != null && avgDiastolic != null) {
        CategoryPresentation.label(CategoryCalculator.calculate(avgSystolic, avgDiastolic).name)
    } else null

    AlertDialog(
        onDismissRequest = close,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text("记录完成")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                Text(
                    "${avgSystolic ?: "—"} / ${avgDiastolic ?: "—"} mmHg",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (categoryLabel != null) {
                    StatusChip(text = categoryLabel, isAbnormal = categoryLabel != "正常")
                }
                Text("$timestampLabel · $timePeriod")
                if (deadlineMillis != null) {
                    if (!reducedMotion) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                    Text(
                        "已记入血压本 · $remainingSeconds 秒后自动返回",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("关闭") } },
        dismissButton = { TextButton(onClick = edit) { Text("修改") } }
    )
}
