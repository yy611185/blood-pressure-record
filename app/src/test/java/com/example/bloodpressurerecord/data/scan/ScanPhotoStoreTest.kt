package com.example.bloodpressurerecord.data.scan

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanPhotoStoreTest {

    private lateinit var root: File
    private lateinit var store: ScanPhotoStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("scan_photos_test").toFile()
        store = ScanPhotoStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `save and read back`() {
        val file = store.save("session-1", 1, byteArrayOf(1, 2, 3))
        assertTrue(file.exists())
        assertEquals(listOf("session-1"), store.listSessionIds())
        assertTrue(store.sizeBytes() > 0)
    }

    @Test
    fun `delete for session removes only that session`() {
        store.save("session-1", 1, byteArrayOf(1))
        store.save("session-2", 1, byteArrayOf(2))
        store.deleteForSession("session-1")
        assertEquals(listOf("session-2"), store.listSessionIds())
    }

    @Test
    fun `delete all returns count`() {
        store.save("session-1", 1, byteArrayOf(1))
        store.save("session-2", 1, byteArrayOf(2))
        assertEquals(2, store.deleteAll())
        assertTrue(store.listSessionIds().isEmpty())
    }

    @Test
    fun `delete older than threshold`() {
        val old = store.save("session-old", 1, byteArrayOf(1))
        val fresh = store.save("session-fresh", 1, byteArrayOf(2))
        old.parentFile.setLastModified(1_000_000L)
        fresh.parentFile.setLastModified(System.currentTimeMillis())
        val removed = store.deleteOlderThan(System.currentTimeMillis() - 60_000L)
        assertEquals(1, removed)
        assertEquals(listOf("session-fresh"), store.listSessionIds())
    }

    @Test
    fun `group number is encoded in file name`() {
        val file = store.save("session-1", 2, byteArrayOf(1))
        assertEquals("2.jpg", file.name)
        assertFalse(store.listSessionIds().isEmpty())
    }
}
