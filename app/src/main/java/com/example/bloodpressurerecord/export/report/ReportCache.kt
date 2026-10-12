package com.example.bloodpressurerecord.export.report

import com.example.bloodpressurerecord.domain.report.ReportPeriod
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Owns only reports/, never other cache entries. Shared exports remain readable for 24 hours. */
class ReportCache(
    private val root: File,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun create(period: ReportPeriod): File {
        check(root.mkdirs() || root.isDirectory)
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir())
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        return File(directory, "血压报告_${period.startDate.format(formatter)}-${period.endDate.format(formatter)}.pdf")
    }

    fun markShared(file: File) {
        val directory = ownedDirectory(file)
        check(file.isFile)
        File(directory, SHARED_MARKER).writeText(nowMillis().toString())
    }

    fun discard(file: File) {
        val directory = ownedDirectory(file)
        if (!File(directory, SHARED_MARKER).exists()) directory.deleteRecursively()
    }

    fun cleanup() {
        if (!root.isDirectory) return
        val current = nowMillis()
        root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
            // Resolve before deletion: a cache symlink must never allow leaving reports/.
            if (directory.canonicalFile.parentFile != root.canonicalFile) return@forEach
            val marker = File(directory, SHARED_MARKER)
            val touchedAt = if (marker.exists()) {
                marker.readText().toLongOrNull() ?: marker.lastModified()
            } else directory.lastModified()
            if (current >= touchedAt && current - touchedAt >= RETENTION_MILLIS) {
                directory.deleteRecursively()
            }
        }
    }

    private fun ownedDirectory(file: File): File {
        val directory = file.canonicalFile.parentFile
        require(directory != null && directory.parentFile == root.canonicalFile) { "Invalid report cache path" }
        return directory
    }

    companion object {
        const val RETENTION_MILLIS = 24L * 60 * 60 * 1000
        private const val SHARED_MARKER = ".shared"
    }
}
