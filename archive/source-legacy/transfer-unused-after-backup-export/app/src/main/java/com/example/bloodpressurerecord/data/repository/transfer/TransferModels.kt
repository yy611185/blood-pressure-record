package com.example.bloodpressurerecord.data.repository.transfer

data class ExportResult(
    val format: String,
    val filePath: String,
    val rowCount: Int
)

data class ImportErrorLine(
    val lineNumber: Int,
    val reason: String
)

data class ImportResult(
    val format: String,
    val filePath: String,
    val totalRows: Int,
    val successRows: Int,
    val failedRows: Int,
    val errors: List<ImportErrorLine>
)
