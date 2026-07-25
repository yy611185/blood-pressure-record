package com.example.bloodpressurerecord.data.repository.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class BackupExportServiceAndroidTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun buildPayload_exportsSavedSessionReadingsIntoMeasurementSheet() = runTest {
        val sessionId = "session-1"
        val sourceReadings = (1..7).map { index ->
            MeasurementReadingEntity(
                "reading-$index",
                sessionId,
                index,
                118 + index,
                77 + index,
                69 + index
            )
        }
        database.measurementSessionDao().insertSessionWithReadings(
            MeasurementSessionEntity(
                id = sessionId,
                measuredAt = 1_774_406_600_000L,
                scene = "晨起",
                note = "导出测试",
                symptomsJson = "[\"头晕\"]",
                avgSystolic = 121,
                avgDiastolic = 80,
                avgPulse = 72,
                category = "NORMAL",
                highRiskAlertTriggered = false,
                createdAt = 1_774_406_601_000L,
                updatedAt = 1_774_406_601_000L
            ),
            sourceReadings
        )

        val payload = BackupExportService(
            sessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao(),
            userProfileDao = database.userProfileDao(),
            appSettingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).buildPayload(appName = "家庭血压记录", appVersion = "test")

        assertEquals(1, payload.measurements.size)
        assertEquals(1, payload.diagnostics.sessionCount)
        assertEquals(7, payload.diagnostics.readingCount)
        assertEquals(7, payload.readings.size)

        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(payload, output)
            output.toByteArray()
        }
        assertFalse(bytes.isEmpty())

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheet("测量记录")
            val row = sheet.getRow(1)
            assertEquals(sessionId, row.getCell(0).stringCellValue)
            assertEquals(7.0, row.getCell(4).numericCellValue, 0.0)
            assertEquals(121.0, row.getCell(5).numericCellValue, 0.0)
            assertEquals(80.0, row.getCell(6).numericCellValue, 0.0)
            assertEquals("晨起", row.getCell(10).stringCellValue)
            assertEquals("[\"头晕\"]", row.getCell(11).stringCellValue)
            assertEquals(7, workbook.getSheet("原始读数").lastRowNum)
        }
    }

    @Test
    fun exportedWorkbook_importsAllReadingsSceneAndSymptoms() = runTest {
        val sessionId = "roundtrip-session"
        database.measurementSessionDao().insertSessionWithReadings(
            MeasurementSessionEntity(
                id = sessionId,
                measuredAt = 1_774_406_600_000L,
                scene = "运动后",
                note = "roundtrip",
                symptomsJson = "[\"心悸\"]",
                avgSystolic = 130,
                avgDiastolic = 84,
                avgPulse = 75,
                category = "STAGE1",
                highRiskAlertTriggered = false,
                createdAt = 1_774_406_601_000L,
                updatedAt = 1_774_406_601_000L
            ),
            (1..8).map { index ->
                MeasurementReadingEntity(
                    id = "roundtrip-$index",
                    sessionId = sessionId,
                    orderIndex = index,
                    systolic = 125 + index,
                    diastolic = 79 + index,
                    pulse = 70 + index
                )
            }
        )
        val settingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext())
        val payload = BackupExportService(
            sessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao(),
            userProfileDao = database.userProfileDao(),
            appSettingsStore = settingsStore
        ).buildPayload("家庭血压记录", "test")
        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(payload, output)
            output.toByteArray()
        }

        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()
        val result = BackupImportService(database, settingsStore)
            .importXlsx(ByteArrayInputStream(bytes))
        val restored = database.measurementSessionDao().getSessionWithReadings(sessionId)

        assertEquals(1, result.sessionCount)
        assertEquals(8, result.readingCount)
        assertEquals("运动后", restored?.session?.scene)
        assertEquals("[\"心悸\"]", restored?.session?.symptomsJson)
        assertEquals(8, restored?.readings?.size)
    }
}
