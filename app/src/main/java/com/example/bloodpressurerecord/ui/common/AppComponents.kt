package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing
import com.example.bloodpressurerecord.ui.theme.BloodPressureVisualStatus
import com.example.bloodpressurerecord.ui.theme.WarmNeutral300
import com.example.bloodpressurerecord.ui.theme.WarmTextMuted
import com.example.bloodpressurerecord.ui.theme.style
import com.example.bloodpressurerecord.ui.theme.bloodPressureChipColors

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        contentPadding = PaddingValues(horizontal = AppSpacing.xLarge, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(AppSpacing.small))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
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
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 54.dp),
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
    content: @Composable () -> Unit
) {
    // 原稿 26dp 圆角、奶白卡片与轻微投影。
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
    val resolvedStatus = status ?: if (isAbnormal) BloodPressureVisualStatus.HIGH else BloodPressureVisualStatus.NORMAL
    val chip = bloodPressureChipColors(text, resolvedStatus, MaterialTheme.colorScheme)

    Row(
        modifier = modifier
            .background(color = chip.container, shape = RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (resolvedStatus == BloodPressureVisualStatus.HIGH_RISK || text.contains("高风险")) {
            Icon(
                imageVector = BloodPressureVisualStatus.HIGH_RISK.style(MaterialTheme.colorScheme).icon,
                contentDescription = null,
                tint = chip.content,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(AppSpacing.xSmall))
        } else {
            Box(Modifier.size(9.dp).background(chip.dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = chip.content,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    hideOnScroll: HideOnScrollState? = null,
    actions: @Composable () -> Unit = {}
) {
    // 滚动隐藏：按 offsetPx 收缩自身布局高度并上移内容，
    // 下方页面内容随之自然上移，露出更多可视区域。
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
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.large))
        }

        Text(
            text = title,
            // 一级页面沿用原稿约 30sp；子页使用较紧凑标题。
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
 * 暖阳配色的分段胶囊选择器。
 *
 * 选中态使用 `primaryContainer` / `onPrimaryContainer`（不再出现近黑块），
 * 每段文字用 `Box(contentAlignment = Center)` 严格水平+垂直居中，
 * 触摸区不小于 48dp；`semantics` 使用 RadioButton 角色，保持无障碍语义。
 */
@Composable
fun SegmentedPillGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceVariant)
            .padding(SegmentedPillGap),
        horizontalArrangement = Arrangement.spacedBy(SegmentedPillGap)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = AppDimensions.minimumTouchTarget)
                    .clip(MaterialTheme.shapes.large)
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
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
