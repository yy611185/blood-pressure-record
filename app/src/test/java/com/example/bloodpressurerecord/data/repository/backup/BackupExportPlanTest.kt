package com.example.bloodpressurerecord.data.repository.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupExportPlanTest {
    @Test
    fun lastVolumeAloneDoesNotMarkCompleteAndRetriesDoNotCountTwice() {
        val plan = BackupExportPlan.fromPayload(payload(10_001))
        assertFalse(plan.markVolumeSaved(2))
        assertFalse(plan.markVolumeSaved(2))
        assertFalse(plan.markVolumeSaved(0))
        assertTrue(plan.markVolumeSaved(1))
    }

    @Test
    fun splitKeepsCompleteSessionsAndIncludesSharedDataOnlyInFirstVolume() {
        val payload = payload(10_001)
        val plan = BackupExportPlan.fromPayload(payload)
        assertEquals(listOf(5_000, 5_000, 1), plan.volumes.map { it.measurements.size })
        assertEquals(10_001, plan.totalRecords)
        assertEquals(payload.measurements.map { it.recordId }.toSet(),
            plan.volumes.flatMap { it.measurements }.map { it.recordId }.toSet())
        assertEquals(20_002, plan.volumes.sumOf { it.readings.size })
        assertEquals(payload.userProfile, plan.volumes.first().userProfile)
        assertEquals(payload.medications, plan.volumes.first().medications)
        assertTrue(plan.volumes.drop(1).all { it.userProfile.isEmpty() && it.medications.isEmpty() &&
            it.medicationTimes.isEmpty() && it.medicationLogs.isEmpty() })
        assertEquals(1, plan.volumes.map { volume ->
            volume.meta.first { it.key == "backup_set_id" }.value
        }.distinct().size)
        // 同一时间的边界记录以 record_id 排序，输入顺序不影响各卷归属。
        assertEquals(plan.volumes.map { it.measurements },
            BackupExportPlan.fromPayload(payload.copy(measurements = payload.measurements.reversed()))
                .volumes.map { it.measurements })
    }

    @Test
    fun splitMedicationsKeepsEachVolumeReadableWithoutItsPredecessor() {
        val sample = payload(0)
        val medicines = (0..BackupImportLimits.MAX_MEDICATIONS).map { index ->
            BackupMedicationRow("med-$index", "药品 $index", "1", true, index.toLong())
        }
        val lastTime = BackupMedicationTimeRow("last-time", medicines.last().backupId, "08:30")
        val log = BackupMedicationLogRow(lastTime.backupId, 20_000, 1_795_432_167_891)
        val plan = BackupExportPlan.fromPayload(sample.copy(
            medications = medicines, medicationTimes = listOf(lastTime), medicationLogs = listOf(log)
        ))
        assertEquals(listOf(1_000, 1), plan.volumes.map { it.medications.size })

        val reader = BackupFileReader()
        val restored = plan.volumes.map { volume ->
            val bytes = ByteArrayOutputStream().use { output ->
                BackupFileWriter().writeXlsx(volume, output)
                output.toByteArray()
            }
            reader.validateExportArchive(bytes)
            reader.readXlsx(bytes.inputStream())
        }
        assertEquals(medicines, restored.flatMap { it.medications })
        assertEquals(listOf(lastTime), restored.last().medicationTimes)
        assertEquals(listOf(log), restored.last().medicationLogs)
    }

    @Test
    fun splitTimesAndLogsRepeatsOnlyTheirParentsAndKeepsEveryLogOnce() {
        val sample = payload(0)
        val medicines = (0..10).map { sample.medications.single().copy(backupId = "med-$it", createdAt = it.toLong()) }
        val times = (0..BackupImportLimits.MAX_MEDICATION_TIMES).map { index ->
            val minute = index % 1_000
            val hhmm = (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')
            BackupMedicationTimeRow("time-$index", medicines[index / 1_000].backupId, hhmm)
        }
        val timesPlan = BackupExportPlan.fromPayload(sample.copy(
            medications = medicines, medicationTimes = times, medicationLogs = emptyList()
        ))
        assertEquals(listOf(10_000, 1), timesPlan.volumes.map { it.medicationTimes.size })
        assertEquals(times, timesPlan.volumes.flatMap { it.medicationTimes })
        assertEquals(listOf(medicines.last()), timesPlan.volumes.last().medications)

        val logs = (0..BackupImportLimits.MAX_MEDICATION_LOGS).map { day ->
            BackupMedicationLogRow("time", day.toLong(), 1_700_000_000_000L + day * 86_400_000L)
        }
        val logsPlan = BackupExportPlan.fromPayload(sample.copy(medicationLogs = logs))
        assertEquals(listOf(50_000, 1), logsPlan.volumes.map { it.medicationLogs.size })
        assertEquals(logs, logsPlan.volumes.flatMap { it.medicationLogs })
        assertTrue(logsPlan.volumes.all { volume ->
            volume.medications == sample.medications && volume.medicationTimes == sample.medicationTimes
        })
        assertTrue(logsPlan.volumes.last().userProfile.isEmpty())
    }

    @Test
    fun fiveThousandAndOneRecordsRoundTripEveryPlainAndEncryptedVolume() {
        val payload = payload(5_001)
        val plan = BackupExportPlan.fromPayload(payload)
        val restored = mutableListOf<BackupImportMeasurement>()
        plan.volumes.forEach { volume ->
            val bytes = ByteArrayOutputStream().use { output ->
                BackupFileWriter().writeXlsx(volume, output)
                output.toByteArray()
            }
            val plain = BackupFileReader().readXlsx(ByteArrayInputStream(bytes))
            val passphrase = "volume-test-password".toCharArray()
            val encrypted = BackupCrypto.encrypt(bytes, passphrase)
            assertTrue(encrypted.size <= BackupImportLimits.MAX_FILE_BYTES)
            val decrypted = BackupFileReader().readXlsx(ByteArrayInputStream(encrypted), passphrase)
            assertEquals(plain, decrypted)
            assertTrue(plain.measurements.size <= BackupImportLimits.MAX_RECORDS)
            restored += plain.measurements
        }
        assertEquals(5_001, restored.size)
        assertEquals(payload.measurements.map { it.recordId }.toSet(), restored.map { it.recordId }.toSet())
        assertTrue(restored.all { record -> record.readings.map { it.systolic to it.diastolic } ==
            listOf(120 to 80, 124 to 82) })
    }

    private fun payload(count: Int): BackupExportPayload {
        val measurements = (0 until count).map { index ->
            BackupMeasurementRow(
                recordId = "session-${index.toString().padStart(5, '0')}",
                measuredAt = "2026-01-01 08:00:00", date = "2026-01-01", time = "08:00",
                groupCount = 2,
                readings = listOf(BackupReadingValue(120, 80, 70), BackupReadingValue(124, 82, null)),
                avgSystolic = 122, avgDiastolic = 81, avgPulse = 70, level = "NORMAL",
                highAlert = false, scene = null, symptomsJson = null, note = null,
                createdAt = null, updatedAt = null
            )
        }
        return BackupExportPayload(
            instructions = emptyList(), measurements = measurements,
            readings = measurements.flatMap { record -> record.readings.mapIndexed { index, reading ->
                BackupReadingRow(record.recordId, index + 1, reading.systolic, reading.diastolic, reading.pulse)
            } },
            userProfile = listOf(BackupUserProfileItem("name", "test")),
            meta = listOf(BackupMetaItem("export_format_version", "6"),
                BackupMetaItem("timezone", "Asia/Taipei"), BackupMetaItem("total_records", count.toString())),
            medications = listOf(BackupMedicationRow("med", "test", "1", true, 1)),
            medicationTimes = listOf(BackupMedicationTimeRow("time", "med", "08:00")),
            medicationLogs = listOf(BackupMedicationLogRow("time", 20_000, 100))
        )
    }
}
