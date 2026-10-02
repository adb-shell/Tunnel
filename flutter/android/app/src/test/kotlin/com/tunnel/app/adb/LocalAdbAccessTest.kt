package com.tunnel.app.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No process/device access; verifies ownership shared by local UI and mirror supervisors. */
class LocalAdbAccessTest {
    @Test fun mirrorBlocksLocalActionsUntilItsOwnerCloses() {
        val mirror = LocalAdbAccess.acquire(true)
        assertNotNull(mirror)
        try {
            assertTrue(LocalAdbAccess.isMirrorActive())
            assertNull(LocalAdbAccess.acquire(false))
            assertNull(LocalAdbAccess.acquire(true))
        } finally { mirror?.close() }
        assertFalse(LocalAdbAccess.isMirrorActive())
    }

    @Test fun oldOwnerCannotReleaseANewerLease() {
        val first = LocalAdbAccess.acquire(false)
        assertNotNull(first)
        first?.close()
        val newer = LocalAdbAccess.acquire(true)
        assertNotNull(newer)
        try {
            first?.close()
            assertTrue(LocalAdbAccess.isMirrorActive())
            assertNull(LocalAdbAccess.acquire(false))
        } finally { newer?.close() }
    }

    @Test fun localPairingBlocksMirrorAdmission() {
        val local = LocalAdbAccess.acquire(false)
        assertNotNull(local)
        try {
            assertFalse(LocalAdbAccess.isMirrorActive())
            assertNull(LocalAdbAccess.acquire(true))
        } finally { local?.close() }
    }
}
