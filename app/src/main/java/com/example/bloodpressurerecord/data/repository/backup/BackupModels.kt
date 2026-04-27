package com.example.bloodpressurerecord.data.repository.backup

data class BackupReadingValue(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?
)

data class BackupMeasurementRow(
    val recordId: String,
    val measuredAt: String,
    val date: String,
    val time: String,
    val groupCount: Int,
    val readings: List<BackupReadingValue>,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val level: String,
    val highAlert: Boolean,
    val note: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class BackupUserProfileItem(
    val key: String,
    val value: String?
)

data class BackupMetaItem(
    val key: String,
    val value: String
)

data class BackupExportDiagnostics(
    val sessionCount: Int = 0,
    val readingCount: Int = 0,
    val legacyBpMeasurementCount: Int = 0,
    val legacyBloodPressureRecordCount: Int = 0
) {
    val totalReadableRecords: Int
        get() = sessionCount + legacyBpMeasurementCount + legacyBloodPressureRecordCount

    fun toUserMessage(): String {
        return "诊断信息：新记录 $sessionCount 条，原始读数 $readingCount 组，旧版单次记录 $legacyBpMeasurementCount 条，旧版历史记录 $legacyBloodPressureRecordCount 条。"
    }
}

data class BackupExportPayload(
    val instructions: List<Pair<String, String>>,
    val measurements: List<BackupMeasurementRow>,
    val userProfile: List<BackupUserProfileItem>,
    val meta: List<BackupMetaItem>,
    val diagnostics: BackupExportDiagnostics = BackupExportDiagnostics()
)
