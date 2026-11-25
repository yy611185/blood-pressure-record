package com.example.bloodpressurerecord.data

import androidx.compose.ui.graphics.toArgb
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.repository.DefaultBloodPressureRepository
import com.example.bloodpressurerecord.data.repository.DefaultMedicationRepository
import com.example.bloodpressurerecord.data.repository.MarkMedicationTakenResult
import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionReading
import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.data.repository.SessionRecord
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.model.TrendRecord
import com.example.bloodpressurerecord.widget.WidgetDiastolicDarkColor
import com.example.bloodpressurerecord.widget.WidgetDiastolicLightColor
import com.example.bloodpressurerecord.widget.renderTrendBitmap
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditPersistenceAndNotificationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun medicationDeleteSnapshotRestoresInactiveSlotsLogsEnabledStateAndOriginalIds() = runBlocking {
        val removedTimeIds = mutableListOf<Long>()
        val repo = DefaultMedicationRepository(
            dao = database.medicationDao(),
            onTimeIdsRemoved = { removedTimeIds += it }
        )
        val medId = repo.addMedication("苯磺酸氨氯地平", "5mg", listOf("08:00", "20:00")).getOrThrow()
        val initialSlots = repo.getSlotsForDay(LocalDate.of(2026, 10, 10))
        assertEquals(2, initialSlots.size)

        // 打卡两个时间点，随后停用 20:00 并把药品整体设为停用
        repo.setTaken(medId, initialSlots[0].timeId, LocalDate.of(2026, 10, 10), taken = true)
        repo.setTaken(medId, initialSlots[1].timeId, LocalDate.of(2026, 10, 9), taken = true)
        repo.updateMedication(medId, "苯磺酸氨氯地平", "5mg", enabled = false, times = listOf("08:00")).getOrThrow()

        val snapshot = repo.deleteMedicationWithSnapshot(medId).getOrThrow()
        assertTrue(database.medicationDao().getMedicationsWithTimes().isEmpty())
        assertTrue(removedTimeIds.containsAll(initialSlots.map { it.timeId }))

        // 完整恢复：原主键、enabled=false、停用时段(20:00)及两天历史打卡全部恢复
        repo.restoreMedication(snapshot).getOrThrow()
        val restoredMed = database.medicationDao().getMedication(medId)!!
        val restoredTimes = database.medicationDao().getTimesForMedication(medId)
        val restoredLogs = database.medicationDao().getLogsForMedication(medId)
        assertEquals(medId, restoredMed.id)
        assertFalse(restoredMed.enabled)
        assertEquals(2, restoredTimes.size)
        assertEquals(1, restoredTimes.count { it.active })
        assertEquals(1, restoredTimes.count { !it.active })
        assertEquals(2, restoredLogs.size)
    }

    @Test
    fun markTakenFromReminderHandlesDeletedMedicationWithoutForeignKeyCrashAndKeepsIdempotency() = runBlocking {
        val repo = DefaultMedicationRepository(dao = database.medicationDao())
        val day = LocalDate.of(2026, 10, 12)
        val medId = repo.addMedication("缬沙坦", "80mg", listOf("07:30")).getOrThrow()
        val slot = repo.getSlotsForDay(day).single()

        // 正常重复打卡幂等成功
        assertEquals(
            MarkMedicationTakenResult.RECORDED,
            repo.markTakenFromReminder(medId, slot.timeId, day).getOrThrow()
        )
        assertEquals(
            MarkMedicationTakenResult.RECORDED,
            repo.markTakenFromReminder(medId, slot.timeId, day).getOrThrow()
        )
        assertEquals(1, database.medicationDao().getLogsForDay(day.toEpochDay()).size)

        // 删除药品后再触发旧通知“已服用”，不抛外键异常，优雅返回 EXPIRED_OR_REMOVED
        repo.deleteMedication(medId).getOrThrow()
        val expiredResult = repo.markTakenFromReminder(medId, slot.timeId, day)
        assertTrue(expiredResult.isSuccess)
        assertEquals(MarkMedicationTakenResult.EXPIRED_OR_REMOVED, expiredResult.getOrThrow())
    }

    @Test
    fun restoreSessionPreservesRepresentativeValuesAndNeverOverwritesExistingSameIdSession() = runBlocking {
        val repo = DefaultBloodPressureRepository(database = database)
        val sessionId = "legacy-discard-first-session"
        val original = SessionRecord(
            id = sessionId,
            measuredAt = 1_760_100_000_000L,
            scene = "晨间",
            note = "原始备注",
            symptoms = listOf("头晕"),
            avgSystolic = 123,
            avgDiastolic = 79,
            avgPulse = 71,
            category = "HIGH_NORMAL",
            containsHighRiskReading = false,
            readings = listOf(
                SessionReading("r-1", 0, 158, 98, 80),
                SessionReading("r-2", 1, 122, 78, 70),
                SessionReading("r-3", 2, 124, 80, 72)
            ),
            averageStrategy = AverageStrategy.DISCARD_FIRST,
            createdAt = 1_760_100_000_000L,
            updatedAt = 1_760_100_000_500L,
            timePeriod = "晨间",
            symptomNote = "轻微头晕",
            factorNote = "没睡好"
        )

        // 首次写入该历史快照（弃首组代表值为 123/79，若被重算为 ALL 则会变成 135/85）
        repo.restoreSession(original).getOrThrow()
        val seeded = repo.observeSession(sessionId).first()!!
        assertEquals(123, seeded.avgSystolic)
        assertEquals(79, seeded.avgDiastolic)
        assertEquals(AverageStrategy.DISCARD_FIRST, seeded.averageStrategy)

        repo.deleteSession(sessionId).getOrThrow()
        assertEquals(null, repo.observeSession(sessionId).first())

        // 正常撤销恢复原记录：代表值、分级、策略、风险标记分毫不改
        repo.restoreSession(original).getOrThrow()
        val restored = repo.observeSession(sessionId).first()!!
        assertEquals(original.avgSystolic, restored.avgSystolic)
        assertEquals(original.avgDiastolic, restored.avgDiastolic)
        assertEquals(original.category, restored.category)
        assertEquals(original.containsHighRiskReading, restored.containsHighRiskReading)
        assertEquals(original.averageStrategy, restored.averageStrategy)

        // 若同 ID 记录已存在（例如已被备份导入更新为 136/86），旧撤销恢复必须拒绝覆盖
        repo.updateSession(
            sessionId,
            SaveSessionInput(
                measuredAt = original.measuredAt,
                scene = "晨间",
                note = "导入后的新内容",
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(136, 86, 74),
                    SessionReadingInput(136, 86, 74)
                ),
                averageStrategy = AverageStrategy.ALL
            )
        ).getOrThrow()

        val conflictRestore = repo.restoreSession(original)
        assertTrue("Restoring over existing sessionId must fail", conflictRestore.isFailure)
        val current = repo.observeSession(sessionId).first()!!
        assertEquals(136, current.avgSystolic)
        assertEquals("导入后的新内容", current.note)
    }

    @Test
    fun widgetTrendBitmapUsesHighContrastPaletteInDarkMode() {
        val sample = listOf(
            TrendRecord("1", 1_000L, 128, 82, 70, "HIGH_NORMAL"),
            TrendRecord("2", 2_000L, 134, 86, 72, "HIGH_NORMAL")
        )
        val lightBmp = renderTrendBitmap(sample, darkTheme = false)
        val darkBmp = renderTrendBitmap(sample, darkTheme = true)

        var foundLightDia = false
        var foundDarkDia = false
        val lightDiaArgb = WidgetDiastolicLightColor.toArgb()
        val darkDiaArgb = WidgetDiastolicDarkColor.toArgb()
        for (y in 0 until lightBmp.height) {
            for (x in 0 until lightBmp.width) {
                if (lightBmp.getPixel(x, y) == lightDiaArgb) foundLightDia = true
                if (darkBmp.getPixel(x, y) == darkDiaArgb) foundDarkDia = true
            }
        }
        assertTrue("Light mode bitmap must draw light diastolic color", foundLightDia)
        assertTrue("Dark mode bitmap must draw high-contrast dark diastolic color", foundDarkDia)
    }
}
