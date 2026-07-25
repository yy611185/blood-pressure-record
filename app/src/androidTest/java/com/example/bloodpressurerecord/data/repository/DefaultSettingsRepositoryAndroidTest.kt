package com.example.bloodpressurerecord.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.BloodPressureMeasurementEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.UserProfileEntity
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
                highRiskAlertTriggered = false,
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
}
