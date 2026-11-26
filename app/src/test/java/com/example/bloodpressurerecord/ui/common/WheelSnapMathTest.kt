package com.example.bloodpressurerecord.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelSnapMathTest {

    @Test
    fun dragBelowHalfWithoutFlick_snapsBackToCurrentItem() {
        assertEquals(
            12,
            computeWheelSnapTargetIndex(
                currentIndex = 12,
                currentOffsetPx = 18,
                itemSizePx = 48,
                initialVelocityPxPerSec = 120f,
                itemCount = 24
            )
        )
    }

    @Test
    fun dragPastHalf_snapsToNextItemImmediately() {
        assertEquals(
            13,
            computeWheelSnapTargetIndex(
                currentIndex = 12,
                currentOffsetPx = 26,
                itemSizePx = 48,
                initialVelocityPxPerSec = 0f,
                itemCount = 24
            )
        )
    }

    @Test
    fun briskFlick_stepsSingleItemAndClampsBounds() {
        assertEquals(
            13,
            computeWheelSnapTargetIndex(
                currentIndex = 12,
                currentOffsetPx = 6,
                itemSizePx = 48,
                initialVelocityPxPerSec = 950f,
                itemCount = 24
            )
        )
        assertEquals(
            11,
            computeWheelSnapTargetIndex(
                currentIndex = 12,
                currentOffsetPx = 6,
                itemSizePx = 48,
                initialVelocityPxPerSec = -950f,
                itemCount = 24
            )
        )
        assertEquals(
            0,
            computeWheelSnapTargetIndex(
                currentIndex = 0,
                currentOffsetPx = 0,
                itemSizePx = 48,
                initialVelocityPxPerSec = -1800f,
                itemCount = 24
            )
        )
        assertEquals(
            59,
            computeWheelSnapTargetIndex(
                currentIndex = 59,
                currentOffsetPx = 12,
                itemSizePx = 48,
                initialVelocityPxPerSec = 1800f,
                itemCount = 60
            )
        )
    }
}
