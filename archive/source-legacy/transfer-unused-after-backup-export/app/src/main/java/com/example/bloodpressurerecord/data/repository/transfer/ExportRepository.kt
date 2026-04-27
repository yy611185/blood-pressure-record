package com.example.bloodpressurerecord.data.repository.transfer

interface ExportRepository {
    suspend fun exportCsv(): Result<ExportResult>
    suspend fun exportXlsx(): Result<ExportResult>
}
