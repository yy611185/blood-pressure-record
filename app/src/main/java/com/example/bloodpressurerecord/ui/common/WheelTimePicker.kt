package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloodpressurerecord.ui.LocalReducedMotion
import com.example.bloodpressurerecord.ui.theme.TabularFeatureSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val WheelVisibleCount = 3

/**
 * 24 小时制轮盘时间选择对话框：左右两个滚轮分别选择小时（00–23）与分钟（00–59），
 * 按距中心距离在 graphicsLayer 连续插值缩放、倾角与透明度，跨格触发轻触觉刻度感。
 * 点选条目会立即记录目标值（减少动态效果时 0ms 同帧直达），点选后立刻点击确定也能提交所选时间。
 */
@Composable
fun WheelTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val itemHeight = with(LocalDensity.current) { 32.sp.toDp().coerceAtLeast(48.dp) }
    val hourState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialHour.coerceIn(0, 23)
    )
    val minuteState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialMinute.coerceIn(0, 59)
    )
    val centeredHour by remember { derivedStateOf { hourState.centeredIndex(24) } }
    val centeredMinute by remember { derivedStateOf { minuteState.centeredIndex(60) } }

    var pendingHourTarget by remember { mutableStateOf<Int?>(null) }
    var pendingMinuteTarget by remember { mutableStateOf<Int?>(null) }
    var hourClickJob by remember { mutableStateOf<Job?>(null) }
    var minuteClickJob by remember { mutableStateOf<Job?>(null) }

    val selectedHour = pendingHourTarget ?: centeredHour
    val selectedMinute = pendingMinuteTarget ?: centeredMinute

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "24小时制时间选择，滑动滚轮选择小时和分钟"
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(itemHeight)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.large
                        )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WheelColumn(
                        state = hourState,
                        itemCount = 24,
                        selectedIndex = selectedHour,
                        centeredIndex = centeredHour,
                        itemHeight = itemHeight,
                        describe = { "$it 时" },
                        onUserTouch = {
                            if (hourClickJob?.isActive != true) {
                                pendingHourTarget = null
                            } else {
                                hourClickJob?.cancel()
                                hourClickJob = null
                                pendingHourTarget = null
                            }
                        },
                        onItemClick = { index ->
                            pendingHourTarget = index
                            hourClickJob?.cancel()
                            val job = scope.launch {
                                if (reducedMotion) {
                                    hourState.scrollToItem(index)
                                } else {
                                    hourState.animateScrollToItem(index)
                                }
                            }
                            hourClickJob = job
                            job.invokeOnCompletion { cancelled ->
                                if (hourClickJob === job) {
                                    hourClickJob = null
                                    if (cancelled == null && hourState.centeredIndex(24) == index) {
                                        pendingHourTarget = null
                                    }
                                }
                            }
                        }
                    )
                    Text(
                        ":",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    WheelColumn(
                        state = minuteState,
                        itemCount = 60,
                        selectedIndex = selectedMinute,
                        centeredIndex = centeredMinute,
                        itemHeight = itemHeight,
                        describe = { "$it 分" },
                        onUserTouch = {
                            if (minuteClickJob?.isActive != true) {
                                pendingMinuteTarget = null
                            } else {
                                minuteClickJob?.cancel()
                                minuteClickJob = null
                                pendingMinuteTarget = null
                            }
                        },
                        onItemClick = { index ->
                            pendingMinuteTarget = index
                            minuteClickJob?.cancel()
                            val job = scope.launch {
                                if (reducedMotion) {
                                    minuteState.scrollToItem(index)
                                } else {
                                    minuteState.animateScrollToItem(index)
                                }
                            }
                            minuteClickJob = job
                            job.invokeOnCompletion { cancelled ->
                                if (minuteClickJob === job) {
                                    minuteClickJob = null
                                    if (cancelled == null && minuteState.centeredIndex(60) == index) {
                                        pendingMinuteTarget = null
                                    }
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pendingHourTarget ?: centeredHour, pendingMinuteTarget ?: centeredMinute) }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 由滚动位置换算当前居中的条目下标。 */
private fun LazyListState.centeredIndex(itemCount: Int): Int {
    val info = layoutInfo
    val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: return firstVisibleItemIndex
    val offsetRatio = firstVisibleItemScrollOffset.toFloat() / itemSize
    return (firstVisibleItemIndex + offsetRatio.roundToInt()).coerceIn(0, itemCount - 1)
}

/**
 * 0x 减少动态效果下滚轮松手即刻落位的目标下标计算：
 * 已拖过半格则按最近格对齐；未拖过半格但带明确轻拂速度时前进/后退一格，绝不滑行。
 */
internal fun computeWheelSnapTargetIndex(
    currentIndex: Int,
    currentOffsetPx: Int,
    itemSizePx: Int,
    initialVelocityPxPerSec: Float,
    itemCount: Int
): Int {
    if (itemCount <= 0) return 0
    if (itemSizePx <= 0) return currentIndex.coerceIn(0, itemCount - 1)
    val nearestStep = (currentOffsetPx.toFloat() / itemSizePx.toFloat()).roundToInt()
    val velocityStep = when {
        nearestStep == 0 && initialVelocityPxPerSec > 600f -> 1
        nearestStep == 0 && initialVelocityPxPerSec < -600f -> -1
        else -> 0
    }
    return (currentIndex + nearestStep + velocityStep).coerceIn(0, itemCount - 1)
}

/** 0x 模式专属吸附：绕过不受系统动画缩放控制的 Decay 惯性曲线，在单次同步 scrollBy 中 0ms 落位。 */
private class ImmediateSnapFlingBehavior(
    private val state: LazyListState,
    private val itemCount: Int
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val itemSize = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return 0f
        if (itemSize <= 0) return 0f
        val currentIndex = state.firstVisibleItemIndex
        val currentOffset = state.firstVisibleItemScrollOffset
        val targetIndex = computeWheelSnapTargetIndex(
            currentIndex = currentIndex,
            currentOffsetPx = currentOffset,
            itemSizePx = itemSize,
            initialVelocityPxPerSec = initialVelocity,
            itemCount = itemCount
        )
        val deltaPx = (targetIndex - currentIndex) * itemSize - currentOffset
        if (deltaPx != 0) {
            scrollBy(deltaPx.toFloat())
        }
        return 0f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    state: LazyListState,
    itemCount: Int,
    selectedIndex: Int,
    centeredIndex: Int,
    itemHeight: Dp,
    describe: (Int) -> String,
    onUserTouch: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    var lastHapticIndex by remember { mutableIntStateOf(centeredIndex) }
    val normalFlingBehavior = rememberSnapFlingBehavior(lazyListState = state)
    val reducedFlingBehavior = remember(state, itemCount) {
        ImmediateSnapFlingBehavior(state, itemCount)
    }

    LaunchedEffect(centeredIndex) {
        if (centeredIndex != lastHapticIndex) {
            if (state.isScrollInProgress && !reducedMotion) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHapticIndex = centeredIndex
        }
    }

    // scrollToItem 会取消同优先级的惯性滚动。手指仍在拖动时，由手势结束后的分支对齐。
    LaunchedEffect(reducedMotion) {
        if (reducedMotion && state.isScrollInProgress) {
            state.scrollToItem(state.centeredIndex(itemCount))
        }
    }

    // 拖动手势取消后也要对齐整格。
    LaunchedEffect(reducedMotion, state.isScrollInProgress) {
        if (reducedMotion && !state.isScrollInProgress && state.firstVisibleItemScrollOffset != 0) {
            state.scrollToItem(state.centeredIndex(itemCount))
        }
    }

    val itemShape = RoundedCornerShape(12.dp)
    LazyColumn(
        state = state,
        flingBehavior = if (reducedMotion) reducedFlingBehavior else normalFlingBehavior,
        modifier = Modifier
            .width(84.dp)
            .height(itemHeight * WheelVisibleCount)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragged && abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                            dragged = true
                            onUserTouch()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentPadding = PaddingValues(vertical = itemHeight)
    ) {
        items(itemCount) { index ->
            val isSelected = index == selectedIndex
            val itemInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .selectable(
                        selected = isSelected,
                        interactionSource = itemInteraction,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onItemClick(index) }
                    )
                    .semantics { contentDescription = describe(index) }
                    .clip(itemShape)
                    .focusRing(itemInteraction, itemShape)
                    .graphicsLayer {
                        if (reducedMotion) {
                            alpha = if (isSelected) 1f else 0.55f
                            scaleX = if (isSelected) 1f else 0.78f
                            scaleY = if (isSelected) 1f else 0.78f
                            rotationX = 0f
                        } else {
                            val layoutInfo = state.layoutInfo
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemSize = itemInfo?.size?.toFloat()?.coerceAtLeast(1f) ?: 1f
                            val signedDistance = if (itemInfo != null) {
                                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                ((itemCenter - viewportCenter) / itemSize).coerceIn(-1.5f, 1.5f)
                            } else {
                                if (isSelected) 0f else 1f
                            }
                            val fraction = abs(signedDistance).coerceIn(0f, 1f)
                            val scale = 1f - 0.22f * fraction
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.52f * fraction
                            rotationX = (-signedDistance * 16f).coerceIn(-22f, 22f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TabularFeatureSettings),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
