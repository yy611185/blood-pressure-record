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

    @Test
    fun `持久草稿可跨新的 SavedStateHandle 恢复`() {
        val repository = InMemoryDraftRepository()
        val draft = SessionFormDraft(
            measuredAtText = "2026-09-06 10:00",
            scene = "晨起",
            readings = listOf(SessionReadingInputUi("123", "81", "68")),
            note = "跨页面恢复",
            symptoms = setOf("头晕", "咖啡")
        )

        SessionDraftStore(SavedStateHandle(), "add", repository).persist(draft).getOrThrow()
        val restored = SessionDraftStore(SavedStateHandle(), "add", repository).restore()

        assertEquals(draft, restored)
    }

    private class InMemoryDraftRepository : SessionDraftRepository {
        private val values = mutableMapOf<String, SessionFormDraft>()
        override fun load(key: String) = Result.success(values[key])
        override fun save(key: String, draft: SessionFormDraft) = Result.success(Unit).also {
            values[key] = draft
        }
        override fun delete(key: String) = Result.success(Unit).also { values.remove(key) }
        override fun clearAll() = Result.success(Unit).also { values.clear() }
    }
}
