package com.example.bloodpressurerecord.data.repository.transfer

import android.net.Uri

interface ImportRepository {
    suspend fun importCsv(): Result<ImportResult>
    suspend fun importXlsx(): Result<ImportResult>

    // 预留系统文件选择器: 先将 Uri 内容复制到应用私有 import 目录
    suspend fun stageImportFromUri(uri: Uri, fileNameHint: String? = null): Result<String>
}
