package com.example.bloodpressurerecord.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.pressableScale
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import java.time.LocalDate

@Composable
internal fun WeekCard(
    state: DashboardUiState,
    onOpenDateRecords: (LocalDate) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    var userSelectedDate by remember(state.today) { mutableStateOf<LocalDate?>(null) }
    val selectedDate = remember(state.week, userSelectedDate) {
        WeekRangeChartMath.resolveSelectedDate(state.week, userSelectedDate)
    }
    val selectedDay = remember(state.week, selectedDate) {
        selectedDate?.let { target -> state.week.firstOrNull { it.date == target && it.hasReadings } }
    }
    val hasAnyWeekReading = remember(state.week) { state.week.any { it.hasReadings } }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag("week-card")
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surface)
            .padding(8.dp)
    ) {
        WeekStreakHeader(state)

        if (state.showTrendChart) {
            if (hasAnyWeekReading && selectedDay != null) {
                Spacer(Modifier.height(10.dp))
                WeekSelectedDayBar(
                    day = selectedDay,
                    today = state.today,
                    onOpenDateRecords = onOpenDateRecords
                )
                Spacer(Modifier.height(6.dp))
                WeekRangeCapsuleChart(
                    state = state,
                    selectedDate = selectedDay.date,
                    onSelectDate = { userSelectedDate = it }
                )
                WeekRangeLegend(
                    normalSystolicCut = state.normalSystolicCut,
                    normalDiastolicCut = state.normalDiastolicCut
                )
            } else {
                WeekEmptyChartState(state)
            }
        } else {
            Text(
                "每天记一记，连续记录会显示在上方。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun WeekStreakHeader(state: DashboardUiState) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val compactThreshold = if (state.showTrendChart) 260.dp else 240.dp
        val compact = maxWidth < compactThreshold * fontScale
        val headerModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 12.dp)

        if (compact) {
            Column(headerModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WeekStreakSummary(state, Modifier.fillMaxWidth())
                if (state.showTrendChart) {
                    WeekAverageSummary(state, alignEnd = false, modifier = Modifier.fillMaxWidth())
                } else {
                    WeekdayIndicators(state, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                headerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeekStreakSummary(state, Modifier.weight(1f))
                if (state.showTrendChart) {
                    WeekAverageSummary(state, alignEnd = true)
                } else {
                    WeekdayIndicators(state, Modifier.width(166.dp))
                }
            }
        }
    }
}

@Composable
private fun WeekStreakSummary(state: DashboardUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val titleText = when {
        state.streakDays <= 0 -> "今天开始"
        state.streakCapped -> "${state.streakDays}+ 天"
        else -> "${state.streakDays} 天"
    }
    val subtitleText = if (state.streakDays <= 0) "每天记一记，养成好习惯" else "连续记录"
    Row(
        modifier.testTag("week-streak-summary"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(25.dp), tint = colors.primary)
        Column {
            Text(
                titleText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = NumberFontFamily,
                    fontSize = if (state.streakDays <= 0) 20.sp else 25.sp,
                    fontFeatureSettings = TabularFeatureSettings
                ),
                color = colors.onPrimaryContainer
            )
            Text(
                subtitleText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekAverageSummary(
    state: DashboardUiState,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val avgSys = state.weekAverageSystolic
    val avgDia = state.weekAverageDiastolic
    val alignment = if (alignEnd) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .testTag("week-average-summary")
            .semantics(mergeDescendants = true) {
                contentDescription = if (avgSys != null && avgDia != null) {
                    val categoryLabel = CategoryPresentation.label(CategoryCalculator.calculate(avgSys, avgDia))
                    "本周平均 $avgSys/$avgDia 毫米汞柱，$categoryLabel"
                } else {
                    "本周平均，暂无记录"
                }
            },
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (avgSys != null && avgDia != null) {
            val category = CategoryCalculator.calculate(avgSys, avgDia)
            val look = gradeLook(category.name)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "本周平均",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                GradeChip(
                    label = CategoryPresentation.label(category),
                    look = look,
                    compact = true,
                    elevatedOnSoft = true
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "$avgSys/$avgDia",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = NumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        fontFeatureSettings = TabularFeatureSettings
                    ),
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    "mmHg",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Medium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            if (state.weekHighRiskCount > 0) {
                Text(
                    "含 ${state.weekHighRiskCount} 次高风险",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onErrorContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.errorContainer)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        } else {
            Text(
                "本周平均",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer
            )
            Text(
                "待记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekSelectedDayBar(
    day: DashboardWeekDay,
    today: LocalDate,
    onOpenDateRecords: (LocalDate) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }

    AnimatedContent(
        targetState = day,
        contentKey = { it.date },
        transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
        label = "week_selected_day_bar"
    ) { current ->
        val sys = current.averageSystolic ?: return@AnimatedContent
        val dia = current.averageDiastolic ?: return@AnimatedContent
        val category = CategoryCalculator.calculate(sys, dia)
        val look = gradeLook(category.name)
        val dateSuffix = if (current.date == today) {
            "今天"
        } else {
            "${current.date.monthValue}/${current.date.dayOfMonth}"
        }
        val countLabel = if (current.sessionCount <= 1) "1次记录" else "${current.sessionCount}次平均"
        val dayMetaText = "周${weekdayLabel(current.date)} · $dateSuffix · $countLabel"

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .testTag("week-selected-day-bar")
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val compact = maxWidth < 250.dp * LocalDensity.current.fontScale
            val infoContent: @Composable (Modifier) -> Unit = { infoModifier ->
                Column(infoModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        dayMetaText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = TabularFeatureSettings
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "$sys/$dia",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = NumberFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = TabularFeatureSettings
                            ),
                            color = colors.onSurface,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        GradeChip(
                            label = CategoryPresentation.label(category),
                            look = look,
                            compact = true
                        )
                        if (current.containsHighRiskReading) {
                            RiskChip()
                        }
                    }
                }
            }
            val actionButton: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .testTag("week-view-day-action")
                        .pressableScale(interactionSource, pressedScale = 0.96f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClickLabel = "查看周${weekdayLabel(current.date)}历史记录",
                            onClick = { onOpenDateRecords(current.date) }
                        )
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "查看当天",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary
                    )
                }
            }

            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    infoContent(Modifier.fillMaxWidth())
                    actionButton()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    infoContent(Modifier.weight(1f))
                    actionButton()
                }
            }
        }
    }
}

