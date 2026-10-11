package com.example.bloodpressurerecord.ui.home

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.domain.model.BpZone
import com.example.bloodpressurerecord.domain.model.BpZonePosition
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.BloodPressureRecordTheme
import com.example.bloodpressurerecord.ui.theme.BpRangeDimensions
import com.example.bloodpressurerecord.ui.theme.LocalBpRangeColors
import kotlin.math.roundToInt

/** 只渲染已计算的位置。四段是等宽示意区间，并非线性 mmHg 刻度。 */
@Composable
fun BpRangeBar(
    state: BpRangeUiState,
    modifier: Modifier = Modifier
) {
    val ready = state as? BpRangeUiState.Ready
    val description = if (ready == null) {
        stringResource(R.string.bp_range_empty_description)
    } else {
        stringResource(R.string.bp_range_description, ready.sys, ready.dia, stringResource(ready.zoneLabelRes))
    }
    val palette = LocalBpRangeColors.current
    val markerColor = MaterialTheme.colorScheme.onSurface
    val animationEnabled = rememberBpRangeAnimationsEnabled()
    val targetPercent = ready?.position?.percent ?: 2f
    val animatedPercent by animateFloatAsState(
        targetValue = targetPercent,
        animationSpec = if (animationEnabled) tween(BpRangeDimensions.markerAnimationMillis) else snap(),
        label = "Bp range marker"
    )
    // 系统在动画过程中关闭动画时，也必须在这一帧直接显示目标位置。
    val percent = if (animationEnabled) animatedPercent else targetPercent

    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        // 父级一次朗读完整读数；色块、三角形、重复标签不单独朗读。
        Column(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Row(Modifier.fillMaxWidth().height(BpRangeDimensions.barHeight).clip(RoundedCornerShape(50))) {
                BpZone.entries.forEach { zone ->
                    Box(Modifier.weight(1f).height(BpRangeDimensions.barHeight)
                        .background(if (ready == null) palette.empty else palette.segment(zone)))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(BpRangeDimensions.markerRowHeight)) {
                if (ready != null) {
                    val density = LocalDensity.current
                    val widthPx = with(density) { maxWidth.toPx() }
                    val markerWidthPx = with(density) { BpRangeDimensions.markerWidth.toPx() }
                    Canvas(Modifier.padding(top = AppSpacing.xSmall).offset {
                        IntOffset(
                            (widthPx * percent / 100f - markerWidthPx / 2f).roundToInt()
                                .coerceIn(0, (widthPx - markerWidthPx).roundToInt().coerceAtLeast(0)),
                            0
                        )
                    }.size(BpRangeDimensions.markerWidth, BpRangeDimensions.markerHeight)) {
                        drawPath(Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }, markerColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                BpZone.entries.forEach { zone ->
                    Text(
                        text = stringResource(zone.labelRes()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = BpRangeDimensions.labelSize),
                        fontWeight = if (ready?.position?.zone == zone) FontWeight.Bold else FontWeight.Normal,
                        color = if (ready?.position?.zone == zone) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (ready == null) {
            Text(
                stringResource(R.string.bp_range_empty_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.small).clearAndSetSemantics {}
            )
        }
    }
}

internal fun BpZone.labelRes(): Int = when (this) {
    BpZone.LOW -> R.string.bp_zone_low
    BpZone.NORMAL -> R.string.bp_zone_normal
    BpZone.ELEVATED -> R.string.bp_zone_elevated
    BpZone.HIGH -> R.string.bp_zone_high
}

@Composable
private fun rememberBpRangeAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    fun readEnabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    var enabled by remember(resolver) { mutableStateOf(readEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = readEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = readEnabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Preview(name = "Empty · light", showBackground = true)
@Composable
private fun BpRangeEmptyPreview() = BpRangePreview(BpRangeUiState.Empty)

@Preview(name = "Normal · light", showBackground = true)
@Composable
private fun BpRangeNormalPreview() = BpRangePreview(previewState(BpZone.NORMAL, 119, 75, 0.96f, 49f))

@Preview(name = "Elevated · light", showBackground = true)
@Composable
private fun BpRangeElevatedPreview() = BpRangePreview(previewState(BpZone.ELEVATED, 130, 85, 0.5f, 62.5f))

@Preview(name = "High · light", showBackground = true)
@Composable
private fun BpRangeHighPreview() = BpRangePreview(previewState(BpZone.HIGH, 160, 100, 0.5f, 87.5f))

@Preview(name = "Empty · dark", showBackground = true)
@Composable
private fun BpRangeEmptyDarkPreview() = BpRangePreview(BpRangeUiState.Empty, dark = true)

@Preview(name = "Normal · dark", showBackground = true)
@Composable
private fun BpRangeNormalDarkPreview() = BpRangePreview(previewState(BpZone.NORMAL, 119, 75, 0.96f, 49f), dark = true)

@Preview(name = "Elevated · dark", showBackground = true)
@Composable
private fun BpRangeElevatedDarkPreview() = BpRangePreview(previewState(BpZone.ELEVATED, 130, 85, 0.5f, 62.5f), dark = true)

@Preview(name = "High · dark", showBackground = true)
@Composable
private fun BpRangeHighDarkPreview() = BpRangePreview(previewState(BpZone.HIGH, 160, 100, 0.5f, 87.5f), dark = true)

private fun previewState(zone: BpZone, sys: Int, dia: Int, offset: Float, percent: Float) =
    BpRangeUiState.Ready(sys, dia, BpZonePosition(zone, offset, percent), zone.labelRes())

@Composable
private fun BpRangePreview(state: BpRangeUiState, dark: Boolean = false) {
    BloodPressureRecordTheme(darkTheme = dark) {
        Surface { BpRangeBar(state, Modifier.padding(AppSpacing.large)) }
    }
}
