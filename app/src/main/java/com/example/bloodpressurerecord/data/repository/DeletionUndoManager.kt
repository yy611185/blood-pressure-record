package com.example.bloodpressurerecord.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UndoableDeletion {
    val tokenId: Long

    data class Session(
        override val tokenId: Long,
        val snapshot: SessionRecord
    ) : UndoableDeletion

    data class Medication(
        override val tokenId: Long,
        val snapshot: DeletedMedicationSnapshot
    ) : UndoableDeletion
}

data class UndoPrompt(
    val tokenId: Long,
    val message: String,
    val pendingCount: Int
)

data class UndoFeedback(
    val message: String,
    val isError: Boolean
)

enum class UndoExecutionStatus {
    RESTORED,
    ALREADY_CONSUMED,
    EXPIRED_OR_INVALIDATED,
    FAILED
}

data class UndoExecutionResult(
    val status: UndoExecutionStatus,
    val message: String? = null
)

/**
 * 统一管理待撤销的血压记录与药品删除快照：
 * - 独立于单页 ViewModel 生命周期，离开详情页或提醒中心后仍可在有效期内撤销；
 * - 连续删除采用后进先出栈：新删除打断旧提示条时保留旧快照，撤销最新项后自动接续前一项；
 * - 提示条自然超时关闭（未被新删除接替）时整栈过期清理；
 * - 清空本地数据或导入冲突备份时立即使对应待撤销项失效并收起提示条；
 * - Mutex 保证重复点击撤销的幂等性。
 */
