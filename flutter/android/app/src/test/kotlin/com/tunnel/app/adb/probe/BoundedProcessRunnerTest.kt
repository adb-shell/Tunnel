package com.tunnel.app.adb.probe

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake child processes only: these tests never launch adb, shells or touch a device. */
class BoundedProcessRunnerTest {
    @Test fun drainsBothStreamsAndRetainsTheRealExitCode() {
        val child = FakeProcess("out".toByteArray(), "err".toByteArray(), code = 7)
        val result = BoundedProcessRunner.runWithFactory(1000, 32) { child }
        assertEquals(7, result.exitCode)
        assertEquals(null, result.failure)
        assertFalse(result.succeeded)
        assertEquals("out", result.stdout.toString(Charsets.UTF_8))
        assertEquals("err", result.stderr.toString(Charsets.UTF_8))
        assertFalse(result.toString().contains("err"))
    }

    @Test fun boundsEachStreamIndependentlyAndDoesNotAcceptTruncatedSuccess() {
        val child = FakeProcess(ByteArray(64) { 65 }, ByteArray(64) { 66 })
        val result = BoundedProcessRunner.runWithFactory(1000, 8) { child }
        assertEquals(ProcessFailure.OUTPUT_LIMIT, result.failure)
        assertEquals(8, result.stdout.size)
        assertEquals(8, result.stderr.size)
        assertFalse(result.succeeded)
    }

    @Test fun deadlineTerminatesOnlyTheOwnedChild() {
        val child = FakeProcess(ByteArray(0), ByteArray(0), running = true)
        val result = BoundedProcessRunner.runWithFactory(10, 32) { child }
        assertEquals(ProcessFailure.TIMEOUT, result.failure)
        assertTrue(child.destroyed)
        assertFalse(child.isAlive)
    }

    @Test fun invalidLimitsAndPreexistingCancellationDoNotStartAChild() {
        var launches = 0
        val launch = { launches++; FakeProcess(ByteArray(0), ByteArray(0)) }
        assertEquals(ProcessFailure.INVALID_LIMITS, BoundedProcessRunner.runWithFactory(0, 32, launch).failure)
        try {
            Thread.currentThread().interrupt()
            assertEquals(ProcessFailure.INTERRUPTED, BoundedProcessRunner.runWithFactory(100, 32, launch).failure)
        } finally {
            Thread.interrupted()
        }
        assertEquals(0, launches)
    }

    @Test fun rejectsAnOverlappingRunWithoutLaunchingAnotherChild() {
        var launches = 0
        val result = BoundedProcessRunner.runWithFactory(1000, 32) {
            launches++
            val overlap = BoundedProcessRunner.runWithFactory(1000, 32) {
                launches++
                FakeProcess(ByteArray(0), ByteArray(0))
            }
            assertEquals(ProcessFailure.BUSY, overlap.failure)
            FakeProcess(ByteArray(0), ByteArray(0))
        }
        assertTrue(result.succeeded)
        assertEquals(1, launches)
    }

    private class FakeProcess(
        out: ByteArray,
        err: ByteArray,
        private val code: Int = 0,
        @Volatile private var running: Boolean = false,
    ) : Process() {
        private val outStream = ByteArrayInputStream(out)
        private val errStream = ByteArrayInputStream(err)
        private val inStream = ByteArrayOutputStream()
        var destroyed = false
            private set
        override fun getInputStream() = outStream
        override fun getErrorStream() = errStream
        override fun getOutputStream() = inStream
        override fun waitFor(): Int = exitValue()
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (running) Thread.sleep(1)
            return !running
        }
        override fun exitValue(): Int {
            if (running) throw IllegalThreadStateException()
            return code
        }
        override fun destroy() { destroyed = true; running = false }
        override fun destroyForcibly(): Process { destroy(); return this }
        override fun isAlive(): Boolean = running
    }
}
