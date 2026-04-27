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
        database.measurementSessionDao().insertSessionWithReadings(
            MeasurementSessionEntity(
                id = sessionId,
                measuredAt = 1_774_406_600_000L,
                scene = "晨起",
                note = "导出测试",
                symptomsJson = null,
                avgSystolic = 121,
                avgDiastolic = 80,
                avgPulse = 72,
                category = "NORMAL",
                highRiskAlertTriggered = false,
                createdAt = 1_774_406_601_000L,
                updatedAt = 1_774_406_601_000L
            ),
            listOf(
                MeasurementReadingEntity("reading-1", sessionId, 1, 120, 79, 71),
                MeasurementReadingEntity("reading-2", sessionId, 2, 122, 81, 73)
            )
        )

        val payload = BackupExportService(
            sessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao(),
            userProfileDao = database.userProfileDao(),
            appSettingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).buildPayload(appName = "家庭血压记录", appVersion = "test")

        assertEquals(1, payload.measurements.size)
        assertEquals(1, payload.diagnostics.sessionCount)
        assertEquals(2, payload.diagnostics.readingCount)

        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(payload, output)
            output.toByteArray()
        }
        assertFalse(bytes.isEmpty())

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheet("测量记录")
            val row = sheet.getRow(1)
            assertEquals(sessionId, row.getCell(0).stringCellValue)
            assertEquals(2.0, row.getCell(4).numericCellValue, 0.0)
            assertEquals(120.0, row.getCell(5).numericCellValue, 0.0)
            assertEquals(79.0, row.getCell(6).numericCellValue, 0.0)
            assertEquals(122.0, row.getCell(8).numericCellValue, 0.0)
            assertEquals(81.0, row.getCell(9).numericCellValue, 0.0)
            assertEquals(121.0, row.getCell(20).numericCellValue, 0.0)
            assertEquals(80.0, row.getCell(21).numericCellValue, 0.0)
        }
    }
}
