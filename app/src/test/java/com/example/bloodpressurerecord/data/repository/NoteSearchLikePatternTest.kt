package com.example.bloodpressurerecord.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteSearchLikePatternTest {
    @Test
    fun blankInputHasNoPattern() {
        assertNull(noteSearchLikePattern(""))
        assertNull(noteSearchLikePattern(" \t\n　"))
    }

    @Test
    fun trimsInputAndKeepsSingleCharacterAndPhrases() {
        assertEquals("%痛%", noteSearchLikePattern("  痛　"))
        assertEquals("%头痛%", noteSearchLikePattern("头痛"))
        assertEquals("%Head Ache%", noteSearchLikePattern(" Head Ache "))
    }

    @Test
    fun escapesWildcardsAndTheEscapeCharacter() {
        assertEquals("""%\%%""", noteSearchLikePattern("%"))
        assertEquals("""%\_%""", noteSearchLikePattern("_"))
        assertEquals("""%\\%""", noteSearchLikePattern("\\"))
        assertEquals("""%血压\%\_\\%""", noteSearchLikePattern("血压%_\\"))
    }
}
