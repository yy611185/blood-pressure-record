package com.example.bloodpressurerecord.export.report

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import com.example.bloodpressurerecord.domain.report.ReportLayoutPlan
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the exact preview plan alive independently of the preview's temporary PDF file. */
class ReportPrintAdapter(
    private val plan: ReportLayoutPlan,
    private val renderer: PdfReportRenderer,
    private val cache: ReportCache
) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var writeJob: Job? = null
    private val documentName: String = "血压报告_${plan.report.period.startDate.format(DateTimeFormatter.BASIC_ISO_DATE)}-" +
        "${plan.report.period.endDate.format(DateTimeFormatter.BASIC_ISO_DATE)}.pdf"

    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(documentInfo(), oldAttributes != newAttributes)
    }

    internal fun documentInfo(): PrintDocumentInfo = PrintDocumentInfo.Builder(documentName)
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
        .setPageCount(plan.pageCount)
        .build()

    override fun onWrite(
        pages: Array<out PageRange>, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal, callback: WriteResultCallback
    ) {
        writeJob?.cancel()
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }
        val selected = ReportPrintPages.select(pages, plan.pageCount)
        if (selected.isEmpty()) {
            callback.onWriteFailed("所选页码没有报告内容")
            return
        }
        val job = scope.launch {
            try {
                val written = writePages(pages, destination, cancellationSignal)
                if (cancellationSignal.isCanceled) callback.onWriteCancelled()
                else callback.onWriteFinished(written)
            } catch (_: CancellationException) {
                callback.onWriteCancelled()
            } catch (_: Exception) {
                callback.onWriteFailed("无法准备打印文件，请重试")
            } finally {
                cancellationSignal.setOnCancelListener(null)
            }
        }
        writeJob = job
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    /** Shared output boundary exercised directly because the framework owns callback constructors. */
    internal suspend fun writePages(
        pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal
    ): Array<PageRange> = withContext(Dispatchers.IO) {
        val taskContext = currentCoroutineContext()
        val cancelled = { cancellationSignal.isCanceled || !taskContext.isActive }
        if (cancelled()) throw CancellationException("Print cancelled")
        val selected = ReportPrintPages.select(pages, plan.pageCount)
        require(selected.isNotEmpty()) { "No report pages selected" }
        var temporary: File? = null
        try {
            val file = cache.create(plan.report.period).also { temporary = it }
            renderer.render(plan, file, selected, cancelled)
            // A duplicate descriptor prevents closing the framework-owned destination.
            ParcelFileDescriptor.AutoCloseOutputStream(
                ParcelFileDescriptor.dup(destination.fileDescriptor)
            ).use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(ReportTheme.PRINT_COPY_BUFFER_BYTES)
                    while (true) {
                        if (cancelled()) throw CancellationException("Print cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    if (cancelled()) throw CancellationException("Print cancelled")
                    output.flush()
                }
            }
            ReportPrintPages.ranges(selected)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                temporary?.let { cache.discard(it) }
            }
        }
    }

    override fun onFinish() {
        scope.cancel()
        super.onFinish()
    }
}

/** Normalize overlapping printer requests to the actual zero-based pages of this snapshot. */
internal object ReportPrintPages {
    fun select(requested: Array<out PageRange>, pageCount: Int): List<Int> =
        (0 until pageCount).filter { index -> requested.any { index in it.start..it.end } }

    fun ranges(selected: List<Int>): Array<PageRange> {
        if (selected.isEmpty()) return emptyArray()
        val result = ArrayList<PageRange>()
        var start = selected.first()
        var end = start
        selected.drop(1).forEach { page ->
            if (page == end + 1) end = page
            else {
                result += PageRange(start, end)
                start = page
                end = page
            }
        }
        result += PageRange(start, end)
        return result.toTypedArray()
    }
}
