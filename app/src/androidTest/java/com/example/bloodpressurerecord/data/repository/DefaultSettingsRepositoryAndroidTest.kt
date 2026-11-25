package com.example.bloodpressurerecord.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import com.example.bloodpressurerecord.data.repository.backup.BackupExportService
import com.example.bloodpressurerecord.data.repository.backup.BackupFileWriter
import com.example.bloodpressurerecord.data.repository.backup.BackupImportService
import com.example.bloodpressurerecord.data.repository.backup.BackupImportOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DefaultSettingsRepositoryAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DefaultSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultSettingsRepository(
            context = context,
            appSettingsStore = AppSettingsStore(context),
            database = database,
            userProfileDao = database.userProfileDao(),
            measurementSessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearAllData_removesAllRoomDataInOneOperation() = runTest {
        database.measurementSessionDao().insertSession(
            MeasurementSessionEntity(
                id = "clear-test",
                measuredAt = 1_000L,
                scene = "晨起",
                note = null,
                symptomsJson = null,
                avgSystolic = 120,
                avgDiastolic = 80,
                avgPulse = 70,
                category = "NORMAL",
                containsHighRiskReading = false,
                createdAt = 1_000L,
                updatedAt = 1_000L
            )
        )
        database.measurementDao().insert(
            BloodPressureMeasurementEntity(
                memberName = "旧记录",
                systolic = 120,
                diastolic = 80,
                pulse = 70,
                measuredAtMillis = 1_000L,
                level = "NORMAL"
            )
        )
        database.userProfileDao().upsert(
            UserProfileEntity(
                name = "测试",
                age = 60,
                gender = null,
                targetSystolic = 120,
                targetDiastolic = 80,
                updatedAt = 1_000L
            )
        )

        repository.clearAllData().getOrThrow()

        assertEquals(0, database.measurementSessionDao().countSessions())
        assertEquals(0, database.measurementDao().countAll())
        assertEquals(null, database.userProfileDao().getProfile())
    }
    @Test
    fun selectedDisplayProfileAndMeasurementsNeverRestoreMedicationTablesOrResync() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AppSettingsStore(context)
        store.clearAll()
        try {
            store.setAppearanceMode("dark")
            store.setMedicationReminderEnabled(false)
            val dao = database.medicationDao()
            val medicationId = dao.insertMedicationWithTimes(
                MedicationEntity(name = "test", dosage = "1", enabled = false, createdAt = 100),
                listOf("08:00")
            )
            val time = dao.getTimesForMedication(medicationId).single()
            dao.setTimeActive(time.id, false)
            dao.insertLog(MedicationIntakeLogEntity(medicationId = medicationId,
                timeId = time.id, epochDay = 20_000, takenAt = 200))
            val payload = BackupExportService(database.measurementSessionDao(),
                database.measurementDao(), database.userProfileDao(), store,
                medicationDao = dao).buildPayload("test", "test")
            val bytes = ByteArrayOutputStream().use {
                BackupFileWriter().writeXlsx(payload, it)
                it.toByteArray()
            }
            // 本机状态与备份不同，若误导入会覆盖 enabled/active 或重新添加打卡。
            dao.updateMedication(dao.getMedicationsWithTimes().single().medication.copy(enabled = true))
            dao.setTimeActive(time.id, true)
            dao.deleteAllLogs()
            store.setAppearanceMode("light")
            store.setMedicationReminderEnabled(true)
            store.setMedicationCalendar(calendarId = 1L, includeDetails = true)
            val beforeMedications = dao.getMedicationsWithTimes()
            val beforeLogs = dao.getAllLogs()
            val beforeSettings = store.settingsFlow.first()
            var medicationResyncCalls = 0
            val alarmSettings = mutableListOf<Boolean>()
            val guardedRepository = DefaultSettingsRepository(context, store, database,
                database.userProfileDao(), database.measurementSessionDao(), database.measurementDao(),
                medicationResync = { medicationResyncCalls++ },
                medicationAlarmResync = { alarmSettings += store.settingsFlow.first().medicationReminderEnabled })
            val service = BackupImportService(database, store)
            val preview = service.previewXlsx(ByteArrayInputStream(bytes))
            assertEquals(1, preview.medicationCount)
            assertTrue(preview.changesReminderEnabled)
            listOf(
                BackupImportOptions(importMeasurements = false, restoreDisplaySettings = true),
                BackupImportOptions(importMeasurements = false, restoreUserProfile = true),
                BackupImportOptions(importMeasurements = true)
            ).forEach { options ->
                val message = guardedRepository.commitBackupImport(preview, options).getOrThrow()
                assertFalse(message.contains("已恢复药品"))
                assertEquals(beforeMedications, dao.getMedicationsWithTimes())
                assertEquals(beforeLogs, dao.getAllLogs())
                assertEquals(0, medicationResyncCalls)
            }
            assertEquals("dark", store.settingsFlow.first().appearanceMode)
            assertEquals(beforeSettings.medicationReminderEnabled, store.settingsFlow.first().medicationReminderEnabled)
            assertEquals(beforeSettings.medicationCalendarId, store.settingsFlow.first().medicationCalendarId)
            assertEquals(beforeSettings.medicationCalendarIncludeDetails, store.settingsFlow.first().medicationCalendarIncludeDetails)
            val none = BackupImportOptions(importMeasurements = false)
            assertTrue(guardedRepository.commitBackupImport(preview, none).isFailure)
            assertEquals(beforeMedications, dao.getMedicationsWithTimes())
            assertEquals(0, medicationResyncCalls)
            // 用药与提醒开关独立选择。只恢复用药时沿用本机开关。
            guardedRepository.commitBackupImport(preview,
                BackupImportOptions(importMeasurements = false, restoreMedications = true)).getOrThrow()
            assertFalse(dao.getMedicationsWithTimes().single().medication.enabled)
            assertFalse(dao.getMedicationsWithTimes().single().times.single().active)
            assertEquals(1, dao.getAllLogs().size)
            assertEquals(1, medicationResyncCalls)
            assertTrue(store.settingsFlow.first().medicationReminderEnabled)
            assertTrue(alarmSettings.isEmpty())

            val remindersOnly = BackupImportOptions(importMeasurements = false, restoreReminderSettings = true)
            guardedRepository.commitBackupImport(preview, remindersOnly).getOrThrow()
            assertFalse(store.settingsFlow.first().medicationReminderEnabled)
            assertEquals(listOf(false), alarmSettings)
            assertEquals(1, medicationResyncCalls)

            // 旧备份没有该键。关闭的开关不因读取旧备份而重置。
            val legacyBytes = ByteArrayOutputStream().use {
                val legacy = payload.copy(userProfile = payload.userProfile.filterNot { item ->
                    item.key == "medication_reminder_enabled"
                })
                BackupFileWriter().writeXlsx(legacy, it)
                it.toByteArray()
            }
            val legacy = service.previewXlsx(ByteArrayInputStream(legacyBytes))
            assertFalse(legacy.changesReminderEnabled)
            guardedRepository.commitBackupImport(legacy, remindersOnly).getOrThrow()
            assertFalse(store.settingsFlow.first().medicationReminderEnabled)
            assertEquals(listOf(false), alarmSettings)
        } finally {
            store.clearAll()
        }
    }

}
