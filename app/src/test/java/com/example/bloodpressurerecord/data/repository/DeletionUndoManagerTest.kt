package com.example.bloodpressurerecord.data.repository

import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationTimeEntity
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeletionUndoManagerTest {

    @Test
    fun `undo cannot restore a row while clear owns the database operation`() = runTest {
        val manager = DeletionUndoManager(
            restoreSession = { error("A cleared snapshot must not reach the repository") },
            restoreMedication = { Result.success(Unit) },
            scope = backgroundScope
        )
        val token = manager.enqueueSession(sampleSession("before-clear"))
        val clearEntered = CompletableDeferred<Unit>()
        val commitClear = CompletableDeferred<Unit>()
        val clear = async {
            manager.clearData { onDatabaseCommitted ->
                clearEntered.complete(Unit)
                commitClear.await()
                onDatabaseCommitted()
                Result.success(ClearAllDataResult(true, true, true, true))
            }
        }
        clearEntered.await()
        val undo = async(start = CoroutineStart.UNDISPATCHED) { manager.undo(token) }
        assertFalse(undo.isCompleted)
        commitClear.complete(Unit)
        assertTrue(clear.await().getOrThrow().databaseCleared)
        assertEquals(UndoExecutionStatus.EXPIRED_OR_INVALIDATED, undo.await().status)
    }

    @Test
    fun `consecutive deletions form LIFO stack and interrupted prompt keeps earlier item`() = runTest {
        val restoredSessions = mutableListOf<String>()
        val restoredMeds = mutableListOf<Long>()
        val manager = DeletionUndoManager(
            restoreSession = { record ->
                restoredSessions += record.id
                Result.success(Unit)
            },
            restoreMedication = { snapshot ->
                restoredMeds += snapshot.medication.id
                Result.success(Unit)
            },
            scope = backgroundScope
        )

        val token1 = manager.enqueueSession(sampleSession("s-1"))
        assertEquals(token1, manager.currentPrompt.value?.tokenId)
        assertEquals(1, manager.currentPrompt.value?.pendingCount)

        val token2 = manager.enqueueMedication(sampleMedication(42L, "氨氯地平"))
        assertEquals(token2, manager.currentPrompt.value?.tokenId)
        assertEquals(2, manager.currentPrompt.value?.pendingCount)

        // 旧提示条因被新提示条打断而收到 Dismissed 时，不得清空栈内 token1
        manager.onPromptDismissed(token1)
        assertEquals(2, manager.currentPrompt.value?.pendingCount)

        // 撤销栈顶药品后自动接续展示前一条血压记录
        val res2 = manager.undo(token2)
        assertEquals(UndoExecutionStatus.RESTORED, res2.status)
        assertEquals(listOf(42L), restoredMeds)
        assertEquals(token1, manager.currentPrompt.value?.tokenId)

        // 重复点击同一 token 幂等忽略
        val duplicateRes2 = manager.undo(token2)
        assertEquals(UndoExecutionStatus.ALREADY_CONSUMED, duplicateRes2.status)
        assertEquals(listOf(42L), restoredMeds)

        // 继续撤销前一条血压记录
        val res1 = manager.undo(token1)
        assertEquals(UndoExecutionStatus.RESTORED, res1.status)
        assertEquals(listOf("s-1"), restoredSessions)
        assertNull(manager.currentPrompt.value)
    }

    @Test
    fun `clearAll and conflicting backup import invalidate matching pending undos`() = runTest {
        val restoredSessions = mutableListOf<String>()
        val manager = DeletionUndoManager(
            restoreSession = { record ->
                restoredSessions += record.id
                Result.success(Unit)
            },
            restoreMedication = { Result.success(Unit) },
            scope = backgroundScope
        )

        val keepToken = manager.enqueueSession(sampleSession("keep-me"))
        val conflictToken = manager.enqueueSession(sampleSession("imported-id"))
        val medToken = manager.enqueueMedication(sampleMedication(7L, "缬沙坦"))

        // 导入了同 ID 记录和用药数据：conflictToken 与 medToken 失效，keepToken 保留
        manager.invalidateOnBackupImport(
            importedSessionIds = setOf("imported-id"),
            restoredMedications = true
        )
        assertEquals(keepToken, manager.currentPrompt.value?.tokenId)
        assertEquals(
            UndoExecutionStatus.EXPIRED_OR_INVALIDATED,
            manager.undo(conflictToken).status
        )
        assertEquals(
            UndoExecutionStatus.EXPIRED_OR_INVALIDATED,
            manager.undo(medToken).status
        )
        assertTrue(restoredSessions.isEmpty())

        // 清空全部数据后，剩余 keepToken 也立即失效
        manager.invalidateAll()
        assertNull(manager.currentPrompt.value)
        assertEquals(
            UndoExecutionStatus.EXPIRED_OR_INVALIDATED,
            manager.undo(keepToken).status
        )
        assertTrue(restoredSessions.isEmpty())
    }

    @Test
    fun `restore failure emits explicit error feedback`() = runTest {
        val manager = DeletionUndoManager(
            restoreSession = { Result.failure(IllegalStateException("同 ID 记录已存在")) },
            restoreMedication = { Result.success(Unit) },
            scope = backgroundScope
        )
        val feedbackDeferred = async(start = CoroutineStart.UNDISPATCHED) { manager.feedback.first() }
        val token = manager.enqueueSession(sampleSession("dup-id"))

        val result = manager.undo(token)
        assertEquals(UndoExecutionStatus.FAILED, result.status)
        val feedback = feedbackDeferred.await()
        assertTrue(feedback.isError)
        assertTrue(feedback.message.contains("恢复失败"))
    }

    private fun sampleSession(id: String) = SessionRecord(
        id = id,
        measuredAt = 1_760_000_000_000L,
        scene = "晨间",
        note = null,
        symptoms = emptyList(),
        avgSystolic = 128,
        avgDiastolic = 82,
        avgPulse = 70,
        category = "HIGH_NORMAL",
        containsHighRiskReading = false,
        readings = listOf(SessionReading("r1", 0, 128, 82, 70)),
        averageStrategy = AverageStrategy.DISCARD_FIRST
    )

    private fun sampleMedication(id: Long, name: String) = DeletedMedicationSnapshot(
        medication = MedicationEntity(id = id, name = name, dosage = "5mg", enabled = false, createdAt = 1000L),
        times = listOf(
            MedicationTimeEntity(id = 10L, medicationId = id, timeText = "08:00", active = true),
            MedicationTimeEntity(id = 11L, medicationId = id, timeText = "20:00", active = false)
        ),
        logs = listOf(
            MedicationIntakeLogEntity(id = 100L, medicationId = id, timeId = 10L, epochDay = 20_000L, takenAt = 2000L)
        )
    )
}
