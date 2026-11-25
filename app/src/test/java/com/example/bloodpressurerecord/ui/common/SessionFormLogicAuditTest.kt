package com.example.bloodpressurerecord.ui.common

import androidx.lifecycle.SavedStateHandle
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.CalendarSessionSummary
import com.example.bloodpressurerecord.data.repository.LatestSessionSummary
import com.example.bloodpressurerecord.data.repository.NoteSearchResult
import com.example.bloodpressurerecord.data.repository.PeriodStatistics
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.data.repository.SessionSummary
import com.example.bloodpressurerecord.ui.home.HomeViewModel
import com.example.bloodpressurerecord.ui.home.MainDispatcherRule
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionFormLogicAuditTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shouldAutoAdvanceField only advances on collapsed tail single digit append`() {
        val sysPrefix = SessionFormLogic.SYSTOLIC_AUTO_ADVANCE_PREFIX // 30

        // "12" 光标位于两数字中间插入 '2' 得到 "122"（光标位于下标 2）-> 绝不跳格
        assertFalse(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "12",
                previousSelectionStart = 1,
                previousSelectionEnd = 1,
                nextText = "122",
                nextSelectionStart = 2,
                nextSelectionEnd = 2,
                maxTwoDigitPrefix = sysPrefix
            )
        )

        // 选中替换 "12" -> "45" -> 不跳格
        assertFalse(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "12",
                previousSelectionStart = 0,
                previousSelectionEnd = 2,
                nextText = "45",
                nextSelectionStart = 2,
                nextSelectionEnd = 2,
                maxTwoDigitPrefix = sysPrefix
            )
        )

        // 多字符粘贴 "" -> "120" -> 不误跳
        assertFalse(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "",
                previousSelectionStart = 0,
                previousSelectionEnd = 0,
                nextText = "120",
                nextSelectionStart = 3,
                nextSelectionEnd = 3,
                maxTwoDigitPrefix = sysPrefix
            )
        )

        // 退格删除 "128" -> "12" -> 不跳格
        assertFalse(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "128",
                previousSelectionStart = 3,
                previousSelectionEnd = 3,
                nextText = "12",
                nextSelectionStart = 2,
                nextSelectionEnd = 2,
                maxTwoDigitPrefix = sysPrefix
            )
        )

        // 输入法组合态未落字 -> 不跳格
        assertFalse(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "12",
                previousSelectionStart = 2,
                previousSelectionEnd = 2,
                nextText = "128",
                nextSelectionStart = 3,
                nextSelectionEnd = 3,
                maxTwoDigitPrefix = sysPrefix,
                hasComposition = true
            )
        )

        // 正常尾部追加第 3 位数字 "12" -> "128" -> 跳格
        assertTrue(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "12",
                previousSelectionStart = 2,
                previousSelectionEnd = 2,
                nextText = "128",
                nextSelectionStart = 3,
                nextSelectionEnd = 3,
                maxTwoDigitPrefix = sysPrefix
            )
        )

        // 正常尾部追加超过两位数前缀上限 "8" -> "85"（低压上限 20）-> 跳格
        assertTrue(
            SessionFormLogic.shouldAutoAdvanceField(
                previousText = "8",
                previousSelectionStart = 1,
                previousSelectionEnd = 1,
                nextText = "85",
                nextSelectionStart = 2,
                nextSelectionEnd = 2,
                maxTwoDigitPrefix = SessionFormLogic.DIASTOLIC_AUTO_ADVANCE_PREFIX
            )
        )
    }

    @Test
    fun `toggleQuickNoteTag preserves user commas spaces and non-standalone substrings`() {
        val original = "昨晚失眠, 喝了咖啡"
        val withTag = SessionFormLogic.toggleQuickNoteTag(original, "没睡好")
        assertEquals("昨晚失眠, 喝了咖啡、没睡好", withTag)
        assertTrue(SessionFormLogic.containsQuickNoteTag(withTag, "没睡好"))

        // 移除标签后 100% 还原用户原有空格与英文逗号
        val restored = SessionFormLogic.toggleQuickNoteTag(withTag, "没睡好")
        assertEquals(original, restored)
        assertFalse(SessionFormLogic.containsQuickNoteTag(restored, "没睡好"))

        // 包含子串的自由文本不误判为独立标签
        val freeText = "早上起来有点头晕目眩, 先坐了五分钟"
        assertFalse(SessionFormLogic.containsQuickNoteTag(freeText, "头晕"))
        val appended = SessionFormLogic.toggleQuickNoteTag(freeText, "头晕")
        assertEquals("早上起来有点头晕目眩, 先坐了五分钟、头晕", appended)
        assertEquals(freeText, SessionFormLogic.toggleQuickNoteTag(appended, "头晕"))
    }

    @Test
    fun `HomeViewModel locks edits while saving and consumes exact submitted snapshot once`() = runTest {
        val saveGate = CompletableDeferred<Result<String>>()
        val fixedNow = DateTimeInputFormatter.parse("2026-10-12 09:00")!!
        val repo = object : BloodPressureRepository {
            override fun observeSession(sessionId: String): Flow<SessionRecord?> = flowOf(null)
            override fun observeLatestSessionSummary(): Flow<LatestSessionSummary?> = flowOf(null)
            override fun observeCalendarSessionSummaries(startInclusive: Long, endExclusive: Long): Flow<List<CalendarSessionSummary>> = flowOf(emptyList())
            override fun observeSessionSummariesInRange(startInclusive: Long, endExclusive: Long): Flow<List<SessionSummary>> = flowOf(emptyList())
            override fun observeNoteSearchResults(keyword: String): Flow<List<NoteSearchResult>> = flowOf(emptyList())
            override fun observePeriodStatistics(startInclusive: Long, endExclusive: Long): Flow<PeriodStatistics> = flowOf(PeriodStatistics())
            override suspend fun saveSession(input: SaveSessionInput): Result<String> = saveGate.await()
            override suspend fun updateSession(sessionId: String, input: SaveSessionInput): Result<Unit> = Result.success(Unit)
            override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)
            override suspend fun restoreSession(session: SessionRecord): Result<Unit> = Result.success(Unit)
        }
        val vm = HomeViewModel(
            repository = repo,
            savedStateHandle = SavedStateHandle(),
            nowMillis = { fixedNow }
        )
        vm.updateMeasuredAtText("2026-10-12 08:30")
        vm.updateReading1Systolic("120")
        vm.updateReading1Diastolic("80")
        vm.updateReading2Systolic("122")
        vm.updateReading2Diastolic("80")

        vm.onSaveClicked()
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)

        // 保存期间尝试改写读数被锁定忽略
        vm.updateReading1Systolic("170")
        vm.updateReading2Systolic("170")
        assertEquals("120", vm.uiState.value.reading1.systolic)

        saveGate.complete(Result.success("saved-id-1"))
        advanceUntilIdle()

        val completed = checkNotNull(vm.consumeSavedSession())
        assertEquals("saved-id-1", completed.sessionId)
        assertEquals(121, completed.avgSystolic)
        assertEquals(80, completed.avgDiastolic)
        // 二次消费为空，避免重复提示或无效路由
        assertNull(vm.consumeSavedSession())
    }
}