class DeletionUndoManager(
    private val restoreSession: suspend (SessionRecord) -> Result<Unit>,
    private val restoreMedication: suspend (DeletedMedicationSnapshot) -> Result<Unit>,
    private val onMedicationRestored: (suspend () -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val mutex = Mutex()
    private var nextTokenId = 1L
    private val entries = ArrayDeque<UndoableDeletion>()
    private val consumedTokens = LinkedHashSet<Long>()
    private val invalidatedTokens = LinkedHashSet<Long>()

    private val _currentPrompt = MutableStateFlow<UndoPrompt?>(null)
    val currentPrompt: StateFlow<UndoPrompt?> = _currentPrompt.asStateFlow()

    private val _feedback = MutableSharedFlow<UndoFeedback>(extraBufferCapacity = 8)
    val feedback: SharedFlow<UndoFeedback> = _feedback.asSharedFlow()

    suspend fun enqueueSession(snapshot: SessionRecord): Long = mutex.withLock {
        enqueueSessionLocked(snapshot)
    }

    suspend fun enqueueMedication(snapshot: DeletedMedicationSnapshot): Long = mutex.withLock {
        enqueueMedicationLocked(snapshot)
    }

    suspend fun deleteSession(
        delete: suspend () -> Result<SessionRecord>
    ): Result<SessionRecord> = mutex.withLock {
        // 成功提交和发布快照必须一起完成，即使发起的页面已经离开。
        withContext(NonCancellable) { delete().onSuccess { enqueueSessionLocked(it) } }
    }

    suspend fun deleteMedication(
        delete: suspend () -> Result<DeletedMedicationSnapshot>
    ): Result<DeletedMedicationSnapshot> = mutex.withLock {
        withContext(NonCancellable) { delete().onSuccess { enqueueMedicationLocked(it) } }
    }

    suspend fun clearData(clear: suspend (onDatabaseCommitted: () -> Unit) -> Result<ClearAllDataResult>):
        Result<ClearAllDataResult> = mutex.withLock {
        withContext(NonCancellable) { clear(::invalidateAllLocked) }
    }

    suspend fun importBackup(
        importedSessionIds: Set<String>,
        restoredMedications: Boolean,
        commit: suspend (onDatabaseCommitted: () -> Unit) -> String
    ): String = mutex.withLock {
        withContext(NonCancellable) {
            commit { invalidateOnBackupImportLocked(importedSessionIds, restoredMedications) }
        }
    }

    private fun enqueueSessionLocked(snapshot: SessionRecord): Long {
        val token = nextTokenId++
        if (entries.size >= MAX_STACK_SIZE) {
            entries.removeFirst()
        }
        entries.addLast(UndoableDeletion.Session(token, snapshot))
        publishPromptLocked()
        return token
    }

    private fun enqueueMedicationLocked(snapshot: DeletedMedicationSnapshot): Long {
        val token = nextTokenId++
        if (entries.size >= MAX_STACK_SIZE) {
            entries.removeFirst()
        }
        entries.addLast(UndoableDeletion.Medication(token, snapshot))
        publishPromptLocked()
        return token
    }

    /**
     * 当提示条未点“撤销”而结束展示时调用：
     * - 若当前栈顶仍是该 token，说明用户主动忽略或等待超时，清空全部待撤销快照；
     * - 若栈顶已变成更新的删除项，说明只是被新提示打断，保留栈内旧快照供逐条撤销。
     */
    suspend fun onPromptDismissed(tokenId: Long) = mutex.withLock {
        if (entries.lastOrNull()?.tokenId == tokenId) {
            entries.clear()
            publishPromptLocked()
        }
    }

    fun onPromptDismissedAsync(tokenId: Long) {
        scope.launch { onPromptDismissed(tokenId) }
    }

    /** 清空数据库时使全部待撤销记录与药品立即失效。 */
    suspend fun invalidateAll() = mutex.withLock {
        invalidateAllLocked()
    }

    private fun invalidateAllLocked() {
        entries.forEach { rememberToken(invalidatedTokens, it.tokenId) }
        entries.clear()
        publishPromptLocked()
    }

    /**
     * 备份导入后使可能冲突的旧撤销项失效：
     * - 导入了测量记录时，移除同 sessionId 的待撤销血压记录；
     * - 恢复了用药数据时，移除待撤销药品，避免旧快照插回覆盖或重复恢复。
     */
    suspend fun invalidateOnBackupImport(
        importedSessionIds: Set<String>,
        restoredMedications: Boolean
    ) = mutex.withLock {
        invalidateOnBackupImportLocked(importedSessionIds, restoredMedications)
    }

    private fun invalidateOnBackupImportLocked(
        importedSessionIds: Set<String>,
        restoredMedications: Boolean
    ) {
        if (importedSessionIds.isEmpty() && !restoredMedications) return
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val shouldRemove = when (item) {
                is UndoableDeletion.Session -> item.snapshot.id in importedSessionIds
                is UndoableDeletion.Medication -> restoredMedications
            }
            if (shouldRemove) {
                rememberToken(invalidatedTokens, item.tokenId)
                iterator.remove()
            }
        }
        publishPromptLocked()
    }

    fun undoAsync(tokenId: Long) {
        scope.launch { undo(tokenId) }
    }

    fun undoCurrentPromptAsync(tokenId: Long) {
        scope.launch { undo(tokenId, requireCurrentPrompt = true) }
    }

    suspend fun undo(tokenId: Long, requireCurrentPrompt: Boolean = false): UndoExecutionResult = mutex.withLock {
        if (requireCurrentPrompt && entries.lastOrNull()?.tokenId != tokenId &&
            entries.any { it.tokenId == tokenId }
        ) {
            return@withLock UndoExecutionResult(UndoExecutionStatus.EXPIRED_OR_INVALIDATED)
        }
        if (tokenId in consumedTokens) {
            return@withLock UndoExecutionResult(UndoExecutionStatus.ALREADY_CONSUMED)
        }
        val index = entries.indexOfFirst { it.tokenId == tokenId }
        if (index < 0) {
            val msg = if (tokenId in invalidatedTokens) {
                "数据已变更，原删除项无法再撤销。"
            } else {
                "撤销已过期。"
            }
            _feedback.tryEmit(UndoFeedback(msg, isError = true))
            return@withLock UndoExecutionResult(UndoExecutionStatus.EXPIRED_OR_INVALIDATED, msg)
        }

        val target = entries.removeAt(index)
        rememberToken(consumedTokens, tokenId)
        publishPromptLocked()

        var reminderFailed = false
        val result = withContext(NonCancellable) {
            when (target) {
                is UndoableDeletion.Session -> restoreSession(target.snapshot)
                is UndoableDeletion.Medication -> restoreMedication(target.snapshot).onSuccess {
                    runCatching { onMedicationRestored?.invoke() }.onFailure {
                        reminderFailed = true
                    }
                }
            }
        }

        result.fold(
            onSuccess = {
                val successMsg = when (target) {
                    is UndoableDeletion.Session -> "已恢复记录。"
                    is UndoableDeletion.Medication -> "已恢复「${target.snapshot.medication.name}」及历史打卡。"
                }
                val message = if (reminderFailed) "$successMsg 提醒或日历同步失败，请稍后重试。" else successMsg
                if (entries.isEmpty() || reminderFailed) {
                    _feedback.tryEmit(UndoFeedback(message, isError = reminderFailed))
                }
                UndoExecutionResult(UndoExecutionStatus.RESTORED, message)
            },
            onFailure = { error ->
                val failMsg = "恢复失败：${error.message ?: "请稍后重试"}"
                _feedback.tryEmit(UndoFeedback(failMsg, isError = true))
                UndoExecutionResult(UndoExecutionStatus.FAILED, failMsg)
            }
        )
    }

    private fun rememberToken(tokens: MutableSet<Long>, tokenId: Long) {
        if (tokens.size >= MAX_STACK_SIZE) tokens.remove(tokens.first())
        tokens += tokenId
    }

    private fun publishPromptLocked() {
        val top = entries.lastOrNull()
        _currentPrompt.value = top?.let { item ->
            val baseMessage = when (item) {
                is UndoableDeletion.Session -> "记录已删除"
                is UndoableDeletion.Medication -> "已删除「${item.snapshot.medication.name}」"
            }
            val suffix = if (entries.size > 1) "（待撤销 ${entries.size} 项）" else ""
            UndoPrompt(
                tokenId = item.tokenId,
                message = baseMessage + suffix,
                pendingCount = entries.size
            )
        }
    }

    private fun <T> ArrayDeque<T>.removeAt(index: Int): T {
        if (index == size - 1) return removeLast()
        if (index == 0) return removeFirst()
        val list = toMutableList()
        val removed = list.removeAt(index)
        clear()
        addAll(list)
        return removed
    }

    companion object {
        private const val MAX_STACK_SIZE = 20
    }
}
