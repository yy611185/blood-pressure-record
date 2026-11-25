package com.example.bloodpressurerecord.data.repository.backup

import java.util.UUID

/** 同一份数据库快照；分卷边界只落在完整 Session 之间。 */
data class BackupExportPlan(val volumes: List<BackupExportPayload>) {
    val totalRecords: Int get() = volumes.sumOf { it.measurements.size }
    private val savedVolumeIndices = mutableSetOf<Int>()

    /** 只在每一卷的写入均成功后确认整套备份完成，调用顺序不影响判断。 */
    @Synchronized
    internal fun markVolumeSaved(index: Int): Boolean {
        require(index in volumes.indices)
        savedVolumeIndices += index
        return savedVolumeIndices.size == volumes.size
    }

    companion object {
        fun fromPayload(payload: BackupExportPayload): BackupExportPlan {
            val rows = payload.measurements.sortedWith(
                compareBy<BackupMeasurementRow> { it.measuredAt }.thenBy { it.recordId }
            )
            val chunks = rows.chunked(BackupImportLimits.MAX_RECORDS)
            val medicationVolumes = splitMedications(payload)
            val volumeCount = maxOf(1, chunks.size, medicationVolumes.size)
            val setId = UUID.randomUUID().toString()
            val readingsById = payload.readings.groupBy { it.recordId }
            return BackupExportPlan(List(volumeCount) { index ->
                val measurements = chunks.getOrNull(index).orEmpty()
                val medicationVolume = medicationVolumes.getOrNull(index)
                payload.copy(
                    measurements = measurements,
                    readings = measurements.flatMap { readingsById[it.recordId].orEmpty() },
                    userProfile = if (index == 0) payload.userProfile else emptyList(),
                    medications = medicationVolume?.medications?.values?.toList().orEmpty(),
                    medicationTimes = medicationVolume?.times?.values?.toList().orEmpty(),
                    medicationLogs = medicationVolume?.logs.orEmpty(),
                    meta = payload.meta.filterNot { it.key == "total_records" } + listOf(
                        BackupMetaItem("total_records", measurements.size.toString()),
                        BackupMetaItem("backup_set_id", setId),
                        BackupMetaItem("backup_volume_index", (index + 1).toString()),
                        BackupMetaItem("backup_volume_count", volumeCount.toString()),
                        BackupMetaItem("backup_total_records", rows.size.toString())
                    ),
                    instructions = payload.instructions + (
                        "分卷恢复" to "第 ${index + 1}/$volumeCount 卷；请逐卷导入，含药品的卷需勾选恢复用药数据。用户资料和设置只在第一卷。"
                    )
                )
            })
        }

        private fun splitMedications(payload: BackupExportPayload): List<MedicationVolume> {
            val volumes = mutableListOf<MedicationVolume>()
            val timesByMedication = payload.medicationTimes.groupBy { it.medicationBackupId }
            val logsByTime = payload.medicationLogs.groupBy { it.timeBackupId }

            fun append(medication: BackupMedicationRow, time: BackupMedicationTimeRow?, log: BackupMedicationLogRow?) {
                var volume = volumes.lastOrNull()
                if (volume == null || volume.isFullFor(medication, time, log)) {
                    volume = MedicationVolume()
                    volumes.add(volume)
                }
                volume.medications[medication.backupId] = medication
                if (time != null) volume.times[time.backupId] = time
                if (log != null) volume.logs.add(log)
            }

            for (medication in payload.medications) {
                val times = timesByMedication[medication.backupId].orEmpty()
                if (times.isEmpty()) append(medication, null, null)
                for (time in times) {
                    val logs = logsByTime[time.backupId].orEmpty()
                    if (logs.isEmpty()) append(medication, time, null)
                    for (log in logs) append(medication, time, log)
                }
            }
            return volumes
        }
    }

    private class MedicationVolume {
        val medications = linkedMapOf<String, BackupMedicationRow>()
        val times = linkedMapOf<String, BackupMedicationTimeRow>()
        val logs = mutableListOf<BackupMedicationLogRow>()

        fun isFullFor(medication: BackupMedicationRow, time: BackupMedicationTimeRow?, log: BackupMedicationLogRow?): Boolean =
            (medications.size == BackupImportLimits.MAX_MEDICATIONS && medication.backupId !in medications) ||
                (time != null && times.size == BackupImportLimits.MAX_MEDICATION_TIMES && time.backupId !in times) ||
                (log != null && logs.size == BackupImportLimits.MAX_MEDICATION_LOGS)
    }
}
