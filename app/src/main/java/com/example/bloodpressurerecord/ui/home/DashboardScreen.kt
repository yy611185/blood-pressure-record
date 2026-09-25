package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloodpressurerecord.data.repository.MedicationSlot
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
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
    onOpenTrend: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 116.dp)
    ) {
        GreetingHeader(state)
        Spacer(Modifier.height(18.dp))
        when {
            state.loading -> EmptyHero("正在读取最近记录…", null, state.showBuddy)
            state.latest == null -> EmptyHero("你好呀，我是小压！测完血压记下来，我会陪着你～", onAddMeasurement, state.showBuddy)
            else -> ReadingHero(state)
        }
        SectionHeader("今天的测量", "早晚各一次最好")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeasurementSlot("早上", true, state.todayMorning, onAddMeasurement, Modifier.weight(1f))
            MeasurementSlot("晚上", false, state.todayEvening, onAddMeasurement, Modifier.weight(1f))
        }
        SectionHeader("今日服药", "${state.medicationSlots.count { it.taken }}/${state.medicationSlots.size} 已打卡")
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
        SectionHeader("这一周", if (state.showTrendChart) "看趋势" else null,
            if (state.showTrendChart) onOpenTrend else null)
        WeekCard(state)
        Text(
            "分级仅供参考，不替代医疗诊断",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center
        )
    }
    SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

internal data class GradeLook(val soft: Color, val ink: Color, val mid: Color, val body: Color, val mood: String)

@Composable
internal fun gradeLook(category: String): GradeLook {
    val look = when (category.uppercase()) {
        "LOW" -> GradeLook(Color(0xFFE9E9FA), Color(0xFF55509B), Color(0xFF8E8BC8), Color(0xFFAAA9E2), "sleepy")
        "NORMAL" -> GradeLook(Color(0xFFDFF4E9), Color(0xFF287958), Color(0xFF69C49D), Color(0xFF8ED5B4), "happy")
        "HIGH_NORMAL", "ELEVATED" -> GradeLook(Color(0xFFEEF3D7), Color(0xFF61742E), Color(0xFFA5BF65), Color(0xFFC5D792), "smile")
        "STAGE1" -> GradeLook(Color(0xFFF7F0CF), Color(0xFF806625), Color(0xFFD9B55E), Color(0xFFE8C982), "meh")
        "STAGE2" -> GradeLook(Color(0xFFFBE5D7), Color(0xFF9F5335), Color(0xFFEAA27E), Color(0xFFF2B696), "worried")
        else -> GradeLook(Color(0xFFFADDD9), Color(0xFFA03F39), Color(0xFFE58178), Color(0xFFED9D93), "worried")
    }
    val dark = MaterialTheme.colorScheme.background.red < 0.5f
    return if (dark) look.copy(soft = look.ink.copy(alpha = 0.23f), ink = look.body) else look
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingHero(state: DashboardUiState) {
    val latest = state.latest ?: return
    val detail = state.latestSession
    val look = gradeLook(latest.category)
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
        "戳我干嘛～ 测之前先静坐 5 分钟哦",
        if (state.streakDays > 1) "已经连续记录 ${state.streakDays} 天啦，继续保持！" else "每天早晚各测一次，我会帮你记着～",
        nextMedication?.let { "今天 ${it.timeText} 记得吃${it.name}" } ?: "今天的药都吃过啦，真棒！"
    )
    var messageIndex by remember(latest.id) { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(look.soft)
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
                Modifier.weight(1f).padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(messages[messageIndex], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "最近一次 · $day ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}${detail?.scene?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = look.ink.copy(alpha = 0.82f)
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text("${latest.avgSystolic}", style = MaterialTheme.typography.displayMedium.copy(fontSize = 58.sp, lineHeight = 62.sp), color = look.ink)
            Text("/", style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp, fontWeight = FontWeight.Light), color = look.ink.copy(alpha = 0.36f))
            Text("${latest.avgDiastolic}", style = MaterialTheme.typography.displayMedium.copy(fontSize = 58.sp, lineHeight = 62.sp), color = look.ink)
            Text("mmHg", style = MaterialTheme.typography.labelMedium, color = look.ink.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GradeChip(CategoryPresentation.label(latest.category), look)
            if (risk) RiskChip()
            detail?.avgPulse?.let { pulse ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp), tint = look.ink)
                    Text(" $pulse 次/分", style = MaterialTheme.typography.labelMedium, color = look.ink)
                }
            }
        }
        val count = detail?.readings?.size
        if (count != null) {
            Text(
                if (detail.averageStrategy == AverageStrategy.DISCARD_FIRST && count >= 2) "弃第1组 · ${count - 1} 组平均" else "$count 组平均",
                style = MaterialTheme.typography.labelMedium, color = look.ink.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 7.dp)
            )
        }
        if (risk) {
            Text(
                "有读数达到 180/120 以上。请静坐休息后复测；伴胸痛、剧烈头痛等请立即就医。",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.error).padding(11.dp)
            )
        }
    }
}

