package com.example.bloodpressurerecord.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloodpressurerecord.data.repository.BloodPressureRepository
import com.example.bloodpressurerecord.data.repository.SessionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HistoryDetailUiState(
    val session: SessionRecord? = null,
    val measuredAtText: String = "",
    val symptomsText: String = "无",
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false,
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
            local.copy(session = null, measuredAtText = "", symptomsText = "无")
        } else {
            local.copy(
                session = session,
                measuredAtText = Instant.ofEpochMilli(session.measuredAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
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
        viewModelScope.launch {
            repository.deleteSession(sessionId)
                .onSuccess {
                    localState.update { it.copy(showDeleteConfirm = false, deleted = true, message = "删除成功。") }
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
