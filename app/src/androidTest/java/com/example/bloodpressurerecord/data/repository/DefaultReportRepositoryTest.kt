package com.example.bloodpressurerecord.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationTimeEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.domain.model.AverageStrategy
import com.example.bloodpressurerecord.domain.report.ReportOptions
import com.example.bloodpressurerecord.domain.report.ReportPeriod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DefaultReportRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DefaultReportRepository
    private val period = ReportPeriod(
        LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), ZoneId.of("Asia/Taipei")
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = DefaultReportRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun rangeIncludesStartAndExcludesEndWithoutRecomputingSavedValues() = runBlocking {
        insertMeasurement("before", period.startInclusive - 1)
        insertMeasurement("start", period.startInclusive, strategy = "DISCARD_FIRST")
        insertMeasurement("last", period.endExclusive - 1, strategy = "unknown-legacy")
        insertMeasurement("end", period.endExclusive)

        val measurements = repository.loadSnapshot(period, ReportOptions()).measurements

        assertEquals(setOf("start", "last"), measurements.map { it.id }.toSet())
        // Raw rows deliberately average to 110/70; the saved representative values are authoritative.
        assertTrue(measurements.all { it.systolic == 137 && it.diastolic == 87 && it.pulse == 73 })
        assertEquals(AverageStrategy.DISCARD_FIRST, measurements.single { it.id == "start" }.averageStrategy)
        assertEquals(AverageStrategy.ALL, measurements.single { it.id == "last" }.averageStrategy)
        assertTrue(measurements.all { it.scene == null })
    }

    @Test
    fun fullNotesKeepAllTextAndDeduplicateLegacyCopies() = runBlocking {
        val completeNote = "完整备注".repeat(25)
        insertMeasurement(
            "notes", period.startInclusive,
            note = completeNote,
            symptomNote = completeNote,
            factorNote = "睡眠不足",
            symptomsJson = "[\"头晕\",\"睡眠不足\"]"
        )

        val note = repository.loadSnapshot(period, ReportOptions()).measurements.single().note!!

        assertTrue(note.contains(completeNote))
        assertTrue(note.contains("可能影响血压：睡眠不足"))
        assertTrue(note.contains("头晕"))
        assertEquals(1, note.windowed(completeNote.length).count { it == completeNote })
        assertEquals(1, note.windowed("睡眠不足".length).count { it == "睡眠不足" })
    }

    @Test
    fun hiddenIdentityNotesAndMedicationNeverEnterSnapshot() = runBlocking {
        insertMeasurement("private", period.startInclusive, note = "仅本机可见", symptomsJson = "[\"头晕\"]")
        database.userProfileDao().upsert(
            UserProfileEntity(name = "测试姓名", age = 61, gender = null,
                targetSystolic = null, targetDiastolic = null, updatedAt = period.startInclusive)
        )
        val medicationId = insertMedication("测试药品")
        val timeId = database.medicationDao().insertTime(MedicationTimeEntity(medicationId = medicationId, timeText = "08:00"))
        insertLog(medicationId, timeId, period.startDate.toEpochDay())

        val hidden = repository.loadSnapshot(period, ReportOptions(false, false, false))
        assertNull(hidden.profile)
        assertNull(hidden.measurements.single().note)
        assertTrue(hidden.medications.isEmpty())
        assertTrue(hidden.intakes.isEmpty())

        val visible = repository.loadSnapshot(period, ReportOptions(includeIdentity = true))
        assertEquals("测试姓名", visible.profile?.name)
        assertEquals(61, visible.profile?.age)
        assertEquals(1, visible.medications.size)
        assertEquals(1, visible.intakes.size)
    }

    @Test
    fun intakeUsesStoredEpochDayAndRetainsInactiveScheduleHistory() = runBlocking {
        val dao = database.medicationDao()
        val medicationId = insertMedication("已停用药品", enabled = false)
        val inactiveTime = dao.insertTime(MedicationTimeEntity(medicationId = medicationId, timeText = "07:00", active = false))
        val activeTime = dao.insertTime(MedicationTimeEntity(medicationId = medicationId, timeText = "20:00"))
        val firstDay = period.startDate.toEpochDay()
        // The action epoch deliberately falls outside the report period.
        insertLog(medicationId, inactiveTime, firstDay, period.endExclusive + 100_000)
        insertLog(medicationId, activeTime, firstDay, period.startInclusive - 100_000)
        insertLog(medicationId, inactiveTime, firstDay - 1, period.startInclusive)
        insertLog(medicationId, activeTime, period.endDate.plusDays(1).toEpochDay(), period.startInclusive)

        val snapshot = repository.loadSnapshot(period, ReportOptions())
        assertEquals(2, snapshot.intakes.size)
        assertTrue(snapshot.intakes.all { it.epochDay == firstDay })
        assertEquals(listOf(inactiveTime, activeTime), snapshot.intakes.map { it.timeId })
        assertFalse(snapshot.medications.single().enabled)
        assertFalse(snapshot.medications.single().times.first().active)
        assertTrue(snapshot.medications.single().times.last().active)
    }

    @Test
    fun deletedDrugCascadeIsOmittedAndFutureDrugWithoutLogsIsExcluded() = runBlocking {
        val dao = database.medicationDao()
        val deletedId = insertMedication("已删除药品")
        val deletedTime = dao.insertTime(MedicationTimeEntity(medicationId = deletedId, timeText = "08:00"))
        insertLog(deletedId, deletedTime, period.startDate.toEpochDay())
        dao.deleteMedication(deletedId)
        insertMedication("未来药品", createdAt = period.endExclusive)
        insertMedication("停用且无本周期打卡", enabled = false)

        val snapshot = repository.loadSnapshot(period, ReportOptions())
        assertTrue(snapshot.medications.isEmpty())
        assertTrue(snapshot.intakes.isEmpty())
        assertTrue(dao.getLogsInRange(period.startDate.toEpochDay(), period.endDate.plusDays(1).toEpochDay()).isEmpty())
    }

    private suspend fun insertMedication(
        name: String, enabled: Boolean = true, createdAt: Long = period.startInclusive
    ): Long = database.medicationDao().insertMedication(
        MedicationEntity(name = name, dosage = "1片", enabled = enabled, createdAt = createdAt)
    )

    private suspend fun insertLog(
        medicationId: Long, timeId: Long, epochDay: Long, takenAt: Long = period.startInclusive
    ) = database.medicationDao().insertLog(
        MedicationIntakeLogEntity(medicationId = medicationId, timeId = timeId, epochDay = epochDay, takenAt = takenAt)
    )

    private suspend fun insertMeasurement(
        id: String, measuredAt: Long, strategy: String = "ALL", note: String? = null,
        symptomNote: String? = null, factorNote: String? = null, symptomsJson: String? = null
    ) {
        database.measurementSessionDao().insertSessionWithReadings(
            MeasurementSessionEntity(
                id = id, measuredAt = measuredAt, scene = "诊室（旧版自由文本）",
                note = note, symptomsJson = symptomsJson,
                avgSystolic = 137, avgDiastolic = 87, avgPulse = 73,
                averageStrategy = strategy, category = "HIGH_NORMAL", containsHighRiskReading = false,
                createdAt = measuredAt, updatedAt = measuredAt,
                symptomNote = symptomNote, factorNote = factorNote
            ),
            listOf(
                MeasurementReadingEntity("$id-1", id, 1, 100, 60, 60),
                MeasurementReadingEntity("$id-2", id, 2, 120, 80, 80)
            )
        )
    }
}
