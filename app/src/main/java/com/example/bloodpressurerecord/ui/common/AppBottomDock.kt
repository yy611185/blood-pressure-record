package com.example.bloodpressurerecord.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.navigation.AppDestination
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import kotlin.math.roundToInt

private class DockExitSnapshot {
    var fraction: Float = Float.NaN
    var tabAlpha: Float = Float.NaN
}

/**
 * 悬浮在页面底部的胶囊 Dock。
 * 收起/展开使用保速弹簧（可随时打断）；当 [active] 为 false（退场中）时立即冻结内部形变与语义，
 * 避免刚开始收缩时打开新增页造成斜向乱飞或暴露无效操作节点。
 */
@Composable
fun AppBottomDock(
    tabs: List<AppDestination>,
    currentRoute: String?,
    onAddMeasurement: () -> Unit,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    active: Boolean = true
) {
    val reducedMotion = LocalReducedMotion.current
    val targetProgress = if (compact) 1f else 0f
    val targetTabAlpha = if (compact) 0f else 1f
    val progress = if (reducedMotion) {
        null
    } else {
        animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = AppMotion.springGentle(reducedMotion = false),
            label = "dockCompact"
        )
    }
    val tabAlpha = if (reducedMotion) {
        null
    } else {
        animateFloatAsState(
            targetValue = targetTabAlpha,
            animationSpec = if (compact) AppMotion.floatPress else AppMotion.floatDockTabsEnter,
            label = "dockTabs"
        )
    }
    val exitSnapshot = remember { DockExitSnapshot() }
    fun resolveFraction(): Float {
        val live = (progress?.value ?: targetProgress).coerceIn(0f, 1f)
        if (active || exitSnapshot.fraction.isNaN()) {
            exitSnapshot.fraction = live
        }
        return if (active) live else exitSnapshot.fraction
    }
    fun resolveTabAlpha(): Float {
        val live = (tabAlpha?.value ?: targetTabAlpha).coerceIn(0f, 1f)
        if (active || exitSnapshot.tabAlpha.isNaN()) {
            exitSnapshot.tabAlpha = live
        }
        return if (active) live else exitSnapshot.tabAlpha
    }

    val capsuleShape = RoundedCornerShape50Percent
    val leftTabs = tabs.take(2)
    val rightTabs: List<AppDestination?> = if (tabs.size == 3) {
        listOf(null, tabs.last())
    } else {
        tabs.drop(2)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { })
            .navigationBarsPadding()
            .padding(
                start = AppDimensions.dockHorizontalMargin,
                end = AppDimensions.dockHorizontalMargin,
                top = 2.dp,
                bottom = AppDimensions.dockBottomMargin
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(AppDimensions.dockCapsuleHeight)) {
            val density = LocalDensity.current
            val compactPx = with(density) { CompactDockSize.toPx() }
            val fullHeightPx = with(density) { AppDimensions.dockCapsuleHeight.toPx() }
            val maxCapsuleWidthPx = with(density) { 430.dp.toPx() }
            // 收起时停靠右下角（符合右手单手拇指热区且少遮挡左对齐正文）。
            val buttonTravelPx = with(density) { (maxWidth / 2 - CompactDockSize / 2).toPx() }

            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val fraction = resolveFraction()
                        val fullWidth = minOf(constraints.maxWidth.toFloat(), maxCapsuleWidthPx)
                        val width = (fullWidth + (compactPx - fullWidth) * fraction).roundToInt()
                        val height = (fullHeightPx + (compactPx - fullHeightPx) * fraction).roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height)
                        )
                        layout(constraints.maxWidth, fullHeightPx.roundToInt()) {
                            val centeredX = (constraints.maxWidth - fullWidth) / 2f
                            val rightX = constraints.maxWidth - compactPx
                            val x = (centeredX + (rightX - centeredX) * fraction).roundToInt()
                            placeable.place(x, fullHeightPx.roundToInt() - height)
                        }
                    }
                    .shadow(6.dp, capsuleShape, clip = false)
                    .background(MaterialTheme.colorScheme.surface, capsuleShape)
            )
            val tabsInteractive = active && !compact
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .height(AppDimensions.dockCapsuleHeight)
                    .graphicsLayer { alpha = resolveTabAlpha() }
                    .then(if (tabsInteractive) Modifier else Modifier.clearAndSetSemantics { })
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTabGroup(leftTabs, currentRoute, onSelect, enabled = tabsInteractive)
                Spacer(Modifier.width(AppDimensions.dockCenterSlot))
                DockTabGroup(rightTabs, currentRoute, onSelect, enabled = tabsInteractive)
            }
            AddMeasurementButton(
                onClick = onAddMeasurement,
                enabled = active,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    val fraction = resolveFraction()
                    translationX = buttonTravelPx * fraction
                    translationY = (fullHeightPx - compactPx) / 2 * fraction
                }
            )
        }
    }
}

@Composable
private fun RowScope.DockTabGroup(
    tabs: List<AppDestination?>,
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit,
    enabled: Boolean
) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
        tabs.forEach { destination ->
            if (destination == null) {
                Spacer(Modifier.weight(1f))
            } else {
                DockTab(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelect(destination) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 中央「记一次血压」按钮：与胶囊同心居中，触控层外置稳定，带跟手按压回弹与清晰焦点环。 */
@Composable
private fun AddMeasurementButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(AppDimensions.dockCenterButtonSize)
            .then(if (enabled) Modifier.semantics { contentDescription = "记一次血压" } else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .focusRing(
                interactionSource = interactionSource,
                shape = CircleShape,
                enabled = enabled,
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.5.dp,
                inset = 1.dp,
                fillAlpha = 0f
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** 单个入口：图标、文字、选中圆点严格垂直居中，带平滑色变、圆点弹簧生长与胶囊焦点环。 */
@Composable
private fun DockTab(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    // 在极端系统大字（2.0x）下对胶囊内 11sp 导航标签做上限兜底，避免挤出 64dp 胶囊。
    val labelFontSize = (11f * density.fontScale.coerceAtMost(1.35f) / density.fontScale.coerceAtLeast(0.5f)).sp
    val targetColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color = appMotionColor(
        targetValue = targetColor,
        reducedMotion = reducedMotion,
        label = "dock_tab_color"
    )
    val targetIconScale = if (selected && !reducedMotion) 1.06f else 1f
    val iconScale = appMotionFloat(
        targetValue = targetIconScale,
        reducedMotion = reducedMotion,
        spec = AppMotion.springSnappy(reducedMotion = false),
        label = "dock_icon_scale"
    )
    val targetDotScale = if (selected) 1f else 0f
    val dotScale = appMotionFloat(
        targetValue = targetDotScale,
        reducedMotion = reducedMotion,
        spec = AppMotion.springCheck(reducedMotion = false),
        label = "dock_dot_scale"
    )
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick
            )
            .pressableScale(interactionSource, enabled = enabled, pressedScale = 0.94f)
            .clip(CircleShape)
            .focusRing(interactionSource, CircleShape, enabled = enabled),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = color
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = labelFontSize),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(4.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                    alpha = dotScale.coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

/** 真胶囊：显式 50% 圆角，避免不同高度下退化成圆角矩形。 */
private val RoundedCornerShape50Percent = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
private val CompactDockSize = 56.dp
