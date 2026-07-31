package com.example.bloodpressurerecord.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val WheelItemHeight = 44.dp
private const val WheelVisibleCount = 3

/**
 * 24 小时制轮盘时间选择对话框：左右两个滚轮分别选择小时（00–23）与分钟（00–59），
 * 滚动自动吸附到中间选中行。替代旧的数字输入方式。
 */
@Composable
fun WheelTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val hourState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialHour.coerceIn(0, 23)
    )
    val minuteState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialMinute.coerceIn(0, 59)
    )
    val selectedHour by remember { derivedStateOf { hourState.centeredIndex(24) } }
    val selectedMinute by remember { derivedStateOf { minuteState.centeredIndex(60) } }

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
                // 中间选中行的药丸高亮带
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(WheelItemHeight)
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
                        describe = { "$it 时" }
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
                        describe = { "$it 分" }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedHour, selectedMinute) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    state: LazyListState,
    itemCount: Int,
    selectedIndex: Int,
    describe: (Int) -> String
) {
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        modifier = Modifier
            .width(84.dp)
            .height(WheelItemHeight * WheelVisibleCount),
        // 上下各留一行高度，让第一项/最后一项也能滚到中间。
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = WheelItemHeight
        )
    ) {
        items(itemCount) { index ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .semantics { contentDescription = describe(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(index),
                    fontSize = if (isSelected) 24.sp else 18.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.alpha(if (isSelected) 1f else 0.55f)
                )
            }
        }
    }
}
