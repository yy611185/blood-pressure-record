package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryDetailUiState(
    val session: SessionRecord? = null,
    val measuredAtText: String = "",
    val symptomsText: String = "无",
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false,
    val deletedSnapshot: SessionRecord? = null,
    val message: String = ""
)

class HistoryDetailViewModel(
    private val sessionId: String,
    private val repository: BloodPressureRepository
) : ViewModel() {
    private val localState = MutableStateFlow(HistoryDetailUiState())

    val uiState: StateFlow<HistoryDetailUiState> = combine(
        repository.observeSession(sessionId),
        localState
    ) { session, local ->
        if (session == null) {
            val snapshot = local.deletedSnapshot
            local.copy(
                session = snapshot,
                measuredAtText = snapshot?.let(::formatMeasuredAt).orEmpty(),
                symptomsText = snapshot?.symptoms?.takeIf { it.isNotEmpty() }
                    ?.joinToString("、") ?: "无"
            )
        } else {
            local.copy(
                session = session,
                measuredAtText = formatMeasuredAt(session),
                symptomsText = if (session.symptoms.isEmpty()) "无" else session.symptoms.joinToString("、")
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailUiState())

    fun requestDelete() {
        localState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDelete() {
        localState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        val snapshot = uiState.value.session ?: return
        viewModelScope.launch {
            repository.deleteSession(sessionId)
                .onSuccess {
                    localState.update {
                        it.copy(
                            showDeleteConfirm = false,
                            deleted = true,
                            deletedSnapshot = snapshot,
                            message = "记录已删除。"
                        )
                    }
                }
                .onFailure { throwable ->
                    localState.update {
                        it.copy(
                            showDeleteConfirm = false,
                            message = "删除失败：${throwable.message ?: "请稍后重试"}"
                        )
                    }
                }
        }
    }

    fun undoDelete() {
        val snapshot = localState.value.deletedSnapshot ?: return
        viewModelScope.launch {
            repository.restoreSession(snapshot)
                .onSuccess {
                    localState.update {
                        it.copy(
                            deleted = false,
                            deletedSnapshot = null,
                            message = "已恢复完整记录。"
                        )
                    }
                }
                .onFailure {
                    localState.update {
                        it.copy(message = "恢复失败，请返回历史页确认记录状态。")
                    }
                }
        }
    }

    private fun formatMeasuredAt(session: SessionRecord): String {
        return Instant.ofEpochMilli(session.measuredAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
    }

    companion object {
        fun provideFactory(
            sessionId: String,
            repository: BloodPressureRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryDetailViewModel(sessionId, repository) as T
            }
        }
    }
}
