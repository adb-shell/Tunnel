package com.tunnel.app.adb.probe

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
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

    @Test fun admitsFourIndependentCommandsAndBoundsTheFifth() {
        var launches = 0
        fun nested(depth: Int): BoundedProcessResult = BoundedProcessRunner.runWithFactory(1000, 32) {
            launches++
            if (depth < 4) assertTrue(nested(depth + 1).succeeded)
            else {
                val excess = BoundedProcessRunner.runWithFactory(1000, 32) {
                    launches++
                    FakeProcess(ByteArray(0), ByteArray(0))
                }
                assertEquals(ProcessFailure.BUSY, excess.failure)
            }
            FakeProcess(ByteArray(0), ByteArray(0))
        }
        assertTrue(nested(1).succeeded)
        assertEquals(4, launches)
    }

    @Test fun cancellationReapsDrainsAndDoesNotPoisonTheNextOperation() {
        val closed = CountDownLatch(1)
        val child = object : Process() {
            private val output = ByteArrayOutputStream()
            private fun drain() = object : InputStream() {
                override fun read(): Int {
                    closed.await()
                    Thread.sleep(50) // The pipe drains only after child termination.
                    return -1
                }
            }
            override fun getInputStream() = drain()
            override fun getErrorStream() = drain()
            override fun getOutputStream() = output
            override fun waitFor(): Int { closed.await(); return 0 }
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
                if (closed.count == 0L) return true
                Thread.currentThread().interrupt()
                return false
            }
            override fun exitValue(): Int {
                if (closed.count != 0L) throw IllegalThreadStateException()
                return 0
            }
            override fun destroy() { closed.countDown() }
            override fun destroyForcibly(): Process { destroy(); return this }
            override fun isAlive() = closed.count != 0L
        }
        try {
            val result = BoundedProcessRunner.runWithFactory(1000, 32,
                cancelled = { Thread.currentThread().isInterrupted }, start = { child })
            assertEquals(ProcessFailure.INTERRUPTED, result.failure)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
        val next = BoundedProcessRunner.runWithFactory(1000, 32) {
            FakeProcess("ready".toByteArray(), ByteArray(0))
        }
        assertTrue(next.succeeded)
        assertEquals("ready", next.stdout.toString(Charsets.UTF_8))
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
