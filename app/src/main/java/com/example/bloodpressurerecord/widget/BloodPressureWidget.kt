package com.example.bloodpressurerecord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.glance.unit.ColorProvider
import com.example.bloodpressurerecord.BloodPressureApplication
import com.example.bloodpressurerecord.MainActivity
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.domain.time.toEpochMillisRange
import com.example.bloodpressurerecord.ui.common.CategoryPresentation
import com.example.bloodpressurerecord.ui.theme.Sage600
import com.example.bloodpressurerecord.ui.theme.Terracotta700
import com.example.bloodpressurerecord.ui.theme.bloodPressureVisualStatus
import com.example.bloodpressurerecord.ui.theme.style
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

class BloodPressureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloodPressureWidget()
}

private data class WidgetData(
    val latest: LatestSessionSummary? = null,
    val todayCount: Int = 0,
    val medTaken: Int = 0,
    val medTotal: Int = 0,
    val trend: List<TrendRecord> = emptyList()
)

/**
 * 桌面小部件：按用户拖拽出的实际尺寸自动切换三档内容——
 * 2×2 最新血压 + 分级；4×2 加今日测量次数与服药进度；4×4 再加近 7 天迷你趋势图。
 */
class BloodPressureWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = runCatching { loadData(context) }.getOrDefault(WidgetData())
        provideContent {
            WidgetContent(data)
        }
    }

    private suspend fun loadData(context: Context): WidgetData {
        val application = context.applicationContext as? BloodPressureApplication
            ?: return WidgetData()
        val container = application.appContainer
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayRange = today.toEpochMillisRange(zone)
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val latest = container.bloodPressureRepository.observeLatestSessionSummary().first()
        val todayStats = container.bloodPressureRepository
            .observePeriodStatistics(todayRange.startInclusive, todayRange.endExclusive)
            .first()
        val slots = container.medicationRepository.getSlotsForDay(today)
        val trend = container.trendRepository.getRecords(weekStart, todayRange.endExclusive)
        return WidgetData(
            latest = latest,
            todayCount = todayStats.recordCount,
            medTaken = slots.count { it.taken },
            medTotal = slots.size,
            trend = trend
        )
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

private val WidgetBackground = Color(0xFFFFFDF9)
private val TextPrimary = Color(0xFF3B322C)
private val TextMuted = Color(0xFF8A7F76)

@Composable
private fun WidgetContent(data: WidgetData) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        LatestBlock(data, compact = size.width < BloodPressureWidget.WIDE.width)
        if (size.width >= BloodPressureWidget.WIDE.width) {
            Spacer(GlanceModifier.height(6.dp))
            StatsRow(data)
        }
        if (size.width >= BloodPressureWidget.LARGE.width &&
            size.height >= BloodPressureWidget.LARGE.height
        ) {
            Spacer(GlanceModifier.height(8.dp))
            TrendBlock(
                trend = data.trend,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

@Composable
private fun LatestBlock(data: WidgetData, compact: Boolean) {
    val latest = data.latest
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "最近血压",
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
            )
            latest?.let {
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    Instant.ofEpochMilli(it.measuredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
                )
            }
        }
        if (latest == null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "还没有记录\n点击开始记一次",
                style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 14.sp)
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${latest.avgSystolic}/${latest.avgDiastolic}",
                    style = TextStyle(
                        color = ColorProvider(Terracotta700),
                        fontSize = if (compact) 30.sp else 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    "mmHg",
                    style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
                )
            }
            val status = bloodPressureVisualStatus(
                latest.category,
                latest.containsHighRiskReading
            ).style(lightColorScheme())
            Text(
                CategoryPresentation.label(latest.category),
                style = TextStyle(
                    color = ColorProvider(status.contentColor),
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
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
            )
            Text(
                "${data.todayCount} 次",
                style = TextStyle(
                    color = ColorProvider(TextPrimary),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                "今日服药",
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
            )
            Text(
                if (data.medTotal == 0) "未添加" else "${data.medTaken}/${data.medTotal}",
                style = TextStyle(
                    color = ColorProvider(
                        if (data.medTotal > 0 && data.medTaken == data.medTotal) {
                            Sage600
                        } else {
                            TextPrimary
                        }
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun TrendBlock(trend: List<TrendRecord>, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        Text(
            "近 7 天趋势",
            style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp)
        )
        Spacer(GlanceModifier.height(4.dp))
        if (trend.size < 2) {
            Text(
                "记录满 2 次后显示趋势",
                style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp)
            )
        } else {
            Image(
                provider = ImageProvider(renderTrendBitmap(trend)),
                contentDescription = "近7天血压趋势",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

/** 用纯 Canvas 画一张迷你双折线图（收缩压陶土橙 / 舒张压鼠尾草绿）。 */
private fun renderTrendBitmap(trend: List<TrendRecord>): Bitmap {
    val width = 640
    val height = 300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
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

    drawSeries(Terracotta700.toArgb()) { it.systolic }
    drawSeries(Sage600.toArgb()) { it.diastolic }
    return bitmap
}
