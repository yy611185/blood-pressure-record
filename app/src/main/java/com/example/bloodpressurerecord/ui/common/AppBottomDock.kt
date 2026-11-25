package com.example.bloodpressurerecord.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.getValue
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

/**
 * 悬浮在页面底部的胶囊 Dock。
 * 收起/展开使用保速弹簧（可随时打断），导航标签在极端大字下做上限兜底防挤出胶囊。
 */
@Composable
fun AppBottomDock(
    tabs: List<AppDestination>,
    currentRoute: String?,
    onAddMeasurement: () -> Unit,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val targetProgress = if (compact) 1f else 0f
    val targetTabAlpha = if (compact) 0f else 1f
    val progress = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = AppMotion.springGentle(reducedMotion),
        label = "dockCompact"
    )
    val tabAlpha = animateFloatAsState(
        targetValue = targetTabAlpha,
        animationSpec = if (reducedMotion) {
            AppMotion.springPress(reducedMotion = true)
        } else if (compact) {
            AppMotion.floatPress
        } else {
            AppMotion.floatDockTabsEnter
        },
        label = "dockTabs"
    )
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
                        val fraction = if (reducedMotion) targetProgress else progress.value.coerceIn(0f, 1f)
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
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .height(AppDimensions.dockCapsuleHeight)
                    .graphicsLayer { alpha = if (reducedMotion) targetTabAlpha else tabAlpha.value }
                    .then(if (compact) Modifier.clearAndSetSemantics { } else Modifier)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTabGroup(leftTabs, currentRoute, onSelect, enabled = !compact)
                Spacer(Modifier.width(AppDimensions.dockCenterSlot))
                DockTabGroup(rightTabs, currentRoute, onSelect, enabled = !compact)
            }
            AddMeasurementButton(
                onClick = onAddMeasurement,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    val fraction = if (reducedMotion) targetProgress else progress.value.coerceIn(0f, 1f)
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

/** 中央「记一次血压」按钮：与胶囊同心居中，带跟手按压回弹。 */
@Composable
private fun AddMeasurementButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(AppDimensions.dockCenterButtonSize)
            .pressableScale(interactionSource, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = "记一次血压" }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
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

/** 单个入口：图标、文字、选中圆点严格垂直居中，带平滑色变与圆点弹簧生长。 */
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
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = AppMotion.colorSpec(reducedMotion),
        label = "dock_tab_color"
    )
    val color = if (reducedMotion) targetColor else animatedColor
    val targetIconScale = if (selected && !reducedMotion) 1.06f else 1f
    val iconScaleState = animateFloatAsState(
        targetValue = targetIconScale,
        animationSpec = AppMotion.springSnappy(reducedMotion),
        label = "dock_icon_scale"
    )
    val targetDotScale = if (selected) 1f else 0f
    val dotScaleState = animateFloatAsState(
        targetValue = targetDotScale,
        animationSpec = AppMotion.springCheck(reducedMotion),
        label = "dock_dot_scale"
    )

    Column(
        modifier = modifier
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = if (reducedMotion) targetIconScale else iconScaleState.value
                    scaleX = s
                    scaleY = s
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
                    val d = if (reducedMotion) targetDotScale else dotScaleState.value
                    scaleX = d
                    scaleY = d
                    alpha = d.coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

/** 真胶囊：显式 50% 圆角，避免不同高度下退化成圆角矩形。 */
private val RoundedCornerShape50Percent = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
private val CompactDockSize = 56.dp
