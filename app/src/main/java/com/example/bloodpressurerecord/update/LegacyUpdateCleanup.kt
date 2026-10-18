package com.example.bloodpressurerecord.update

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Deletes only the retired updater's private directories; never follows symbolic links. */
internal fun cleanupLegacyUpdateFiles(filesDir: File, cacheDir: File) {
    for (parent in listOf(filesDir, cacheDir)) {
        try {
            val root = parent.canonicalFile.toPath().resolve("updates")
            if (Files.isSymbolicLink(root) || !Files.exists(root, NOFOLLOW_LINKS)) continue
            if (root.toRealPath() != root) continue
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir.toRealPath() == dir) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc == null) Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) {
            // Best effort; a failed cleanup must never block records, reminders or update checks.
        } catch (_: SecurityException) {
            // Retry on a later process start without requesting storage permissions.
        }
    }
}
