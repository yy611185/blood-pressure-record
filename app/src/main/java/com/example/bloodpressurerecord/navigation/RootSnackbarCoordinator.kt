package com.example.bloodpressurerecord.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.example.bloodpressurerecord.data.repository.UndoFeedback
import com.example.bloodpressurerecord.data.repository.UndoPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 抽象单通道 Snackbar 展示器：生产环境委托给根 Scaffold 唯一的 [SnackbarHostState]，
 * 单元测试可注入受控假实现验证高优抢占、普通消息延后与 token 生命周期。
 */
internal fun interface RootSnackbarPresenter {
    suspend fun show(
        message: String,
        actionLabel: String?,
        duration: SnackbarDuration
    ): SnackbarResult
}

internal data class NormalSnackbarRequest(
    val message: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val isStillValid: () -> Boolean = { true },
    val onAction: (() -> Unit)? = null,
    val onFinished: (() -> Unit)? = null
)

internal class NormalSnackbarHandle internal constructor(
    private val cancelCallback: () -> Unit
) {
    fun cancelIfActiveOrQueued() {
        cancelCallback()
    }
}

/**
 * B3 根级单通道 Snackbar 优先级调度器：
 * - P0（删除撤销 [UndoPrompt]）严格最高优先：到达时立即取消低优先级协程切入展示；
 * - P0 展示期间到达的 P2 普通提示绝不调用 dismiss() 打断撤销条，避免触发 Dismissed 清空撤销栈；
 * - P1（撤销恢复反馈 [UndoFeedback]）在无活跃 P0 时优先于普通提示展示；
 * - P2（普通提示）合并保留最新 1 条待播请求，轮到展示及触发动作前执行 [NormalSnackbarRequest.isStillValid] 校验。
 */
