package com.example.bloodpressurerecord.domain.ocr

import org.junit.Assert.assertNull
import org.junit.Test

class CalibratedLcdRecognizerTest {

    @Test
    fun blankImageIsRejected() {
        val image = BinaryImage(160, 240, BooleanArray(160 * 240))

        assertNull(CalibratedLcdRecognizer.recognize(image))
    }

    @Test
    fun unrelatedDensePatternIsRejected() {
        val width = 160
        val height = 240
        val pixels = BooleanArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (x / 4 + y / 4) % 2 == 0
        }
        val image = BinaryImage(width, height, pixels)

        assertNull(CalibratedLcdRecognizer.recognize(image))
    }
}
