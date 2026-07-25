package com.example.bloodpressurerecord.ui.common

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionDraftStoreTest {
    @Test
    fun `草稿保存恢复并可明确清除`() {
        val handle = SavedStateHandle()
        val store = SessionDraftStore(handle, "test")
        val draft = SessionFormDraft(
            measuredAtText = "2026-07-25 09:30",
            scene = "晨起",
            readings = listOf(
                SessionReadingInputUi("120", "80", "70"),
                SessionReadingInputUi("122", "82", "72"),
                SessionReadingInputUi("124", "84", "74")
            ),
            note = "未保存备注",
            symptoms = setOf("头晕")
        )

        store.save(draft)
        assertEquals(draft, store.restore())

        store.clear()
        assertNull(store.restore())
    }
}
