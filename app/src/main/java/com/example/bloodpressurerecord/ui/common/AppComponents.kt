package com.example.bloodpressurerecord.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppMotion
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus
import com.example.bloodpressurerecord.ui.theme.LocalBpGradePalette
import com.example.bloodpressurerecord.ui.theme.bloodPressureChipColors
import com.example.bloodpressurerecord.ui.theme.style
import kotlin.math.roundToInt

/**
 * 零重排按压缩放修饰符：仅驱动 GPU graphicsLayer，不改变触控热区。
 * 调用方将同一个 [interactionSource] 传给 Button / clickable 以保证热区外置稳定。
 */
@Composable
fun Modifier.pressableScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (enabled && pressed && !reducedMotion) pressedScale else 1f
    val scaleState = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = AppMotion.springPress(reducedMotion),
        label = "pressable_scale"
    )
    return this.graphicsLayer {
        val s = if (reducedMotion || !enabled) 1f else scaleState.value
        scaleX = s
        scaleY = s
    }
}

@Composable
private fun appMotionColor(
    targetValue: Color,
    reducedMotion: Boolean,
    label: String,
    duration: Int = AppMotion.Fast
): Color = if (reducedMotion) targetValue else {
    animateColorAsState(targetValue, AppMotion.colorSpec(false, duration), label = label).value
}

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val containerColor = appMotionColor(
        targetValue = if (enabled) colors.primary else colors.surfaceContainerHighest,
        reducedMotion = reducedMotion,
        label = "primary_btn_container"
    )
    val contentColor = appMotionColor(
        targetValue = if (enabled) colors.onPrimary else colors.onSurfaceVariant,
        reducedMotion = reducedMotion,
        label = "primary_btn_content"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 54.dp)
            .pressableScale(interactionSource, enabled = enabled),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (enabled) 3.dp else 0.dp),
        contentPadding = PaddingValues(horizontal = AppSpacing.xLarge, vertical = 12.dp)
    ) {
        if (content != null) {
            content()
        } else {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(AppSpacing.small))
            }
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val containerColor = appMotionColor(
        targetValue = if (enabled) colors.surfaceVariant else colors.surfaceContainerLow,
        reducedMotion = reducedMotion,
        label = "secondary_btn_container"
    )
    val contentColor = appMotionColor(
        targetValue = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        reducedMotion = reducedMotion,
        label = "secondary_btn_content"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 54.dp)
            .pressableScale(interactionSource, enabled = enabled),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 54.dp)
            .pressableScale(interactionSource, enabled = enabled),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DataCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        // Keep the pointer node outside the visual layer so the touch area stays unchanged.
        Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .pressableScale(interactionSource, pressedScale = 0.985f)
    } else {
        Modifier
    }
    // 原稿 26dp 圆角、奶白卡片与轻微投影。
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.padding(AppDimensions.cardPadding)) {
            content()
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    isAbnormal: Boolean,
    modifier: Modifier = Modifier,
    status: BloodPressureVisualStatus? = null
) {
    val reducedMotion = LocalReducedMotion.current
    val resolvedStatus = status ?: if (isAbnormal) BloodPressureVisualStatus.HIGH else BloodPressureVisualStatus.NORMAL
    val chip = bloodPressureChipColors(text, resolvedStatus, MaterialTheme.colorScheme, LocalBpGradePalette.current)
    val container = appMotionColor(chip.container, reducedMotion, label = "chip_bg")
    val content = appMotionColor(chip.content, reducedMotion, label = "chip_fg")
    val dot = appMotionColor(chip.dot, reducedMotion, label = "chip_dot")

    Row(
        modifier = modifier
            .background(color = container, shape = RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (resolvedStatus == BloodPressureVisualStatus.HIGH_RISK || text.contains("高风险")) {
            Icon(
                imageVector = BloodPressureVisualStatus.HIGH_RISK.style(MaterialTheme.colorScheme).icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(AppSpacing.xSmall))
        } else {
            Box(Modifier.size(9.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 全应用唯一的 48dp 顶栏返回按钮（取代旧 AppBackButton）。 */
@Composable
fun AppBackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = appMotionColor(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        reducedMotion = reducedMotion,
        duration = AppMotion.Micro,
        label = "back_btn_bg"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(48.dp)
            .pressableScale(interactionSource, pressedScale = 0.93f)
            .background(background, RoundedCornerShape(16.dp))
    ) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = stringResource(R.string.back),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    hideOnScroll: HideOnScrollState? = null,
    actions: @Composable () -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null
) {
    val collapseModifier = if (hideOnScroll != null) {
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                hideOnScroll.barHeightPx = placeable.height.toFloat()
                val offset = hideOnScroll.offsetPx.roundToInt()
                layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) {
                    placeable.place(0, offset)
                }
            }
    } else {
        Modifier
    }
    Row(
        modifier = collapseModifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = AppDimensions.pageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            AppBackIconButton(onClick = onBack)
            Spacer(modifier = Modifier.width(AppSpacing.large))
        }

        if (titleContent != null) {
            Box(Modifier.weight(1f)) { titleContent() }
        } else Text(
            text = title,
            style = if (onBack != null) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineLarge
            },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        actions()
    }
}

/**
 * 暖阳配色的分段胶囊选择器：单一共享弹簧滑块跟手滑移（中途连点实时转向），
 * 窄屏大字（如 320dp、2.0x）下自动收紧内边距或纵向堆叠，保证每一项文字完整可见不裁切。
 */
@Composable
fun SegmentedPillGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return
    val reducedMotion = LocalReducedMotion.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val animatedIndexState = animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = AppMotion.springSnappy(reducedMotion),
        label = "segment_slider_index"
    )
    val sliderColor = colors.primaryContainer
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    val maxOptionWidthPx = remember(options, density.fontScale, density.density, labelStyle) {
        options.maxOf { option ->
            textMeasurer.measure(
                text = option,
                style = labelStyle,
                maxLines = 1,
                softWrap = false
            ).size.width
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceVariant)
            .padding(SegmentedPillGap)
    ) {
        val count = options.size
        val gapPx = with(density) { SegmentedPillGap.toPx() }
        val innerWidthPx = with(density) { maxWidth.toPx() }
        val singleRowItemWidthPx = ((innerWidthPx - gapPx * (count - 1)).coerceAtLeast(0f)) / count
        val minCompactPaddingPx = with(density) { 2.dp.toPx() }
        val roundingSlackPx = with(density) { 6.dp.toPx() }
        val stacked = singleRowItemWidthPx < maxOptionWidthPx + minCompactPaddingPx * 2f + roundingSlackPx
        val effectiveHorizontalPadding = if (stacked) {
            horizontalPadding
        } else {
            val sparePerSidePx = ((singleRowItemWidthPx - maxOptionWidthPx - roundingSlackPx) / 2f)
                .coerceAtLeast(minCompactPaddingPx)
            minOf(horizontalPadding, with(density) { sparePerSidePx.toDp() })
        }

        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val currentIndex = if (reducedMotion) clampedIndex.toFloat() else animatedIndexState.value
                        val itemHeight = ((size.height - gapPx * (count - 1)).coerceAtLeast(0f)) / count
                        val y = (itemHeight + gapPx) * currentIndex
                        val radiusPx = 16.dp.toPx()
                        drawRoundRect(
                            color = sliderColor,
                            topLeft = Offset(0f, y),
                            size = Size(size.width, itemHeight),
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(SegmentedPillGap)
            ) {
                options.forEachIndexed { index, option ->
                    SegmentedPillOption(
                        option = option,
                        labelStyle = labelStyle,
                        selected = index == clampedIndex,
                        enabled = enabled,
                        reducedMotion = reducedMotion,
                        horizontalPadding = effectiveHorizontalPadding,
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val currentIndex = if (reducedMotion) clampedIndex.toFloat() else animatedIndexState.value
                        val itemWidth = ((size.width - gapPx * (count - 1)).coerceAtLeast(0f)) / count
                        val x = (itemWidth + gapPx) * currentIndex
                        val radiusPx = 16.dp.toPx()
                        drawRoundRect(
                            color = sliderColor,
                            topLeft = Offset(x, 0f),
                            size = Size(itemWidth, size.height),
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(SegmentedPillGap)
            ) {
                options.forEachIndexed { index, option ->
                    SegmentedPillOption(
                        option = option,
                        labelStyle = labelStyle,
                        selected = index == clampedIndex,
                        enabled = enabled,
                        reducedMotion = reducedMotion,
                        horizontalPadding = effectiveHorizontalPadding,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedPillOption(
    option: String,
    labelStyle: TextStyle,
    selected: Boolean,
    enabled: Boolean,
    reducedMotion: Boolean,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val targetTextColor = when {
        !enabled && selected -> colors.onPrimaryContainer.copy(alpha = 0.6f)
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.6f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = AppMotion.colorSpec(reducedMotion),
        label = "Segment text"
    )
    val textColor = if (reducedMotion) targetTextColor else animatedTextColor
    Box(
        modifier = modifier
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option,
            style = labelStyle.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

/** 分段胶囊的内外边距（外框与内部选中块共用，保证选中态与外框同心）。 */
private val SegmentedPillGap = 4.dp

/** 暖阳设计的 46dp 圆形图标底座，用于设置列表等。 */
@Composable
fun RoundIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
