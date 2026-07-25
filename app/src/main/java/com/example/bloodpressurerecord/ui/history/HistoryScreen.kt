package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit,
    onAddMeasurement: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMonthPicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    if (showMonthPicker) {
        val initialMillis = uiState.displayedMonth.atDay(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showMonthPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.showMonth(YearMonth.from(date))
                        }
                        showMonthPicker = false
                    }
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showMonthPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("选择年月") })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(title = stringResource(R.string.history_title))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimensions.pageHorizontalPadding,
                end = AppDimensions.pageHorizontalPadding,
                bottom = AppSpacing.xLarge
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            item {
                DataCard {
                    CalendarMonth(
                        month = uiState.displayedMonth,
                        summaries = uiState.daySummaries,
                        selectedDate = uiState.selectedDate,
                        today = today,
                        onPreviousMonth = viewModel::showPreviousMonth,
                        onNextMonth = viewModel::showNextMonth,
                        onChooseMonth = { showMonthPicker = true },
                        onDateSelected = viewModel::selectDate
                    )
                }
            }

            when (uiState.monthState) {
                CalendarLoadingState.LOADING -> item {
                    CalendarMessage("正在加载本月记录…")
                }
                CalendarLoadingState.ERROR -> item {
                    ErrorState(
                        message = uiState.monthError ?: "无法加载本月记录。",
                        onRetry = viewModel::retryMonth
                    )
                }
                CalendarLoadingState.CONTENT -> {
                    when {
                        !uiState.monthHasRecords -> item {
                            EmptyMonthState(onAddMeasurement)
                        }
                        uiState.selectedDate == null -> item {
                            CalendarMessage(stringResource(R.string.calendar_select_recorded_day))
                        }
                        uiState.dayState == CalendarLoadingState.ERROR -> item {
                            ErrorState(
                                message = uiState.dayError ?: "无法加载当天记录。",
                                onRetry = viewModel::retryDay
                            )
                        }
                        else -> {
                            item {
                                SelectedDaySummary(uiState)
                            }
                            items(
                                items = uiState.selectedDayRecords,
                                key = { it.id }
                            ) { session ->
                                HistorySessionCard(
                                    session = session,
                                    onClick = { onOpenDetail(session.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonth(
    month: YearMonth,
    summaries: Map<LocalDate, CalendarDaySummary>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onChooseMonth: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit
) {
    val weekLabels = listOf(
        stringResource(R.string.weekday_sunday),
        stringResource(R.string.weekday_monday),
        stringResource(R.string.weekday_tuesday),
        stringResource(R.string.weekday_wednesday),
        stringResource(R.string.weekday_thursday),
        stringResource(R.string.weekday_friday),
        stringResource(R.string.weekday_saturday)
    )
    val weeks = remember(month) {
        CalendarMonthLayout.cells(month).chunked(CalendarMonthLayout.COLUMN_COUNT)
    }
    val currentOnDateSelected by rememberUpdatedState(onDateSelected)
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(AppDimensions.minimumTouchTarget)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_month))
            }
            TextButton(
                onClick = onChooseMonth,
                modifier = Modifier.semantics {
                    contentDescription = "当前显示${month.year}年${month.monthValue}月，点击选择年月"
                }
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.xSmall))
                Text(
                    "${month.year}年${month.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(AppDimensions.minimumTouchTarget)
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_month))
            }
        }

        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val date = cell.date
                    if (date == null) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .heightIn(min = AppDimensions.calendarDayMinHeight)
                        )
                    } else {
                        val onCellClick = remember(date) {
                            { currentOnDateSelected(date) }
                        }
                        CalendarDay(
                            date = date,
                            summary = summaries[date],
                            selected = selectedDate == date,
                            today = today == date,
                            onClick = onCellClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalendarDay(
    date: LocalDate,
    summary: CalendarDaySummary?,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = summary != null
    val description = remember(date, today, summary, selected) {
        buildString {
            append("${date.monthValue}月${date.dayOfMonth}日")
            if (today) append("，今天")
            if (enabled) {
                append("，有${summary?.recordCount}条记录，可选择")
                if (summary?.containsHighRisk == true) append("，包含高风险读数")
            } else {
                append("，无记录，不可选择")
            }
            if (selected) append("，已选择")
        }
    }
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    var dayModifier = modifier
        .heightIn(min = AppDimensions.calendarDayMinHeight)
        .padding(2.dp)
        .clip(MaterialTheme.shapes.small)
        .background(background)
        .semantics {
            contentDescription = description
            role = Role.Button
            this.selected = selected
            if (!enabled) disabled()
        }
    if (today) {
        dayModifier = dayModifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            MaterialTheme.shapes.small
        )
    }
    if (enabled) {
        dayModifier = dayModifier.clickable(onClick = onClick)
    }

    Box(dayModifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal
            )
            when {
                summary?.containsHighRisk == true -> Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(10.dp)
                )
                summary != null -> Box(
                    Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                today -> Text(
                    "今",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SelectedDaySummary(state: HistoryUiState) {
    val date = state.selectedDate ?: return
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                pluralStringResource(
                    R.plurals.measurement_count,
                    state.selectedDayRecords.size,
                    state.selectedDayRecords.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.selectedDayAverageSystolic != null && state.selectedDayAverageDiastolic != null) {
                Text(
                    "当天平均 ${state.selectedDayAverageSystolic} / ${state.selectedDayAverageDiastolic} mmHg",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun HistorySessionCard(session: HistorySessionItemUi, onClick: () -> Unit) {
    val visualStatus = bloodPressureVisualStatus(
        category = when (session.categoryText) {
            "正常" -> "NORMAL"
            "偏高" -> "ELEVATED"
            "1级偏高" -> "STAGE1"
            "2级偏高" -> "STAGE2"
            else -> session.categoryText
        },
        containsHighRiskReading = session.containsHighRiskReading
    )
    DataCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(session.measuredAtText, style = MaterialTheme.typography.titleMedium)
                StatusChip(
                    text = if (session.containsHighRiskReading) "含高风险读数" else session.categoryText,
                    isAbnormal = visualStatus.name != "NORMAL",
                    status = visualStatus
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    session.avgBloodPressureText.replace("/", " / "),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(AppSpacing.small))
                Text("mmHg", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "脉搏 ${session.avgPulseText} 次/分 · ${session.scene}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.noteSummary != HistoryViewModel.NO_NOTE_TEXT) {
                Text(
                    session.noteSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyMonthState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Text(stringResource(R.string.calendar_empty_month), style = MaterialTheme.typography.titleMedium)
        AppPrimaryButton(
            text = "新增测量",
            icon = Icons.Default.Add,
            onClick = onAdd
        )
    }
}

@Composable
private fun CalendarMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.large),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.xSmall))
                Text("重试")
            }
        }
    }
}
