package com.example.bloodpressurerecord.ui.common

import androidx.lifecycle.SavedStateHandle

data class SessionFormDraft(
    val measuredAtText: String,
    val scene: String,
    val readings: List<SessionReadingInputUi>,
    val note: String,
    val symptoms: Set<String>
)

/** 页面内状态写入 SavedStateHandle；用户明确保存时再提交到独立草稿仓库。 */
class SessionDraftStore(
    private val savedStateHandle: SavedStateHandle,
    private val keyPrefix: String,
    private val repository: SessionDraftRepository? = null
) {
    fun save(draft: SessionFormDraft) {
        savedStateHandle[key("present")] = true
        savedStateHandle[key("measured_at")] = draft.measuredAtText
        savedStateHandle[key("scene")] = draft.scene
        savedStateHandle[key("systolic")] = ArrayList(draft.readings.map { it.systolic })
        savedStateHandle[key("diastolic")] = ArrayList(draft.readings.map { it.diastolic })
        savedStateHandle[key("pulse")] = ArrayList(draft.readings.map { it.pulse })
        savedStateHandle[key("note")] = draft.note
        savedStateHandle[key("symptoms")] = ArrayList(draft.symptoms.sorted())
    }

    fun restore(): SessionFormDraft? {
        if (savedStateHandle.get<Boolean>(key("present")) != true) {
            return repository?.load(keyPrefix)?.getOrNull()?.also(::save)
        }
        val systolic = savedStateHandle.get<ArrayList<String>>(key("systolic")).orEmpty()
        val diastolic = savedStateHandle.get<ArrayList<String>>(key("diastolic")).orEmpty()
        val pulse = savedStateHandle.get<ArrayList<String>>(key("pulse")).orEmpty()
        val count = maxOf(systolic.size, diastolic.size, pulse.size, 2)
        return SessionFormDraft(
            measuredAtText = savedStateHandle[key("measured_at")] ?: "",
            scene = savedStateHandle[key("scene")] ?: "晨起",
            readings = List(count) { index ->
                SessionReadingInputUi(
                    systolic = systolic.getOrNull(index).orEmpty(),
                    diastolic = diastolic.getOrNull(index).orEmpty(),
                    pulse = pulse.getOrNull(index).orEmpty()
                )
            },
            note = savedStateHandle[key("note")] ?: "",
            symptoms = savedStateHandle.get<ArrayList<String>>(key("symptoms")).orEmpty().toSet()
        )
    }

    fun clear() {
        listOf(
            "present",
            "measured_at",
            "scene",
            "systolic",
            "diastolic",
            "pulse",
            "note",
            "symptoms"
        ).forEach { savedStateHandle.remove<Any>(key(it)) }
        repository?.delete(keyPrefix)
    }

    fun persist(draft: SessionFormDraft): Result<Unit> {
        save(draft)
        return repository?.save(keyPrefix, draft) ?: Result.success(Unit)
    }

    private fun key(suffix: String): String = "$keyPrefix.$suffix"
}
