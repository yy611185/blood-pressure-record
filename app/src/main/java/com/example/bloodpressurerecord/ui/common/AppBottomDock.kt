package com.example.bloodpressurerecord.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import kotlin.math.roundToInt

/**
 * 悬浮在页面底部的胶囊 Dock。
 *
 * 结构性约定（不要在别处重新引入）：
 * - 它**不是** `Scaffold.bottomBar`，而是叠在 NavHost 之上的 overlay，
 *   因此不会占掉一整块矩形布局区域，也不会把内容整体顶高；
 * - 页面只保留一份避免被遮挡的底部留白，由 [dockContentBottomPadding] 提供；
 * - 胶囊用 `RoundedCornerShape(percent = 50)`，中央按钮与四个入口完全落在胶囊内部；
 * - 收起只改变浮层内部的空背景尺寸与按钮图层，不改变页面留白或测量页面内容。
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
    val progress = animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = AppMotion.floatDock,
        label = "dockCompact"
    )
    val tabAlpha = animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        // Reveal labels after the capsule has opened far enough to contain the outer tabs.
        animationSpec = if (compact) AppMotion.floatPress else AppMotion.floatDockTabsEnter,
        label = "dockTabs"
    )
    val capsuleShape = RoundedCornerShape50Percent
    val leftTabs = tabs.take(2)
    // 只有 3 个 tab（关闭趋势图）时，右侧留一个空槽，保证中央按钮仍然居中。
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
            val buttonTravelPx = with(density) { (CompactDockSize / 2 - maxWidth / 2).toPx() }

            // Only this empty background is measured during the width transition. Tabs keep their
            // full layout, and the NavHost / lists / Canvas never measure because of this progress.
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val fraction = progress.value
                        val fullWidth = minOf(constraints.maxWidth.toFloat(), maxCapsuleWidthPx)
                        val width = (fullWidth + (compactPx - fullWidth) * fraction).roundToInt()
                        val height = (fullHeightPx + (compactPx - fullHeightPx) * fraction).roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height)
                        )
                        layout(constraints.maxWidth, fullHeightPx.roundToInt()) {
                            val x = ((constraints.maxWidth - fullWidth) / 2 * (1f - fraction)).roundToInt()
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
                    .graphicsLayer { alpha = tabAlpha.value }
                    .then(if (compact) Modifier.clearAndSetSemantics { } else Modifier)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTabGroup(leftTabs, currentRoute, onSelect, enabled = !compact)
                Spacer(Modifier.width(AppDimensions.dockCenterSlot))
                DockTabGroup(rightTabs, currentRoute, onSelect, enabled = !compact)
            }
            // One persistent + button: its visual layer and hit area travel together, preserving
            // the existing 52dp target at every point of the transition (compact shell is 56dp).
            AddMeasurementButton(
                onClick = onAddMeasurement,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    translationX = buttonTravelPx * progress.value
                    translationY = (fullHeightPx - compactPx) / 2 * progress.value
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

/** 中央「记一次血压」按钮：与胶囊同心居中，不再向上突出。 */
@Composable
private fun AddMeasurementButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(AppDimensions.dockCenterButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = "记一次血压" }
            .clickable(role = Role.Button, onClick = onClick),
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

/** 单个入口：图标、文字、选中圆点严格垂直居中，触摸区不小于 48dp。 */
@Composable
private fun DockTab(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(destination.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(4.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
                )
        )
    }
}

/** 真胶囊：显式 50% 圆角，避免不同高度下退化成圆角矩形。 */
private val RoundedCornerShape50Percent = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
private val CompactDockSize = 56.dp
