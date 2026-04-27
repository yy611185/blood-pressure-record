package com.example.bloodpressurerecord.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.data.db.dao.MeasurementSessionDao
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementSessionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MeasurementSessionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.measurementSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_and_query_session_with_readings() = runBlocking {
        val session = MeasurementSessionEntity(
            id = "session-1",
            measuredAt = 1000L,
            scene = "居家安静",
            note = "早餐前",
            symptomsJson = "[\"头晕\"]",
            avgSystolic = 132,
            avgDiastolic = 84,
            avgPulse = 72,
            category = "STAGE1",
            highRiskAlertTriggered = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val readings = listOf(
            MeasurementReadingEntity(
                id = "r1",
                sessionId = "session-1",
                orderIndex = 1,
                systolic = 130,
                diastolic = 82,
                pulse = 70
            ),
            MeasurementReadingEntity(
                id = "r2",
                sessionId = "session-1",
                orderIndex = 2,
                systolic = 134,
                diastolic = 86,
                pulse = 74
            )
        )

        dao.insertSessionWithReadings(session, readings)
        val loaded = dao.getSessionWithReadings("session-1")

        assertNotNull(loaded)
        assertEquals("session-1", loaded?.session?.id)
        assertEquals(2, loaded?.readings?.size)
        assertEquals(1, loaded?.readings?.first()?.orderIndex)
    }
}
