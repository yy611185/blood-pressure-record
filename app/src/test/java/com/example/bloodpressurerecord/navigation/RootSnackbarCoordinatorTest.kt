package com.example.bloodpressurerecord.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.data.repository.UndoFeedback
import com.example.bloodpressurerecord.data.repository.UndoPrompt
import com.example.bloodpressurerecord.ui.common.DockCollapseThreshold
import com.example.bloodpressurerecord.ui.common.DockExpandThreshold
import com.example.bloodpressurerecord.ui.common.DockScrollState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootSnackbarCoordinatorTest {

    private class FakeSnackbarCall(
        val message: String,
        val actionLabel: String?,
        val duration: SnackbarDuration
    ) {
        val deferred = CompletableDeferred<SnackbarResult>()
        var wasCancelled = false
    }

    /** 模拟真实 [androidx.compose.material3.SnackbarHostState] 的单通道 Mutex 串行展示行为。 */
    private class FakePresenter : RootSnackbarPresenter {
        private val mutex = Mutex()
        val history = mutableListOf<FakeSnackbarCall>()
        var currentCall: FakeSnackbarCall? = null

        override suspend fun show(
            message: String,
            actionLabel: String?,
            duration: SnackbarDuration
        ): SnackbarResult = mutex.withLock {
            val call = FakeSnackbarCall(message, actionLabel, duration)
            history += call
            currentCall = call
            try {
                call.deferred.await()
            } catch (t: Throwable) {
                call.wasCancelled = true
                throw t
            } finally {
                if (currentCall === call) {
                    currentCall = null
                }
            }
        }
    }

    @Test
    fun undoPrompt_preemptsActiveNormalSnackbarImmediately() = runTest {
        val presenter = FakePresenter()
        val undoActions = mutableListOf<Long>()
        val naturalDismissals = mutableListOf<Long>()
        var normalFinished = false
        val coordinator = RootSnackbarCoordinator(
            scope = backgroundScope,
            presenter = presenter,
            onUndoAction = { undoActions += it },
            onUndoNaturalDismiss = { naturalDismissals += it }
        )

        coordinator.showNormal(
            NormalSnackbarRequest(
                message = "已记录早间服药",
                actionLabel = "撤销",
                onFinished = { normalFinished = true }
            )
        )
        runCurrent()
        val firstCall = presenter.currentCall
        assertNotNull(firstCall)
        assertEquals("已记录早间服药", firstCall?.message)

        // 高优先级删除撤销到达：立即取消正在展示的普通提示，切入删除撤销条
        coordinator.onUndoPromptChanged(UndoPrompt(tokenId = 10L, message = "记录已删除", pendingCount = 1))
        runCurrent()

        assertTrue("Active normal snackbar must be cancelled by P0 UndoPrompt", firstCall!!.wasCancelled)
        assertTrue("Cancelled normal snackbar must still invoke onFinished cleanup", normalFinished)
        assertEquals("记录已删除", presenter.currentCall?.message)
        assertEquals("撤销", presenter.currentCall?.actionLabel)
        assertEquals(SnackbarDuration.Long, presenter.currentCall?.duration)
        assertTrue(naturalDismissals.isEmpty())
    }

    @Test
    fun normalSnackbarDuringActiveUndo_neverDismissesUndo_andPlaysDeferredIfStillValid() = runTest {
        val presenter = FakePresenter()
        val undoActions = mutableListOf<Long>()
        val naturalDismissals = mutableListOf<Long>()
        var overriddenFinished = false
        var latestActionTriggered = false
        val coordinator = RootSnackbarCoordinator(
            scope = backgroundScope,
            presenter = presenter,
            onUndoAction = { undoActions += it },
            onUndoNaturalDismiss = { naturalDismissals += it }
        )

        coordinator.onUndoPromptChanged(UndoPrompt(tokenId = 1L, message = "记录已删除", pendingCount = 1))
        runCurrent()
        val undoCall = presenter.currentCall
        assertNotNull(undoCall)
        assertEquals("记录已删除", undoCall?.message)

        // 删除撤销展示期间到达两条普通提示：不得打断撤销条，不得触发 onUndoNaturalDismiss，且合并保留最新一条
        coordinator.showNormal(
            NormalSnackbarRequest(
                message = "第一条普通提示",
                onFinished = { overriddenFinished = true }
            )
        )
        coordinator.showNormal(
            NormalSnackbarRequest(
                message = "已保存 120/80 mmHg",
                actionLabel = "再改改",
                onAction = { latestActionTriggered = true }
            )
        )
        runCurrent()

        assertTrue(overriddenFinished)
        assertFalse("Undo snackbar must NEVER be cancelled by normal messages", undoCall!!.wasCancelled)
        assertEquals("记录已删除", presenter.currentCall?.message)
        assertTrue(naturalDismissals.isEmpty())
        assertEquals(1, presenter.history.size)

        // 用户点击删除撤销 -> 消费撤销回调后自动接续延后的最新普通提示
        undoCall.deferred.complete(SnackbarResult.ActionPerformed)
        runCurrent()
        assertEquals(listOf(1L), undoActions)
        assertTrue(naturalDismissals.isEmpty())

        val deferredCall = presenter.currentCall
        assertNotNull(deferredCall)
        assertEquals("已保存 120/80 mmHg", deferredCall?.message)
        deferredCall?.deferred?.complete(SnackbarResult.ActionPerformed)
        runCurrent()
        assertTrue(latestActionTriggered)
    }

    @Test
    fun consecutiveUndoTokens_cancelPreviousWithoutNaturalDismiss_andStackedUndoPreservesDeferredNormal() = runTest {
        val presenter = FakePresenter()
        val undoActions = mutableListOf<Long>()
        val naturalDismissals = mutableListOf<Long>()
        val coordinator = RootSnackbarCoordinator(
            scope = backgroundScope,
            presenter = presenter,
            onUndoAction = { undoActions += it },
            onUndoNaturalDismiss = { naturalDismissals += it }
        )

        coordinator.onUndoPromptChanged(UndoPrompt(tokenId = 101L, message = "记录已删除", pendingCount = 1))
        runCurrent()
        val token1Call = presenter.currentCall
        assertNotNull(token1Call)

        // 连续第二次删除：token 102 接管，token 101 被取消但绝不调用 onUndoNaturalDismiss(101L)
        coordinator.onUndoPromptChanged(
            UndoPrompt(tokenId = 102L, message = "记录已删除（待撤销 2 项）", pendingCount = 2)
        )
        runCurrent()
        assertTrue(token1Call!!.wasCancelled)
        assertTrue("Token takeover must not trigger natural dismiss", naturalDismissals.isEmpty())

        // 栈顶展示期间到达一条普通提示：应保持挂起，即使撤销栈顶后接续前序项也不得提前消耗该普通提示
        coordinator.showSimpleMessage("用户资料已保存")
        runCurrent()

        val token2Call = presenter.currentCall
        assertNotNull(token2Call)
        assertEquals("记录已删除（待撤销 2 项）", token2Call?.message)

        // 点击撤销栈顶 token 102（pendingCount=2）：等待下一帧推送 token 101，普通提示仍保留
        token2Call?.deferred?.complete(SnackbarResult.ActionPerformed)
        runCurrent()
        assertEquals(listOf(102L), undoActions)
        assertNull("Should wait for stacked token 101 before draining deferredNormal", presenter.currentCall)

        // 仓库推送前序待撤销项 token 101
        coordinator.onUndoPromptChanged(UndoPrompt(tokenId = 101L, message = "记录已删除", pendingCount = 1))
        runCurrent()
        assertEquals("记录已删除", presenter.currentCall?.message)

        // 当前栈顶自然超时关闭：准确触发 onUndoNaturalDismiss(101L)，随后自动播出此前挂起的普通提示
        presenter.currentCall!!.deferred.complete(SnackbarResult.Dismissed)
        runCurrent()
        assertEquals(listOf(101L), naturalDismissals)
        assertEquals("用户资料已保存", presenter.currentCall?.message)
    }

    @Test
    fun undoFeedbackOutranksDeferredNormal_andStaleDeferredNormalIsSkipped() = runTest {
        val presenter = FakePresenter()
        var staleStillValid = true
        var staleFinished = false
        var staleActionCalled = false
        val coordinator = RootSnackbarCoordinator(
            scope = backgroundScope,
            presenter = presenter,
            onUndoAction = {},
            onUndoNaturalDismiss = {}
        )

        coordinator.onUndoPromptChanged(UndoPrompt(tokenId = 7L, message = "记录已删除", pendingCount = 1))
        runCurrent()
        coordinator.showNormal(
            NormalSnackbarRequest(
                message = "已记录早间服药",
                actionLabel = "撤销",
                isStillValid = { staleStillValid },
                onAction = { staleActionCalled = true },
                onFinished = { staleFinished = true }
            )
        )
        runCurrent()

        // 撤销期间用户已在卡片上手动反转了该服药状态，普通提示已过期
        staleStillValid = false

        // 用户点击删除撤销：由于 deferredNormal 已失效，直接跳过并回调 onFinished，不上屏展示
        presenter.currentCall!!.deferred.complete(SnackbarResult.ActionPerformed)
        runCurrent()
        assertTrue(staleFinished)
        assertFalse(staleActionCalled)
        assertNull(presenter.currentCall)

        // 验证 P1 抢占正在展示的普通提示，并在结束后播放新延后的有效普通提示
        coordinator.showSimpleMessage("普通提示 A")
        runCurrent()
        assertEquals("普通提示 A", presenter.currentCall?.message)
        coordinator.enqueueUndoFeedback(UndoFeedback(message = "已恢复记录。", isError = false))
        runCurrent()
        assertEquals("已恢复记录。", presenter.currentCall?.message)
        assertEquals(SnackbarDuration.Short, presenter.currentCall?.duration)

        coordinator.showSimpleMessage("普通提示 B")
        runCurrent()
        // P1 展示期间同样不打断 P1
        assertEquals("已恢复记录。", presenter.currentCall?.message)
        presenter.currentCall!!.deferred.complete(SnackbarResult.Dismissed)
        runCurrent()
        assertEquals("普通提示 B", presenter.currentCall?.message)
    }

    @Test
    fun normalHandle_cancelsDeferredOrActiveNormalCleanly() = runTest {
        val presenter = FakePresenter()
        var finishedCount = 0
        val coordinator = RootSnackbarCoordinator(
            scope = backgroundScope,
            presenter = presenter,
            onUndoAction = {},
            onUndoNaturalDismiss = {}
        )

        val handle = coordinator.showNormal(
            NormalSnackbarRequest(
                message = "已保存 126/82 mmHg",
                actionLabel = "再改改",
                onFinished = { finishedCount += 1 }
            )
        )
        runCurrent()
        assertEquals("已保存 126/82 mmHg", presenter.currentCall?.message)

        // 模拟路由切换使 SaveEditActionTicket 失效并主动收起当前提示条
        handle.cancelIfActiveOrQueued()
        runCurrent()
        assertNull(presenter.currentCall)
        assertEquals(1, finishedCount)
    }

    @Test
    fun dockScrollState_usesSymmetric28dpThreshold_andAutoExpandsWhenReturningToTop() {
        assertEquals(28.dp, DockCollapseThreshold)
        assertEquals(28.dp, DockExpandThreshold)

        val dock = DockScrollState(collapseThresholdPx = 28f, expandThresholdPx = 28f)
        assertFalse(dock.isCompact)

        // 首帧/空页面 canScrollBackward=false 不产生伪触发
        dock.onScrollablePositionChanged(canScrollBackward = false)
        assertFalse(dock.isCompact)

        // 向下推内容（手指向下滚列表内容向上走，consumed.y < 0）：未满 28px 不收起，反向滑动清零累计距离
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, -20f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        assertFalse(dock.isCompact)
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, 6f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, -20f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        assertFalse("Direction reversal must reset accumulated collapse distance", dock.isCompact)

        // 连续达到 28px 收起阈值，页面处于已离开顶部状态
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, -10f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        dock.onScrollablePositionChanged(canScrollBackward = true)
        assertTrue(dock.isCompact)

        dock.onScrollablePositionChanged(null)
        dock.onScrollablePositionChanged(false)
        assertTrue("Empty content or its initial layout must not expand the dock", dock.isCompact)
        dock.onScrollablePositionChanged(true)

        // 微小回拉（20px < 28px）不误展开（消除 18dp 轻微抖动弹开问题）
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, 20f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        assertTrue(dock.isCompact)

        // 满 28px 对称展开阈值后展开
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, 9f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        assertFalse(dock.isCompact)

        // 再次收起后模拟惯性 fling（无 UserInput 累计满 28px）直接滑回顶部 canScrollBackward=false -> 自动展开
        dock.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, -30f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput
        )
        assertTrue(dock.isCompact)
        dock.onScrollablePositionChanged(canScrollBackward = false)
        assertFalse("Returning to top via fling or list shrink must auto-expand Dock", dock.isCompact)
    }
}