internal class RootSnackbarCoordinator(
    private val scope: CoroutineScope,
    private val presenter: RootSnackbarPresenter,
    private val onUndoAction: (Long) -> Unit,
    private val onUndoNaturalDismiss: (Long) -> Unit
) {
    constructor(
        scope: CoroutineScope,
        hostState: SnackbarHostState,
        onUndoAction: (Long) -> Unit,
        onUndoNaturalDismiss: (Long) -> Unit
    ) : this(
        scope = scope,
        presenter = RootSnackbarPresenter { message, actionLabel, duration ->
            hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration
            )
        },
        onUndoAction = onUndoAction,
        onUndoNaturalDismiss = onUndoNaturalDismiss
    )

    private var activeUndoPrompt: UndoPrompt? = null
    private val pendingUndoFeedback = ArrayDeque<UndoFeedback>()
    private var deferredNormal: NormalSnackbarRequest? = null

    private var activeJob: Job? = null
    private var activeUndoTokenId: Long? = null
    private var activeUndoMessage: String? = null
    private var activeNormalRequest: NormalSnackbarRequest? = null
    private var activeIsUndoFeedback = false

    fun onUndoPromptChanged(prompt: UndoPrompt?) {
        if (prompt?.tokenId == activeUndoPrompt?.tokenId &&
            prompt?.message == activeUndoPrompt?.message
        ) {
            return
        }
        activeUndoPrompt = prompt
        if (prompt != null) {
            if (activeUndoTokenId != prompt.tokenId || activeUndoMessage != prompt.message) {
                cancelActiveJob()
            }
            pump()
        } else {
            if (activeUndoTokenId != null) {
                cancelActiveJob()
            }
            pump()
        }
    }

    fun enqueueUndoFeedback(feedback: UndoFeedback) {
        if (pendingUndoFeedback.size >= MAX_FEEDBACK_QUEUE) {
            pendingUndoFeedback.removeFirst()
        }
        pendingUndoFeedback.addLast(feedback)
        if (activeUndoPrompt == null && activeNormalRequest != null) {
            cancelActiveJob()
        }
        pump()
    }

    fun showNormal(request: NormalSnackbarRequest): NormalSnackbarHandle {
        deferredNormal?.onFinished?.invoke()
        deferredNormal = request
        // 严禁在存在删除撤销（P0）或恢复反馈（P1）展示期间打断当前提示条
        if (activeUndoPrompt == null && activeUndoTokenId == null && !activeIsUndoFeedback) {
            if (activeNormalRequest != null) {
                cancelActiveJob()
            }
            pump()
        }
        return NormalSnackbarHandle {
            if (deferredNormal === request) {
                deferredNormal = null
                request.onFinished?.invoke()
            } else if (activeNormalRequest === request) {
                cancelActiveJob()
                pump()
            }
        }
    }

    fun showSimpleMessage(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short
    ): NormalSnackbarHandle = showNormal(
        NormalSnackbarRequest(message = message, duration = duration)
    )

    private fun cancelActiveJob() {
        val job = activeJob
        activeJob = null
        activeUndoTokenId = null
        activeUndoMessage = null
        activeNormalRequest = null
        activeIsUndoFeedback = false
        job?.cancel()
    }

    private fun pump() {
        if (activeJob?.isActive == true) return

        val undoPrompt = activeUndoPrompt
        if (undoPrompt != null) {
            val tokenId = undoPrompt.tokenId
            val message = undoPrompt.message
            activeUndoTokenId = tokenId
            activeUndoMessage = message
            activeNormalRequest = null
            activeIsUndoFeedback = false
            lateinit var launchedJob: Job
            launchedJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val result = presenter.show(
                        message = message,
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Long
                    )
                    if (activeJob === launchedJob) {
                        activeJob = null
                        activeUndoTokenId = null
                        activeUndoMessage = null
                    }
                    when (result) {
                        SnackbarResult.ActionPerformed -> {
                            if (activeUndoPrompt?.tokenId == tokenId) {
                                activeUndoPrompt = null
                            }
                            onUndoAction(tokenId)
                            if (undoPrompt.pendingCount > 1 && activeUndoPrompt == null) {
                                // 栈内仍有待接续的前序删除撤销项，等待 currentPrompt 推送新栈顶后再泵送，防止提前消耗 deferredNormal
                                return@launch
                            }
                        }
                        SnackbarResult.Dismissed -> {
                            // 仅当未被新 token 接管且未外部失效时，才通知仓库按自然超时/主动关闭清栈
                            if (activeUndoPrompt?.tokenId == tokenId) {
                                activeUndoPrompt = null
                                onUndoNaturalDismiss(tokenId)
                            }
                        }
                    }
                    pump()
                } catch (cancelled: CancellationException) {
                    if (activeJob === launchedJob) {
                        activeJob = null
                        activeUndoTokenId = null
                        activeUndoMessage = null
                    }
                    throw cancelled
                }
            }
            if (launchedJob.isActive) {
                activeJob = launchedJob
            }
            return
        }

        val feedback = pendingUndoFeedback.removeFirstOrNull()
        if (feedback != null) {
            activeIsUndoFeedback = true
            activeNormalRequest = null
            lateinit var launchedJob: Job
            launchedJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    presenter.show(
                        message = feedback.message,
                        actionLabel = null,
                        duration = if (feedback.isError) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (activeJob === launchedJob) {
                        activeJob = null
                        activeIsUndoFeedback = false
                    }
                    pump()
                } catch (cancelled: CancellationException) {
                    if (activeJob === launchedJob) {
                        activeJob = null
                        activeIsUndoFeedback = false
                    }
                    throw cancelled
                }
            }
            if (launchedJob.isActive) {
                activeJob = launchedJob
            }
            return
        }

        while (true) {
            val normal = deferredNormal ?: return
            deferredNormal = null
            if (!normal.isStillValid()) {
                normal.onFinished?.invoke()
                continue
            }
            activeNormalRequest = normal
            activeIsUndoFeedback = false
            lateinit var launchedJob: Job
            launchedJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val result = presenter.show(
                        message = normal.message,
                        actionLabel = normal.actionLabel,
                        duration = normal.duration
                    )
                    if (activeJob === launchedJob) {
                        activeJob = null
                        activeNormalRequest = null
                    }
                    if (result == SnackbarResult.ActionPerformed && normal.isStillValid()) {
                        normal.onAction?.invoke()
                    }
                    normal.onFinished?.invoke()
                    pump()
                } catch (cancelled: CancellationException) {
                    if (activeJob === launchedJob) {
                        activeJob = null
                        activeNormalRequest = null
                    }
                    normal.onFinished?.invoke()
                    throw cancelled
                }
            }
            if (launchedJob.isActive) {
                activeJob = launchedJob
            }
            return
        }
    }

    companion object {
        private const val MAX_FEEDBACK_QUEUE = 4
    }
}
