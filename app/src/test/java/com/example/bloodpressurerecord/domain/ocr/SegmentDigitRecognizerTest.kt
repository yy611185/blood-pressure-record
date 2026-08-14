package com.example.bloodpressurerecord.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentDigitRecognizerTest {

    @Test
    fun `recognizes three rows of seven segment digits`() {
        val image = sevenSegmentScreen(listOf("103", "68", "69"))

        val result = SegmentDigitRecognizer.recognize(image)

        assertNotNull(result)
        assertEquals(103, result!!.candidate.systolic)
        assertEquals(68, result.candidate.diastolic)
        assertEquals(69, result.candidate.pulse)
        assertTrue(ReadingFlag.FROM_FALLBACK in result.candidate.flags)
    }

    @Test
    fun `two valid rows are accepted without inventing pulse`() {
        val image = sevenSegmentScreen(listOf("120", "80"))

        val result = SegmentDigitRecognizer.recognize(image)

        assertNotNull(result)
        assertEquals(120, result!!.candidate.systolic)
        assertEquals(80, result.candidate.diastolic)
        assertEquals(null, result.candidate.pulse)
        assertTrue(ReadingFlag.PARTIAL_STRUCTURE in result.candidate.flags)
    }

    @Test
    fun `template table covers remaining digits`() {
        val image = sevenSegmentScreen(listOf("245", "147", "57"))

        val result = SegmentDigitRecognizer.recognize(image)

        assertNotNull(result)
        assertEquals(245, result!!.candidate.systolic)
        assertEquals(147, result.candidate.diastolic)
        assertEquals(57, result.candidate.pulse)
    }

    @Test
    fun `standby style single zero is rejected`() {
        val image = sevenSegmentScreen(listOf("0"))
        assertEquals(null, SegmentDigitRecognizer.recognize(image))
    }

    private fun sevenSegmentScreen(rows: List<String>): BinaryImage {
        val width = 320
        val height = 480
        val pixels = BooleanArray(width * height)
        val rowTops = listOf(60, 190, 320)
        rows.forEachIndexed { rowIndex, value ->
            val digitWidth = 44
            val digitHeight = 82
            val gap = 12
            val rowWidth = value.length * digitWidth + (value.length - 1) * gap
            var left = (width - rowWidth) / 2
            value.forEach { character ->
                drawDigit(
                    pixels = pixels,
                    imageWidth = width,
                    left = left,
                    top = rowTops[rowIndex],
                    width = digitWidth,
                    height = digitHeight,
                    digit = character.digitToInt()
                )
                left += digitWidth + gap
            }
        }
        return BinaryImage(width, height, pixels)
    }

    private fun drawDigit(
        pixels: BooleanArray,
        imageWidth: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        digit: Int
    ) {
        val active = templates.getValue(digit)
        val thickness = 7
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0 until y1) {
                for (x in x0 until x1) pixels[y * imageWidth + x] = true
            }
        }
        if (active[0]) fill(left + thickness, top, left + width - thickness, top + thickness)
        if (active[1]) fill(left, top + thickness, left + thickness, top + height / 2 - 2)
        if (active[2]) fill(left + width - thickness, top + thickness, left + width, top + height / 2 - 2)
        if (active[3]) fill(
            left + thickness,
            top + height / 2 - thickness / 2,
            left + width - thickness,
            top + height / 2 + thickness / 2 + 1
        )
        if (active[4]) fill(left, top + height / 2 + 2, left + thickness, top + height - thickness)
        if (active[5]) fill(
            left + width - thickness,
            top + height / 2 + 2,
            left + width,
            top + height - thickness
        )
        if (active[6]) fill(
            left + thickness,
            top + height - thickness,
            left + width - thickness,
            top + height
        )
    }

    private companion object {
        val templates = mapOf(
            0 to listOf(true, true, true, false, true, true, true),
            1 to listOf(false, false, true, false, false, true, false),
            2 to listOf(true, false, true, true, true, false, true),
            3 to listOf(true, false, true, true, false, true, true),
            4 to listOf(false, true, true, true, false, true, false),
            5 to listOf(true, true, false, true, false, true, true),
            6 to listOf(true, true, false, true, true, true, true),
            7 to listOf(true, false, true, false, false, true, false),
            8 to listOf(true, true, true, true, true, true, true),
            9 to listOf(true, true, true, true, false, true, true)
        )
    }
}
