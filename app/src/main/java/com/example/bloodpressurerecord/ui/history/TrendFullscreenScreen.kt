package com.example.bloodpressurerecord.ui.history

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.ui.theme.NumberFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 同一导航项内切换全屏，旋转与返回继续使用原趋势状态。 */
@Composable
internal fun TrendFullscreenScreen(
    uiState: TrendUiState,
    viewModel: TrendViewModel,
    onOpenRecord: (String) -> Unit
) {
    BackHandler {
        if (uiState.dayDetails != null) viewModel.dismissDayDetails() else viewModel.setFullscreen(false)
    }
    val selected = uiState.series.points.firstOrNull { it.id == uiState.selectedPointId }
    val displayed = selected ?: uiState.series.points.lastOrNull()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag("fullscreenTrend")) {
        val sideDetails = maxWidth / fontScale >= 650.dp
        if (!sideDetails) uiState.dayDetails?.let { details ->
            TrendDayDetailsSheet(details, viewModel::dismissDayDetails, onOpenRecord)
        }
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TrendFullscreenToolbar(uiState, viewModel, displayed)
                // 主图直接使用余下高度；没有底部操作栏或最小高度滚动容器。
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val pulseHeight = if (uiState.fullscreenShowPulse) maxHeight * 0.26f else 0.dp
                    val pressureHeight = maxHeight - pulseHeight
                    Column(Modifier.fillMaxSize()) {
                        SessionTimeSeriesDualLineChart(
                            series = uiState.series,
                            selectedPoint = selected,
                            onPointSelected = viewModel::selectPoint,
                            controller = viewModel.chartController,
                            targetSystolic = uiState.targetSystolic,
                            targetDiastolic = uiState.targetDiastolic,
                            showSystolic = uiState.metric != TrendMetricType.DIASTOLIC,
                            showDiastolic = uiState.metric != TrendMetricType.SYSTOLIC,
                            chartHeight = pressureHeight,
                            showHint = false,
                            showTimeLabels = uiState.showTimeLabels,
                            denseYAxis = true,
                            modifier = Modifier.fillMaxWidth().height(pressureHeight)
                                .testTag("fullscreenPressureChart")
                        )
                        if (uiState.fullscreenShowPulse) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SessionTimeSeriesPulseChart(
                                series = uiState.series,
                                selectedPoint = selected,
                                onPointSelected = viewModel::selectPoint,
                                controller = viewModel.chartController,
                                chartHeight = (pulseHeight - 1.dp).coerceAtLeast(0.dp),
                                showTimeLabels = uiState.showTimeLabels,
                                modifier = Modifier.fillMaxWidth().height((pulseHeight - 1.dp).coerceAtLeast(0.dp))
                            )
                        }
                    }
                }
            }
            if (sideDetails) uiState.dayDetails?.let { details ->
                Column(
                    Modifier.width(320.dp).fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("测量明细", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = viewModel::dismissDayDetails) {
                            Icon(Icons.Default.Close, contentDescription = "关闭明细")
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        TrendDayDetailsContent(
                            details,
                            onOpenRecord = { id -> viewModel.dismissDayDetails(); onOpenRecord(id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendFullscreenToolbar(uiState: TrendUiState, viewModel: TrendViewModel, displayed: TrendPoint?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val readoutWidth = minOf(maxWidth * 0.46f, 360.dp * LocalDensity.current.fontScale)
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { viewModel.setFullscreen(false) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出全屏")
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrendMenu(uiState.range.label, TrendRange.entries, { it.label }, viewModel::setRange)
                TrendMenu(uiState.metric.label, TrendMetricType.entries, { it.label }, viewModel::setMetric)
                TextButton(
                    onClick = { viewModel.setShowTimeLabels(!uiState.showTimeLabels) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "${if (uiState.showTimeLabels) "隐藏" else "显示"}横轴具体时分"
                    }
                ) {
                    Text(
                        "时分", style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.showTimeLabels) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false
                    )
                }
                TrendFullscreenActionsMenu(uiState, viewModel, displayed)
            }
            TrendFullscreenReadout(displayed, Modifier.width(readoutWidth))
        }
    }
}

private val TrendMetricType.label: String
    get() = when (this) {
        TrendMetricType.BOTH -> "双指标"
        TrendMetricType.SYSTOLIC -> "收缩压"
        TrendMetricType.DIASTOLIC -> "舒张压"
    }

@Composable
private fun <T> TrendMenu(value: String, items: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(label(item)) }, onClick = { expanded = false; onSelect(item) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendFullscreenReadout(point: TrendPoint?, modifier: Modifier = Modifier) {
    if (point == null) {
        Text("暂无测量记录", modifier.testTag("fullscreenReadout"), style = MaterialTheme.typography.labelMedium)
        return
    }
    val date = Instant.ofEpochMilli(point.timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    val pressure = "${point.systolic}/${point.diastolic} mmHg"
    val pulse = point.pulse?.let { "脉搏 $it 次/分" } ?: "脉搏 —"
    Column(
        modifier.testTag("fullscreenReadout").semantics(mergeDescendants = true) {
            contentDescription = "$date，收缩压 ${point.systolic}，舒张压 ${point.diastolic}，$pulse" +
                if (point.containsHighRiskReading) "，含高风险读数" else ""
        },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(pressure, style = MaterialTheme.typography.titleSmall, fontFamily = NumberFontFamily,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(pulse, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TrendFullscreenActionsMenu(uiState: TrendUiState, viewModel: TrendViewModel, displayed: TrendPoint?) {
    var expanded by remember { mutableStateOf(false) }
    val points = uiState.series.points
    val index = points.indexOfFirst { it.id == displayed?.id }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "趋势操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("脉搏副图") },
                trailingIcon = { if (uiState.fullscreenShowPulse) Icon(Icons.Default.Check, contentDescription = "已显示") },
                onClick = { expanded = false; viewModel.setFullscreenShowPulse(!uiState.fullscreenShowPulse) }
            )
            HorizontalDivider()
            DropdownMenuItem(text = { Text("上一条") }, enabled = index > 0,
                onClick = { expanded = false; viewModel.selectPrevious() })
            DropdownMenuItem(text = { Text("下一条") }, enabled = index >= 0 && index < points.lastIndex,
                onClick = { expanded = false; viewModel.selectNext() })
            DropdownMenuItem(text = { Text("明细") }, enabled = displayed != null,
                onClick = { expanded = false; displayed?.let(viewModel::openPointDetails) })
            DropdownMenuItem(text = { Text("恢复") }, enabled = points.isNotEmpty(),
                onClick = { expanded = false; viewModel.resetChart() })
            DropdownMenuItem(text = { Text("回到最新") }, enabled = points.isNotEmpty(),
                onClick = { expanded = false; viewModel.moveToLatest() })
        }
    }
}

/** 在旋转重建时保留进入前策略；方向限制被忽略时仍能使用自适应详情。 */
@Composable
internal fun TrendFullscreenOrientation(fullscreen: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    var previousOrientation by rememberSaveable { mutableStateOf<Int?>(null) }
    DisposableEffect(activity, fullscreen) {
        if (fullscreen) {
            if (previousOrientation == null) previousOrientation = activity.requestedOrientation
            if (activity.resources.configuration.smallestScreenWidthDp < 600 && !activity.isInMultiWindowMode) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            previousOrientation?.let { activity.requestedOrientation = it }
            previousOrientation = null
        }
        onDispose {
            if (fullscreen && !activity.isChangingConfigurations) {
                previousOrientation?.let { activity.requestedOrientation = it }
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
