package com.example.bloodpressurerecord.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import com.example.bloodpressurerecord.data.repository.backup.BackupExportService
import com.example.bloodpressurerecord.data.repository.backup.BackupFileWriter
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import com.example.bloodpressurerecord.data.repository.backup.BackupImportService
import com.example.bloodpressurerecord.domain.time.MeasurementTimestampValidator
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultBloodPressureRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DefaultBloodPressureRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = DefaultBloodPressureRepository(
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingImportedLegacyRecordsKeepsThemOutOfBackupUntilUndo() = runBlocking {
        val measurements = database.measurementDao()
        val old = BloodPressureMeasurementEntity(7, "甲", 120, 80, 70, 1_700_000_000_000L, "NORMAL")
        measurements.insert(old)
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            """CREATE TABLE blood_pressure_records (
                id INTEGER PRIMARY KEY, memberName TEXT, systolic INTEGER, diastolic INTEGER,
                pulse INTEGER, measuredAtMillis INTEGER, level TEXT, remark TEXT)"""
        )
        sql.execSQL(
            "INSERT INTO blood_pressure_records VALUES (7, '乙', 122, 82, 72, 1700000000000, 'HIGH_NORMAL', '备注')"
        )
        val settings = AppSettingsStore(ApplicationProvider.getApplicationContext())
        val export = BackupExportService(database.measurementSessionDao(), measurements, database.userProfileDao(), settings)
        val initial = export.buildPayload("test", "test")
        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(initial, output)
            output.toByteArray()
        }
        val importer = BackupImportService(database, settings)
        importer.commitImport(importer.previewXlsx(bytes.inputStream()), BackupImportOptions())

        measurements.insert(old.copy(id = 8))
        assertTrue(repository.deleteSession("bp_measurements:8").isFailure)
        for (id in listOf("bp_measurements:7", "blood_pressure_records:7")) {
            val snapshot = requireNotNull(repository.observeSession(id).first())
            repository.deleteSession(id).getOrThrow()
            val afterDelete = export.buildPayload("test", "test").measurements.map { it.recordId }
            val expected = setOf("bp_measurements:7", "blood_pressure_records:7", "bp_measurements:8")
            assertEquals(expected - id, afterDelete.toSet())

            repository.restoreSession(snapshot).getOrThrow()
            assertEquals(snapshot, repository.observeSession(id).first())
            val restored = export.buildPayload("test", "test").measurements
            assertEquals(1, restored.count { it.recordId == id })
        }

        // 新设备仍可删除从别处恢复的旧记录，即使本机没有该可选的历史表。
        sql.execSQL("DROP TABLE blood_pressure_records")
        repository.deleteSession("blood_pressure_records:7").getOrThrow()
        assertEquals(listOf(8L), measurements.getAll().map { it.id })
    }

    @Test
    fun save_and_read_session_success() = runBlocking {
        val save = repository.saveSession(
            SaveSessionInput(
                measuredAt = 1_700_000_000_000L,
                scene = "晨起",
                note = "测试记录",
                symptoms = listOf("头晕"),
                readings = listOf(
                    SessionReadingInput(systolic = 130, diastolic = 82, pulse = 72),
                    SessionReadingInput(systolic = 134, diastolic = 84, pulse = 74)
                )
            )
        )

        assertNotNull(save.getOrNull())
        val sessionId = save.getOrThrow()
        val loaded = repository.observeSession(sessionId).first()

        assertNotNull(loaded)
        assertEquals(132, loaded?.avgSystolic)
        assertEquals(83, loaded?.avgDiastolic)
        assertEquals(73, loaded?.avgPulse)
        // 中国指南：132/83 属于正常高值
        assertEquals("HIGH_NORMAL", loaded?.category)
        assertFalse(loaded?.containsHighRiskReading ?: true)
        assertEquals(2, loaded?.readings?.size)
    }

    @Test
    fun save_and_read_session_with_dynamic_readings_success() = runBlocking {
        val save = repository.saveSession(
            SaveSessionInput(
                measuredAt = 1_700_000_100_000L,
                scene = "鍏朵粬",
                note = "dynamic",
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(systolic = 120, diastolic = 80, pulse = 70),
                    SessionReadingInput(systolic = 124, diastolic = 82, pulse = 72),
                    SessionReadingInput(systolic = 126, diastolic = 84, pulse = 73),
                    SessionReadingInput(systolic = 128, diastolic = 85, pulse = 74),
                    SessionReadingInput(systolic = 130, diastolic = 86, pulse = 75)
                )
            )
        )

        val sessionId = save.getOrThrow()
        val loaded = repository.observeSession(sessionId).first()

        assertNotNull(loaded)
        assertEquals(5, loaded?.readings?.size)
        assertEquals(126, loaded?.avgSystolic)
        assertEquals(83, loaded?.avgDiastolic)
    }

    @Test
    fun edit_recalculates_normal_record_to_high_risk_from_raw_reading() = runBlocking {
        val sessionId = repository.saveSession(
            SaveSessionInput(
                measuredAt = 1_700_000_200_000L,
                scene = "晨起",
                note = null,
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(120, 80, 70),
                    SessionReadingInput(122, 82, 72)
                )
            )
        ).getOrThrow()

        repository.updateSession(
            sessionId,
            SaveSessionInput(
                measuredAt = 1_700_000_200_000L,
                scene = "晨起",
                note = null,
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(190, 90, 70),
                    SessionReadingInput(110, 70, 72)
                )
            )
        ).getOrThrow()

        assertTrue(repository.observeSession(sessionId).first()!!.containsHighRiskReading)
    }

    @Test
    fun edit_recalculates_high_risk_record_to_normal() = runBlocking {
        val sessionId = repository.saveSession(
            SaveSessionInput(
                measuredAt = 1_700_000_300_000L,
                scene = "晨起",
                note = null,
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(190, 90, 70),
                    SessionReadingInput(110, 70, 72)
                )
            )
        ).getOrThrow()

        repository.updateSession(
            sessionId,
            SaveSessionInput(
                measuredAt = 1_700_000_300_000L,
                scene = "晨起",
                note = null,
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(118, 76, 68),
                    SessionReadingInput(122, 78, 70)
                )
            )
        ).getOrThrow()

        assertFalse(repository.observeSession(sessionId).first()!!.containsHighRiskReading)
    }

    @Test
    fun repository_rejects_future_measurement_time() = runBlocking {
        val fixedNow = 1_700_000_000_000L
        val guardedRepository = DefaultBloodPressureRepository(
            database = database,
            nowMillis = { fixedNow }
        )

        val result = guardedRepository.saveSession(
            SaveSessionInput(
                measuredAt = fixedNow + MeasurementTimestampValidator.FUTURE_TOLERANCE_MILLIS + 1,
                scene = "晨起",
                note = null,
                symptoms = emptyList(),
                readings = listOf(
                    SessionReadingInput(120, 80, 70),
                    SessionReadingInput(122, 82, 72)
                )
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            MeasurementTimestampValidator.FUTURE_MEASUREMENT_TIME_MESSAGE,
            result.exceptionOrNull()?.message
        )
    }
}
