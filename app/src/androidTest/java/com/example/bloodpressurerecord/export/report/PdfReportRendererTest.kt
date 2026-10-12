package com.example.bloodpressurerecord.export.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bloodpressurerecord.domain.report.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReportRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Taipei")

    @Test fun fixedTemplateFixture_hasExpectedPageCountA4SizeAndVisibleInk() {
        val renderer = PdfReportRenderer("家庭血压记录")
        val report = requireNotNull(ReportBuilder.build(fixture(30), period(30), ReportOptions(includeIdentity = true), period(30).endExclusive - 1))
        val plan = ReportLayoutPlanner.plan(report, ReportTheme.layoutSpec, renderer.textMetrics())
        assertEquals(2, plan.pageCount)
        // Kept in app files for an adb pull and visual comparison with the supplied template.
        val output = File(context.filesDir, "report-layout-smoke.pdf")
        output.delete()
        renderer.render(plan, output)
        inspect(output, plan.pageCount)
    }

    @Test fun oneDayAndLargePeriod_renderAllPlannedPages() {
        val renderer = PdfReportRenderer("家庭血压记录")
        listOf(1, 90, 365).forEach { dayCount ->
            val period = period(dayCount)
            val report = requireNotNull(ReportBuilder.build(fixture(dayCount), period, ReportOptions(), period.endExclusive - 1))
            val plan = ReportLayoutPlanner.plan(report, ReportTheme.layoutSpec, renderer.textMetrics())
            val file = if (dayCount == 365) File(context.filesDir, "report-layout-365.pdf")
                else File(context.cacheDir, "render-$dayCount.pdf")
            file.delete()
            try {
                renderer.render(plan, file)
                inspect(file, plan.pageCount)
            } finally { if (dayCount != 365) file.delete() }
        }
    }

    @Test fun sevenDayTemplate_withMultipleMorningMeasurements_rendersSummaryDailyAndSupplement() {
        val renderer = PdfReportRenderer("家庭血压记录")
        val snapshot = fixture(7)
        val firstMorning = snapshot.measurements.first()
        val report = requireNotNull(ReportBuilder.build(
            snapshot.copy(measurements = snapshot.measurements + firstMorning.copy(
                id = "extra-morning", measuredAt = firstMorning.measuredAt + 30 * 60 * 1000L,
                systolic = 150, diastolic = 100
            )),
            period(7), ReportOptions(template = ReportTemplate.HOME_SEVEN_DAY),
            period(7).endExclusive - 1
        ))
        val plan = ReportLayoutPlanner.plan(report, ReportTheme.layoutSpec, renderer.textMetrics())
        assertEquals(3, plan.pageCount)
        assertEquals(7, (plan.pages[1] as ReportPage.DailyDetail).rows.size)
        val output = File(context.filesDir, "report-layout-seven-day.pdf")
        output.delete()
        renderer.render(plan, output)
        inspect(output, plan.pageCount)
    }

    @Test fun reportProvider_exposesOnlyReportCacheSubdirectory() {
        val cache = ReportCache(File(context.cacheDir, "reports"))
        val file = cache.create(period(30)).apply { writeText("fixture") }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports.files", file)
            assertEquals("content", uri.scheme)
            val foreign = File(context.cacheDir, "outside-reports.pdf").apply { writeText("fixture") }
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    FileProvider.getUriForFile(context, "${context.packageName}.reports.files", foreign)
                }
            } finally { foreign.delete() }
        } finally { cache.discard(file) }
    }

    private fun inspect(file: File, expectedPages: Int) {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertEquals(expectedPages, pdf.pageCount)
            repeat(pdf.pageCount) { index ->
                pdf.openPage(index).use { page ->
                    assertEquals(ReportTheme.PAGE_WIDTH, page.width)
                    assertEquals(ReportTheme.PAGE_HEIGHT, page.height)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        var ink = 0
                        for (y in ReportTheme.MARGIN.toInt() until ReportTheme.FOOTER_LINE_Y.toInt()) {
                            for (x in ReportTheme.MARGIN.toInt() until (ReportTheme.MARGIN + ReportTheme.CONTENT_WIDTH).toInt()) {
                                val pixel = bitmap.getPixel(x, y)
                                if (Color.red(pixel) < 220 || Color.green(pixel) < 220 || Color.blue(pixel) < 220) ink++
                            }
                        }
                        assertTrue("Page $index must contain visible report content", ink > 100)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    private fun period(days: Int): ReportPeriod {
        val last = LocalDate.of(2026, 10, 11)
        return ReportPeriod(last.minusDays(days.toLong() - 1), last, zone)
    }

    private fun fixture(days: Int): ReportSnapshot {
        val period = period(days)
        return ReportSnapshot(
            measurements = (0 until days).flatMap { index ->
                val date = period.startDate.plusDays(index.toLong())
                listOf(8, 20).map { hour -> ReportMeasurement(
                    id = "$index-$hour", measuredAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
                    systolic = 130 + index % 20, diastolic = 78 + index % 12, pulse = 72,
                    note = if (index == 5 && hour == 8) "早餐前测量" else null
                ) }
            },
            profile = ReportUserProfile("示例用户", 62),
            medications = listOf(ReportMedication(1, "药品 A", "5 mg", true,
                listOf(ReportMedicationTime(1, "08:00", true)))),
            intakes = listOf(ReportIntake(1, 1, period.endDate.toEpochDay(), period.endExclusive - 1))
        )
    }
}
