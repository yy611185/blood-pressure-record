package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.example.bloodpressurerecord.ui.common.SegmentedPillGroup
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
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
import com.example.bloodpressurerecord.ui.theme.WarmError
import com.example.bloodpressurerecord.ui.theme.WarmTextFaint
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import androidx.compose.ui.graphics.luminance
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    // 双击带备注红点的日期：选中后等当天记录加载完成，滚动到第一条带备注的记录卡片。
    var pendingNoteScrollDate by remember { mutableStateOf<LocalDate?>(null) }

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
    LaunchedEffect(
        pendingNoteScrollDate,
        uiState.selectedDate,
        uiState.dayState,
        uiState.selectedDayRecords
    ) {
        val targetDate = pendingNoteScrollDate ?: return@LaunchedEffect
        if (uiState.viewMode != HistoryViewMode.CALENDAR ||
            uiState.selectedDate != targetDate ||
            uiState.dayState != CalendarLoadingState.CONTENT ||
            uiState.selectedDayRecords.isEmpty()
        ) {
            return@LaunchedEffect
        }
        // 列表固定前缀：模式切换(1) + 鼓励条(有记录时 1) + 日历卡(1) + 当天摘要(1)。
        val itemsBeforeRecords = 1 +
            (if (uiState.daySummaries.isNotEmpty()) 1 else 0) +
            1 + // 日历卡
            1   // 当天摘要
        val notedIndex = uiState.selectedDayRecords
            .indexOfFirst { it.noteSummary != HistoryViewModel.NO_NOTE_TEXT }
        val targetIndex = itemsBeforeRecords + notedIndex.coerceAtLeast(0)
        listState.animateScrollToItem(targetIndex)
        pendingNoteScrollDate = null
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTopPadding())
            .nestedScroll(topBarScroll.nestedScrollConnection)
    ) {
        AppTopBar(title = stringResource(R.string.history_title), hideOnScroll = topBarScroll)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimensions.pageHorizontalPadding,
                end = AppDimensions.pageHorizontalPadding,
                bottom = dockContentBottomPadding()
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
                item {
                DataCard {
                    CalendarMonth(
                        month = uiState.displayedMonth,
                        summaries = uiState.daySummaries,
                        targetSystolic = uiState.targetSystolic,
                        targetDiastolic = uiState.targetDiastolic,
                        selectedDate = uiState.selectedDate,
                        today = today,
                        onPreviousMonth = viewModel::showPreviousMonth,
                        onNextMonth = viewModel::showNextMonth,
                        onChooseMonth = { showMonthPicker = true },
                        onDateSelected = viewModel::selectDate,
                        onDateDoubleClick = { date ->
                            pendingNoteScrollDate = date
                            viewModel.selectDate(date)
                        }
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
                                if (uiState.selectedDayRecords.isEmpty()) {
                                    item {
                                        DataCard {
                                            Text("这天没有测量记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
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
                                            date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
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
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large)
            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.WbSunny,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(AppSpacing.small))
        Text(
            "$month 月你已经记录了 $recordedDays 天，真不错",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
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
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    // 与「我的 → 平均值怎么算」共用同一个分段胶囊组件，视觉与无障碍语义保持一致。
    SegmentedPillGroup(
        options = options.map { it.second },
        selectedIndex = selectedIndex,
        onSelect = { index -> options.getOrNull(index)?.let { onSelected(it.first) } },
        horizontalPadding = AppSpacing.small
    )
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
    targetSystolic: Int?,
    targetDiastolic: Int?,
    selectedDate: LocalDate?,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onChooseMonth: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
    onDateDoubleClick: (LocalDate) -> Unit
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
    val currentOnDateDoubleClick by rememberUpdatedState(onDateDoubleClick)
    val monthRecordCount = summaries.values.sumOf { it.recordCount }
    val targetDays = if (targetSystolic != null && targetDiastolic != null) {
        summaries.values.count {
            val systolic = it.averageSystolic
            val diastolic = it.averageDiastolic
            systolic != null && diastolic != null && systolic < targetSystolic &&
                diastolic < targetDiastolic && systolic >= 90 && diastolic >= 60
        }
    } else null
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = NumberFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (targetDays == null) "${monthRecordCount} 次测量" else "${monthRecordCount} 次测量 · ${targetDays} 天达标",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onNextMonth,
                enabled = month < YearMonth.from(today),
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
                        val onCellDoubleClick = remember(date) {
                            { currentOnDateDoubleClick(date) }
                        }
                        CalendarDay(
                            date = date,
                            summary = summaries[date],
                            selected = selectedDate == date,
                            today = today == date,
                            currentDate = today,
                            onClick = onCellClick,
                            onDoubleClick = onCellDoubleClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        CalendarLegend()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CalendarDay(
    date: LocalDate,
    summary: CalendarDaySummary?,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentDate: LocalDate = LocalDate.now()
) {
    val enabled = !date.isAfter(currentDate)
    val description = remember(date, today, summary, selected, enabled) {
        buildString {
            append("${date.monthValue}月${date.dayOfMonth}日")
            if (today) append("，今天")
            if (summary != null) {
                append("，有${summary.recordCount}条记录，可选择")
                if (summary.averageSystolic != null && summary.averageDiastolic != null) {
                    append("，平均${summary.averageSystolic}/${summary.averageDiastolic}")
                    summary.category?.let { append("，${com.example.bloodpressurerecord.ui.common.CategoryPresentation.label(it)}") }
                }
                if (summary?.containsHighRisk == true) append("，包含高风险读数")
                if (summary?.hasNote == true) append("，含自定义备注，双击查看")
            } else {
                append(if (enabled) "，无记录，可选择" else "，未来日期，不可选择")
            }
            if (selected) append("，已选择")
        }
    }
    val grade = historyGradeColors(summary?.category, summary?.containsHighRisk == true)
    val background = if (summary != null) grade.first else Color.Transparent
    val contentColor = if (summary != null) grade.second else MaterialTheme.colorScheme.onSurfaceVariant
    var cellModifier = modifier
        .heightIn(min = AppDimensions.calendarDayMinHeight)
        .semantics {
            contentDescription = description
            role = Role.Button
            this.selected = selected
            if (!enabled) disabled()
        }
    if (enabled) {
        cellModifier = cellModifier.combinedClickable(
            onClick = onClick,
            onDoubleClick = onDoubleClick
        )
    }

    Box(cellModifier, contentAlignment = Alignment.Center) {
        var circleModifier = Modifier
            .size(AppDimensions.calendarDaySize)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
        if (today) {
            circleModifier = circleModifier.border(
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                RoundedCornerShape(13.dp)
            )
        }
        if (selected) {
            // 选中态用暖色主色，避免日历里出现近黑色描边。
            circleModifier = circleModifier.border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                RoundedCornerShape(13.dp)
            )
        }
        Box(circleModifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = NumberFontFamily,
                    color = contentColor,
                    fontWeight = if (summary != null || selected) FontWeight.Bold else FontWeight.Normal
                )
                if (summary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(summary.recordCount.coerceAtMost(3)) {
                            Box(Modifier.size(3.dp).background(contentColor.copy(alpha = .8f), CircleShape))
                        }
                    }
                }
            }
        }
        // 备注使用中性文档标记，避免与高风险警示争夺红色语义。
        if (summary?.hasNote == true) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 3.dp)
                .size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        if (summary?.containsHighRisk == true) {
            Text(
                "!",
                modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** 日历图例可作为整体换行，避免大字模式丢项。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarLegend() {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        LegendItem(color = historyGradeColors(BloodPressureCategory.NORMAL, false).first, label = "正常")
        LegendItem(color = historyGradeColors(BloodPressureCategory.HIGH_NORMAL, false).first, label = "正常高值")
        LegendItem(color = historyGradeColors(BloodPressureCategory.STAGE1, false).first, label = "1级")
        LegendItem(color = historyGradeColors(BloodPressureCategory.STAGE2, false).first, label = "2级")
        LegendItem(color = historyGradeColors(BloodPressureCategory.STAGE3, false).first, label = "3级")
        LegendItem(color = historyGradeColors(BloodPressureCategory.LOW, false).first, label = "偏低")
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "有备注")
        LegendItem(color = MaterialTheme.colorScheme.error, label = "高风险")
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun historyGradeColors(
    category: BloodPressureCategory?,
    containsHighRisk: Boolean
): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (category) {
        BloodPressureCategory.NORMAL -> if (dark) Color(0xFF244B3B) to Color(0xFFC4F1D2)
            else Color(0xFFDDF3DF) to Color(0xFF24563A)
        BloodPressureCategory.HIGH_NORMAL -> if (dark) Color(0xFF414B25) to Color(0xFFE7F0AF)
            else Color(0xFFEDF3D2) to Color(0xFF53612D)
        BloodPressureCategory.STAGE1 -> if (dark) Color(0xFF554326) to Color(0xFFF6D79A)
            else Color(0xFFFFEDC9) to Color(0xFF80582A)
        BloodPressureCategory.STAGE2 -> if (dark) Color(0xFF603C2C) to Color(0xFFF8C6A5)
            else Color(0xFFFCE1CC) to Color(0xFF904B2D)
        BloodPressureCategory.STAGE3 -> if (dark) Color(0xFF622D32) to Color(0xFFFFC5C9)
            else Color(0xFFF8D7D8) to Color(0xFF9E363F)
        BloodPressureCategory.LOW -> if (dark) Color(0xFF303F60) to Color(0xFFC9DAFF)
            else Color(0xFFDDE8FA) to Color(0xFF3E5A8C)
        null -> if (containsHighRisk) MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun SelectedDaySummary(state: HistoryUiState) {
    val date = state.selectedDate ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold
        )
        if (state.selectedDayRecords.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.measurement_count, state.selectedDayRecords.size, state.selectedDayRecords.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HistorySessionCard(
    session: HistorySessionItemUi,
    onClick: () -> Unit,
    showDate: Boolean = false
) {
    val visualStatus = bloodPressureVisualStatus(category = session.category, containsHighRiskReading = false)
    val riskStatus = bloodPressureVisualStatus(category = session.category, containsHighRiskReading = true)
    DataCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.width(54.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        session.measuredAtText,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = NumberFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        session.scene,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        session.avgBloodPressureText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = NumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "脉搏 ${session.avgPulseText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusChip(text = session.categoryText, isAbnormal = visualStatus.name != "NORMAL", status = visualStatus)
                    if (session.containsHighRiskReading) {
                        StatusChip(text = "高风险", isAbnormal = true, status = riskStatus)
                    }
                }
            }
            if (session.noteSummary != HistoryViewModel.NO_NOTE_TEXT) {
                Text(
                    "“${session.noteSummary}”",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
