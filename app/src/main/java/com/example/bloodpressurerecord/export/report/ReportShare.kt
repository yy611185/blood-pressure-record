package com.example.bloodpressurerecord.export.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlan
import com.example.bloodpressurerecord.domain.report.ReportPolicy
import java.time.format.DateTimeFormatter

class ReportShare(private val context: Context, private val cache: ReportCache) {
    fun share(file: File, title: String = ReportPolicy.TITLE) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享$title").apply {
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

    /** Invoke from an Activity on the main thread after the user has reviewed this plan. */
    fun print(activity: Activity, plan: ReportLayoutPlan, renderer: PdfReportRenderer): PrintJob {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        return manager.print(plan.report.template.title, ReportPrintAdapter(plan, renderer, cache), attributes)
    }

    suspend fun saveSummaryImage(plan: ReportLayoutPlan, renderer: PdfReportRenderer, uri: Uri) =
        withContext(Dispatchers.IO) {
            val bitmap = renderer.summaryBitmap(plan)
            try {
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Unable to open image destination")
                output.use {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) {
                        throw IOException("Unable to write report image")
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }

    fun summaryImageName(plan: ReportLayoutPlan): String {
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        return "血压报告_${plan.report.period.startDate.format(formatter)}-" +
            "${plan.report.period.endDate.format(formatter)}.png"
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val PNG_MIME_TYPE = "image/png"
        private const val PNG_QUALITY = 100
    }
}
