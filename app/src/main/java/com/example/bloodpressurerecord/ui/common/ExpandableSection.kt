package com.example.bloodpressurerecord.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.AppMotion
import kotlin.math.roundToInt

/**
 * 保持内容常驻组合（避免折叠图表丢失缩放/选中状态），
 * 由单一 expandProgress 在布局与绘制阶段读取（不在组合阶段读取动画值），
 * 开启减少动态效果时直接使用目标值实现 0ms 同帧落位。
 */
@Composable
fun ExpandableSection(
    title: String,
    summary: String,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    stateKey: String = title,
    content: @Composable ColumnScope.() -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    var expanded by rememberSaveable(stateKey) { mutableStateOf(initiallyExpanded) }
    val targetProgress = if (expanded) 1f else 0f
    val expandProgressState = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = AppMotion.springGentle(reducedMotion),
        label = "section_expand_progress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起$title" else "展开$title",
                    onClick = { expanded = !expanded }
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AnimatedVisibility(
                    visible = !expanded,
                    enter = AppMotion.expandVisibilityEnter(reducedMotion),
                    exit = AppMotion.expandVisibilityExit(reducedMotion)
                ) {
                    Text(
                        text = summary.ifBlank { "暂无数据" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val progress = if (reducedMotion) {
                            targetProgress
                        } else {
                            expandProgressState.value.coerceIn(0f, 1f)
                        }
                        rotationZ = progress * 180f
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                .layout { measurable, constraints ->
                    val progress = if (reducedMotion) {
                        targetProgress
                    } else {
                        expandProgressState.value.coerceIn(0f, 1f)
                    }
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val targetHeight = (placeable.height * progress).roundToInt()
                    layout(placeable.width, targetHeight) {
                        // 完全收起时不再放置子节点，彻底隔离触摸与无障碍焦点。
                        if (targetHeight > 0) {
                            placeable.placeRelativeWithLayer(0, 0) {
                                alpha = progress
                            }
                        }
                    }
                }
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
