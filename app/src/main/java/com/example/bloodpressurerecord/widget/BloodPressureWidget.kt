package com.example.bloodpressurerecord.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.MainActivity
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.time.dayTicks
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.navigation.AppDestination
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.theme.Sage400
import com.example.bloodpressurerecord.ui.theme.Sage600
import com.example.bloodpressurerecord.ui.theme.Terracotta400
import com.example.bloodpressurerecord.ui.theme.Terracotta700
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import com.example.bloodpressurerecord.ui.theme.style
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class BloodPressureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloodPressureWidget()
}

internal data class WidgetData(
    val latest: LatestSessionSummary? = null,
    val todayCount: Int = 0,
    val medTaken: Int = 0,
    val medTotal: Int = 0,
    val trend: List<TrendRecord> = emptyList(),
    val zoneId: String = ZoneId.systemDefault().id,
    val isAvailable: Boolean = true
)

/**
 * 桌面小部件：按用户拖拽出的实际尺寸自动切换三档内容——
 * 2×2 最新血压 + 分级；4×2 加今日测量次数与服药进度；4×4 再加近 7 天迷你趋势图。
 * 组合内持续观察 Room 数据流、日期/时区变化与 [AppWidgetUpdater.refreshSignal]，
 * 避免活跃 Glance 会话保留旧快照或多实例状态不一致。
 */
class BloodPressureWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        provideContent {
            val liveFlow = remember(appContext) { observeWidgetData(appContext) }
            val themeFlow = remember(appContext) {
                AppWidgetUpdater.refreshSignal.map { isSystemDark(appContext) }.distinctUntilChanged()
            }
            val data by liveFlow.collectAsState(initial = WidgetData(isAvailable = false))
            val darkTheme by themeFlow.collectAsState(initial = isSystemDark(appContext))
            WidgetContent(data, darkTheme)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeWidgetData(context: Context): Flow<WidgetData> {
        val application = context.applicationContext as? BloodPressureApplication
            ?: return flowOf(WidgetData(isAvailable = false))
        val container = application.appContainer
        val dateTicks = dayTicks(
            zoneProvider = ZoneId::systemDefault,
            externalTrigger = container.systemTimeChangeSignal
        )
        return combine(AppWidgetUpdater.refreshSignal, dateTicks) { _, _ -> Unit }
            .flatMapLatest {
                flow {
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val todayRange = today.toEpochMillisRange(zone)
                    val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                    emitAll(
                        combine(
                            container.bloodPressureRepository.observeLatestSessionSummary(),
                            container.bloodPressureRepository.observePeriodStatistics(
                                todayRange.startInclusive,
                                todayRange.endExclusive
                            ),
                            container.medicationRepository.observeSlotsForDay(today),
                            container.trendRepository.observeRecords(weekStart, todayRange.endExclusive)
                        ) { latest, todayStats, slots, trend ->
                            WidgetData(
                                latest = latest,
                                todayCount = todayStats.recordCount,
                                medTaken = slots.count { it.taken },
                                medTotal = slots.size,
                                trend = trend,
                                zoneId = zone.id
                            )
                        }
                    )
                }.catch {
                    // 保留上次成功结果，下次刷新或跨日时重新订阅。
                }
            }
            .distinctUntilChanged()
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

private val WidgetBackground = ColorProvider(day = Color(0xFFFFFDF9), night = Color(0xFF241E1B))
private val WidgetChipBackground = ColorProvider(day = Color(0xFFF3E2D8), night = Color(0xFF3E2D26))
private val WidgetChipContent = ColorProvider(day = Terracotta700, night = Color(0xFFFFB59E))
private val TextPrimary = ColorProvider(day = Color(0xFF3B322C), night = Color(0xFFF3ECE6))
private val TextMuted = ColorProvider(day = Color(0xFF645C55), night = Color(0xFFB6B0A9))
private val ReadingAccent = ColorProvider(day = Terracotta700, night = Terracotta400)
private val MedDoneColor = ColorProvider(day = Sage600, night = Sage400)

internal val WidgetDiastolicLightColor = Color(0xFF02578B)
internal val WidgetDiastolicDarkColor = Color(0xFF8BD2FF)
internal val WidgetGridLightColor = Color(0x448A7F76)
internal val WidgetGridDarkColor = Color(0x66B6B0A9)

private fun openHomeIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

private fun openAddMeasurementIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_DESTINATION, AppDestination.AddMeasurement.route)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

private fun isSystemDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

@Composable
private fun WidgetContent(data: WidgetData, darkTheme: Boolean) {
    val size = LocalSize.current
    val context = LocalContext.current
    val rootClickAction = if (data.isAvailable && data.latest == null) {
        actionStartActivity(openAddMeasurementIntent(context))
    } else {
        actionStartActivity(openHomeIntent(context))
    }
    val isLarge = size.width >= BloodPressureWidget.LARGE.width &&
        size.height >= BloodPressureWidget.LARGE.height
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 12.dp, vertical = if (isLarge) 12.dp else 10.dp)
            .clickable(rootClickAction)
    ) {
        LatestBlock(
            data = data,
            compact = size.width < BloodPressureWidget.WIDE.width,
            tall = isLarge,
            addActionIntent = openAddMeasurementIntent(context)
        )
        if (data.isAvailable && size.width >= BloodPressureWidget.WIDE.width) {
            Spacer(GlanceModifier.height(if (isLarge) 6.dp else 4.dp))
            StatsRow(data)
        }
        if (data.isAvailable && isLarge) {
            Spacer(GlanceModifier.height(8.dp))
            TrendBlock(
                trend = data.trend,
                darkTheme = darkTheme,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

@Composable
private fun LatestBlock(
    data: WidgetData,
    compact: Boolean,
    tall: Boolean,
    addActionIntent: Intent
) {
    val latest = data.latest
    val displayZone = runCatching { ZoneId.of(data.zoneId) }.getOrElse { ZoneId.systemDefault() }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "最近血压",
                    style = TextStyle(color = TextMuted, fontSize = 11.sp),
                    maxLines = 1
                )
                latest?.let {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        Instant.ofEpochMilli(it.measuredAt)
                            .atZone(displayZone)
                            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                        style = TextStyle(color = TextMuted, fontSize = 11.sp),
                        maxLines = 1
                    )
                }
            }
            if (latest != null && !compact) {
                // FRZ-03 / AUD-19：扩大「+ 记一次」外围可点击命中区与胶囊尺寸，同时保持下方 34.sp 核心读数满宽不挤压
                Box(
                    modifier = GlanceModifier
                        .then(if (tall) GlanceModifier.height(48.dp) else GlanceModifier)
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                        .clickable(actionStartActivity(addActionIntent)),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "+ 记一次",
                        style = TextStyle(
                            color = WidgetChipContent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .background(WidgetChipBackground)
                            .cornerRadius(16.dp)
                            .padding(
                                horizontal = if (tall) 14.dp else 12.dp,
                                vertical = if (tall) 9.dp else 7.dp
                            )
                    )
                }
            }
        }
        if (latest == null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (data.isAvailable) "还没有记录\n点击开始记一次" else "暂未载入记录",
                style = TextStyle(color = TextPrimary, fontSize = 14.sp)
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${latest.avgSystolic}/${latest.avgDiastolic}",
                    style = TextStyle(
                        color = ReadingAccent,
                        fontSize = if (compact) 30.sp else 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    "mmHg",
                    style = TextStyle(color = TextMuted, fontSize = 11.sp)
                )
            }
            val visualStatus = bloodPressureVisualStatus(
                latest.category,
                latest.containsHighRiskReading
            )
            val statusColor = ColorProvider(
                day = visualStatus.style(lightColorScheme()).contentColor,
                night = visualStatus.style(darkColorScheme()).contentColor
            )
            val categoryText = buildString {
                append(CategoryPresentation.label(latest.category))
                if (latest.containsHighRiskReading) append(" · 高风险")
            }
            Text(
                categoryText,
                style = TextStyle(
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun StatsRow(data: WidgetData) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                "今日测量",
                style = TextStyle(color = TextMuted, fontSize = 11.sp)
            )
            Text(
                "${data.todayCount} 次",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                "今日服药",
                style = TextStyle(color = TextMuted, fontSize = 11.sp)
            )
            Text(
                if (data.medTotal == 0) "未添加" else "${data.medTaken}/${data.medTotal}",
                style = TextStyle(
                    color = if (data.medTotal > 0 && data.medTaken == data.medTotal) {
                        MedDoneColor
                    } else {
                        TextPrimary
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun TrendBlock(
    trend: List<TrendRecord>,
    darkTheme: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        Text(
            "近 7 天趋势",
            style = TextStyle(color = TextMuted, fontSize = 11.sp)
        )
        Spacer(GlanceModifier.height(4.dp))
        if (trend.size < 2) {
            Text(
                "记录满 2 次后显示趋势",
                style = TextStyle(color = TextMuted, fontSize = 12.sp)
            )
        } else {
            val bitmap = remember(trend, darkTheme) {
                renderTrendBitmap(trend, darkTheme = darkTheme)
            }
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "近 7 天血压趋势图，点击进入应用查看详情",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

/**
 * 用纯 Canvas 画一张迷你双折线图：
 * 浅色使用陶土红 + 深海蓝，深色切换为高对比暖珊瑚 + 明亮天蓝并提高参考线可见度。
 */
internal fun renderTrendBitmap(
    trend: List<TrendRecord>,
    darkTheme: Boolean = false
): Bitmap {
    val width = 640
    val height = 300
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val padding = 16f

    val sorted = trend.sortedBy { it.measuredAt }
    val values = sorted.flatMap { listOf(it.systolic, it.diastolic) }
    val minValue = (values.min() - 10).coerceAtLeast(0)
    val maxValue = values.max() + 10
    val valueSpan = (maxValue - minValue).coerceAtLeast(1)
    val minTime = sorted.first().measuredAt
    val timeSpan = (sorted.last().measuredAt - minTime).coerceAtLeast(1)

    fun x(measuredAt: Long): Float =
        padding + (measuredAt - minTime).toFloat() / timeSpan * (width - 2 * padding)

    fun y(value: Int): Float =
        height - padding - (value - minValue).toFloat() / valueSpan * (height - 2 * padding)

    val gridColor = if (darkTheme) WidgetGridDarkColor else WidgetGridLightColor
    val systolicColor = if (darkTheme) Terracotta400 else Terracotta700
    val diastolicColor = if (darkTheme) WidgetDiastolicDarkColor else WidgetDiastolicLightColor

    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridColor.toArgb()
        strokeWidth = 2f
    }
    // 140/90 参考线（在范围内时）
    listOf(140, 90).forEach { ref ->
        if (ref in minValue..maxValue) {
            canvas.drawLine(padding, y(ref), width - padding, y(ref), gridPaint)
        }
    }

    fun drawSeries(color: Int, valueOf: (TrendRecord) -> Int) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        var prevX = 0f
        var prevY = 0f
        sorted.forEachIndexed { index, record ->
            val cx = x(record.measuredAt)
            val cy = y(valueOf(record))
            if (index > 0) canvas.drawLine(prevX, prevY, cx, cy, linePaint)
            canvas.drawCircle(cx, cy, 6f, dotPaint)
            prevX = cx
            prevY = cy
        }
    }

    drawSeries(systolicColor.toArgb()) { it.systolic }
    drawSeries(diastolicColor.toArgb()) { it.diastolic }
    return bitmap
}