@Composable
private fun WeekRangeCapsuleChart(
    state: DashboardUiState,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val reducedMotion = LocalReducedMotion.current
    val textMeasurer = rememberTextMeasurer()

    val axis = remember(state.week, state.normalSystolicCut, state.normalDiastolicCut) {
        WeekRangeChartMath.computeAxis(
            days = state.week,
            normalSysCut = state.normalSystolicCut,
            normalDiaCut = state.normalDiastolicCut
        )
    }

    val valueLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontFamily = NumberFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TabularFeatureSettings
    )
    val axisRefLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontFamily = NumberFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TabularFeatureSettings
    )

    val sampleValueSize = remember(valueLabelStyle, density) {
        textMeasurer.measure("188", style = valueLabelStyle, softWrap = false, maxLines = 1).size
    }
    val sampleAxisRefSize = remember(axisRefLabelStyle, density) {
        textMeasurer.measure("120", style = axisRefLabelStyle, softWrap = false, maxLines = 1).size
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .testTag("week-range-chart")
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val gutterGapPx = with(density) { 4.dp.toPx() }
        val candidateAxisGutterPx = sampleAxisRefSize.width + gutterGapPx
        val columnCount = state.week.size.coerceAtLeast(7)
        val columnSpacingPx = with(density) { WeekSpacing.toPx() }

        // 大字模式（如 320dp + 2.0x）下优先把宽度让给 7 列胶囊与星期轴，自动隐藏易拥挤的微缩数字。
        val showAxisLabels = (totalWidthPx - candidateAxisGutterPx) / columnCount >= with(density) { 34.dp.toPx() }
        val axisGutterDp: Dp = if (showAxisLabels) with(density) { candidateAxisGutterPx.toDp() } else 0.dp
        val plotColumnsWidthPx = (totalWidthPx - with(density) { axisGutterDp.toPx() } - columnSpacingPx * (columnCount - 1))
            .coerceAtLeast(1f)
        val singleColumnWidthPx = plotColumnsWidthPx / columnCount
        val showInlineNumbers = singleColumnWidthPx >= sampleValueSize.width + with(density) { 4.dp.toPx() }

        val labelBandDp = if (showInlineNumbers) {
            with(density) { (sampleValueSize.height + 6.dp.toPx()).toDp() }.coerceAtLeast(18.dp)
        } else {
            10.dp
        }
        val plotCoreHeightDp = 104.dp
        val plotTotalHeightDp = plotCoreHeightDp + labelBandDp * 2

        Box(Modifier.fillMaxWidth()) {
            // 背景参考虚线层（与 7 列使用完全一致的顶部 labelBandDp 与核心 plotCoreHeightDp，严丝合缝对齐）
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(plotTotalHeightDp)
            ) {
                val plotTopPx = labelBandDp.toPx()
                val plotHeightPx = plotCoreHeightDp.toPx()
                val lineStartX = axisGutterDp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                val guideColor = colors.outline.copy(alpha = 0.45f)
                val refTextColor = colors.onSurfaceVariant.copy(alpha = 0.78f)

                val sysRefY = axis.refSystolic?.let {
                    WeekRangeChartMath.valueToY(it, axis, plotTopPx, plotHeightPx)
                }
                val diaRefY = axis.refDiastolic?.let {
                    WeekRangeChartMath.valueToY(it, axis, plotTopPx, plotHeightPx)
                }

                sysRefY?.let { y ->
                    drawLine(
                        color = guideColor,
                        start = Offset(lineStartX, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    if (showAxisLabels) {
                        val layout = textMeasurer.measure(
                            axis.refSystolic.toString(),
                            style = axisRefLabelStyle.copy(color = refTextColor),
                            softWrap = false
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = (lineStartX - layout.size.width - gutterGapPx).coerceAtLeast(0f),
                                y = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                            )
                        )
                    }
                }

                diaRefY?.let { y ->
                    drawLine(
                        color = guideColor,
                        start = Offset(lineStartX, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    val enoughGapFromSys = sysRefY == null || kotlin.math.abs(y - sysRefY) >= sampleAxisRefSize.height + 2.dp.toPx()
                    if (showAxisLabels && enoughGapFromSys) {
                        val layout = textMeasurer.measure(
                            axis.refDiastolic.toString(),
                            style = axisRefLabelStyle.copy(color = refTextColor),
                            softWrap = false
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = (lineStartX - layout.size.width - gutterGapPx).coerceAtLeast(0f),
                                y = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                            )
                        )
                    }
                }
            }

            // 前景 7 日胶囊列 + 底部唯一星期轴
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = axisGutterDp),
                horizontalArrangement = Arrangement.spacedBy(WeekSpacing)
            ) {
                state.week.forEachIndexed { index, day ->
                    val isSelected = selectedDate == day.date && day.hasReadings
                    val isToday = day.date == state.today
                    val isFuture = day.date.isAfter(state.today)
                    val categoryName = day.categoryName()
                    val look = if (day.hasReadings) gradeLook(categoryName) else null

                    WeekRangeDayColumn(
                        index = index,
                        day = day,
                        axis = axis,
                        look = look,
                        isSelected = isSelected,
                        isToday = isToday,
                        isFuture = isFuture,
                        plotTotalHeight = plotTotalHeightDp,
                        labelBand = labelBandDp,
                        plotCoreHeight = plotCoreHeightDp,
                        showInlineNumbers = showInlineNumbers,
                        valueLabelStyle = valueLabelStyle,
                        reducedMotion = reducedMotion,
                        onSelect = { onSelectDate(day.date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekRangeDayColumn(
    index: Int,
    day: DashboardWeekDay,
    axis: WeekRangeAxis,
    look: GradeLook?,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    plotTotalHeight: Dp,
    labelBand: Dp,
    plotCoreHeight: Dp,
    showInlineNumbers: Boolean,
    valueLabelStyle: TextStyle,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val interactionSource = remember { MutableInteractionSource() }

    val targetHighlight = when {
        isSelected && look != null -> look.soft.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val highlightColor by animateColorAsState(
        targetValue = targetHighlight,
        animationSpec = AppMotion.colorSpec(reducedMotion),
        label = "week_col_highlight"
    )

    val plotTopPx = with(density) { labelBand.toPx() }
    val plotHeightPx = with(density) { plotCoreHeight.toPx() }
    val minCapsuleHeightPx = with(density) { 16.dp.toPx() }

    val targetGeometry = if (day.hasReadings) {
        WeekRangeChartMath.computeCapsuleGeometry(
            systolic = day.averageSystolic!!,
            diastolic = day.averageDiastolic!!,
            axis = axis,
            plotTopPx = plotTopPx,
            plotHeightPx = plotHeightPx,
            minCapsuleHeightPx = minCapsuleHeightPx
        )
    } else {
        null
    }

    val animatedTopPx by animateFloatAsState(
        targetValue = targetGeometry?.topPx ?: (plotTopPx + plotHeightPx / 2f),
        animationSpec = AppMotion.springGentle(reducedMotion),
        label = "week_capsule_top"
    )
    val animatedBottomPx by animateFloatAsState(
        targetValue = targetGeometry?.bottomPx ?: (plotTopPx + plotHeightPx / 2f),
        animationSpec = AppMotion.springGentle(reducedMotion),
        label = "week_capsule_bottom"
    )

    val semanticsLabel = buildString {
        append("周")
        append(weekdayLabel(day.date))
        if (isToday) append("，今天")
        if (day.hasReadings) {
            val gradeLabel = CategoryPresentation.label(day.categoryName())
            append("，平均高压 ")
            append(day.averageSystolic)
            append("，低压 ")
            append(day.averageDiastolic)
            append("，")
            append(gradeLabel)
            if (day.containsHighRiskReading) append("，含高风险读数")
        } else if (isFuture) {
            append("，未到日期")
        } else {
            append("，未测量")
        }
    }

    val columnShape = RoundedCornerShape(14.dp)
    val clickableModifier = if (day.hasReadings) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = Role.Button,
            onClickLabel = "查看周${weekdayLabel(day.date)}详情",
            onClick = onSelect
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .testTag("week-day-column-$index")
            .clip(columnShape)
            .background(highlightColor)
            .then(clickableModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsLabel
                selected = isSelected
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(plotTotalHeight)
        ) {
            val centerX = size.width / 2f
            if (day.hasReadings && look != null) {
                val topY = if (reducedMotion && targetGeometry != null) targetGeometry.topPx else animatedTopPx
                val bottomY = if (reducedMotion && targetGeometry != null) targetGeometry.bottomPx else animatedBottomPx
                val capsuleWidth = (if (isSelected) 14.dp else 12.dp).toPx().coerceAtMost(size.width * 0.52f)
                val capRadius = capsuleWidth / 2f
                val endpointRadius = (if (isSelected) 4.4.dp else 3.8.dp).toPx().coerceAtMost(capRadius - 1.dp.toPx())
                val bodyTop = (topY - capRadius).coerceAtLeast(0f)
                val bodyBottom = (bottomY + capRadius).coerceAtMost(size.height)
                val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(capsuleWidth)

                // 胶囊区间体：连接低压与高压
                drawRoundRect(
                    color = look.mid.copy(alpha = if (isSelected) 0.45f else 0.28f),
                    topLeft = Offset(centerX - capRadius, bodyTop),
                    size = Size(capsuleWidth, bodyHeight),
                    cornerRadius = CornerRadius(capRadius, capRadius)
                )
                if (day.containsHighRiskReading) {
                    drawRoundRect(
                        color = colors.error,
                        topLeft = Offset(centerX - capRadius, bodyTop),
                        size = Size(capsuleWidth, bodyHeight),
                        cornerRadius = CornerRadius(capRadius, capRadius),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }

                // 顶端实心圆点：高压（收缩压）
                val systolicDotColor = if (day.containsHighRiskReading) colors.error else look.ink
                drawCircle(
                    color = systolicDotColor,
                    radius = endpointRadius,
                    center = Offset(centerX, topY)
                )

                // 底端空心圆环：低压（舒张压）
                drawCircle(
                    color = colors.surface,
                    radius = endpointRadius,
                    center = Offset(centerX, bottomY)
                )
                drawCircle(
                    color = look.ink,
                    radius = endpointRadius,
                    center = Offset(centerX, bottomY),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 上下数值直接标注（大字宽度不足时由外层自动关闭，由上方读数条承接）
                if (showInlineNumbers) {
                    val sysColor = when {
                        day.containsHighRiskReading -> colors.error
                        isSelected -> look.ink
                        else -> colors.onSurface
                    }
                    val diaColor = if (isSelected) look.ink else colors.onSurfaceVariant
                    val textGapPx = 3.dp.toPx()

                    val sysLayout = textMeasurer.measure(
                        text = day.averageSystolic.toString(),
                        style = valueLabelStyle.copy(
                            color = sysColor,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                        ),
                        softWrap = false,
                        maxLines = 1
                    )
                    val sysTextY = (bodyTop - textGapPx - sysLayout.size.height).coerceAtLeast(0f)
                    drawText(
                        textLayoutResult = sysLayout,
                        topLeft = Offset(
                            x = (centerX - sysLayout.size.width / 2f).coerceIn(0f, (size.width - sysLayout.size.width).coerceAtLeast(0f)),
                            y = sysTextY
                        )
                    )

                    val diaLayout = textMeasurer.measure(
                        text = day.averageDiastolic.toString(),
                        style = valueLabelStyle.copy(
                            color = diaColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        softWrap = false,
                        maxLines = 1
                    )
                    val diaTextY = (bodyBottom + textGapPx)
                        .coerceAtMost((size.height - diaLayout.size.height).coerceAtLeast(0f))
                    drawText(
                        textLayoutResult = diaLayout,
                        topLeft = Offset(
                            x = (centerX - diaLayout.size.width / 2f).coerceIn(0f, (size.width - diaLayout.size.width).coerceAtLeast(0f)),
                            y = diaTextY
                        )
                    )
                }
            } else {
                // 未测/未来日：柔和居中短横占位
                val dashWidth = 10.dp.toPx().coerceAtMost(size.width * 0.4f)
                val dashHeight = 3.dp.toPx()
                val midY = plotTopPx + plotHeightPx * 0.55f
                drawRoundRect(
                    color = colors.outlineVariant.copy(alpha = if (isFuture) 0.45f else 0.8f),
                    topLeft = Offset(centerX - dashWidth / 2f, midY - dashHeight / 2f),
                    size = Size(dashWidth, dashHeight),
                    cornerRadius = CornerRadius(dashHeight / 2f, dashHeight / 2f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 底部唯一星期轴 + 打卡点指示
        val pillBg = when {
            isToday -> colors.primary.copy(alpha = 0.18f)
            isSelected && look != null -> look.ink.copy(alpha = 0.14f)
            else -> Color.Transparent
        }
        val labelColor = when {
            isToday -> colors.onPrimaryContainer
            isSelected && look != null -> look.ink
            day.recorded -> colors.onSurface
            isFuture -> colors.onSurfaceVariant.copy(alpha = 0.55f)
            else -> colors.onSurfaceVariant
        }
        val weekdayFontSize = if (LocalDensity.current.fontScale >= 1.5f) 10.sp else 11.sp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(pillBg)
                .then(
                    if (isToday) Modifier.border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                    else Modifier
                )
                .padding(horizontal = 2.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                weekdayLabel(day.date),
                modifier = Modifier.testTag("week-day-label-$index"),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = weekdayFontSize),
                fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(Modifier.height(3.dp))
        val dotColor = when {
            day.containsHighRiskReading -> colors.error
            day.recorded && look != null -> look.mid
            day.recorded -> colors.primary
            else -> Color.Transparent
        }
        Box(
            Modifier
                .size(4.dp)
                .background(dotColor, CircleShape)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekRangeLegend(
    normalSystolicCut: Int,
    normalDiastolicCut: Int
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Canvas(Modifier.size(8.dp)) {
                drawCircle(color = colors.onSurfaceVariant, radius = size.minDimension / 2f)
            }
            Text(
                "实心高压",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Canvas(Modifier.size(8.dp)) {
                drawCircle(
                    color = colors.onSurfaceVariant,
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
            Text(
                "空心低压 · 日均值（虚线 $normalSystolicCut/$normalDiastolicCut）",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularFeatureSettings),
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekEmptyChartState(state: DashboardUiState) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("week-empty-state")
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "本周还没有测量记录，记一次血压后这里会展示每天的高低压区间变化。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        WeekdayIndicators(state, Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeekdayIndicators(state: DashboardUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.testTag("week-day-indicators"),
        horizontalArrangement = Arrangement.spacedBy(WeekSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.week.forEachIndexed { index, day ->
            val today = day.date == state.today
            val future = day.date.isAfter(state.today)
            val container = when {
                today -> colors.primary.copy(alpha = 0.22f)
                day.recorded -> colors.primary
                future -> colors.surfaceContainerHighest
                else -> colors.primary.copy(alpha = 0.18f)
            }
            val content = when {
                today -> colors.onPrimaryContainer
                day.recorded -> colors.onPrimary
                future -> colors.onSurfaceVariant.copy(alpha = 0.55f)
                else -> colors.onPrimaryContainer
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(container)
                    .then(if (today) Modifier.border(1.dp, colors.primary, RoundedCornerShape(8.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    weekdayLabel(day.date),
                    modifier = Modifier.testTag("week-day-label-$index"),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = content
                )
            }
        }
    }
}

/** 自然周固定 7 格，格间距统一。 */
private val WeekSpacing = 2.dp

private fun DashboardWeekDay.categoryName(): String =
    if (averageSystolic != null && averageDiastolic != null) {
        CategoryCalculator.calculate(averageSystolic, averageDiastolic).name
    } else {
        ""
    }

/** 自然周固定顺序：周一起，与历史月历及“本周”统计完全统一。 */
private val WeekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

internal fun weekdayLabel(date: LocalDate): String = WeekDayLabels[date.dayOfWeek.value - 1]
