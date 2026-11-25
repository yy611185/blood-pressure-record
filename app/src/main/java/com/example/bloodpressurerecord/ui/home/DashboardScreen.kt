package com.example.bloodpressurerecord.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.common.dockContentBottomPadding
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
import kotlin.math.max
import kotlin.math.min

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddMeasurement: () -> Unit,
    onViewTodayRecords: () -> Unit,
    onOpenMedicationSettings: () -> Unit = {},
    onOpenTrend: () -> Unit = {},
    onOpenSessionDetail: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.medicationFeedback.collect { feedback ->
            if (feedback.success) {
                val result = snackbarHostState.showSnackbar(
                    message = if (feedback.taken) "已打卡 · ${feedback.slot.name}" else "已取消打卡 · ${feedback.slot.name}",
                    actionLabel = "撤销"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.toggleMedicationTaken(feedback.slot, !feedback.taken)
                }
            } else {
                snackbarHostState.showSnackbar("保存打卡失败，请重试")
            }
        }
    }
    val bottomInset = dockContentBottomPadding()
    Box(Modifier.fillMaxSize().testTag("dashboardScreen")) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
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
                    heroState.latest == null -> EmptyHero("你好呀，我是小压！测完血压记下来，我会陪着你～", onAddMeasurement, heroState.showBuddy)
                    else -> ReadingHero(heroState, onOpenDetail = onOpenSessionDetail)
                }
            }
            SectionHeader("今天的测量", "早晚各一次最好")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.small)
                        .pressableScale(otherInteraction, pressedScale = 0.985f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = otherInteraction,
                            indication = LocalIndication.current,
                            onClick = onViewTodayRecords
                        )
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
            WeekCard(state)
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset)
        )
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
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 29.sp),
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
    val softState = animateColorAsState(
        targetValue = look.soft,
        animationSpec = AppMotion.colorSpec(reducedMotion, AppMotion.Medium),
        label = "hero_soft_bg"
    )
    val inkState = animateColorAsState(
        targetValue = look.ink,
        animationSpec = AppMotion.colorSpec(reducedMotion, AppMotion.Medium),
        label = "hero_ink"
    )
    val animatedSoft = if (reducedMotion) look.soft else softState.value
    val animatedInk = if (reducedMotion) look.ink else inkState.value
    val risk = latest.containsHighRiskReading
    val time = Instant.ofEpochMilli(latest.measuredAt).atZone(ZoneId.systemDefault())
    val day = if (time.toLocalDate() == state.today) "今天" else time.format(DateTimeFormatter.ofPattern("M月d日"))
    val guidance = when {
        risk -> "这次读数很高，先休息一下，再复测一次好吗？"
        latest.category.equals("LOW", true) -> "有点偏低哦，起身慢一点，多喝点水～"
        latest.category.equals("NORMAL", true) -> "漂亮！今天的血压很听话 ✓"
        latest.category.equals("HIGH_NORMAL", true) || latest.category.equals("ELEVATED", true) -> "还不错，稍微留意一下盐和睡眠。"
        latest.category.equals("STAGE1", true) -> "有点偏高，放松一下，过会儿再测一次？"
        latest.category.equals("STAGE2", true) -> "偏高了，记得按时吃药，不舒服要联系医生。"
        else -> "读数很高！请休息后复测，持续偏高请及时就医。"
    }
    val nextMedication = state.medicationSlots.firstOrNull { !it.taken }
    val messages = listOf(
        guidance,
        "测血压前先静坐休息 5 分钟，结果更准哦～",
        if (state.streakDays > 1) "已经连续记录 ${state.streakDays} 天啦，继续保持！" else "平时少吃咸，睡个好觉，血压更平稳～",
        nextMedication?.let { "今天 ${it.timeText} 记得吃${it.name}" }
            ?: if (state.medicationSlots.isEmpty()) "每天早晚各测一次，我会帮你记着～" else "今天的药都吃过啦，真棒！"
    )
    var messageIndex by remember(latest.id) { mutableIntStateOf(0) }
    val readingInteraction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(animatedSoft)
            .then(if (risk) Modifier.border(2.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(32.dp)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        if (state.showBuddy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Buddy(
                look.copy(mood = if (risk) "worried" else look.mood),
                Modifier.size(82.dp, 78.dp),
                onClick = { messageIndex = (messageIndex + 1) % messages.size }
            )
            Box(
                Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                AnimatedContent(
                    targetState = messages[messageIndex],
                    transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
                    label = "Buddy message"
                ) { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 可点击读数区：直达最近一次记录详情，核对原始读数或修改备注一步到位。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pressableScale(readingInteraction, pressedScale = 0.985f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = readingInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "查看最近一次测量详情",
                    onClick = { onOpenDetail(latest.id) }
                )
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
                    detail?.avgPulse?.let { pulse ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xSmall),
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "脉搏 $pulse 次/分" }
                        ) {
                            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp), tint = animatedInk)
                            Text(
                                "$pulse 次/分",
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
                            "弃第1组 · ${count - 1} 组平均"
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
        BpRangeBar(state.bpRange, Modifier.padding(top = AppSpacing.small))
        AnimatedVisibility(
            visible = risk,
            enter = AppMotion.expandVisibilityEnter(reducedMotion),
            exit = AppMotion.expandVisibilityExit(reducedMotion)
        ) {
            Text(
                "有读数达到 180/120 以上。请静坐休息后复测；伴胸痛、剧烈头痛等请立即就医。",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(11.dp)
            )
        }
    }
}

/** 为右侧单位与脉搏保留空间，小屏或大字时按实际文字宽度调整血压字号。 */
@Composable
private fun HeroPressureValue(sys: Int, dia: Int, color: Color, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.displayMedium.copy(
        fontSize = 58.sp,
        lineHeight = 62.sp,
        fontFeatureSettings = TabularFeatureSettings
    )
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
private fun RiskChip() {
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
            .pressableScale(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .border(2.dp, colors.outlineVariant, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "记录$label 血压",
                onClick = onClick
            )
    } else {
        modifier
            .heightIn(min = 104.dp)
            .pressableScale(interactionSource, pressedScale = 0.97f)
            .clip(shape)
            .background(colors.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "查看$label 测量详情",
                onClick = onClick
            )
    }
    Column(
        base
            .testTag("measurement-slot-$label")
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (morning) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            val time = session?.let {
                Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            } ?: "待测"
            Text(
                " $label · $time",
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
private fun GradeChip(
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
            style = MaterialTheme.typography.labelMedium.copy(fontSize = if (compact) 11.sp else 13.sp),
            fontWeight = FontWeight.Bold,
            color = look.ink,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyMedication(onOpenSettings: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressableScale(interactionSource, pressedScale = 0.985f)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onOpenSettings
            )
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
    val checkBackground by animateColorAsState(
        targetValue = if (slot.taken) colors.secondaryContainer else colors.surfaceContainerHighest,
        animationSpec = AppMotion.colorSpec(reducedMotion),
        label = "Medication background"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (slot.taken) 1f else 0.72f,
        animationSpec = AppMotion.springCheck(reducedMotion),
        label = "Medication check scale"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
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
                "${slot.timeText} · ${slot.dosage}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFeatureSettings),
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .size(68.dp, 48.dp)
                .pressableScale(interactionSource, enabled = !pending, pressedScale = 0.92f)
                .clip(RoundedCornerShape(15.dp))
                .background(checkBackground)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = !pending,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                )
                .semantics {
                    contentDescription = if (slot.taken) "${slot.name}，已服用，点击取消" else "${slot.name}，点击打卡"
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = slot.taken,
                transitionSpec = { AppMotion.fadeThrough(reducedMotion) },
                contentAlignment = Alignment.Center,
                label = "Medication label"
            ) { taken ->
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (taken) "吃了" else "打卡",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (taken) colors.onSecondaryContainer else colors.onSurfaceVariant
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

@Composable
internal fun WeekCard(state: DashboardUiState) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LocalReducedMotion.current
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("week-card")
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surface)
            .padding(8.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 240.dp * LocalDensity.current.fontScale
            val headerModifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 12.dp)
            if (compact) {
                Column(headerModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeekStreakSummary(state, Modifier.fillMaxWidth())
                    WeekdayIndicators(state, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    headerModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WeekSpacing)
                ) {
                    WeekStreakSummary(state, Modifier.weight(1f))
                    WeekdayIndicators(state, Modifier.width(166.dp))
                }
            }
        }
        if (state.showTrendChart) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(WeekSpacing),
                verticalAlignment = Alignment.Bottom
            ) {
                state.week.forEach { day ->
                    val targetHeight = weekBarHeight(day)
                    val animatedHeightState = animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = AppMotion.springGentle(reducedMotion),
                        label = "week_bar_height"
                    )
                    val animatedHeight = if (reducedMotion) targetHeight else animatedHeightState.value
                    val targetBarColor = if (day.averageSystolic == null) {
                        colors.surfaceContainerHighest
                    } else {
                        gradeLook(day.categoryName()).mid
                    }
                    val animatedBarColorState = animateColorAsState(
                        targetValue = targetBarColor,
                        animationSpec = AppMotion.colorSpec(reducedMotion),
                        label = "week_bar_color"
                    )
                    val animatedBarColor = if (reducedMotion) targetBarColor else animatedBarColorState.value
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            day.averageSystolic?.toString() ?: "",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = NumberFontFamily,
                                fontSize = 11.sp,
                                fontFeatureSettings = TabularFeatureSettings
                            ),
                            color = colors.onSurfaceVariant
                        )
                        Box(
                            Modifier
                                .widthIn(max = 26.dp)
                                .fillMaxWidth()
                                .height(animatedHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .background(animatedBarColor)
                        )
                        val today = day.date == state.today
                        Text(
                            weekdayLabel(day.date),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            fontWeight = if (today) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (today) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
            Text(
                "柱子数字为当天高压平均值",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
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
        horizontalArrangement = Arrangement.spacedBy(WeekSpacing)
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
            Text(subtitleText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
        }
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

/** 自然周固定 7 格，格间距与柱状图保持一致。 */
private val WeekSpacing = 2.dp

/** 柱高只由收缩压平均值决定，圆点行与柱状图共用同一份自然周数据。 */
private fun weekBarHeight(day: DashboardWeekDay): androidx.compose.ui.unit.Dp =
    if (day.averageSystolic == null) 8.dp
    else max(14f, min(84f, (day.averageSystolic - 95f) * 1.4f)).dp

private fun DashboardWeekDay.categoryName(): String =
    if (averageSystolic != null && averageDiastolic != null) {
        CategoryCalculator.calculate(averageSystolic, averageDiastolic).name
    } else {
        ""
    }

/** 自然周固定顺序：周一起，与历史月历及“本周”统计完全统一。 */
private val WeekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

internal fun weekdayLabel(date: LocalDate): String = WeekDayLabels[date.dayOfWeek.value - 1]
