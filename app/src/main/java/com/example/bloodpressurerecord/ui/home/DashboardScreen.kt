package com.example.bloodpressurerecord.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.appMotionColor
import com.example.bloodpressurerecord.ui.common.appMotionFloat
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
import com.example.bloodpressurerecord.ui.common.focusRing
import com.example.bloodpressurerecord.ui.common.pressableScale
import com.example.bloodpressurerecord.ui.common.statusBarTopPadding
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.LocalBpGradePalette
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddMeasurement: () -> Unit,
    onViewTodayRecords: () -> Unit,
    onViewDateRecords: (LocalDate) -> Unit = { onViewTodayRecords() },
    onOpenMedicationSettings: () -> Unit = {},
    onOpenTrend: () -> Unit = {},
    onOpenSessionDetail: (String) -> Unit = {},
    onScrollBackwardChanged: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.canScrollBackward) {
        onScrollBackwardChanged(scrollState.canScrollBackward)
    }
    val bottomInset = dockContentBottomPadding()
    Box(Modifier.fillMaxSize().testTag("dashboardScreen")) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = AppDimensions.pageHorizontalPadding)
                .padding(
                    top = statusBarTopPadding(extra = 12.dp),
                    bottom = bottomInset
                )
        ) {
            GreetingHeader(state)
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = state,
                contentKey = { heroState ->
                    when {
                        heroState.loading -> 0
                        heroState.latest == null -> 1
                        else -> 2
                    }
                },
                transitionSpec = {
                    if (reducedMotion) {
                        AppMotion.fadeThrough(reducedMotion = true)
                    } else {
                        (fadeIn(AppMotion.floatNormal) togetherWith fadeOut(AppMotion.floatNormal)).using(null)
                    }
                },
                label = "Dashboard hero"
            ) { heroState ->
                when {
                    heroState.loading -> EmptyHero("正在读取最近记录…", null, heroState.showBuddy)
                    heroState.latest == null -> EmptyHero(
                        if (heroState.showBuddy) {
                            "你好呀，我是小压！测完血压记下来，我陪你一起看变化。"
                        } else {
                            "还没有血压记录，测完血压后点下方按钮记下来吧。"
                        },
                        onAddMeasurement,
                        heroState.showBuddy
                    )
                    else -> ReadingHero(heroState, onOpenDetail = onOpenSessionDetail)
                }
            }
            SectionHeader("今天的测量", "建议早晚各测一次")
            MeasurementSlotsSection(
                morning = state.todayMorning,
                evening = state.todayEvening,
                onAdd = onAddMeasurement,
                onOpenDetail = onOpenSessionDetail
            )
            AnimatedVisibility(
                visible = state.todayOtherSessions.isNotEmpty(),
                enter = AppMotion.expandVisibilityEnter(reducedMotion),
                exit = AppMotion.expandVisibilityExit(reducedMotion)
            ) {
                val otherCount = state.todayOtherSessions.size
                val otherInteraction = remember { MutableInteractionSource() }
                val otherShape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.small)
                        .clickable(
                            interactionSource = otherInteraction,
                            indication = null,
                            onClick = onViewTodayRecords
                        )
                        .pressableScale(otherInteraction, pressedScale = 0.985f)
                        .clip(otherShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .indication(otherInteraction, LocalIndication.current)
                        .focusRing(otherInteraction, otherShape)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "今天其他时段还测了 $otherCount 次 · 查看详情",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val medicationTrailing = if (state.medicationSlots.isEmpty()) {
                null
            } else {
                "${state.medicationSlots.count { it.taken }}/${state.medicationSlots.size} 已打卡"
            }
            SectionHeader("今日服药", medicationTrailing)
            if (state.medicationSlots.isEmpty()) {
                EmptyMedication(onOpenMedicationSettings)
            } else {
                state.medicationSlots.forEach { slot ->
                    MedicationRow(slot, slot.timeId in state.pendingMedicationTimeIds) {
                        viewModel.toggleMedicationTaken(slot, !slot.taken)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            SectionHeader(
                "这一周",
                if (state.showTrendChart) "看趋势" else null,
                if (state.showTrendChart) onOpenTrend else null
            )
            WeekCard(state, onOpenDateRecords = onViewDateRecords)
        }
    }
}

@Composable
private fun GreetingHeader(state: DashboardUiState) {
    val greeting = when (LocalTime.now().hour) {
        in 0..5 -> "夜深了"
        in 6..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    Text(
        state.today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "$greeting${state.userName?.let { "，$it" } ?: ""}",
        style = MaterialTheme.typography.headlineLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal data class GradeLook(val soft: Color, val ink: Color, val mid: Color, val body: Color, val mood: String)

@Composable
internal fun gradeLook(category: String): GradeLook {
    val visual = LocalBpGradePalette.current.forCategory(category)
    return GradeLook(
        soft = visual.soft,
        ink = visual.ink,
        mid = visual.mid,
        body = visual.body,
        mood = visual.mood
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingHero(
    state: DashboardUiState,
    onOpenDetail: (String) -> Unit = {}
) {
    val latest = state.latest ?: return
    val detail = state.latestSession
    val reducedMotion = LocalReducedMotion.current
    val look = gradeLook(latest.category)
    val animatedSoft = appMotionColor(
        targetValue = look.soft,
        reducedMotion = reducedMotion,
        label = "hero_soft_bg",
        duration = AppMotion.Medium
    )
    val animatedInk = appMotionColor(
        targetValue = look.ink,
        reducedMotion = reducedMotion,
        label = "hero_ink",
        duration = AppMotion.Medium
    )
    val risk = latest.containsHighRiskReading
    val time = Instant.ofEpochMilli(latest.measuredAt).atZone(ZoneId.systemDefault())
    val isToday = time.toLocalDate() == state.today
    val day = if (isToday) "今天" else time.format(DateTimeFormatter.ofPattern("M月d日"))
    val guidance = when {
        risk -> "读数达到高风险范围，请先查看下方提示：若有胸痛、呼吸困难等不适请立即就医；无不适请静坐休息后复测。"
        latest.category.equals("LOW", true) -> "读数偏低，起身慢一点；如感头晕、眼前发黑或乏力，请坐卧休息并及时咨询医生。"
        latest.category.equals("NORMAL", true) -> if (isToday) {
            "本次读数在正常范围，继续保持。"
        } else {
            "上次（$day）血压正常，今天记得量一量。"
        }
        latest.category.equals("HIGH_NORMAL", true) || latest.category.equals("ELEVATED", true) -> if (isToday) {
            "处于正常高值，平时注意清淡饮食、规律作息，继续观察变化。"
        } else {
            "上次（$day）处于正常高值，今天记得规律测量观察变化。"
        }
        latest.category.equals("STAGE1", true) -> if (isToday) {
            "本次读数偏高，请静坐休息片刻再复测；若持续偏高，请咨询医生。"
        } else {
            "上次（$day）读数偏高，记得规律测量并关注变化。"
        }
        latest.category.equals("STAGE2", true) -> "读数明显偏高，请静坐休息后复测；如已遵医嘱服药请注意规律用药，持续偏高或不适请及时联系医生。"
        else -> "本次读数很高，请静坐休息后复测；若伴有胸痛、剧烈头痛等不适或持续偏高，请及时就医。"
    }
    val nextMedication = state.medicationSlots.firstOrNull { !it.taken }
    val messages = listOf(
        guidance,
        "测血压前先静坐休息 5 分钟，读数更准确。",
        if (state.streakDays > 1) {
            if (state.streakCapped) {
                "已经连续记录 ${state.streakDays} 天以上啦，继续保持！"
            } else {
                "已经连续记录 ${state.streakDays} 天，继续保持！"
            }
        } else {
            "平时少盐饮食、睡个好觉，有助于血压平稳。"
        },
        nextMedication?.let { "今天 ${it.timeText} 有「${it.name}」的服药计划，别忘了打卡。" }
            ?: if (state.medicationSlots.isEmpty()) "每天早晚各测一次，记在这里方便回看变化。" else "今天的服药计划都打卡啦，真棒！"
    )
    var messageIndex by remember(latest.id) { mutableIntStateOf(0) }
    var bubbleTapCount by remember(latest.id) { mutableIntStateOf(0) }
    var showRangeInfo by remember { mutableStateOf(false) }
    val advanceBuddyMessage = { messageIndex = (messageIndex + 1) % messages.size }
    val readingInteraction = remember { MutableInteractionSource() }
    val bubbleInteraction = remember { MutableInteractionSource() }

    if (showRangeInfo) {
        AlertDialog(
            onDismissRequest = { showRangeInfo = false },
            title = { Text("医学分级与个人参考区间") },
            text = {
                Text(
                    "1. 上方状态标签（如「正常」「正常高值」「1级高血压」）严格遵循《中国高血压防治指南》六级医学标准，固定不变。\n\n" +
                        "2. 下方四色条为你的个人日常观察参考区间（当前正常上界为 ${state.normalSystolicCut}/${state.normalDiastolicCut} mmHg" +
                        "${if (state.isCustomBpZone) "，已自定义" else "，默认参考"}），可在「我的 → 首页四色条区间」按医嘱或个人习惯微调，不影响医学分级与高风险警示。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showRangeInfo = false }) {
                    Text("知道了")
                }
            }
        )
    }

    // 外层水平内距 14.dp + 非点击兄弟行/点击读数区各自内部 6.dp = 全卡可见内容严格共线于 20.dp 内轴（AUD-06）。
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(animatedSoft)
            .then(if (risk) Modifier.border(2.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(32.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        if (state.showBuddy) {
            val bubbleShape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Buddy(
                    look = look.copy(mood = if (risk) "worried" else look.mood),
                    modifier = Modifier.size(82.dp, 78.dp),
                    onClick = advanceBuddyMessage,
                    externalTapTrigger = bubbleTapCount
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                        .heightIn(min = AppDimensions.minimumTouchTarget)
                        .semantics {
                            stateDescription = "第 ${messageIndex + 1} 条，共 ${messages.size} 条"
                        }
                        .clickable(
                            interactionSource = bubbleInteraction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "切换下一条小压提示",
                            onClick = {
                                bubbleTapCount += 1
                                advanceBuddyMessage()
                            }
                        )
                        .pressableScale(bubbleInteraction, pressedScale = 0.985f)
                        .clip(bubbleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .indication(bubbleInteraction, LocalIndication.current)
                        .focusRing(bubbleInteraction, bubbleShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedContent(
                        targetState = messages[messageIndex],
                        transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
                        label = "Buddy message"
                    ) { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        messages.indices.forEach { dotIndex ->
                            val active = dotIndex == messageIndex
                            Box(
                                Modifier
                                    .size(if (active) 6.dp else 4.dp)
                                    .background(
                                        if (active) look.mid else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 可点击读数区：直达最近一次记录详情，核对原始读数或修改备注一步到位。
        val readingShape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = readingInteraction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "查看最近一次测量详情",
                    onClick = { onOpenDetail(latest.id) }
                )
                .pressableScale(readingInteraction, pressedScale = 0.985f)
                .clip(readingShape)
                .indication(readingInteraction, LocalIndication.current)
                .focusRing(readingInteraction, readingShape)
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "最近一次 · $day ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}${detail?.scene?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = animatedInk,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = animatedInk
                )
            }
            val pulseValue = detail?.avgPulse
            if (pulseValue == null) {
                HeroPressureSingleRowWithUnit(
                    sys = latest.avgSystolic,
                    dia = latest.avgDiastolic,
                    color = animatedInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.xSmall)
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.xSmall),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                    verticalAlignment = Alignment.Bottom
                ) {
                    HeroPressureValue(latest.avgSystolic, latest.avgDiastolic, animatedInk, Modifier.weight(1f))
                    Column(
                        Modifier.padding(bottom = AppSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                    ) {
                        // 去掉 0.7f 透明度衰减以保证 WCAG AA >= 4.5:1 对比度
                        Text("mmHg", style = MaterialTheme.typography.labelMedium, color = animatedInk)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xSmall),
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "脉搏 $pulseValue 次/分"
                            }
                        ) {
                            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp), tint = animatedInk)
                            Text(
                                "$pulseValue 次/分",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                                color = animatedInk
                            )
                        }
                    }
                }
            }
            // UX-03: 始终明确展示《中国高血压防治指南》六级分级芯片，符合 Status Text Rule
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xSmall),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
            ) {
                GradeChip(
                    label = CategoryPresentation.label(latest.category),
                    look = look,
                    elevatedOnSoft = true
                )
                if (risk) RiskChip()
                val count = detail?.readings?.size
                if (count != null) {
                    Text(
                        if (detail.averageStrategy == AverageStrategy.DISCARD_FIRST && count >= 2) {
                            "弃第 1 组 · ${count - 1} 组平均"
                        } else {
                            "$count 组平均"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedInk,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
        HeroPersonalRangeSection(
            state = state,
            onShowInfo = { showRangeInfo = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.small, start = 6.dp, end = 6.dp)
        )
        AnimatedVisibility(
            visible = risk,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Text(
                stringResource(R.string.high_risk_reading_guidance),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 6.dp, end = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(11.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroPersonalRangeSection(
    state: DashboardUiState,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val containerShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "查看个人参考区间说明",
                onClick = onShowInfo
            )
            .pressableScale(interactionSource, pressedScale = 0.99f)
            .clip(containerShape)
            .background(colors.surface.copy(alpha = 0.78f))
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, containerShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "个人参考区间",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant
                )
                if (state.isCustomBpZone) {
                    Text(
                        "已自定义",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier
                    .size(15.dp)
                    .align(Alignment.CenterVertically),
                tint = colors.onSurfaceVariant
            )
        }
        BpRangeBar(state.bpRange)
    }
}

/** 无脉搏单行状态下直接与右侧 mmHg 建立真实字体基线对齐（AUD-06）。 */
@Composable
private fun HeroPressureSingleRowWithUnit(
    sys: Int,
    dia: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.displayLarge
    val unitStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    fun pressureText(scale: Float) = buildAnnotatedString {
        append(sys.toString())
        withStyle(SpanStyle(fontSize = (48 * scale).sp, fontWeight = FontWeight.Light, color = color.copy(alpha = 0.45f))) {
            append("/")
        }
        append(dia.toString())
    }
    BoxWithConstraints(modifier) {
        val gapPx = with(density) { AppSpacing.small.roundToPx() }
        val unitWidth = textMeasurer.measure("mmHg", unitStyle, softWrap = false, maxLines = 1).size.width
        val availablePressureWidth = (constraints.maxWidth - unitWidth - gapPx).coerceAtLeast(1)
        val fullWidth = textMeasurer.measure(pressureText(1f), style, softWrap = false, maxLines = 1).size.width
        val scale = (availablePressureWidth.toFloat() / fullWidth.coerceAtLeast(1)).coerceAtMost(1f)
        Row(Modifier.fillMaxWidth()) {
            Text(
                pressureText(scale),
                modifier = Modifier.alignByBaseline(),
                style = style.copy(fontSize = (58 * scale).sp, lineHeight = (62 * scale).sp),
                color = color,
                softWrap = false,
                maxLines = 1
            )
            Spacer(Modifier.width(AppSpacing.small))
            Text(
                "mmHg",
                modifier = Modifier.alignByBaseline(),
                style = unitStyle,
                color = color,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

/** 为右侧单位与脉搏保留空间，小屏或大字时按实际文字宽度调整血压字号。 */
@Composable
private fun HeroPressureValue(sys: Int, dia: Int, color: Color, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.displayLarge
    fun pressureText(scale: Float) = buildAnnotatedString {
        append(sys.toString())
        withStyle(SpanStyle(fontSize = (48 * scale).sp, fontWeight = FontWeight.Light, color = color.copy(alpha = 0.45f))) {
            append("/")
        }
        append(dia.toString())
    }
    BoxWithConstraints(modifier) {
        val fullWidth = textMeasurer.measure(pressureText(1f), style, softWrap = false, maxLines = 1).size.width
        val scale = (constraints.maxWidth.toFloat() / fullWidth.coerceAtLeast(1)).coerceAtMost(1f)
        Text(
            pressureText(scale),
            style = style.copy(fontSize = (58 * scale).sp, lineHeight = (62 * scale).sp),
            color = color,
            softWrap = false,
            maxLines = 1
        )
    }
}

@Composable
internal fun RiskChip() {
    Text(
        "高风险读数",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onError,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun EmptyHero(message: String, onAdd: (() -> Unit)?, showBuddy: Boolean) {
    val look = gradeLook("STAGE2")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(look.soft)
            .padding(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (showBuddy) Buddy(look.copy(mood = "idle"), Modifier.size(82.dp, 78.dp))
            Box(
                Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onAdd != null) {
            AppPrimaryButton(
                text = "记第一次血压",
                icon = Icons.Default.Add,
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            )
        }
    }
}

@Composable
internal fun SectionHeader(title: String, trailing: String?, onTrailing: (() -> Unit)? = null) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .testTag("section-header-$title")
            .padding(top = 24.dp, bottom = 10.dp, start = 2.dp, end = 2.dp)
    ) {
        val fontScale = LocalDensity.current.fontScale
        val compact = trailing != null && maxWidth < 240.dp * fontScale
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.ExtraBold
                )
                trailing?.let { SectionHeaderTrailing(it, onTrailing) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.ExtraBold
                )
                if (trailing != null) SectionHeaderTrailing(trailing, onTrailing)
            }
        }
    }
}

@Composable
private fun SectionHeaderTrailing(trailing: String, onTrailing: (() -> Unit)?) {
    if (onTrailing == null) {
        Text(
            trailing,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        TextButton(onClick = onTrailing, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MeasurementSlotsSection(
    morning: SessionSummary?,
    evening: SessionSummary?,
    onAdd: () -> Unit,
    onOpenDetail: (String) -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 270.dp * LocalDensity.current.fontScale
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasurementSlot("早上", true, morning, onAdd, onOpenDetail, Modifier.fillMaxWidth())
                MeasurementSlot("晚上", false, evening, onAdd, onOpenDetail, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeasurementSlot("早上", true, morning, onAdd, onOpenDetail, Modifier.weight(1f))
                MeasurementSlot("晚上", false, evening, onAdd, onOpenDetail, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun MeasurementSlot(
    label: String,
    morning: Boolean,
    session: SessionSummary?,
    onAdd: () -> Unit,
    onOpenDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    val shape = RoundedCornerShape(22.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val onClick = {
        if (session == null) onAdd() else onOpenDetail(session.id)
    }
    val base = if (session == null) {
        modifier
            .heightIn(min = 104.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "记录$label 血压",
                onClick = onClick
            )
            .pressableScale(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .border(2.dp, colors.outlineVariant, shape)
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, shape)
    } else {
        modifier
            .heightIn(min = 104.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "查看$label 测量详情",
                onClick = onClick
            )
            .pressableScale(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(colors.surface)
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, shape)
    }
    Column(
        base
            .testTag("measurement-slot-$label")
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(if (morning) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            val time = session?.let {
                Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            } ?: "待测"
            Text(
                "$label · $time",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant
            )
        }
        AnimatedContent(
            targetState = session,
            contentKey = { it?.id },
            transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
            label = "slot_content"
        ) { currentSession ->
            if (currentSession == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("去测一次", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = colors.primary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${currentSession.avgSystolic}/${currentSession.avgDiastolic}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = NumberFontFamily,
                            fontSize = 27.sp,
                            fontFeatureSettings = TabularFeatureSettings
                        ),
                        maxLines = 1
                    )
                    GradeChip(CategoryPresentation.label(currentSession.category), gradeLook(currentSession.category), compact = true)
                }
            }
        }
    }
}

@Composable
internal fun GradeChip(
    label: String,
    look: GradeLook,
    compact: Boolean = false,
    elevatedOnSoft: Boolean = false
) {
    val bg = if (elevatedOnSoft) MaterialTheme.colorScheme.surface.copy(alpha = 0.78f) else look.soft
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 2.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(8.dp).background(look.mid, CircleShape))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = look.ink,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyMedication(onOpenSettings: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpenSettings
            )
            .pressableScale(interactionSource, pressedScale = 0.985f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .indication(interactionSource, LocalIndication.current)
            .focusRing(interactionSource, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("还没有添加药品", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("去添加", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MedicationRow(slot: MedicationSlot, pending: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val checkBackground = appMotionColor(
        targetValue = if (slot.taken) colors.secondaryContainer else colors.primaryContainer,
        reducedMotion = reducedMotion,
        label = "Medication background"
    )
    val checkScale = appMotionFloat(
        targetValue = if (slot.taken) 1f else 0.72f,
        reducedMotion = reducedMotion,
        spec = AppMotion.springCheck(reducedMotion = false),
        label = "Medication check scale"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            PillGlyph(colors.primary, colors.surface)
        }
        Column(Modifier.weight(1f)) {
            Text(
                slot.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (slot.dosage.isBlank()) slot.timeText else "${slot.timeText} · ${slot.dosage}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
        val checkShape = MaterialTheme.shapes.extraSmall
        Box(
            Modifier
                .widthIn(min = 76.dp)
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = if (slot.taken) "${slot.name}，已打卡，点击取消" else "${slot.name}，点击打卡"
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !pending,
                    role = Role.Button,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                )
                .pressableScale(interactionSource, enabled = !pending, pressedScale = 0.92f)
                .clip(checkShape)
                .background(checkBackground)
                .focusRing(interactionSource, checkShape, enabled = !pending)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = slot.taken,
                transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
                contentAlignment = Alignment.Center,
                label = "Medication label"
            ) { taken ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (taken) Icon(
                        Icons.Outlined.Check,
                        null,
                        Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            },
                        tint = colors.onSecondaryContainer
                    )
                    Text(
                        if (taken) "已打卡" else "打卡",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (taken) colors.onSecondaryContainer else colors.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PillGlyph(primary: Color, secondary: Color) {
    Canvas(Modifier.size(27.dp, 16.dp)) {
        rotate(-35f) {
            drawRoundRect(
                primary,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(size.height / 2)
            )
            drawRect(secondary, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
            drawLine(primary.copy(alpha = 0.6f), Offset(size.width / 2, 1f), Offset(size.width / 2, size.height - 1f), 1.dp.toPx())
        }
    }
}
