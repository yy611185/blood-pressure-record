package com.example.bloodpressurerecord.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPayload
import com.example.bloodpressurerecord.data.repository.backup.BackupExportPlan
import com.example.bloodpressurerecord.ui.settings.SettingsViewModel
import kotlinx.coroutines.withContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.datastore.PreferenceKeys
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
    private lateinit var preferences: DataStore<Preferences>
    private lateinit var storeScope: CoroutineScope
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(files, "test.preferences_pb")
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultSettingsRepository(
            context = context,
            appSettingsStore = AppSettingsStore.using(preferences),
            database = database,
            userProfileDao = database.userProfileDao(),
            measurementSessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao(),
            cancelRemovedMedicationNotifications = {},
            cancelMeasurementNotifications = {},
            applyMeasurementReminders = {}
        )
    }

    @After
    fun tearDown() {
        storeScope.cancel()
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
        val store = AppSettingsStore.using(preferences)
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
                applyMeasurementReminders = {},
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

    @Test
    fun secondVolumeResumesAndRestoredPickerCannotWriteWithoutItsPlan() = runTest {
        val payload = BackupExportPayload(
            instructions = emptyList(), measurements = emptyList(),
            userProfile = emptyList(), meta = emptyList()
        )
        val saved = SavedStateHandle()
        val volumes = mutableListOf<Int>()
        val source = object : SettingsRepository by repository {
            override suspend fun prepareBackupExport() =
                Result.success(BackupExportPlan(listOf(payload, payload)))

            override suspend fun exportBackupVolumeToUri(
                plan: BackupExportPlan,
                volumeIndex: Int,
                uri: Uri,
                passphrase: CharArray?
            ): Result<Unit> {
                assertEquals("safe-test-password", passphrase?.concatToString())
                volumes += volumeIndex
                return Result.success(Unit)
            }
        }
        withContext(Dispatchers.Main) {
            val vm = SettingsViewModel(source, savedStateHandle = saved)
            vm.prepareBackupExport("test", "safe-test-password".toCharArray())
            val first = requireNotNull(vm.uiState.value.exportVolumeRequestId)
            assertTrue(vm.beginBackupLocation(first))
            vm.finishExportBackupLocation(first, Uri.parse("content://backup-test/first"))
            assertEquals(listOf(0), volumes)
            vm.pauseBackupExport()
            vm.requestBackupExport()
            vm.dismissSwitchExportFormat()
            vm.requestEncryptedBackupExport()
            assertEquals(1, vm.uiState.value.exportVolumeIndex)
            val second = requireNotNull(vm.uiState.value.exportVolumeRequestId)
            assertTrue(vm.beginBackupLocation(second))

            val restored = SettingsViewModel(source, savedStateHandle = saved)
            assertEquals(second, restored.interruptedExportRequestId)
            assertTrue(restored.uiState.value.message.contains("重新导出全部卷"))
            restored.finishExportBackupLocation(second, Uri.parse("content://backup-test/stale"))
            assertEquals(listOf(0), volumes)
        }
    }

    @Test
    fun singlePlainWriteFailureAndRestoredPickerDoNotRearmAutoLaunch() = runTest {
        val payload = BackupExportPayload(
            instructions = emptyList(), measurements = emptyList(),
            userProfile = emptyList(), meta = emptyList()
        )
        val saved = SavedStateHandle()
        var writes = 0
        val source = object : SettingsRepository by repository {
            override suspend fun prepareBackupExport() =
                Result.success(BackupExportPlan(listOf(payload)))

            override suspend fun exportBackupVolumeToUri(
                plan: BackupExportPlan, volumeIndex: Int, uri: Uri, passphrase: CharArray?
            ): Result<Unit> {
                writes++
                return Result.failure(IllegalStateException("模拟写入失败"))
            }
        }
        withContext(Dispatchers.Main) {
            val vm = SettingsViewModel(source, savedStateHandle = saved)
            vm.requestBackupExport()
            val request = requireNotNull(vm.uiState.value.autoLaunchExportRequestId)
            assertTrue(vm.consumeAutoLaunchExportRequest(request))
            assertTrue(vm.beginBackupLocation(request))
            val destination = Uri.parse("content://backup-test/single")
            vm.finishExportBackupLocation(request, destination)
            assertEquals(1, writes)
            assertEquals(null, vm.uiState.value.autoLaunchExportRequestId)
            assertEquals(null, vm.uiState.value.exportVolumeRequestId)
            assertTrue(vm.uiState.value.messageIsError)

            vm.requestBackupExport()
            val retry = requireNotNull(vm.uiState.value.autoLaunchExportRequestId)
            assertTrue(vm.consumeAutoLaunchExportRequest(retry))
            assertTrue(vm.beginBackupLocation(retry))
            val restored = SettingsViewModel(source, savedStateHandle = saved)
            assertEquals(retry, restored.interruptedExportRequestId)
            restored.finishExportBackupLocation(retry, destination)
            assertEquals(1, writes)
            assertEquals(null, restored.uiState.value.autoLaunchExportRequestId)
        }
    }

    @Test
    fun setMedicationCalendar_handlesMissingAndLegacyKeysSafely() = runTest {
        val store = AppSettingsStore.using(preferences)
        preferences.edit { it.clear() }
        try {
            // 新安装/键不存在：开启、更新、重复关闭都不应因空 Boolean Preference 抛出异常。
            store.setMedicationCalendar(calendarId = 42L, includeDetails = true)
            val enabled = store.settingsFlow.first()
            assertTrue(enabled.medicationCalendarSyncEnabled)
            assertEquals(42L, enabled.medicationCalendarId)
            assertTrue(enabled.medicationCalendarIncludeDetails)
            assertFalse(store.calendarEventsMayExist.first())

            store.setMedicationCalendar(calendarId = null, includeDetails = false)
            store.setMedicationCalendar(calendarId = null, includeDetails = false)
            val disabled = store.settingsFlow.first()
            assertFalse(disabled.medicationCalendarSyncEnabled)
            assertEquals(null, disabled.medicationCalendarId)
            assertFalse(disabled.medicationCalendarIncludeDetails)
            assertFalse(store.calendarEventsMayExist.first())

            // 旧版本开启过同步（存在 legacy true 且未写入 events_may_exist）：迁移为 events_may_exist = true 并移除旧键。
            preferences.edit { prefs ->
                prefs.clear()
                prefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED] = true
            }
            store.setMedicationCalendar(calendarId = 7L, includeDetails = false)
            val migratedPrefs = preferences.data.first()
            assertEquals(null, migratedPrefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED])
            assertEquals(true, migratedPrefs[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST])
            assertTrue(store.calendarEventsMayExist.first())
            assertEquals(7L, store.settingsFlow.first().medicationCalendarId)

            // 旧版本显式关闭过同步（legacy false）：移除旧键但不误标记 events_may_exist。
            preferences.edit { prefs ->
                prefs.clear()
                prefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED] = false
            }
            store.setMedicationCalendar(calendarId = null, includeDetails = false)
            val legacyDisabledPrefs = preferences.data.first()
            assertEquals(null, legacyDisabledPrefs[PreferenceKeys.MEDICATION_CALENDAR_SYNC_ENABLED])
            assertEquals(null, legacyDisabledPrefs[PreferenceKeys.MEDICATION_CALENDAR_EVENTS_MAY_EXIST])
            assertFalse(store.calendarEventsMayExist.first())
        } finally {
            preferences.edit { it.clear() }
        }
    }

}
