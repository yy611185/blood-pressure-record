package com.example.bloodpressurerecord.update

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyUpdateCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `cleanup removes only retired update directories and is repeatable`() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val retained = listOf(File(files, "datastore/software_updates.preferences_pb"),
            File(files, "drafts/record"), File(files, "backup.bpx"), File(cache, "reports/report.pdf"),
            temporary.newFile("health.db"))
        retained.forEach { requireNotNull(it.parentFile).mkdirs(); it.writeText("keep") }
        listOf(File(files, "updates/partial/45.part"), File(files, "updates/verified/45.apk"),
            File(files, "updates/task.properties"), File(cache, "updates/old.apk")).forEach {
            requireNotNull(it.parentFile).mkdirs(); it.writeText("old updater")
        }
        repeat(2) { cleanupLegacyUpdateFiles(files, cache) }
        assertFalse(File(files, "updates").exists())
        assertFalse(File(cache, "updates").exists())
        retained.forEach { assertTrue(it.readText() == "keep") }
    }

    @Test fun `cleanup does not traverse nested symbolic links`() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val data = temporary.newFolder("user-data")
        val record = File(data, "health.db").apply { writeText("keep") }
        val updates = File(files, "updates").apply { mkdirs() }
        createLinkOrSkip(File(updates, "outside"), data)
        cleanupLegacyUpdateFiles(files, cache)
        assertTrue(record.readText() == "keep")
        assertFalse(updates.exists())
    }

    @Test fun `cleanup skips a symbolic link at its root`() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val data = temporary.newFolder("user-data")
        val record = File(data, "health.db").apply { writeText("keep") }
        val link = File(files, "updates")
        createLinkOrSkip(link, data)
        cleanupLegacyUpdateFiles(files, cache)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertTrue(record.readText() == "keep")
        Files.delete(link.toPath())
    }

    private fun createLinkOrSkip(link: File, target: File) {
        try { Files.createSymbolicLink(link.toPath(), target.toPath()) }
        catch (error: Exception) { assumeNoException("Symbolic links unavailable on this host", error) }
    }
}
