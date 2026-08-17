package com.example.bloodpressurerecord.data.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionIdsTest {

    @Test
    fun `accepts uuid and legacy backup prefixes`() {
        assertTrue(ScanSessionIds.isSafe("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(ScanSessionIds.isSafe("bp_measurements:12"))
        assertTrue(ScanSessionIds.isSafe("blood_pressure_records:34"))
        assertTrue(ScanSessionIds.isSafe("session-1"))
    }

    @Test
    fun `rejects empty overly long and traversal values`() {
        assertFalse(ScanSessionIds.isSafe(""))
        assertFalse(ScanSessionIds.isSafe(".."))
        assertFalse(ScanSessionIds.isSafe("../photos"))
        assertFalse(ScanSessionIds.isSafe("..\\photos"))
        assertFalse(ScanSessionIds.isSafe("/tmp/x"))
        assertFalse(ScanSessionIds.isSafe("a/b"))
        assertFalse(ScanSessionIds.isSafe("id with space"))
        assertFalse(ScanSessionIds.isSafe("x".repeat(ScanSessionIds.MAX_LENGTH + 1)))
    }
}
