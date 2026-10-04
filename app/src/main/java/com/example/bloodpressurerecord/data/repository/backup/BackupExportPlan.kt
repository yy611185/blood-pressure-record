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
            val chunks = rows.chunked(BackupImportLimits.MAX_RECORDS).ifEmpty { listOf(emptyList()) }
            val setId = UUID.randomUUID().toString()
            val readingsById = payload.readings.groupBy { it.recordId }
            return BackupExportPlan(chunks.mapIndexed { index, measurements ->
                payload.copy(
                    measurements = measurements,
                    readings = measurements.flatMap { readingsById[it.recordId].orEmpty() },
                    userProfile = if (index == 0) payload.userProfile else emptyList(),
                    medications = if (index == 0) payload.medications else emptyList(),
                    medicationTimes = if (index == 0) payload.medicationTimes else emptyList(),
                    medicationLogs = if (index == 0) payload.medicationLogs else emptyList(),
                    meta = payload.meta.filterNot { it.key == "total_records" } + listOf(
                        BackupMetaItem("total_records", measurements.size.toString()),
                        BackupMetaItem("backup_set_id", setId),
                        BackupMetaItem("backup_volume_index", (index + 1).toString()),
                        BackupMetaItem("backup_volume_count", chunks.size.toString()),
                        BackupMetaItem("backup_total_records", rows.size.toString())
                    ),
                    instructions = payload.instructions + (
                        "分卷恢复" to "第 ${index + 1}/${chunks.size} 卷；请保存并逐卷导入全部文件。资料、设置和用药数据仅在第一卷。"
                    )
                )
            })
        }
    }
}
