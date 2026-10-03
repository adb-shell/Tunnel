package com.tunnel.app.adb.probe

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

internal enum class ProcessFailure {
    INVALID_LIMITS, BUSY, START_FAILED, TIMEOUT, INTERRUPTED, OUTPUT_LIMIT, DRAIN_FAILED, RUNNER_UNAVAILABLE
}

/** Never log or send to a peer; only the explicit phone-local terminal may display bounded output. */
internal class BoundedProcessResult(
    val exitCode: Int?,
    val stdout: ByteArray,
    val stderr: ByteArray,
    val failure: ProcessFailure?,
) {
    val succeeded: Boolean get() = failure == null && exitCode == 0
    override fun toString(): String = "BoundedProcessResult(exitCode=$exitCode, failure=$failure)"
}

/**
 * Executes only caller-owned finite commands. No shell wrapper, logging, or global ADB cleanup.
 * Callers must use fixed command forms and validated parameters; this is not a remote shell API.
 * Runtime is bounded after start() returns; a stuck OS process-start syscall cannot be interrupted
 * safely by Java. A drain that cannot terminate poisons this runner instead of leaking more threads.
 */
internal object BoundedProcessRunner {
    private val unavailable = AtomicBoolean(false)
    private val gate = Semaphore(1)

    fun run(
        command: List<String>,
        workingDirectory: File,
        environment: Map<String, String>,
        timeoutMillis: Long = 5_000,
        maxBytesPerStream: Int = 8 * 1024,
        stdin: ByteArray? = null,
        cancelled: () -> Boolean = { false },
    ): BoundedProcessResult = runControlled(timeoutMillis, maxBytesPerStream, stdin, cancelled) {
        ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(false)
            .apply {
                // Do not inherit ADB_SERVER_SOCKET, ANDROID_SERIAL, ADB_TRACE or injected options.
                environment().clear()
                environment().putAll(environment)
            }.start()
    }

    internal fun runWithFactory(
        timeoutMillis: Long,
        maxBytesPerStream: Int,
        start: () -> Process,
    ): BoundedProcessResult = runControlled(timeoutMillis, maxBytesPerStream, null, { false }, start)

    internal fun runWithFactory(
        timeoutMillis: Long,
        maxBytesPerStream: Int,
        cancelled: () -> Boolean,
        start: () -> Process,
    ): BoundedProcessResult = runControlled(timeoutMillis, maxBytesPerStream, null, cancelled, start)

    private fun runControlled(
        timeoutMillis: Long, maxBytesPerStream: Int, stdin: ByteArray?,
        cancelled: () -> Boolean, start: () -> Process,
    ): BoundedProcessResult {
        if (timeoutMillis !in 1..60_000 || maxBytesPerStream !in 1..1_048_576 || (stdin?.size ?: 0) > 128) {
            return emptyResult(ProcessFailure.INVALID_LIMITS)
        }
        if (unavailable.get()) return emptyResult(ProcessFailure.RUNNER_UNAVAILABLE)
        if (Thread.currentThread().isInterrupted || cancelled()) return emptyResult(ProcessFailure.INTERRUPTED)
        if (!gate.tryAcquire()) return emptyResult(ProcessFailure.BUSY)
        return try {
            if (unavailable.get()) emptyResult(ProcessFailure.RUNNER_UNAVAILABLE)
            else runOwned(timeoutMillis, maxBytesPerStream, stdin, cancelled, start)
        } finally {
            gate.release()
        }
    }

