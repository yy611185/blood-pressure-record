package com.example.bloodpressurerecord.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppCompactActionPill
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.AppSecondaryButton
import com.example.bloodpressurerecord.ui.common.AppTopBar
import com.example.bloodpressurerecord.ui.common.AppTopBarIconButton
import com.example.bloodpressurerecord.ui.common.DataCard
import com.example.bloodpressurerecord.ui.common.SegmentedPillGroup
import com.example.bloodpressurerecord.ui.common.SegmentedPillSize
import com.example.bloodpressurerecord.ui.common.appMotionColor
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.focusRing
import com.example.bloodpressurerecord.ui.common.pageContentBottomPadding
import com.example.bloodpressurerecord.ui.common.pressableScale
import com.example.bloodpressurerecord.ui.common.rememberHideOnScrollState
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.common.StatusChip
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.LocalBpGradePalette
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import com.example.bloodpressurerecord.domain.model.BloodPressureCategory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val calendarSelectionHalf = tween<Float>(AppMotion.Fast / 2, easing = FastOutSlowInEasing)

private enum class CalendarHeader { MODE, MONTH, DAY }

private fun calendarSessionKey(sessionId: String) = "history:session:$sessionId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDetail: (String) -> Unit,
    onAddMeasurement: () -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onScrollBackwardChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    var showMonthPicker by remember { mutableStateOf(false) }
    // 选中日显式「定位备注」请求：仅作用于发起请求时的当前日期，切换日期/月份/模式立即作废过期请求（AUD-25 / S8）。
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
                            pendingNoteScrollDate = null
                            viewModel.cancelLocation()
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var highlightedTarget by remember { mutableStateOf<HistoryLocation.Pending?>(null) }
    val showCalendarRecords = uiState.viewMode == HistoryViewMode.CALENDAR &&
        uiState.monthState == CalendarLoadingState.CONTENT && uiState.monthHasRecords &&
        uiState.selectedDate != null && uiState.dayState != CalendarLoadingState.ERROR
    val calendarHeaders = remember(uiState.viewMode, showCalendarRecords) {
        buildList {
            add(CalendarHeader.MODE)
            if (uiState.viewMode == HistoryViewMode.CALENDAR) add(CalendarHeader.MONTH)
            if (showCalendarRecords) add(CalendarHeader.DAY)
        }
    }
    val calendarRecords = if (showCalendarRecords) uiState.selectedDayRecords else emptyList()
    val target = location as? HistoryLocation.Pending
    val targetIndex = target?.let { pending ->
        calendarRecords.indexOfFirst { it.id == pending.sessionId }
            .takeIf { it >= 0 }?.plus(calendarHeaders.size)
    } ?: -1
    val locationReady = target != null && uiState.viewMode == HistoryViewMode.CALENDAR &&
        uiState.selectedDate == target.date && uiState.displayedMonth == YearMonth.from(target.date) &&
        uiState.monthState == CalendarLoadingState.CONTENT && uiState.dayState == CalendarLoadingState.CONTENT
    // Read the current layout and location again after scrolling. A Room update can move or remove a card.
    val currentTarget by rememberUpdatedState(target)
    val currentTargetIndex by rememberUpdatedState(targetIndex)
    LaunchedEffect(lifecycle, target, targetIndex, locationReady) {
        if (target == null || !locationReady) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Let the independently observed day and record queries settle after an edit or deletion.
            val found = if (targetIndex < 0) {
                delay(2_000)
                false
            } else {
                withTimeoutOrNull(2_000) {
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
                    val targetKey = calendarSessionKey(target.sessionId)
                    var visible: Boolean
                    do {
                        listState.scrollToItem(targetIndex)
                        visible = listState.layoutInfo.visibleItemsInfo.any { it.key == targetKey }
                        if (!visible) withFrameNanos {}
                    } while (!visible && currentTarget == target && currentTargetIndex == targetIndex)
                    visible
                } ?: false
            }
            if (currentTarget == target && currentTargetIndex == targetIndex) {
                if (viewModel.finishLocation(target, found) && found) highlightedTarget = target
            }
        }
    }
    val highlight = highlightedTarget
    LaunchedEffect(lifecycle, highlight) {
        if (highlight == null) return@LaunchedEffect
        try {
            withTimeoutOrNull(2_000) {
                lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) }
            }
        } finally {
            if (highlightedTarget == highlight) highlightedTarget = null
        }
    }
    // 日历卡较高，快速回滚到列表顶部时部分设备不会再派发足够的反向滚动量。
    // 以列表真实位置兜底复位，确保“历史记录”标题一定能重新显示。
    LaunchedEffect(listState.canScrollBackward, onBack) {
        if (!listState.canScrollBackward) topBarScroll.expand()
        if (onBack == null) {
            onScrollBackwardChanged(listState.canScrollBackward)
        }
    }
    LaunchedEffect(uiState.viewMode, uiState.selectedDate, uiState.displayedMonth) {
        val targetDate = pendingNoteScrollDate ?: return@LaunchedEffect
        if (uiState.viewMode != HistoryViewMode.CALENDAR ||
            uiState.selectedDate != targetDate ||
            uiState.displayedMonth != YearMonth.from(targetDate)
        ) {
            pendingNoteScrollDate = null
        }
    }
    LaunchedEffect(
        pendingNoteScrollDate,
        uiState.viewMode,
        uiState.selectedDate,
        uiState.dayState,
        calendarHeaders,
        calendarRecords
    ) {
        val targetDate = pendingNoteScrollDate ?: return@LaunchedEffect
        if (uiState.viewMode != HistoryViewMode.CALENDAR ||
            uiState.selectedDate != targetDate ||
            uiState.dayState != CalendarLoadingState.CONTENT ||
            calendarRecords.isEmpty()
        ) {
            return@LaunchedEffect
        }
        val notedIndex = calendarRecords.indexOfFirst {
            it.noteSummary != HistoryViewModel.NO_NOTE_TEXT
        }
        if (notedIndex >= 0) {
            listState.animateScrollToItem(calendarHeaders.size + notedIndex)
        }
        pendingNoteScrollDate = null
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTopPadding())
            .then(if (onBack == null) Modifier.nestedScroll(topBarScroll.nestedScrollConnection) else Modifier)
    ) {
        AppTopBar(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            hideOnScroll = topBarScroll.takeIf { onBack == null },
            actions = {
                if (onOpenSearch != null) {
                    AppTopBarIconButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.note_search_title),
                        onClick = onOpenSearch
                    )
                }
            }
        )
        val locationMessage = when (location) {
            HistoryLocation.Loading -> R.string.note_search_locating
            HistoryLocation.Unavailable -> R.string.note_search_location_unavailable
            is HistoryLocation.Pending -> when {
                uiState.monthState == CalendarLoadingState.ERROR || uiState.dayState == CalendarLoadingState.ERROR ->
                    R.string.note_search_location_unavailable
                else -> R.string.note_search_locating
            }
            HistoryLocation.Idle -> null
        }
        if (locationMessage != null) {
            Box(Modifier.padding(horizontal = AppDimensions.pageHorizontalPadding)) {
                CalendarMessage(stringResource(locationMessage))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("history-records-list"),
            contentPadding = PaddingValues(
                start = AppDimensions.pageHorizontalPadding,
                end = AppDimensions.pageHorizontalPadding,
                bottom = if (onBack == null) dockContentBottomPadding() else pageContentBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
        ) {
            calendarHeaders.forEach { header ->
                when (header) {
                    CalendarHeader.MODE -> item(key = "history:mode") {
                        HistoryModeSelector(
                            selected = uiState.viewMode,
                            onSelected = {
                                pendingNoteScrollDate = null
                                viewModel.cancelLocation()
                                viewModel.setViewMode(it)
                            }
                        )
                    }
                    CalendarHeader.MONTH -> item(key = "history:month") {
                        DataCard {
                            CalendarMonth(
                                month = uiState.displayedMonth,
                                summaries = uiState.daySummaries,
                                targetSystolic = uiState.targetSystolic,
                                targetDiastolic = uiState.targetDiastolic,
                                selectedDate = uiState.selectedDate,
                                today = uiState.today,
                                onPreviousMonth = {
                                    pendingNoteScrollDate = null
                                    viewModel.cancelLocation()
                                    viewModel.showPreviousMonth()
                                },
                                onNextMonth = {
                                    pendingNoteScrollDate = null
                                    viewModel.cancelLocation()
                                    viewModel.showNextMonth()
                                },
                                onChooseMonth = { showMonthPicker = true },
                                onJumpToToday = {
                                    pendingNoteScrollDate = null
                                    viewModel.cancelLocation()
                                    viewModel.openDateWhenAvailable(uiState.today)
                                },
                                onDateSelected = {
                                    pendingNoteScrollDate = null
                                    viewModel.cancelLocation()
                                    viewModel.selectDate(it)
                                }
                            )
                        }
                    }
                    CalendarHeader.DAY -> item(key = "history:day") {
                        val notedKeys = remember(calendarRecords) {
                            calendarRecords
                                .filter { it.noteSummary != HistoryViewModel.NO_NOTE_TEXT }
                                .map { calendarSessionKey(it.id) }
                        }
                        val allNotesFullyVisible = notedKeys.isNotEmpty() && run {
                            val layout = listState.layoutInfo
                            val visibleByKey = layout.visibleItemsInfo.associateBy { it.key }
                            layout.viewportEndOffset > 0 && notedKeys.all { key ->
                                val info = visibleByKey[key] ?: return@all false
                                info.offset >= layout.viewportStartOffset &&
                                    info.offset + info.size <= layout.viewportEndOffset
                            }
                        }
                        SelectedDaySummary(
                            state = uiState,
                            showLocateNote = notedKeys.isNotEmpty() && !allNotesFullyVisible,
                            onLocateNote = {
                                uiState.selectedDate?.let { date ->
                                    viewModel.cancelLocation()
                                    pendingNoteScrollDate = date
                                }
                            }
                        )
                    }
                }
            }
            items(calendarRecords, key = { calendarSessionKey(it.id) }) { session ->
                HistorySessionCard(
                    session = session,
                    highlighted = highlight?.sessionId == session.id &&
                        highlight.date == uiState.selectedDate && location == HistoryLocation.Idle,
                    onClick = { onOpenDetail(session.id) }
                )
            }
            if (uiState.viewMode == HistoryViewMode.CALENDAR) {
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
                                if (uiState.selectedDayRecords.isEmpty()) {
                                    item {
                                        DataCard {
                                            Text("这天没有测量记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
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
                        if (uiState.recentRecords.isEmpty()) {
                            item { EmptyRecentState(onAddMeasurement) }
                        } else {
                            item {
                                RecentSummaryCard(
                                    period = uiState.recentPeriod,
                                    summary = uiState.recentSummary
                                )
                            }
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
        onSelected = onSelected,
        size = SegmentedPillSize.Compact
    )
}

@Composable
private fun <T> SelectionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    size: SegmentedPillSize = SegmentedPillSize.Primary
) {
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    // 使用分段胶囊组件，保留一致的视觉与无障碍语义。
    SegmentedPillGroup(
        options = options.map { it.second },
        selectedIndex = selectedIndex,
        onSelect = { index -> options.getOrNull(index)?.let { onSelected(it.first) } },
        size = size,
        horizontalPadding = AppSpacing.small
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (summary != null) {
                    Text(
                        "${summary.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} 至 " +
                            summary.endDateInclusive.format(DateTimeFormatter.ofPattern("M月d日")),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularFeatureSettings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            if (summary != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)
                ) {
                    SummaryValue(
                        label = "平均高压（收缩压）",
                        value = summary.averageSystolic?.let { "$it mmHg" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryValue(
                        label = "平均低压（舒张压）",
                        value = summary.averageDiastolic?.let { "$it mmHg" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                ) {
                    SummaryMetaItem("测量次数", "${summary.recordCount} 次")
                    summary.averagePulse?.let { SummaryMetaItem("平均脉搏", "$it 次/分") }
                    SummaryMetaItem("高风险记录", "${summary.highRiskCount} 次")
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TabularFeatureSettings),
            fontFamily = NumberFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SummaryMetaItem(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularFeatureSettings),
            fontFamily = NumberFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
    onJumpToToday: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
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
    val currentOnDateSelected by rememberUpdatedState(onDateSelected)
    val monthRecordCount = summaries.values.sumOf { it.recordCount }
    val targetDays = if (targetSystolic != null && targetDiastolic != null) {
        summaries.values.count {
            val systolic = it.averageSystolic
            val diastolic = it.averageDiastolic
            systolic != null && diastolic != null && systolic < targetSystolic &&
                diastolic < targetDiastolic && systolic >= 90 && diastolic >= 60
        }
    } else null
    val showBackToToday = month != YearMonth.from(today) || (selectedDate != null && selectedDate != today)

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = month,
                            transitionSpec = {
                                AppMotion.directionalSlideFade(
                                    forward = targetState > initialState,
                                    distancePx = 32,
                                    reducedMotion = reducedMotion
                                )
                            },
                            label = "calendarMonthTitle"
                        ) { displayedMonth ->
                            Text(
                                "${displayedMonth.year}年${displayedMonth.monthValue}月",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFeatureSettings = TabularFeatureSettings
                                ),
                                fontFamily = NumberFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        if (targetDays == null) "${monthRecordCount} 次测量" else "${monthRecordCount} 次测量 · 日均达标 ${targetDays} 天",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularFeatureSettings),
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

        AnimatedVisibility(
            visible = showBackToToday,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(
                    onClick = onJumpToToday,
                    modifier = Modifier.heightIn(min = AppDimensions.minimumTouchTarget)
                ) {
                    Text("回到今天", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
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

        AnimatedContent(
            targetState = month,
            transitionSpec = {
                AppMotion.directionalSlideFade(
                    forward = targetState > initialState,
                    distancePx = 56,
                    reducedMotion = reducedMotion
                )
            },
            label = "calendarMonthGrid"
        ) { displayedMonth ->
            val weeks = remember(displayedMonth) {
                CalendarMonthLayout.cells(displayedMonth).chunked(CalendarMonthLayout.COLUMN_COUNT)
            }
            Column {
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
                                key(date) {
                                    CalendarDay(
                                        date = date,
                                        summary = summaries[date],
                                        selected = selectedDate == date,
                                        today = today == date,
                                        currentDate = today,
                                        onClick = onCellClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        CalendarLegend()
    }
}

private class SelectionPulseTracker(initialSelected: Boolean) {
    private var wasSelected = initialSelected
    private var pulseGeneration = 0

    fun onSelectionObserved(selected: Boolean, reducedMotion: Boolean): Int {
        val justSelected = selected && !wasSelected
        wasSelected = selected
        if (reducedMotion) {
            pulseGeneration = 0
        } else if (justSelected) {
            pulseGeneration += 1
        }
        return pulseGeneration
    }
}

@Composable
internal fun CalendarDay(
    date: LocalDate,
    summary: CalendarDaySummary?,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentDate: LocalDate = LocalDate.now()
) {
    val enabled = !date.isAfter(currentDate)
    val description = remember(date, today, summary, selected, enabled) {
        buildString {
            append("${date.monthValue}月${date.dayOfMonth}日")
            if (today) append("，今天")
            if (summary != null) {
                append("，有${summary.recordCount}次测量，可选择")
                if (summary.averageSystolic != null && summary.averageDiastolic != null) {
                    append("，平均${summary.averageSystolic}/${summary.averageDiastolic}")
                    summary.category?.let { append("，${com.example.bloodpressurerecord.ui.common.CategoryPresentation.label(it)}") }
                }
                if (summary?.containsHighRisk == true) append("，包含高风险读数")
                if (summary?.hasNote == true) append("，含备注")
            } else {
                append(if (enabled) "，无记录，可选择" else "，未来日期，不可选择")
            }
            if (selected) append("，已选择")
        }
    }
    val reducedMotion = LocalReducedMotion.current
    val grade = historyGradeColors(summary?.category, summary?.containsHighRisk == true)
    val gradeBackground = if (summary != null) grade.first else Color.Transparent
    val targetBackground = when {
        selected && summary != null -> lerp(gradeBackground, MaterialTheme.colorScheme.primaryContainer, .06f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .12f)
        else -> gradeBackground
    }
    val background = appMotionColor(
        targetValue = targetBackground,
        reducedMotion = reducedMotion,
        label = "calendarDayBackground"
    )
    val selectionScale = remember(date) { Animatable(1f) }
    val pulseTracker = remember(date) { SelectionPulseTracker(selected) }
    val triggerPulse = pulseTracker.onSelectionObserved(selected, reducedMotion)
    // Only a new selection in normal motion pulses; toggling to 0x mid-pulse resets immediately.
    if (reducedMotion) {
        if (selectionScale.value != 1f) {
            LaunchedEffect(date, reducedMotion) { selectionScale.snapTo(1f) }
        }
    } else if (triggerPulse > 0) {
        LaunchedEffect(date, triggerPulse) {
            selectionScale.snapTo(1f)
            selectionScale.animateTo(1.035f, calendarSelectionHalf)
            selectionScale.animateTo(1f, calendarSelectionHalf)
        }
    }
    val contentColor = when {
        summary != null -> grade.second
        today -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interactionSource = remember { MutableInteractionSource() }
    var cellModifier = modifier
        .heightIn(min = AppDimensions.calendarDayMinHeight)
        .semantics {
            contentDescription = description
            role = Role.Button
            this.selected = selected
            if (!enabled) disabled()
        }
    if (enabled) {
        cellModifier = cellModifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressableScale(interactionSource, enabled = true, pressedScale = 0.92f)
            .focusRing(interactionSource, RoundedCornerShape(15.dp), inset = 2.dp)
    }

    Box(cellModifier, contentAlignment = Alignment.Center) {
        // AUD-05：互斥单层边框（selected -> 2.dp primary，today && !selected -> 1.5.dp outline），
        // 同时通过今日底部主色微条与 ExtraBold 字重在 today && selected 时清晰保留「今天」地标身份。
        val cellShape = RoundedCornerShape(13.dp)
        val circleModifier = Modifier
            .size(AppDimensions.calendarDaySize)
            .graphicsLayer {
                val s = if (reducedMotion) 1f else selectionScale.value
                scaleX = s
                scaleY = s
            }
            .clip(cellShape)
            .background(background)
            .then(
                when {
                    selected -> Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), cellShape)
                    today -> Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), cellShape)
                    else -> Modifier
                }
            )
        Box(circleModifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = NumberFontFamily,
                    color = contentColor,
                    fontWeight = when {
                        today -> FontWeight.ExtraBold
                        summary != null || selected -> FontWeight.Bold
                        else -> FontWeight.Normal
                    }
                )
                if (summary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(summary.recordCount.coerceAtMost(3)) {
                            Box(Modifier.size(3.dp).background(contentColor.copy(alpha = .8f), CircleShape))
                        }
                    }
                }
            }
            if (today) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .size(width = 8.dp, height = 2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        // AUD-12：备注使用沉稳中性暖灰标记，不再使用红色争夺高风险警示语义。
        if (summary?.hasNote == true) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 3.dp)
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
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

/** 日历图例：圆角方色块表达 6 档分级（配 1.2.dp 同级细描边）、中性圆点表达有备注、红色 ! 表达高风险。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarLegend() {
    val palette = LocalBpGradePalette.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        LegendSwatchItem(fillColor = palette.normal.soft, borderColor = palette.normal.mid, label = "正常")
        LegendSwatchItem(fillColor = palette.highNormal.soft, borderColor = palette.highNormal.mid, label = "正常高值")
        LegendSwatchItem(fillColor = palette.stage1.soft, borderColor = palette.stage1.mid, label = "1级")
        LegendSwatchItem(fillColor = palette.stage2.soft, borderColor = palette.stage2.mid, label = "2级")
        LegendSwatchItem(fillColor = palette.stage3.soft, borderColor = palette.stage3.mid, label = "3级")
        LegendSwatchItem(fillColor = palette.low.soft, borderColor = palette.low.mid, label = "偏低")
        LegendDotItem(color = MaterialTheme.colorScheme.onSurfaceVariant, label = "有备注")
        LegendExclamationItem(color = MaterialTheme.colorScheme.error, label = "高风险")
    }
}

@Composable
private fun LegendSwatchItem(fillColor: Color, borderColor: Color, label: String) {
    val swatchShape = RoundedCornerShape(3.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(11.dp)
                .background(fillColor, swatchShape)
                .border(1.2.dp, borderColor, swatchShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDotItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendExclamationItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("!", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = color)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun historyGradeColors(
    category: BloodPressureCategory?,
    containsHighRisk: Boolean
): Pair<Color, Color> {
    val palette = LocalBpGradePalette.current
    return when (category) {
        BloodPressureCategory.NORMAL -> palette.normal.soft to palette.normal.ink
        BloodPressureCategory.HIGH_NORMAL -> palette.highNormal.soft to palette.highNormal.ink
        BloodPressureCategory.STAGE1 -> palette.stage1.soft to palette.stage1.ink
        BloodPressureCategory.STAGE2 -> palette.stage2.soft to palette.stage2.ink
        BloodPressureCategory.STAGE3 -> palette.stage3.soft to palette.stage3.ink
        BloodPressureCategory.LOW -> palette.low.soft to palette.low.ink
        null -> if (containsHighRisk) {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectedDaySummary(
    state: HistoryUiState,
    showLocateNote: Boolean = false,
    onLocateNote: () -> Unit = {}
) {
    val date = state.selectedDate ?: return
    val isToday = date == state.today
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)) +
                if (isToday) " · 今天" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            if (state.selectedDayRecords.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.measurement_count, state.selectedDayRecords.size, state.selectedDayRecords.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showLocateNote) {
                AppCompactActionPill(
                    text = "定位备注",
                    onClick = onLocateNote
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistorySessionCard(
    session: HistorySessionItemUi,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val visualStatus = bloodPressureVisualStatus(category = session.category, containsHighRiskReading = false)
    val riskStatus = bloodPressureVisualStatus(category = session.category, containsHighRiskReading = true)
    val colors = MaterialTheme.colorScheme
    val background = appMotionColor(
        targetValue = if (highlighted) lerp(colors.surface, colors.primaryContainer, 0.65f) else colors.surface,
        reducedMotion = reducedMotion,
        label = "historySessionHighlight"
    )
    val borderColor = appMotionColor(
        targetValue = if (highlighted) colors.primary else Color.Transparent,
        reducedMotion = reducedMotion,
        label = "historySessionBorder"
    )
    val pulseDisplayText = remember(session.avgPulseText) {
        when {
            session.avgPulseText == "--" || session.avgPulseText == "—" -> "脉搏 —"
            session.avgPulseText.endsWith("次/分") -> "脉搏 ${session.avgPulseText}"
            else -> "脉搏 ${session.avgPulseText} 次/分"
        }
    }
    DataCard(
        onClick = onClick,
        containerColor = background,
        modifier = if (highlighted) {
            Modifier.border(1.5.dp, borderColor, MaterialTheme.shapes.large)
        } else {
            Modifier
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val compactStack = maxWidth < 250.dp * fontScale
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = 54.dp)
                                .alignBy(FirstBaseline),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                session.measuredAtText,
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                                fontFamily = NumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                session.scene,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .alignBy(FirstBaseline),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                session.avgBloodPressureText,
                                modifier = Modifier.semantics {
                                    contentDescription = "血压 ${session.avgBloodPressureText} mmHg"
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                                fontFamily = NumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                pulseDisplayText,
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularFeatureSettings),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!compactStack) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StatusChip(text = session.categoryText, isAbnormal = visualStatus.name != "NORMAL", status = visualStatus)
                            if (session.containsHighRiskReading) {
                                StatusChip(text = "高风险", isAbnormal = true, status = riskStatus)
                            }
                        }
                    }
                }
                if (compactStack) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
            text = "记一次血压",
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
            text = "记一次血压",
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
            AppSecondaryButton(text = "重试", onClick = onRetry)
        }
    }
}
