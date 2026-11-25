package com.example.bloodpressurerecord.ui.history

import androidx.compose.runtime.MonotonicFrameClock
import com.example.bloodpressurerecord.domain.model.TrendAggregation
import com.example.bloodpressurerecord.domain.model.TrendPoint
import com.example.bloodpressurerecord.domain.model.TrendRange
import com.example.bloodpressurerecord.domain.model.TrendSeries
import com.example.bloodpressurerecord.domain.model.TrendYAxis
import com.example.bloodpressurerecord.domain.time.dayTicks
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrendChartAuditTest {

    private fun testFrameClock(): MonotonicFrameClock = object : MonotonicFrameClock {
        private var frameTimeNanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16L)
            frameTimeNanos += 16_000_000L
            return onFrame(frameTimeNanos)
        }
    }

    @Test
    fun `reset animates back to default viewport without self cancellation and keeps parent scope when panel detaches`() = runTest {
        val uiScope = CoroutineScope(coroutineContext + testFrameClock())
        val controller = TrendChartController()
        val rootOwner = Any()
        val pulsePanelOwner = Any()
        controller.attachUiScope(rootOwner, uiScope, reduced = false)
        controller.attachUiScope(pulsePanelOwner, uiScope, reduced = false)

        val start = 1_000_000L
        val end = 10_000_000L
        val series = TrendSeries(
            range = TrendRange.DAYS_30,
            aggregation = TrendAggregation.RAW,
            points = listOf(
                TrendPoint("p1", start + 1_000_000L, start, start + 1L, 120, 80, 70, "NORMAL", false, 1, TrendAggregation.RAW),
                TrendPoint("p2", end - 1_000_000L, end - 1L, end, 125, 82, 72, "HIGH_NORMAL", false, 1, TrendAggregation.RAW)
            ),
            rawRecordCount = 2,
            averageSystolic = 123,
            averageDiastolic = 81,
            yAxis = TrendYAxis(70, 150, 10),
            rangeStart = start,
            rangeEnd = end
        )
        controller.updateSeries(series)
        val vp = controller.viewportFor(TrendRange.DAYS_30)
        val defaultStartRatio = vp.defaultStartRatio
        val defaultEndRatio = vp.defaultEndRatio

        vp.zoomBy(zoomChange = 3f, focusRatio = 0.5, minSpanRatio = 0.05)
        assertFalse(vp.isAtDefault)

        // 关闭脉搏面板卸载子作用域，不应清空父页面作用域
        controller.detachUiScope(pulsePanelOwner)

        // 触发复位并模拟图表副作用再次传入同一份 series：不得取消正在运行的复位动画
        controller.reset()
        runCurrent()
        controller.updateSeries(series)
        advanceUntilIdle()

        assertEquals(defaultStartRatio, vp.startRatio, 1e-4)
        assertEquals(defaultEndRatio, vp.endRatio, 1e-4)
        assertEquals(1, controller.resetToken)

        controller.detachUiScope(rootOwner)
    }

    @Test
    fun `interrupting viewport animation preserves new job reference for touch cancel`() = runTest {
        val uiScope = CoroutineScope(coroutineContext + testFrameClock())
        val viewport = TrendTimeViewportState()
        viewport.reset(
            domainStart = 0L,
            domainEnd = 100_000L,
            defaultStart = 0L,
            defaultEnd = 100_000L
        )
        viewport.zoomBy(zoomChange = 4f, focusRatio = 0.1, minSpanRatio = 0.05)

        viewport.animateWindowTo(0.20, 0.45, uiScope, reducedMotion = false)
        runCurrent()
        // 紧接着发起第二次动画：旧协程 finally 不得把新协程引用置空
        viewport.animateWindowTo(0.65, 0.90, uiScope, reducedMotion = false)
        runCurrent()
        advanceTimeBy(32L)

        // 手指按下中断在途新动画：必须立即停在当前帧，不能继续跑到 0.65..0.90
        viewport.cancelAnimation()
        val interruptedStart = viewport.startRatio
        advanceUntilIdle()
        assertEquals(interruptedStart, viewport.startRatio, 1e-6)
        assertTrue("Touch cancel must stop animation before reaching final target", interruptedStart < 0.65)
    }

    @Test
    fun `resolveTargetLabelPositions separates close personal target labels`() {
        val placements = TrendChartMath.resolveTargetLabelPositions(
            specs = listOf(
                TargetLabelSpec(lineY = 120f, width = 52f, height = 28f),
                TargetLabelSpec(lineY = 126f, width = 52f, height = 28f)
            ),
            left = 40f,
            right = 300f,
            top = 30f,
            bottom = 260f,
            lineGapPx = 2f,
            labelGapPx = 4f,
            rightMarginPx = 4f
        )
        assertEquals(2, placements.size)
        val p0 = placements[0]
        val p1 = placements[1]
        assertTrue("Labels must stay inside plotTop", p0.y >= 30f && p1.y >= 30f)
        assertTrue("Labels must stay inside plotBottom", p0.y + 28f <= 260f && p1.y + 28f <= 260f)
        assertTrue(
            "Target labels must not overlap vertically: p0=[${p0.y}..${p0.y + 28f}], p1=[${p1.y}..${p1.y + 28f}]",
            p1.y >= p0.y + 28f + 3.5f
        )
    }

    @Test
    fun `dayTicks reacts immediately when timezone or system clock jumps`() = runTest {
        var currentZone = ZoneId.of("UTC")
        val nowMillis = LocalDate.of(2026, 5, 1).atTime(20, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val collected = mutableListOf<LocalDate>()

        val job = launch {
            dayTicks(
                zoneProvider = { currentZone },
                clockMillis = { nowMillis },
                externalTrigger = trigger
            ).take(2).toList(collected)
        }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 5, 1)), collected)

        // 同一 UTC 时刻切换到东八区（2026-05-02 04:00），发送时区变更信号后立即发出新日期
        currentZone = ZoneId.of("Asia/Shanghai")
        trigger.tryEmit(Unit)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2)), collected)
        job.cancel()
    }
}
