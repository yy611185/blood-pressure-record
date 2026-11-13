package com.example.bloodpressurerecord.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchSnippetTest {
    @Test
    fun `finds a single Chinese character in each full note field`() {
        val text = "有点头痛"
        val expected = NoteSearchSnippet(text, listOf(2..2))
        assertEquals(expected, buildNoteSearchSnippet("  头  ", text, null, null))
        assertEquals(expected, buildNoteSearchSnippet("头", null, text, null))
        assertEquals(expected, buildNoteSearchSnippet("头", null, null, text))
    }

    @Test
    fun `includes the end of a long note and highlights the match`() {
        val note = "今天测量正常。".repeat(45) + "晚餐后有点头痛"
        val snippet = requireNotNull(buildNoteSearchSnippet("头痛", note, null, null))

        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("晚餐后有点头痛"))
        assertTrue(snippet.text.length < note.length)
        assertEquals(listOf("头痛"), snippet.matches.map { snippet.text.substring(it) })
    }

    @Test
    fun `centers a mid-note match and preserves surrounding context`() {
        val note = "记录".repeat(60) + "午饭后出现头痛，随后缓解" + "记录".repeat(60)
        val snippet = requireNotNull(buildNoteSearchSnippet("头痛", note, null, null))

        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("…"))
        assertTrue(snippet.text.contains("午饭后出现头痛，随后缓解"))
        assertEquals(listOf("头痛"), snippet.matches.map { snippet.text.substring(it) })
    }

    @Test
    fun `uses the field with more visible matches and matches ASCII case insensitively`() {
        val snippet = requireNotNull(buildNoteSearchSnippet("ache", "Headache", "ACHE / HeadAche", null))

        assertEquals("ACHE / HeadAche", snippet.text)
        assertEquals(listOf("ACHE", "Ache"), snippet.matches.map { snippet.text.substring(it) })
        assertNull(buildNoteSearchSnippet("é", "É", null, null))
    }

    @Test
    fun `highlights literal punctuation and overlapping matches`() {
        val keyword = "%_\\"
        val text = "备忘 $keyword / $keyword"
        val snippet = requireNotNull(buildNoteSearchSnippet(keyword, text, null, null))

        assertEquals(listOf(keyword, keyword), snippet.matches.map { snippet.text.substring(it) })
        assertEquals(listOf(0..1, 1..2), buildNoteSearchSnippet("aa", "AAA", null, null)?.matches)
    }

    @Test
    fun `keeps an entire long keyword and ignores blank or unmatched notes`() {
        val keyword = "搜索".repeat(80)
        val snippet = requireNotNull(buildNoteSearchSnippet(keyword, "前文$keyword 后文", null, null))

        assertFalse(snippet.text.contains("前文"))
        assertEquals(keyword, snippet.text.substring(snippet.matches.single()))
        assertNull(buildNoteSearchSnippet(" ", "有点头痛", null, null))
        assertNull(buildNoteSearchSnippet("运动", "有点头痛", null, null))
        assertNotNull(buildNoteSearchSnippet("头痛", null, "有点头痛", null))
    }
}
