package com.example.bloodpressurerecord.export.report

import com.example.bloodpressurerecord.domain.report.ReportPeriod
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val period = ReportPeriod(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 10, 11), ZoneId.of("Asia/Taipei"))

    @Test fun uniqueFiles_usePeriodWithoutIdentity_andCancelledFilesAreDiscarded() {
        val cache = ReportCache(temporary.newFolder("reports"))
        val first = cache.create(period).apply { writeText("pdf") }
        val second = cache.create(period).apply { writeText("pdf") }
        assertEquals("血压报告_20260912-20261011.pdf", first.name)
        assertNotEquals(first.parentFile, second.parentFile)
        cache.discard(first)
        assertFalse(first.exists())
        assertTrue(second.exists())
    }

    @Test fun sharedReport_survivesDiscard_andExpiresOnlyAfter24HoursSinceLastShare() {
        var now = 10_000L
        val cache = ReportCache(temporary.newFolder("reports")) { now }
        val file = cache.create(period).apply { writeText("pdf") }
        cache.markShared(file)
        cache.discard(file)
        assertTrue(file.exists())
        now += ReportCache.RETENTION_MILLIS - 1
        cache.cleanup()
        assertTrue(file.exists())
        cache.markShared(file)
        now += ReportCache.RETENTION_MILLIS - 1
        cache.cleanup()
        assertTrue(file.exists())
        now++
        cache.cleanup()
        assertFalse(file.exists())
    }

    @Test fun stalePartialRemoved_otherCacheDirectoryNeverTouched() {
        val root = temporary.newFolder("reports")
        val now = System.currentTimeMillis() + ReportCache.RETENTION_MILLIS
        val cache = ReportCache(root) { now }
        val file = cache.create(period)
        File(file.parentFile, "${file.name}.partial").writeText("incomplete")
        file.parentFile!!.setLastModified(now - ReportCache.RETENTION_MILLIS)
        val foreign = temporary.newFile("outside.pdf").apply { writeText("keep") }
        cache.cleanup()
        assertFalse(file.parentFile!!.exists())
        assertTrue(foreign.exists())
    }

    @Test fun foreignPathCannotBeDeletedOrMarkedShared() {
        val cache = ReportCache(temporary.newFolder("reports"))
        val foreign = temporary.newFile("outside.pdf")
        assertThrows(IllegalArgumentException::class.java) { cache.discard(foreign) }
        assertThrows(IllegalArgumentException::class.java) { cache.markShared(foreign) }
        assertTrue(foreign.exists())
    }
}
