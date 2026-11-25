package com.example.bloodpressurerecord.export.report

import com.example.bloodpressurerecord.domain.report.ReportPeriod
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Owns only reports/, never other cache entries. Shared exports remain readable for 24 hours. */
class ReportCache(
    private val root: File,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    @Volatile
    internal var generation = 0L
        private set

    @Synchronized
    fun create(period: ReportPeriod, expectedGeneration: Long = generation): File {
        check(expectedGeneration == generation) { "Report cache was cleared" }
        check(root.mkdirs() || root.isDirectory)
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir())
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        return File(directory, "血压报告_${period.startDate.format(formatter)}-${period.endDate.format(formatter)}.pdf")
    }

    /** Clears even recently shared reports. The tree walk never follows symbolic links. */
    @Synchronized
    fun clearAll(revokePdf: (File) -> Unit) {
        generation++
        val cachePath = root.toPath()
        if (Files.notExists(cachePath, LinkOption.NOFOLLOW_LINKS)) return
        var revokeFailure: Exception? = null
        Files.walkFileTree(cachePath, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (attributes.isRegularFile && file.toFile().extension.equals("pdf", ignoreCase = true)) {
                    try {
                        revokePdf(file.toFile())
                    } catch (error: Exception) {
                        revokeFailure = error
                    }
                }
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(directory)
                return FileVisitResult.CONTINUE
            }
        })
        revokeFailure?.let { throw it }
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
