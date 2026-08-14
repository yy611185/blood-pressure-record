package com.example.bloodpressurerecord.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BpReadingParserTest {

    private fun block(
        text: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        confidence: Float? = null
    ): OcrBlock = OcrBlock(
        text = text,
        left = left,
        top = top,
        right = left + width,
        bottom = top + height,
        confidence = confidence
    )

    private fun result(vararg blocks: OcrBlock): OcrResult =
        OcrResult(1080, 2400, blocks.toList())

    @Test
    fun `three line layout with time and user icon`() {
        val r = result(
            block("12:01", 400f, 120f, 160f, 70f),
            block("2", 80f, 130f, 50f, 50f),
            block("103", 400f, 700f, 240f, 240f),
            block("68", 400f, 1000f, 210f, 240f),
            block("69", 420f, 1320f, 200f, 200f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(103, c!!.systolic)
        assertEquals(68, c.diastolic)
        assertEquals(69, c.pulse)
        assertTrue(c.flags.isEmpty())
    }

    @Test
    fun `heart icon prefix is stripped`() {
        val r = result(
            block("❤69", 420f, 1320f, 200f, 200f),
            block("103", 400f, 700f, 240f, 240f),
            block("68", 400f, 1000f, 210f, 240f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(69, c!!.pulse)
    }

    @Test
    fun `two lines without pulse is allowed and flagged partial`() {
        val r = result(
            block("103", 400f, 700f, 240f, 240f),
            block("68", 400f, 1000f, 210f, 240f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(103, c!!.systolic)
        assertEquals(68, c.diastolic)
        assertNull(c.pulse)
        assertTrue(ReadingFlag.PARTIAL_STRUCTURE in c.flags)
    }

    @Test
    fun `two rows without dominant top row are rejected as mislabeled pair`() {
        // 实测故障：只识别到低压 83 + 脉搏 62，被错配成高压 83 / 低压 62。
        val r = result(
            block("83", 400f, 700f, 210f, 240f),
            block("62", 420f, 1000f, 200f, 200f)
        )
        assertNull(BpReadingParser.parse(r))
    }

    @Test
    fun `two rows with dominant top row are still accepted`() {
        val r = result(
            block("121", 400f, 700f, 240f, 240f),
            block("83", 400f, 1000f, 210f, 240f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(121, c!!.systolic)
        assertEquals(83, c.diastolic)
        assertNull(c.pulse)
        assertTrue(ReadingFlag.PARTIAL_STRUCTURE in c.flags)
    }

    @Test
    fun `standby screen returns null`() {
        val r = result(
            block("12:00", 400f, 120f, 160f, 70f),
            block("2", 80f, 130f, 50f, 50f),
            block("0", 400f, 1700f, 160f, 200f)
        )
        assertNull(BpReadingParser.parse(r))
        assertTrue(BpReadingParser.looksLikeStandby(r))
    }

    @Test
    fun `garbage or empty input returns null`() {
        assertNull(BpReadingParser.parse(result()))
        assertNull(BpReadingParser.parse(result(block("mmHg", 100f, 100f, 200f, 60f))))
    }

    @Test
    fun `invalid relationship returns null`() {
        val r = result(
            block("120", 400f, 700f, 240f, 240f),
            block("150", 400f, 1000f, 210f, 240f)
        )
        assertNull(BpReadingParser.parse(r))
    }

    @Test
    fun `boundary value is flagged`() {
        val r = result(
            block("292", 400f, 700f, 240f, 240f),
            block("80", 400f, 1000f, 210f, 240f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertTrue(ReadingFlag.BOUNDARY in c!!.flags)
    }

    @Test
    fun `low confidence is flagged ambiguous`() {
        val r = result(
            block("103", 400f, 700f, 240f, 240f, confidence = 0.4f),
            block("68", 400f, 1000f, 210f, 240f),
            block("69", 420f, 1320f, 200f, 200f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertTrue(ReadingFlag.AMBIGUOUS_DIGIT in c!!.flags)
        assertTrue(ReadingFlag.LOW_CONFIDENCE in c.flags)
    }

    @Test
    fun `common OCR glyph confusion is normalized and flagged`() {
        val r = result(
            block("1O3", 400f, 700f, 240f, 240f),
            block("68", 400f, 1000f, 210f, 240f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(103, c!!.systolic)
        assertTrue(ReadingFlag.AMBIGUOUS_DIGIT in c.flags)
    }

    @Test
    fun `seven segment source is exposed to review`() {
        val r = result(
            block("103", 400f, 700f, 240f, 240f),
            block("68", 400f, 1000f, 210f, 240f),
            block("69", 420f, 1320f, 200f, 200f)
        ).copy(source = OcrSource.SEVEN_SEGMENT)
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertTrue(ReadingFlag.FROM_FALLBACK in c!!.flags)
    }

    @Test
    fun `multiple digit groups on same row merge and are flagged`() {
        val r = result(
            block("10", 400f, 700f, 120f, 240f),
            block("3", 540f, 710f, 60f, 240f),
            block("68", 400f, 1000f, 210f, 240f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(103, c!!.systolic)
        assertTrue(ReadingFlag.AMBIGUOUS_DIGIT in c.flags)
    }

    @Test
    fun `single small digit near main rows does not affect result`() {
        val r = result(
            block("103", 400f, 700f, 240f, 240f),
            block("68", 400f, 1000f, 210f, 240f),
            block("2", 900f, 1050f, 40f, 40f)
        )
        val c = BpReadingParser.parse(r)
        assertNotNull(c)
        assertEquals(103, c!!.systolic)
        assertEquals(68, c.diastolic)
        assertNull(c.pulse)
    }
}
