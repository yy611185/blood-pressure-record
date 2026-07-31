package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
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
        modifier = modifier.height(AppDimensions.primaryButtonHeight),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = WarmNeutral300,
            disabledContentColor = WarmTextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        contentPadding = PaddingValues(horizontal = AppSpacing.xLarge)
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
        modifier = modifier.height(AppDimensions.primaryButtonHeight),
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
        modifier = modifier.height(AppDimensions.primaryButtonHeight),
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
    // 暖阳设计：28dp 圆角、柔和投影、无描边细线。
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
    val resolved = status?.style(MaterialTheme.colorScheme)
    val containerColor = resolved?.containerColor ?: if (isAbnormal) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = resolved?.contentColor ?: if (isAbnormal) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .background(color = containerColor, shape = MaterialTheme.shapes.large)
            .padding(horizontal = AppSpacing.medium, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        resolved?.let {
            Icon(
                imageVector = it.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(AppSpacing.xSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
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
            .height(AppDimensions.primaryButtonHeight + AppSpacing.small)
            .padding(horizontal = AppDimensions.pageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
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
            // 一级页面 24sp 大标题，带返回键的子页 22sp。
            style = if (onBack != null) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        actions()
    }
}

/** 暖阳设计的 46dp 圆形图标底座，用于设置列表等。 */
@Composable
fun RoundIconBadge(
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
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