@Composable
private fun RiskChip() {
    Text("高风险读数", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onError,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.error).padding(horizontal = 9.dp, vertical = 5.dp))
}

@Composable
private fun EmptyHero(message: String, onAdd: (() -> Unit)?, showBuddy: Boolean) {
    val look = gradeLook("STAGE2")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(look.soft).padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (showBuddy) Buddy(look.copy(mood = "idle"), Modifier.size(82.dp, 78.dp))
            Box(Modifier.weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onAdd != null) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Text("记第一次血压")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.ExtraBold)
        if (trailing != null) {
            if (onTrailing == null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else TextButton(onClick = onTrailing, modifier = Modifier.height(48.dp)) {
                Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MeasurementSlot(label: String, morning: Boolean, session: SessionSummary?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val base = if (session == null) modifier.height(104.dp).border(2.dp, colors.outlineVariant, shape).clickable(onClick = onAdd)
    else modifier.height(104.dp).clip(shape).background(colors.surface)
    Column(base.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (morning) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            val time = session?.let { Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "待测"
            Text(" $label · $time", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
        }
        if (session == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("去测一次", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = colors.primary)
            }
        } else {
            Column {
                Text("${session.avgSystolic}/${session.avgDiastolic}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = NumberFontFamily, fontSize = 27.sp), maxLines = 1)
                GradeChip(CategoryPresentation.label(session.category), gradeLook(session.category), compact = true)
            }
        }
    }
}

@Composable
private fun GradeChip(label: String, look: GradeLook, compact: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(look.soft)
        .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 2.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(look.mid, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = if (compact) 11.sp else 13.sp),
            fontWeight = FontWeight.Bold, color = look.ink, maxLines = 1)
    }
}

@Composable
private fun EmptyMedication(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
        .clickable(onClick = onOpenSettings).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("还没有添加药品", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("去添加", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MedicationRow(slot: MedicationSlot, pending: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            PillGlyph(colors.primary, colors.surface)
        }
        Column(Modifier.weight(1f)) {
            Text(slot.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${slot.timeText} · ${slot.dosage}", style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant, maxLines = 1)
        }
        Box(Modifier.size(68.dp, 48.dp).clip(RoundedCornerShape(15.dp))
            .background(if (slot.taken) colors.secondaryContainer else colors.surfaceContainerHighest)
            .clickable(enabled = !pending, onClick = onToggle)
            .semantics { contentDescription = if (slot.taken) "${slot.name}，已服用，点击取消" else "${slot.name}，点击打卡" },
            contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (slot.taken) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = colors.onSecondaryContainer)
                Text(if (slot.taken) "吃了" else "打卡", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold, color = if (slot.taken) colors.onSecondaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PillGlyph(primary: Color, secondary: Color) {
    Canvas(Modifier.size(27.dp, 16.dp)) {
        rotate(-35f) {
            drawRoundRect(primary, size = Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            drawRect(secondary, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
            drawLine(primary.copy(alpha = 0.6f), Offset(size.width / 2, 1f), Offset(size.width / 2, size.height - 1f), 1.dp.toPx())
        }
    }
}

@Composable
private fun WeekCard(state: DashboardUiState) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(27.dp), tint = colors.primary)
            Column {
                Text("${state.streakDays} 天", style = MaterialTheme.typography.titleLarge.copy(fontFamily = NumberFontFamily, fontSize = 27.sp), color = colors.onPrimaryContainer)
                Text("连续记录", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
            }
            Spacer(Modifier.weight(1f))
            state.week.forEach { day ->
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (day.recorded) colors.primary else colors.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(if (day.date == state.today) "今" else weekday(day.date),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), fontWeight = FontWeight.ExtraBold,
                        color = if (day.recorded) colors.onPrimary else colors.onPrimaryContainer)
                }
            }
        }
        if (state.showTrendChart) {
        Row(Modifier.fillMaxWidth().height(138.dp).padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            state.week.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(day.averageSystolic?.toString() ?: "", style = MaterialTheme.typography.labelMedium.copy(fontFamily = NumberFontFamily, fontSize = 11.sp), color = colors.onSurfaceVariant)
                    val height = if (day.averageSystolic == null) 8.dp else max(14f, min(84f, (day.averageSystolic - 95f) * 1.4f)).dp
                    val category = if (day.averageSystolic != null && day.averageDiastolic != null)
                        CategoryCalculator.calculate(day.averageSystolic, day.averageDiastolic).name else ""
                    Box(Modifier.widthIn(max = 30.dp).fillMaxWidth().height(height).clip(RoundedCornerShape(10.dp))
                        .background(if (day.averageSystolic == null) colors.surfaceContainerHighest else gradeLook(category).mid))
                    Text(if (day.date == state.today) "今" else weekday(day.date),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        fontWeight = if (day.date == state.today) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (day.date == state.today) colors.onSurface else colors.onSurfaceVariant)
                }
            }
        }
        Text("柱子数字为当天收缩压平均值", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.72f), modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        } else {
            Text("每天记一记，连续记录会显示在上方。", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
        }
    }
}

private fun weekday(date: LocalDate): String = listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1]
