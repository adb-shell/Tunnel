package com.tunnel.app.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No processes/devices: one UiAutomation helper can coexist with ordinary ADB clients. */
class LocalAdbAccessTest {
    @Test fun mirrorIsExclusiveButLocalCommandsRemainAvailable() {
        val mirror = LocalAdbAccess.acquire(true)
        val local = LocalAdbAccess.acquire(false)
        try {
            assertNotNull(mirror)
            assertNotNull(local)
            assertTrue(LocalAdbAccess.isMirrorActive())
            assertNull(LocalAdbAccess.acquire(true))
        } finally { local?.close(); mirror?.close() }
        assertFalse(LocalAdbAccess.isMirrorActive())
    }

    @Test fun oldOwnerCannotReleaseANewerMirrorLease() {
        val first = LocalAdbAccess.acquire(true)
        assertNotNull(first)
        first?.close()
        val newer = LocalAdbAccess.acquire(true)
        assertNotNull(newer)
        try {
            first?.close()
            assertTrue(LocalAdbAccess.isMirrorActive())
            assertNull(LocalAdbAccess.acquire(true))
        } finally { newer?.close() }
    }

    @Test fun ordinaryClientCapacityDoesNotConsumeTheMirrorSlot() {
        val local = (1..4).map { LocalAdbAccess.acquire(false) }
        val mirror = LocalAdbAccess.acquire(true)
        try {
            local.forEach { assertNotNull(it) }
            assertNull(LocalAdbAccess.acquire(false))
            assertNotNull(mirror)
            local[0]?.close()
            val replacement = LocalAdbAccess.acquire(false)
            try { assertNotNull(replacement) } finally { replacement?.close() }
        } finally { local.forEach { it?.close() }; mirror?.close() }
    }
}
