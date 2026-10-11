package com.example.bloodpressurerecord.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.domain.model.BpZone

/** 展示区间专用色，不替代既有医学分级或风险提醒色。 */
data class BpRangeColors(
    val low: Color,
    val normal: Color,
    val elevated: Color,
    val high: Color,
    val empty: Color
) {
    fun segment(zone: BpZone): Color = when (zone) {
        BpZone.LOW -> low
        BpZone.NORMAL -> normal
        BpZone.ELEVATED -> elevated
        BpZone.HIGH -> high
    }
}

val LightBpRangeColors = BpRangeColors(
    low = Color(0xFFBFE3D0),
    normal = Color(0xFF7FCAA6),
    elevated = Color(0xFFF2C48D),
    high = Color(0xFFE9967F),
    empty = Color(0xFFD8D4CE)
)

val DarkBpRangeColors = BpRangeColors(
    low = Color(0xFF577A68),
    normal = Color(0xFF4E9B78),
    elevated = Color(0xFFBA8B50),
    high = Color(0xFFC87963),
    empty = Color(0xFF57514B)
)

val LocalBpRangeColors = staticCompositionLocalOf { LightBpRangeColors }

object BpRangeDimensions {
    val barHeight = 10.dp
    val markerWidth = 10.dp
    val markerHeight = 6.dp
    val markerRowHeight = 12.dp
    val labelSize = 11.sp
    const val markerAnimationMillis = 400
}
