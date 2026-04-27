package com.example.bloodpressurerecord.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            sessionDao = database.measurementSessionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
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
        assertEquals("STAGE1", loaded?.category)
        assertFalse(loaded?.highRiskAlertTriggered ?: true)
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
}
