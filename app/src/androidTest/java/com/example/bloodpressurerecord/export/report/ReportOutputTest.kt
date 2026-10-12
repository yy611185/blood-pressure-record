package com.example.bloodpressurerecord.export.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.domain.report.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportOutputTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = PdfReportRenderer("家庭血压记录")

    @Test fun summaryImage_isA4At150DpiAndContainsVisibleContent() {
        val bitmap = renderer.summaryBitmap(plan(30))
        try {
            assertEquals(1240, bitmap.width)
            assertEquals(1754, bitmap.height)
            assertEquals(150, bitmap.density)
            var ink = 0
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.red(pixel) < 220 || Color.green(pixel) < 220 || Color.blue(pixel) < 220) ink++
                }
            }
            assertTrue(ink > 1000)
        } finally { bitmap.recycle() }
    }

    @Test fun missingSafDestination_returnsFailureAndPreservesOriginalPdf() = runBlocking {
        val cache = ReportCache(File(context.cacheDir, "reports"))
        val plan = plan(30)
        val file = cache.create(plan.report.period)
        try {
            renderer.render(plan, file)
            val original = file.readBytes()
            val uri = Uri.parse("content://com.example.bloodpressure.missing.provider/report.pdf")
            assertTrue(runCatching { ReportShare(context, cache).save(file, uri) }.isFailure)
            assertArrayEquals(original, file.readBytes())
            assertTrue(runCatching {
                ReportShare(context, cache).saveSummaryImage(plan, renderer, uri)
            }.isFailure)
            assertArrayEquals(original, file.readBytes())
        } finally { cache.discard(file) }
    }

    @Test fun imageSave_writesPngAndKeepsThePdfUntouched() = runBlocking {
        val cache = ReportCache(File(context.cacheDir, "reports"))
        val plan = plan(30)
        val file = cache.create(plan.report.period)
        val image = File(file.parentFile, ReportShare(context, cache).summaryImageName(plan))
        try {
            renderer.render(plan, file)
            val original = file.readBytes()
            ReportShare(context, cache).saveSummaryImage(plan, renderer, Uri.fromFile(image))
            assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), image.readBytes().take(8).toByteArray())
            val bitmap = requireNotNull(BitmapFactory.decodeFile(image.absolutePath))
            try {
                assertEquals(1240, bitmap.width)
                assertEquals(1754, bitmap.height)
            } finally { bitmap.recycle() }
            assertArrayEquals(original, file.readBytes())
            assertFalse(image.name.contains("姓名"))
        } finally { cache.discard(file) }
    }

    @Test fun printLayout_reportsOriginalCountAndAnonymousDocumentName() {
        val plan = plan(90)
        val cache = ReportCache(File(context.cacheDir, "reports"))
        val adapter = ReportPrintAdapter(plan, renderer, cache)
        try {
            val info = adapter.documentInfo()
            assertEquals(plan.pageCount, info.pageCount)
            assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, info.contentType)
            assertEquals("血压报告_20260715-20261012.pdf", info.name)
        } finally { adapter.onFinish() }
    }

    @Test fun printSelectedPages_returnsActualRangesAndPreservesOriginalPageContent() = runBlocking {
        val plan = plan(90)
        val directory = File(context.cacheDir, "print-test-${System.nanoTime()}").apply { mkdirs() }
        val cache = ReportCache(File(directory, "reports"))
        val adapter = ReportPrintAdapter(plan, renderer, cache)
        val destination = File(directory, "selected.pdf")
        val expected = File(directory, "expected.pdf")
        val descriptor = ParcelFileDescriptor.open(destination,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
        try {
            val written = adapter.writePages(
                arrayOf(PageRange(1, 1), PageRange(3, 10), PageRange(3, 3)), descriptor, CancellationSignal()
            )
            assertArrayEquals(arrayOf(PageRange(1, 1), PageRange(3, 3)), written)
            renderer.render(plan, expected, listOf(1, 3))
            assertEquals(2, pdfPageCount(destination))
            assertArrayEquals(renderPixels(expected, 0), renderPixels(destination, 0))
            assertArrayEquals(renderPixels(expected, 1), renderPixels(destination, 1))
            assertTrue(File(directory, "reports").listFiles().orEmpty().isEmpty())
        } finally {
            adapter.onFinish()
            descriptor.close()
            directory.deleteRecursively()
        }
    }

    @Test fun cancelledPrint_neverWritesDestination() = runBlocking {
        val directory = File(context.cacheDir, "cancel-print-${System.nanoTime()}").apply { mkdirs() }
        val adapter = ReportPrintAdapter(plan(30), renderer, ReportCache(File(directory, "reports")))
        val file = File(directory, "cancelled.pdf")
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)
        try {
            val failure = runCatching {
                adapter.writePages(arrayOf(PageRange.ALL_PAGES), descriptor, CancellationSignal().apply { cancel() })
            }.exceptionOrNull()
            assertTrue(failure is java.util.concurrent.CancellationException)
            assertEquals(0L, file.length())
            assertTrue(File(directory, "reports").listFiles().orEmpty().isEmpty())
        } finally {
            adapter.onFinish()
            descriptor.close()
            directory.deleteRecursively()
        }
    }

    @Test fun cancellationDuringPageRendering_removesPartialPdf() {
        val file = File(context.cacheDir, "cancel-render-${System.nanoTime()}.pdf")
        var pageChecks = 0
        val failure = runCatching {
            renderer.render(plan(90), file, isCancelled = { ++pageChecks >= 2 })
        }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.CancellationException)
        assertFalse(file.exists())
        assertFalse(File(file.parentFile, "${file.name}.partial").exists())
    }

    private fun plan(days: Int): ReportLayoutPlan {
        val last = LocalDate.of(2026, 10, 12)
        val zone = ZoneId.of("Asia/Taipei")
        val period = ReportPeriod(last.minusDays(days.toLong() - 1), last, zone)
        val snapshot = ReportSnapshot(measurements = (0 until days).map { index ->
            ReportMeasurement("day-$index", period.startDate.plusDays(index.toLong())
                .atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), 130 + index % 20, 80, 72)
        })
        val report = requireNotNull(ReportBuilder.build(snapshot, period, ReportOptions(), period.endExclusive - 1))
        return ReportLayoutPlanner.plan(report, ReportTheme.layoutSpec, renderer.textMetrics())
    }

    private fun pdfPageCount(file: File): Int =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }

    private fun renderPixels(file: File, index: Int): IntArray =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    IntArray(bitmap.width * bitmap.height).also {
                        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    }
                } finally { bitmap.recycle() }
            }
        }
}
