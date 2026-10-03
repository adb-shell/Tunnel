package com.tunnel.app.adb

import com.tunnel.app.adb.probe.LocalAdbTarget
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Phone-local interactive shell. Its lease excludes pairing and the remote video helper. */
internal class LocalAdbTerminal private constructor(
    private val process: Process,
    private val lease: Closeable,
    private val output: (String) -> Unit,
    private val onExit: (Boolean) -> Unit,
) : Closeable {
    private val closing = AtomicBoolean(false)
    private val requestedStop = AtomicBoolean(false)
    private val released = CountDownLatch(1)
    private val pending = ArrayBlockingQueue<String>(16)
    val running: Boolean get() = !closing.get() && process.isAlive

    private fun begin() {
        fun drain(stream: InputStream, name: String) = thread(isDaemon = true, name = name) {
            try {
                stream.reader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val size = reader.read(buffer)
                        if (size < 0) break
                        if (size > 0) output(String(buffer, 0, size))
                    }
                }
            } catch (_: Exception) { /* Transport closure is reported by the process owner. */ }
        }
        val stdout = drain(process.inputStream, "tunnel-adb-terminal-out")
        val stderr = drain(process.errorStream, "tunnel-adb-terminal-err")
        val writer = thread(isDaemon = true, name = "tunnel-adb-terminal-in") {
            try {
                while (!closing.get()) {
                    val text = pending.take()
                    process.outputStream.write(text.toByteArray(Charsets.UTF_8))
                    process.outputStream.flush()
                }
            } catch (_: Exception) { terminate() }
        }
        thread(isDaemon = true, name = "tunnel-adb-terminal-owner") {
            var cleanExit = false
            try { cleanExit = process.waitFor() == 0 } catch (_: InterruptedException) { terminate() }
            finally {
                closing.set(true)
                pending.clear()
                writer.interrupt()
                try { stdout.join(500); stderr.join(500) } catch (_: InterruptedException) { }
                // Update the old terminal's state before another operation can own
                // the transport; otherwise this callback could clear its fresh state.
                try { onExit(requestedStop.get() || cleanExit) }
                finally { lease.close(); released.countDown() }
            }
        }
    }

    // Queueing keeps MethodChannel responsive even when a foreground program blocks stdin.
    fun send(command: String): Boolean = running && pending.offer(command + "\n")
    fun interrupt(): Boolean = running && pending.offer("\u0003")

    override fun close() {
        requestedStop.set(true)
        terminate()
    }

    private fun terminate() {
        if (closing.compareAndSet(false, true)) {
            pending.clear()
            // Only this adb shell client is owned here. Never kill the private daemon.
            try { process.destroyForcibly() } catch (_: Exception) { }
        }
    }

    fun awaitStopped(timeoutMillis: Long): Boolean = released.await(timeoutMillis, TimeUnit.MILLISECONDS)

    companion object {
        fun start(spec: LocalAdbProcessSpec, target: LocalAdbTarget, lease: Closeable,
                  output: (String) -> Unit, onExit: (Boolean) -> Unit): LocalAdbTerminal {
            // PTY is needed for Ctrl+C, cd/env continuity and interactive shell programs.
            val process = spec.processBuilder(target, listOf("shell", "-tt")).start()
            val terminal = LocalAdbTerminal(process, lease, output, onExit)
            try { terminal.begin() } catch (error: Exception) {
                terminal.close()
                lease.close()
                throw error
            }
            return terminal
        }
    }
}
