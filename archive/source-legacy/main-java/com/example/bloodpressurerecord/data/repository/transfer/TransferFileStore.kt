package com.example.bloodpressurerecord.data.repository.transfer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TransferFileStore(
    private val context: Context
) {
    private val exportDir: File = File(context.filesDir, "backup")
    private val importDir: File = File(context.filesDir, "import")
    private val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun createExportFile(extension: String): File {
        exportDir.mkdirs()
        val stamp = LocalDateTime.now().format(stampFormatter)
        return File(exportDir, "bp_export_$stamp.$extension")
    }

    fun findImportFile(extension: String): File? {
        val fromImportDir = importDir.takeIf { it.exists() }?.listFiles()
            ?.filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
        if (fromImportDir != null) return fromImportDir
        return exportDir.takeIf { it.exists() }?.listFiles()
            ?.filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
    }

    suspend fun stageImportFromUri(
        source: Uri,
        fileNameHint: String?
    ): Result<File> = runCatching {
        importDir.mkdirs()
        val resolvedName = fileNameHint?.takeIf { it.isNotBlank() }
            ?: "import_${System.currentTimeMillis()}"
        val target = File(importDir, resolvedName)
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: error("无法读取所选文件")
        }
        target
    }

    fun exportDirectoryPath(): String = exportDir.absolutePath
}
