package com.example.bloodpressurerecord.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Observes consumed page scroll; never takes distance away from the page or its top bar. */
@Stable
class DockScrollState(
    private val collapseThresholdPx: Float,
    private val expandThresholdPx: Float
) {
    var isCompact by mutableStateOf(false)
        private set

    // Plain fields: pointer samples do not invalidate composition until a threshold is crossed.
    private var distance = 0f
    private var direction = 0

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // Ignore flings, programmatic scroll, and gestures that did not move page content.
            if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                observe(consumed.y)
            }
            return Offset.Zero
        }
    }

    private fun observe(deltaY: Float) {
        val nextDirection = if (deltaY < 0f) -1 else 1
        if (nextDirection != direction) {
            direction = nextDirection
            distance = 0f
        }
        if ((!isCompact && nextDirection < 0) || (isCompact && nextDirection > 0)) {
            distance += kotlin.math.abs(deltaY)
            val threshold = if (isCompact) expandThresholdPx else collapseThresholdPx
            if (distance >= threshold) {
                isCompact = !isCompact
                distance = 0f
            }
        } else {
            distance = 0f
        }
    }
}

/** A new route starts expanded; this UI-only state never changes saved page scroll positions. */
@Composable
fun rememberDockScrollState(route: String?): DockScrollState {
    val density = LocalDensity.current
    return remember(route, density) {
        with(density) { DockScrollState(28.dp.toPx(), 18.dp.toPx()) }
    }
}
