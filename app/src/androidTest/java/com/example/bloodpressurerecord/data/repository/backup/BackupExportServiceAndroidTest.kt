package com.example.bloodpressurerecord.data.repository.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bloodpressurerecord.data.datastore.AppSettingsStore
import com.example.bloodpressurerecord.data.db.AppDatabase
import com.example.bloodpressurerecord.data.db.entity.MeasurementReadingEntity
import com.example.bloodpressurerecord.data.db.entity.MeasurementSessionEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationEntity
import com.example.bloodpressurerecord.data.db.entity.MedicationIntakeLogEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
                containsHighRiskReading = false,
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
                containsHighRiskReading = false,
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

        assertEquals(1, result.insertedCount)
        assertEquals(0, result.replacedCount)
        assertEquals(8, result.readingCount)
        assertEquals("运动后", restored?.session?.scene)
        assertEquals("[\"心悸\"]", restored?.session?.symptomsJson)
        assertEquals(8, restored?.readings?.size)
    }

    @Test
    fun version3RoundTrip_preservesDiscardFirstStrategyAndStoredAverages() = runTest {
        val sessionId = "discard-first-roundtrip"
        val bytes = exportSingleSession(
            sessionId = sessionId,
            values = listOf(
                Triple(160, 100, 90),
                Triple(120, 80, 60),
                Triple(122, 82, 62)
            ),
            strategy = com.example.bloodpressurerecord.domain.model.AverageStrategy.DISCARD_FIRST
        )

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            assertEquals(
                "DISCARD_FIRST",
                workbook.getSheet("测量记录").getRow(1).getCell(15).stringCellValue
            )
        }
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()

        BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).importXlsx(ByteArrayInputStream(bytes))
        val restored = database.measurementSessionDao().getSessionWithReadings(sessionId)?.session

        assertEquals("DISCARD_FIRST", restored?.averageStrategy)
        assertEquals(121, restored?.avgSystolic)
        assertEquals(81, restored?.avgDiastolic)
        assertEquals(61, restored?.avgPulse)
    }

    @Test
    fun previewDoesNotWrite_andCommitUsesTheValidatedPreview() = runTest {
        val bytes = exportSingleSession(
            "preview-only",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()
        val service = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        )

        val preview = service.previewXlsx(ByteArrayInputStream(bytes))

        assertEquals(1, preview.validRecordCount)
        assertEquals(0, database.measurementSessionDao().countSessions())
        val result = service.commitImport(
            preview,
            BackupImportOptions(
                importMeasurements = true,
                restoreUserProfile = false,
                restoreDisplaySettings = false,
                restoreReminderSettings = false
            )
        )

        assertEquals(1, result.insertedCount)
        assertEquals(1, database.measurementSessionDao().countSessions())
    }

    @Test
    fun preview_skipsMeasurementBeyondFutureToleranceWithoutWriting() = runTest {
        val bytes = exportSingleSession(
            "future-import",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()
        val future = mutateWorkbook(bytes) { workbook ->
            workbook.getSheet("测量记录").getRow(1).getCell(1).setCellValue(
                LocalDateTime.now()
                    .plusMinutes(5)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            )
        }

        val preview = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).previewXlsx(ByteArrayInputStream(future))

        assertEquals(0, preview.validRecordCount)
        assertEquals(1, preview.skippedCount)
        assertEquals(BackupImportErrorCode.FUTURE_MEASUREMENT_TIME, preview.errors.single().code)
        assertEquals(0, database.measurementSessionDao().countSessions())
    }

    @Test
    fun import_recalculatesTamperedAverageAndRiskFromRawReadings() = runTest {
        val sessionId = "tampered-derived"
        val bytes = exportSingleSession(
            sessionId,
            listOf(
                Triple(190, 90, 70),
                Triple(110, 70, 72)
            )
        )
        val tampered = mutateWorkbook(bytes) { workbook ->
            workbook.getSheet("测量记录").getRow(1).apply {
                getCell(5).setCellValue(999.0)
                getCell(6).setCellValue(199.0)
                getCell(8).setCellValue("NORMAL")
                getCell(9).setCellValue(false)
            }
        }
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()

        val result = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).importXlsx(ByteArrayInputStream(tampered))
        val restored = database.measurementSessionDao().getSessionWithReadings(sessionId)

        assertEquals(1, result.correctedCount)
        assertEquals(150, restored?.session?.avgSystolic)
        assertEquals(80, restored?.session?.avgDiastolic)
        assertTrue(restored?.session?.containsHighRiskReading == true)
    }

    @Test
    fun import_skipsRecordWhenDiastolicIsNotLowerThanSystolic() = runTest {
        val bytes = exportSingleSession(
            "invalid-relation",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        val tampered = mutateWorkbook(bytes) { workbook ->
            workbook.getSheet("原始读数").getRow(1).apply {
                getCell(3).setCellValue(getCell(2).numericCellValue)
            }
        }
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()

        val result = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).importXlsx(ByteArrayInputStream(tampered))

        assertEquals(1, result.skippedCount)
        assertEquals(1, result.errorCount)
        assertEquals(0, database.measurementSessionDao().countSessions())
    }

    @Test
    fun import_preservesHistoricalRecordWithOneReading() = runTest {
        val bytes = exportSingleSession(
            "too-few",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        val tampered = mutateWorkbook(bytes) { workbook ->
            val sheet = workbook.getSheet("原始读数")
            sheet.removeRow(sheet.getRow(2))
        }
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()

        val result = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        ).importXlsx(ByteArrayInputStream(tampered))

        assertEquals(0, result.skippedCount)
        assertEquals(1, result.insertedCount)
        assertEquals(1, database.measurementSessionDao().countReadings())
    }

    @Test
    fun importingSameBackupTwiceIsIdempotent() = runTest {
        val bytes = exportSingleSession(
            "idempotent",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()
        val service = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        )

        val first = service.importXlsx(ByteArrayInputStream(bytes))
        val second = service.importXlsx(ByteArrayInputStream(bytes))

        assertEquals(1, first.insertedCount)
        assertEquals(1, second.replacedCount)
        assertEquals(1, database.measurementSessionDao().countSessions())
        assertEquals(2, database.measurementSessionDao().countReadings())
    }

    @Test
    fun version4_roundTripsMedicationTimesAndIntakeLogsIdempotently() = runTest {
        val dao = database.medicationDao()
        val medicationId = dao.insertMedicationWithTimes(
            MedicationEntity(
                name = "降压药",
                dosage = "1片",
                enabled = false,
                createdAt = 1_777_777L
            ),
            listOf("08:00", "20:00")
        )
        val morning = dao.getTimesForMedication(medicationId).first { it.timeText == "08:00" }
        dao.insertLog(
            MedicationIntakeLogEntity(
                medicationId = medicationId,
                timeId = morning.id,
                epochDay = 20_000,
                takenAt = 1_800_000L
            )
        )
        val bytes = exportCurrentDatabase()
        dao.deleteAllLogs()
        dao.deleteAllTimes()
        dao.deleteAllMedications()

        val service = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        )
        service.importXlsx(ByteArrayInputStream(bytes))
        service.importXlsx(ByteArrayInputStream(bytes))

        val restored = dao.getMedicationsWithTimes().single()
        assertEquals("降压药", restored.medication.name)
        assertFalse(restored.medication.enabled)
        assertEquals(listOf("08:00", "20:00"), restored.times.map { it.timeText }.sorted())
        assertEquals(1, dao.getLogsForDay(20_000).size)
    }

    @Test
    fun duplicateRecordIdRejectsWholeFileBeforeWriting() = runTest {
        val bytes = exportSingleSession(
            "duplicate",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        val duplicated = mutateWorkbook(bytes) { workbook ->
            val sheet = workbook.getSheet("测量记录")
            val source = sheet.getRow(1)
            val target = sheet.createRow(2)
            val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
            for (index in 0 until source.lastCellNum) {
                target.createCell(index).setCellValue(
                    dataFormatter.formatCellValue(source.getCell(index))
                )
            }
        }
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()

        try {
            BackupImportService(
                database,
                AppSettingsStore(ApplicationProvider.getApplicationContext())
            ).importXlsx(ByteArrayInputStream(duplicated))
            fail("duplicate record_id should fail")
        } catch (expected: BackupFormatException) {
            assertTrue(expected.message.orEmpty().contains("重复"))
        }
        assertEquals(0, database.measurementSessionDao().countSessions())
    }

    @Test
    fun emptyAndCorruptFilesFailWithoutWriting() = runTest {
        val service = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext())
        )
        listOf(byteArrayOf(), "not-xlsx".toByteArray()).forEach { bytes ->
            try {
                service.importXlsx(ByteArrayInputStream(bytes))
                fail("invalid file should fail")
            } catch (_: BackupFormatException) {
                // expected
            }
        }
        assertEquals(0, database.measurementSessionDao().countSessions())
    }

    @Test
    fun exceptionDuringImportRollsBackWholeRoomTransaction() = runTest {
        val bytes = exportTwoSessions()
        database.measurementSessionDao().deleteAllReadings()
        database.measurementSessionDao().deleteAllSessions()
        val service = BackupImportService(
            database,
            AppSettingsStore(ApplicationProvider.getApplicationContext()),
            beforeRecordWrite = { index -> if (index == 1) error("injected failure") }
        )

        try {
            service.importXlsx(ByteArrayInputStream(bytes))
            fail("injected failure should escape")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(0, database.measurementSessionDao().countSessions())
        assertEquals(0, database.measurementSessionDao().countReadings())
    }

    @Test
    fun importsOneThousandAndFiveThousandRecordsInBatches() = runTest {
        listOf(1_000, 5_000).forEach { count ->
            val bytes = buildLargeBackup(count)
            database.measurementSessionDao().deleteAllReadings()
            database.measurementSessionDao().deleteAllSessions()

            val result = BackupImportService(
                database,
                AppSettingsStore(ApplicationProvider.getApplicationContext())
            ).importXlsx(ByteArrayInputStream(bytes))

            assertEquals(count, result.insertedCount)
            assertEquals(count, database.measurementSessionDao().countSessions())
            assertEquals(count * 2, database.measurementSessionDao().countReadings())
            database.measurementSessionDao().deleteAllReadings()
            database.measurementSessionDao().deleteAllSessions()
        }
    }

    private suspend fun exportSingleSession(
        sessionId: String,
        values: List<Triple<Int, Int, Int>>,
        strategy: com.example.bloodpressurerecord.domain.model.AverageStrategy =
            com.example.bloodpressurerecord.domain.model.AverageStrategy.ALL
    ): ByteArray {
        val averages = com.example.bloodpressurerecord.domain.calculator.AverageCalculator.calculate(
            values.map {
                com.example.bloodpressurerecord.domain.model.ReadingValue(it.first, it.second, it.third)
            },
            strategy
        )
        database.measurementSessionDao().insertSessionWithReadings(
            MeasurementSessionEntity(
                id = sessionId,
                measuredAt = 1_774_406_600_000L,
                scene = "晨起",
                note = null,
                symptomsJson = null,
                avgSystolic = averages.avgSystolic,
                avgDiastolic = averages.avgDiastolic,
                avgPulse = averages.avgPulse,
                averageStrategy = strategy.name,
                category = com.example.bloodpressurerecord.domain.calculator.CategoryCalculator
                    .calculate(averages.avgSystolic, averages.avgDiastolic).name,
                containsHighRiskReading = false,
                createdAt = 1_774_406_600_000L,
                updatedAt = 1_774_406_600_000L
            ),
            values.mapIndexed { index, value ->
                MeasurementReadingEntity(
                    id = "$sessionId-$index",
                    sessionId = sessionId,
                    orderIndex = index + 1,
                    systolic = value.first,
                    diastolic = value.second,
                    pulse = value.third
                )
            }
        )
        return exportCurrentDatabase()
    }

    private suspend fun exportTwoSessions(): ByteArray {
        exportSingleSession(
            "rollback-1",
            listOf(Triple(120, 80, 70), Triple(122, 82, 72))
        )
        exportSingleSession(
            "rollback-2",
            listOf(Triple(124, 84, 74), Triple(126, 86, 76))
        )
        return exportCurrentDatabase()
    }

    private suspend fun buildLargeBackup(count: Int): ByteArray {
        val dao = database.measurementSessionDao()
        (0 until count).chunked(250).forEach { indexes ->
            dao.insertSessions(
                indexes.map { index ->
                    MeasurementSessionEntity(
                        id = "bulk-$index",
                        measuredAt = 1_700_000_000_000L + index * 60_000L,
                        scene = "批量测试",
                        note = "record-$index",
                        symptomsJson = null,
                        avgSystolic = 121,
                        avgDiastolic = 81,
                        avgPulse = 71,
                        category = "NORMAL",
                        containsHighRiskReading = false,
                        createdAt = 1_700_000_000_000L + index * 60_000L,
                        updatedAt = 1_700_000_000_000L + index * 60_000L
                    )
                }
            )
            dao.insertReadings(
                indexes.flatMap { index ->
                    listOf(
                        MeasurementReadingEntity(
                            id = "bulk-$index-1",
                            sessionId = "bulk-$index",
                            orderIndex = 1,
                            systolic = 120,
                            diastolic = 80,
                            pulse = 70
                        ),
                        MeasurementReadingEntity(
                            id = "bulk-$index-2",
                            sessionId = "bulk-$index",
                            orderIndex = 2,
                            systolic = 122,
                            diastolic = 82,
                            pulse = 72
                        )
                    )
                }
            )
        }
        return exportCurrentDatabase()
    }

    private suspend fun exportCurrentDatabase(): ByteArray {
        val settingsStore = AppSettingsStore(ApplicationProvider.getApplicationContext())
        val payload = BackupExportService(
            sessionDao = database.measurementSessionDao(),
            measurementDao = database.measurementDao(),
            userProfileDao = database.userProfileDao(),
            appSettingsStore = settingsStore,
            medicationDao = database.medicationDao()
        ).buildPayload("家庭血压记录", "test")
        return ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(payload, output)
            output.toByteArray()
        }
    }

    private fun mutateWorkbook(
        source: ByteArray,
        block: (XSSFWorkbook) -> Unit
    ): ByteArray {
        return XSSFWorkbook(ByteArrayInputStream(source)).use { workbook ->
            block(workbook)
            ByteArrayOutputStream().use { output ->
                workbook.write(output)
                output.toByteArray()
            }
        }
    }
}
