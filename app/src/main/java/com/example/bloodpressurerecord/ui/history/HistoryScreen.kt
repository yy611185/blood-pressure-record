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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.bloodpressurerecord.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.Sage200
import com.example.bloodpressurerecord.ui.theme.Sage300
import com.example.bloodpressurerecord.ui.theme.Sage900
import com.example.bloodpressurerecord.ui.theme.Terracotta300
import com.example.bloodpressurerecord.ui.theme.Terracotta600
import com.example.bloodpressurerecord.ui.theme.Terracotta700
import com.example.bloodpressurerecord.ui.theme.Terracotta800
import com.example.bloodpressurerecord.ui.theme.Terracotta900
import com.example.bloodpressurerecord.ui.theme.WarmTextFaint
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

    val topBarScroll = rememberHideOnScrollState()
    val listState = rememberLazyListState()
    // 日历卡较高，快速回滚到列表顶部时部分设备不会再派发足够的反向滚动量。
    // 以列表真实位置兜底复位，确保“历史记录”标题一定能重新显示。
    LaunchedEffect(listState.canScrollBackward) {
        if (!listState.canScrollBackward) topBarScroll.expand()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarScroll.nestedScrollConnection)
    ) {
        AppTopBar(title = stringResource(R.string.history_title), hideOnScroll = topBarScroll)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimensions.pageHorizontalPadding,
                end = AppDimensions.pageHorizontalPadding,
                bottom = AppSpacing.xLarge
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            item {
                HistoryModeSelector(
                    selected = uiState.viewMode,
                    onSelected = viewModel::setViewMode
                )
            }

            if (uiState.viewMode == HistoryViewMode.CALENDAR) {
                if (uiState.daySummaries.isNotEmpty()) {
                    item {
                        MonthEncouragementBar(
                            month = uiState.displayedMonth.monthValue,
                            recordedDays = uiState.daySummaries.size
                        )
                    }
                }
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
            } else {
                item {
                    RecentPeriodSelector(
                        selected = uiState.recentPeriod,
                        onSelected = viewModel::setRecentPeriod
                    )
                }
                when (uiState.recentState) {
                    CalendarLoadingState.LOADING -> item {
                        CalendarMessage("正在加载近期记录…")
                    }
                    CalendarLoadingState.ERROR -> item {
                        ErrorState(
                            message = uiState.recentError ?: "无法加载近期记录。",
                            onRetry = viewModel::retryRecent
                        )
                    }
                    CalendarLoadingState.CONTENT -> {
                        item {
                            RecentSummaryCard(
                                period = uiState.recentPeriod,
                                summary = uiState.recentSummary
                            )
                        }
                        if (uiState.recentRecords.isEmpty()) {
                            item { EmptyRecentState(onAddMeasurement) }
                        } else {
                            uiState.recentRecords
                                .groupBy { it.measuredDate }
                                .forEach { (date, records) ->
                                    item(key = "date-$date") {
                                        Text(
                                            date.format(DateTimeFormatter.ofPattern("M月d日 EEEE")),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    items(records, key = { it.id }) { session ->
                                        HistorySessionCard(
                                            session = session,
                                            showDate = true,
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
}

@Composable
private fun MonthEncouragementBar(month: Int, recordedDays: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Sage200, MaterialTheme.shapes.large)
            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.WbSunny,
            contentDescription = null,
            tint = Terracotta600,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(AppSpacing.small))
        Text(
            "$month 月你已经记录了 $recordedDays 天，真不错",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Sage900
        )
    }
}

@Composable
private fun HistoryModeSelector(
    selected: HistoryViewMode,
    onSelected: (HistoryViewMode) -> Unit
) {
    SelectionRow(
        options = listOf(
            HistoryViewMode.CALENDAR to stringResource(R.string.history_mode_calendar),
            HistoryViewMode.RECENT to stringResource(R.string.history_mode_recent)
        ),
        selected = selected,
        onSelected = onSelected
    )
}

@Composable
private fun RecentPeriodSelector(
    selected: RecentPeriod,
    onSelected: (RecentPeriod) -> Unit
) {
    SelectionRow(
        options = listOf(
            RecentPeriod.THIS_WEEK to stringResource(R.string.history_period_week),
            RecentPeriod.THIS_MONTH to stringResource(R.string.history_period_month)
        ),
        selected = selected,
        onSelected = onSelected
    )
}

@Composable
private fun <T> SelectionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.shapes.large
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface
                        else Color.Transparent
                    )
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = if (isSelected) "$label，已选择" else label
                    }
                    .clickable { onSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        Terracotta800
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RecentSummaryCard(
    period: RecentPeriod,
    summary: RecentSummary?
) {
    val title = if (period == RecentPeriod.THIS_WEEK) {
        stringResource(R.string.history_period_week)
    } else {
        stringResource(R.string.history_period_month)
    }
    DataCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (summary != null) {
                Text(
                    "${summary.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} 至 " +
                        summary.endDateInclusive.format(DateTimeFormatter.ofPattern("M月d日")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("测量次数：${summary.recordCount} 次")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
                ) {
                    SummaryValue(
                        label = "平均收缩压",
                        value = summary.averageSystolic?.let { "$it mmHg" } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryValue(
                        label = "平均舒张压",
                        value = summary.averageDiastolic?.let { "$it mmHg" } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                }
                summary.averagePulse?.let { Text("平均脉搏：$it 次/分") }
                Text("高风险记录：${summary.highRiskCount} 条")
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    // 与 CalendarMonthLayout 一致：周一开头。
    val weekLabels = listOf(
        stringResource(R.string.weekday_monday),
        stringResource(R.string.weekday_tuesday),
        stringResource(R.string.weekday_wednesday),
        stringResource(R.string.weekday_thursday),
        stringResource(R.string.weekday_friday),
        stringResource(R.string.weekday_saturday),
        stringResource(R.string.weekday_sunday)
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

        CalendarLegend()
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
    // 暖阳设计：38dp 圆形日期，靠底色区分状态。
    val background = when {
        selected -> Terracotta600
        summary?.containsHighRisk == true -> Terracotta300
        enabled -> Sage300
        else -> Color.Transparent
    }
    val contentColor = when {
        selected -> Color(0xFFFFF7EF)
        summary?.containsHighRisk == true -> Terracotta900
        enabled -> Sage900
        else -> WarmTextFaint
    }
    var cellModifier = modifier
        .heightIn(min = AppDimensions.calendarDayMinHeight)
        .semantics {
            contentDescription = description
            role = Role.Button
            this.selected = selected
            if (!enabled) disabled()
        }
    if (enabled) {
        cellModifier = cellModifier.clickable(onClick = onClick)
    }

    Box(cellModifier, contentAlignment = Alignment.Center) {
        var circleModifier = Modifier
            .size(AppDimensions.calendarDaySize)
            .clip(CircleShape)
            .background(background)
        if (today) {
            circleModifier = circleModifier.border(
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                CircleShape
            )
        }
        Box(circleModifier, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = if (enabled || selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/** 日历图例：有记录 / 含偏高读数 / 选中。 */
@Composable
internal fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
    ) {
        LegendItem(color = Sage300, label = "有记录")
        LegendItem(color = Terracotta300, label = "含偏高读数")
        LegendItem(color = Terracotta600, label = "选中")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
fun HistorySessionCard(
    session: HistorySessionItemUi,
    onClick: () -> Unit,
    showDate: Boolean = false
) {
    val visualStatus = bloodPressureVisualStatus(
        category = session.category,
        containsHighRiskReading = session.containsHighRiskReading
    )
    // 暖阳设计 3c：紧凑行卡——左侧时间·场景与脉搏，右侧数值与状态药丸。
    DataCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        buildString {
                            if (showDate) {
                                append(session.measuredDate.format(DateTimeFormatter.ofPattern("MM-dd")))
                                append(" ")
                            }
                            append(session.measuredAtText)
                            append(" · ")
                            append(session.scene)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "脉搏 ${session.avgPulseText} 次/分",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        session.avgBloodPressureText,
                        fontSize = 22.sp,
                        fontFamily = NumberFontFamily,
                        color = Terracotta700
                    )
                    StatusChip(
                        text = if (session.containsHighRiskReading) "含高风险读数" else session.categoryText,
                        isAbnormal = visualStatus.name != "NORMAL",
                        status = visualStatus
                    )
                }
            }
            if (session.noteSummary != HistoryViewModel.NO_NOTE_TEXT) {
                Text(
                    session.noteSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun EmptyRecentState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Text("当前范围还没有血压记录", style = MaterialTheme.typography.titleMedium)
        AppPrimaryButton(
            text = stringResource(R.string.add_measurement),
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