    private fun runOwned(
        timeoutMillis: Long,
        maxBytesPerStream: Int,
        stdin: ByteArray?,
        cancelled: () -> Boolean,
        start: () -> Process,
    ): BoundedProcessResult {
        val process = try { start() } catch (_: Exception) {
            return emptyResult(ProcessFailure.START_FAILED)
        }
        val stdout = Drain(process.inputStream, maxBytesPerStream)
        val stderr = Drain(process.errorStream, maxBytesPerStream)
        val outThread = drainThread(stdout, "tunnel-adb-probe-out")
        val errThread = drainThread(stderr, "tunnel-adb-probe-err")
        var failure: ProcessFailure? = null
        var exitCode: Int? = null
        var interrupted = false
        try {
            outThread.start()
            errThread.start()
            // At most 128 bytes (pairing code only); never arbitrary interactive stdin.
            if (stdin != null) {
                process.outputStream.write(stdin)
                process.outputStream.flush()
            }
            process.outputStream.close()
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                if (cancelled()) {
                    failure = ProcessFailure.INTERRUPTED
                    break
                }
                if (stdout.overflow.get() || stderr.overflow.get()) {
                    failure = ProcessFailure.OUTPUT_LIMIT
                    break
                }
                if (stdout.failed.get() || stderr.failed.get()) {
                    failure = ProcessFailure.DRAIN_FAILED
                    break
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) {
                    failure = ProcessFailure.TIMEOUT
                    break
                }
                if (process.waitFor(min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS)) {
                    exitCode = process.exitValue()
                    break
                }
            }
        } catch (_: InterruptedException) {
            interrupted = true
            failure = ProcessFailure.INTERRUPTED
        } catch (_: Exception) {
            failure = ProcessFailure.DRAIN_FAILED
        } finally {
            // Cancellation commonly arrives while waiting or just before teardown.
            // Clear it while reaping/joining: otherwise join() throws immediately,
            // healthy drains look stuck and one cancelled pairing permanently
            // poisons every future ADB operation until the app is restarted.
            if (Thread.interrupted()) interrupted = true
            if (exitCode == null) {
                try { process.destroyForcibly() } catch (_: Exception) { }
                val reapDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250)
                while (System.nanoTime() < reapDeadline) {
                    try {
                        if (process.waitFor((reapDeadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS))
                            exitCode = process.exitValue()
                        break
                    } catch (_: InterruptedException) { interrupted = true
                    } catch (_: Exception) { break }
                }
            }
            fun finishDrain(thread: Thread) {
                val joinDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
                while (thread.isAlive && System.nanoTime() < joinDeadline) {
                    try {
                        thread.join(TimeUnit.NANOSECONDS.toMillis(joinDeadline - System.nanoTime()).coerceAtLeast(1))
                    } catch (_: InterruptedException) { interrupted = true }
                }
            }
            finishDrain(outThread)
            finishDrain(errThread)
            if (outThread.isAlive || errThread.isAlive) {
                // A pipe close may wait on the same monitor as a blocked read. Do not let
                // teardown make the caller unbounded; retain at most these two daemon
                // drains, with bounded buffers, and permanently reject further children.
                unavailable.set(true)
                if (failure == null) failure = ProcessFailure.DRAIN_FAILED
            }
            if (!outThread.isAlive) {
                try { process.inputStream.close() } catch (_: Exception) { }
            }
            if (!errThread.isAlive) {
                try { process.errorStream.close() } catch (_: Exception) { }
            }
            try { process.outputStream.close() } catch (_: Exception) { }
            if (exitCode == null) {
                try { exitCode = process.exitValue() } catch (_: IllegalThreadStateException) {
                    unavailable.set(true) // Do not accumulate children that the OS did not reap.
                }
            }
        }
        if (failure == null && (stdout.overflow.get() || stderr.overflow.get())) failure = ProcessFailure.OUTPUT_LIMIT
        if (failure == null && (stdout.failed.get() || stderr.failed.get())) failure = ProcessFailure.DRAIN_FAILED
        if (interrupted) {
            failure = ProcessFailure.INTERRUPTED
            Thread.currentThread().interrupt()
        }
        return BoundedProcessResult(exitCode, stdout.snapshot(), stderr.snapshot(), failure)
    }

    private fun emptyResult(failure: ProcessFailure) =
        BoundedProcessResult(null, ByteArray(0), ByteArray(0), failure)

    private fun drainThread(drain: Drain, name: String): Thread = Thread(drain, name).apply {
        isDaemon = true
    }

    private class Drain(private val input: InputStream, private val limit: Int) : Runnable {
        private val bytes = ByteArrayOutputStream(min(limit, 1024))
        val overflow = AtomicBoolean(false)
        val failed = AtomicBoolean(false)

        override fun run() {
            try {
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    synchronized(bytes) {
                        val accepted = min(count, limit - bytes.size())
                        if (accepted > 0) bytes.write(buffer, 0, accepted)
                        if (accepted < count) overflow.set(true)
                    }
                    // Keep draining excess bytes until the owner terminates the process.
                }
            } catch (_: Exception) {
                failed.set(true)
            } finally {
                try { input.close() } catch (_: Exception) { }
            }
        }

        fun snapshot(): ByteArray = synchronized(bytes) { bytes.toByteArray() }
    }
}
