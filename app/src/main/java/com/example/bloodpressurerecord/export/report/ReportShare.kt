package com.example.bloodpressurerecord.export.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportShare(private val context: Context, private val cache: ReportCache) {
    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "血压报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享血压报告").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        cache.markShared(file)
    }

    /** Called on the background dispatcher by the export flow. */
    suspend fun save(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Unable to open report destination")
        output.use { destination -> file.inputStream().use { source -> source.copyTo(destination) } }
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
