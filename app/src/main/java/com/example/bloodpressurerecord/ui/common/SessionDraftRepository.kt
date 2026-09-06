package com.example.bloodpressurerecord.ui.common

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface SessionDraftRepository {
    fun load(key: String): Result<SessionFormDraft?>
    fun save(key: String, draft: SessionFormDraft): Result<Unit>
    fun delete(key: String): Result<Unit>
    fun clearAll(): Result<Unit>
}

/** 应用私有目录中的版本化草稿；正式记录仍只写入 Room。 */
class FileSessionDraftRepository(context: Context) : SessionDraftRepository {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    override fun load(key: String): Result<SessionFormDraft?> = runCatching {
        val file = draftFile(key)
        if (!file.exists()) return@runCatching null
        val root = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
        require(root.optInt("version") == FORMAT_VERSION) { "不支持的草稿版本" }
        val readingsJson = root.getJSONArray("readings")
        val readings = List(readingsJson.length()) { index ->
            val reading = readingsJson.getJSONObject(index)
            SessionReadingInputUi(
                systolic = reading.optString("systolic"),
                diastolic = reading.optString("diastolic"),
                pulse = reading.optString("pulse")
            )
        }
        val symptomsJson = root.optJSONArray("symptoms") ?: JSONArray()
        SessionFormDraft(
            measuredAtText = root.optString("measuredAtText"),
            scene = root.optString("scene", "晨起"),
            readings = readings.ifEmpty { List(2) { SessionReadingInputUi() } },
            note = root.optString("note"),
            symptoms = buildSet {
                repeat(symptomsJson.length()) { add(symptomsJson.getString(it)) }
            }
        )
    }

    override fun save(key: String, draft: SessionFormDraft): Result<Unit> = runCatching {
        check(directory.exists() || directory.mkdirs()) { "无法创建草稿目录" }
        val readings = JSONArray().apply {
            draft.readings.forEach { reading ->
                put(JSONObject().apply {
                    put("systolic", reading.systolic)
                    put("diastolic", reading.diastolic)
                    put("pulse", reading.pulse)
                })
            }
        }
        val root = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("measuredAtText", draft.measuredAtText)
            put("scene", draft.scene)
            put("readings", readings)
            put("note", draft.note)
            put("symptoms", JSONArray(draft.symptoms.sorted()))
        }
        val atomicFile = AtomicFile(draftFile(key))
        val output = atomicFile.startWrite()
        try {
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(output)
            throw throwable
        }
    }

    override fun delete(key: String): Result<Unit> = runCatching {
        val file = draftFile(key)
        if (file.exists() && !file.delete()) error("无法删除草稿")
    }

    override fun clearAll(): Result<Unit> = runCatching {
        if (!directory.exists()) return@runCatching
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && !file.delete()) error("无法清除草稿")
        }
        if (!directory.delete() && directory.exists()) error("无法清除草稿目录")
    }

    private fun draftFile(key: String): File {
        val safeKey = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(safeKey.isNotBlank()) { "草稿标识不能为空" }
        return File(directory, "$safeKey.json")
    }

    private companion object {
        const val DIRECTORY_NAME = "session_drafts"
        const val FORMAT_VERSION = 1
    }
}
