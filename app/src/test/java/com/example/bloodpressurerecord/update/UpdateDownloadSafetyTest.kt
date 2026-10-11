package com.example.bloodpressurerecord.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadSafetyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun writeAllowsExactDeclaredSize() {
        UpdateDownloadSafety.requireWriteFits(95, 5, 100)
        UpdateDownloadSafety.requireWriteFits(0, 32 * 1024, UpdateDownloadSafety.MAX_BYTES)
    }

    @Test fun writeRejectsOverflowAndHardLimit() {
        rejects { UpdateDownloadSafety.requireWriteFits(99, 2, 100) }
        rejects { UpdateDownloadSafety.requireWriteFits(Long.MAX_VALUE, 1, 100) }
        rejects { UpdateDownloadSafety.requireWriteFits(0, 1, UpdateDownloadSafety.MAX_BYTES + 1) }
        rejects { UpdateDownloadSafety.requireWriteFits(-1, 1, 100) }
    }

    @Test fun rangeRequiresExactRemainingSuffix() {
        assertEquals(60L, UpdateDownloadSafety.requireRange("bytes 40-99/100", 40, 100))
        rejects { UpdateDownloadSafety.requireRange("bytes 39-99/100", 40, 100) }
        rejects { UpdateDownloadSafety.requireRange("bytes 40-99/101", 40, 100) }
        rejects { UpdateDownloadSafety.requireRange("bytes 40-100/100", 40, 100) }
        rejects { UpdateDownloadSafety.requireRange("bytes 40-80/100", 40, 100) }
        rejects { UpdateDownloadSafety.requireRange("bytes 40-99/*", 40, 100) }
        rejects { UpdateDownloadSafety.requireRange("bytes 999999999999999999999-99/100", 40, 100) }
        rejects { UpdateDownloadSafety.requireRange(null, 40, 100) }
    }

    @Test fun digestDetectsTampering() {
        val file = temporary.newFile()
        file.writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateDownloadSafety.sha256(file))
        file.appendText("d")
        assertFalse(UpdateDownloadSafety.sha256(file) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test fun productionPackageAndHigherExactVersionRequired() {
        UpdateDownloadSafety.requirePackage("com.yang.bloodpressure", 44, 44, 43, false, false)
        rejects { UpdateDownloadSafety.requirePackage("com.yang.bloodpressure.debug", 44, 44, 43, false, false) }
        rejects { UpdateDownloadSafety.requirePackage("com.yang.bloodpressure", 43, 43, 43, false, false) }
        rejects { UpdateDownloadSafety.requirePackage("com.yang.bloodpressure", 45, 44, 43, false, false) }
        rejects { UpdateDownloadSafety.requirePackage("com.yang.bloodpressure", 44, 44, 43, true, false) }
        rejects { UpdateDownloadSafety.requirePackage("com.yang.bloodpressure", 44, 44, 43, false, true) }
    }

    @Test fun unchangedSignerAcceptedButUnsignedAndUnanchoredRejected() {
        assertTrue(accept(setOf("root"), setOf("root"), setOf("root"), setOf("root")))
        assertFalse(accept(setOf("unknown"), emptySet(), setOf("unknown"), emptySet()))
        assertFalse(accept(setOf("root"), emptySet(), emptySet(), emptySet()))
        assertFalse(accept(emptySet(), emptySet(), setOf("root"), emptySet()))
    }

    @Test fun rotationAcceptedOnlyForwardFromCurrentSigner() {
        assertTrue(accept(setOf("root"), setOf("root"), setOf("next"), setOf("root", "next")))
        assertTrue(accept(setOf("next"), setOf("root", "next"), setOf("future"), setOf("root", "next", "future")))
        // Shared old root is insufficient for a fork; an old signer is not a forward update.
        assertFalse(accept(setOf("next"), setOf("root", "next"), setOf("fork"), setOf("root", "fork")))
        assertFalse(accept(setOf("next"), setOf("root", "next"), setOf("root"), setOf("root")))
        assertFalse(accept(setOf("root"), setOf("root"), setOf("next"), setOf("root", "next"), false))
    }

    @Test fun multipleSignersRequireCompleteIdenticalSet() {
        assertTrue(accept(setOf("root", "second"), emptySet(), setOf("second", "root"), emptySet()))
        assertFalse(accept(setOf("root", "second"), emptySet(), setOf("root"), setOf("root", "second")))
        assertFalse(accept(setOf("root"), emptySet(), setOf("root", "second"), setOf("root")))
    }

    private fun accept(installed: Set<String>, old: Set<String>, next: Set<String>, history: Set<String>,
                       rotation: Boolean = true) = UpdateDownloadSafety.acceptsSigningIdentity(
        installed, old, next, history, rotation, "root")

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected update safety rejection") }
        catch (_: UpdateVerificationException) { /* expected */ }
    }
}
