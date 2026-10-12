package com.example.bloodpressurerecord.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallStateTest {
    @Test fun `every initial state can advance within three attempts after return or completion`() {
        for (preparing in listOf(false, true)) {
            for (permissionPending in listOf(false, true)) {
                for (installRequested in listOf(false, true)) {
                    for (promptAllowed in listOf(false, true)) {
                        var state = InstallAttemptState(preparing, permissionPending, installRequested)
                        var allowed = promptAllowed
                        var advanced = false
                        repeat(3) { attempt ->
                            if (canAttemptInstall(state, allowed)) {
                                advanced = true
                                // An accepted click is followed by a completed/cancelled effect.
                                state = finishInstallAttempt(state.copy(preparing = true, installRequested = true))
                            } else if (attempt == 0) {
                                // Returning from settings/installer (or READY) clears saved requests.
                                state = resetInstallRequests(state)
                                allowed = true
                            } else {
                                // In-flight preparation is allowed to finish, rather than assuming it never runs.
                                state = finishInstallAttempt(state)
                            }
                        }
                        assertTrue("Initial state $preparing/$permissionPending/$installRequested/$promptAllowed", advanced)
                    }
                }
            }
        }
    }

    @Test fun `return resets both requests without claiming active preparation is finished`() {
        assertEquals(
            InstallAttemptState(preparing = true),
            resetInstallRequests(InstallAttemptState(true, true, true))
        )
    }

    @Test fun `every effect exit releases preparation and both requests`() {
        for (preparing in listOf(false, true)) {
            for (permissionPending in listOf(false, true)) {
                for (installRequested in listOf(false, true)) {
                    val finished = finishInstallAttempt(InstallAttemptState(preparing, permissionPending, installRequested))
                    assertEquals(InstallAttemptState(), finished)
                    assertTrue(canAttemptInstall(finished, promptAllowed = true))
                }
            }
        }
    }

    @Test fun `closed prompt gate never becomes a saved request lock`() {
        val finished = finishInstallAttempt(InstallAttemptState(installRequested = true))
        assertFalse(canAttemptInstall(finished, promptAllowed = false))
        assertTrue(canAttemptInstall(finished, promptAllowed = true))
    }

    @Test fun `preparation and each outstanding request reject overlapping attempts`() {
        assertTrue(canAttemptInstall(InstallAttemptState(), promptAllowed = true))
        assertFalse(canAttemptInstall(InstallAttemptState(preparing = true), promptAllowed = true))
        assertFalse(canAttemptInstall(InstallAttemptState(permissionPending = true), promptAllowed = true))
        assertFalse(canAttemptInstall(InstallAttemptState(installRequested = true), promptAllowed = true))
    }
}
