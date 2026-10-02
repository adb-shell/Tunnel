package com.tunnel.app.adb.mirror

import android.content.Context
import android.os.SystemClock
import com.tunnel.adb.protocol.AdbCommands
import com.tunnel.adb.protocol.AdbWire
import com.tunnel.app.adb.LocalAdbProcessSpec
import com.tunnel.app.adb.probe.BoundedProcessRunner
import com.tunnel.app.adb.probe.LocalAdbIdentityProbe
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import com.tunnel.app.adb.mirror.PackagedAdbHelper.hex
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** One helper process, one authenticated epoch, bounded resources. Never owns an ADB server. */
internal class TunnelAdbSession(
    private val context: Context,
    private val serial: String,
    val epoch: Long,
    private val lease: Closeable,
    private val events: Events,
) : Closeable {
    interface Events {
        fun packet(packet: AdbWire.Packet): Boolean
        fun capabilities(mask: Int)
        fun ended(reason: String, cleanupComplete: Boolean)
    }

    private val random = SecureRandom()
    private val closed = AtomicBoolean()
    private val finished = AtomicBoolean()
    private val resources = mutableListOf<Closeable>()
    private val threads = mutableListOf<Thread>()
    private val writeLock = Any()
    private val ids = AtomicLong()
    private data class Pending(val since: Long, val result: CompletableFuture<AdbCommands.Result>)
    private val pending = ConcurrentHashMap<Long, Pending>()
    @Volatile private var control: AdbWire.Session? = null
    @Volatile private var process: Process? = null
    @Volatile private var authenticated = false
    @Volatile private var capturePaused = false
    @Volatile private var lastVideoAt = 0L
    @Volatile private var writeSince = 0L
    @Volatile private var failure = "STOPPED"
    private var sequence = 0L
    private val startedAt = SystemClock.elapsedRealtime()

    fun start() { launch("supervisor") { execute() } }
    fun setCapturePaused(paused: Boolean) { if (!paused) lastVideoAt = SystemClock.elapsedRealtime(); capturePaused = paused }

    fun keyframe() { try { write(AdbWire.REQUEST_KEYFRAME) } catch (_: Exception) { fail("CONTROL_FAILED") } }

    fun operation(operation: Int, a: Int = 0, b: Int = 0, c: Int = 0, d: Int = 0, e: Int = 0, f: Int = 0): CompletableFuture<AdbCommands.Result> {
        val result = CompletableFuture<AdbCommands.Result>()
        if (closed.get() || control == null || pending.size >= 64) {
            result.completeExceptionally(IOException("HELPER_BUSY")); return result
        }
        try {
            synchronized(writeLock) {
                val id = ids.incrementAndGet()
                val command = AdbCommands.Command(id, operation, a, b, c, d, e, f)
                pending[id] = Pending(SystemClock.elapsedRealtime(), result)
                write(AdbWire.OPERATION, command.encode())
            }
        } catch (_: Exception) { result.completeExceptionally(IOException("CONTROL_FAILED")); fail("CONTROL_FAILED") }
        return result
    }

    private fun execute() {
        var artifact: PackagedAdbHelper.Artifact? = null
        var remoteDirectory: String? = null
        var created = false
        var cleaned = false
        var processSpec: LocalAdbProcessSpec? = null
        var target: com.tunnel.app.adb.probe.LocalAdbTarget? = null
        launch("watchdog") {
            while (!finished.get()) {
                val now = SystemClock.elapsedRealtime()
                if (!authenticated && now - startedAt > 45_000) fail("HELPER_START_TIMEOUT")
                if (writeSince != 0L && now - writeSince > 2_000) fail("HELPER_WRITE_TIMEOUT")
                if (authenticated && !capturePaused && now - lastVideoAt > 10_000) fail("VIDEO_STALLED")
                if (pending.values.any { now - it.since > 10_000 }) fail("HELPER_OPERATION_TIMEOUT")
                Thread.sleep(100)
            }
        }
        try {
            val spec = LocalAdbProcessSpec(context)
            processSpec = spec
            checkOpen()
            val trusted = LocalAdbIdentityProbe(context).probe(serial).trustedTarget ?: throw IOException("LOCAL_ADB_REQUIRED")
            target = trusted
            artifact = PackagedAdbHelper.load(context)
            remoteDirectory = "/data/local/tmp/tunnel-adb-${ByteArray(16).also(random::nextBytes).hex()}"
            val directory = remoteDirectory
            val remoteJar = "$directory/helper.jar"
            fun finite(arguments: List<String>, timeout: Long = 10_000): ByteArray {
                checkOpen()
                if (LocalAdbTargetPolicy.validate(serial) == null) throw IOException("TARGET_NOT_LOCAL")
                val response = BoundedProcessRunner.run(spec.command(trusted, arguments), spec.workingDirectory, spec.environment, timeout)
                if (!response.succeeded) throw IOException("HELPER_PREPARE_FAILED")
                checkOpen(); return response.stdout
            }
            finite(listOf("shell", "-T", "-n", "umask 077; mkdir '$directory'"))
            created = true
            finite(listOf("push", artifact.file.absolutePath, remoteJar))
            val digest = finite(listOf("shell", "-T", "-n", "chmod 400 '$remoteJar' && sha256sum '$remoteJar'"))
                .toString(Charsets.US_ASCII).trim()
            if (digest != "${artifact.sha256}  $remoteJar") throw IOException("HELPER_HASH_INVALID")
            val videoListener = listener()
            val controlListener = listener()
            if (!LocalAdbIdentityProbe(context).probe(serial).shellIdentityVerified) throw IOException("LOCAL_ADB_REQUIRED")
            checkOpen()
            val child = spec.processBuilder(trusted, listOf("shell", "-T",
                "CLASSPATH='$remoteJar' exec app_process / ${PackagedAdbHelper.ENTRY_POINT}")).start()
            process = child
            checkOpen()
            drain(child.inputStream); drain(child.errorStream)
            val secret = ByteArray(32).also(random::nextBytes)
            try {
                AdbWire.Bootstrap(secret, epoch, videoListener.localPort, controlListener.localPort, 1280, 30, 4_000_000, AdbWire.SESSION_DURATION)
                    .use { AdbWire.writeBootstrap(child.outputStream, it) }
                val video = accept(videoListener, secret, AdbWire.CHANNEL_VIDEO)
                val commands = accept(controlListener, secret, AdbWire.CHANNEL_CONTROL)
                control = commands
                videoListener.close(); controlListener.close()
                lastVideoAt = SystemClock.elapsedRealtime(); authenticated = true
                launch("control-read") {
                    while (!closed.get()) {
                        val packet = commands.read()
                        when (packet.kind) {
                            AdbWire.CAPABILITIES -> {
                                val bits = ByteBuffer.wrap(packet.payloadCopy()); bits.int
                                events.capabilities(bits.int)
                            }
                            AdbWire.RESULT -> {
                                val reply = AdbCommands.Result.decode(packet.payloadCopy())
                                pending.remove(reply.id)?.result?.complete(reply)
                            }
                            AdbWire.PONG -> Unit
                            else -> throw IOException("CONTROL_INVALID")
                        }
                    }
                }
                launch("heartbeat") {
                    while (!closed.get()) { write(AdbWire.PING); Thread.sleep(1_000) }
                }
                keyframe()
                var dropping = false
                while (!closed.get()) {
                    val packet = video.read()
                    lastVideoAt = SystemClock.elapsedRealtime()
                    val config = packet.kind == AdbWire.VIDEO_CONFIG
                    val key = packet.flags and AdbWire.FLAG_KEY_FRAME != 0
                    if (dropping && !config && !key) continue
                    if (events.packet(packet)) { if (key) dropping = false }
                    else { dropping = true; keyframe() }
                }
            } finally { secret.fill(0) }
        } catch (_: Exception) { if (!closed.get()) fail("HELPER_OR_TRANSPORT_FAILED")
        } finally {
            close()
            val child = process
            try {
                if (child != null && !child.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    child.destroyForcibly(); child.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            } catch (_: Exception) { try { child?.destroyForcibly() } catch (_: Exception) { } }
            // Deleting the fixed artifact only, under the random directory this instance created.
            val spec = processSpec
            if (!created) cleaned = true
            if (created && remoteDirectory != null && target != null && spec != null && LocalAdbTargetPolicy.validate(serial) != null) {
                try {
                    cleaned = BoundedProcessRunner.run(spec.command(target, listOf("shell", "-T", "-n",
                        "rm -f '$remoteDirectory/helper.jar'; rmdir '$remoteDirectory'")), spec.workingDirectory, spec.environment, 2_000).succeeded
                } catch (_: Exception) { }
            }
            artifact?.file?.delete()
            finished.set(true)
            val children = synchronized(threads) { threads.filter { it !== Thread.currentThread() }.toList() }
            children.forEach { it.interrupt() }
            val joinDeadline = SystemClock.elapsedRealtime() + 1000
            children.forEach {
                val remaining = joinDeadline - SystemClock.elapsedRealtime()
                if (remaining > 0) try { it.join(remaining) } catch (_: InterruptedException) { Thread.interrupted() }
            }
            // The helper has independent stdin EOF, control heartbeat and startup timeout guards.
            val processStopped = child == null || !child.isAlive
            // Retain the lease if an OEM process cannot terminate; do not start another privileged owner.
            val threadsStopped = children.none { it.isAlive }
            if (processStopped && threadsStopped) lease.close()
            events.ended(failure, cleaned && processStopped && threadsStopped)
        }
    }

    private fun listener(): ServerSocket = own(ServerSocket().apply {
        bind(InetSocketAddress(InetAddress.getByName(AdbWire.LOOPBACK_HOST), 0), 1); soTimeout = 10_000
    })
    private fun accept(listener: ServerSocket, secret: ByteArray, channel: Int): AdbWire.Session {
        val socket = own(listener.accept()); socket.soTimeout = 10_000; socket.tcpNoDelay = true
        val session = own(AdbWire.authenticateClient(socket.getInputStream(), socket.getOutputStream(), secret, epoch, channel, random))
        // A paused video read stays cancellable by the independent control/watchdog socket close.
        if (channel == AdbWire.CHANNEL_VIDEO) socket.soTimeout = 0
        return session
    }
    private fun write(kind: Int, bytes: ByteArray = byteArrayOf()) = synchronized(writeLock) {
        checkOpen(); val stream = control ?: throw IOException("CONTROL_NOT_READY")
        writeSince = SystemClock.elapsedRealtime()
        try { stream.write(AdbWire.Packet.of(kind, 0, epoch, 0, ++sequence, 0, 0, 0, bytes)) }
        finally { writeSince = 0 }
    }
    private fun drain(input: InputStream) = launch("drain") {
        try {
            val buffer = ByteArray(2048); var window = SystemClock.elapsedRealtime(); var bytes = 0
            while (!closed.get()) {
                val n = input.read(buffer); if (n < 0) break
                if (SystemClock.elapsedRealtime() - window > 60_000) { window = SystemClock.elapsedRealtime(); bytes = 0 }
                bytes += n; if (bytes > 64 * 1024) throw IOException("HELPER_OUTPUT_LIMIT")
            }
        } finally { try { input.close() } catch (_: Exception) { } }
    }
    private fun launch(name: String, body: () -> Unit) {
        val thread = Thread({ try { body() } catch (_: Exception) { if (!closed.get()) fail("HELPER_IO_FAILED") } }, "tunnel-adb-$name")
        thread.isDaemon = true; synchronized(threads) { threads.add(thread) }; thread.start()
    }
    private fun <T : Closeable> own(value: T): T = synchronized(resources) {
        if (closed.get()) { value.close(); throw IOException("CLOSED") }; resources.add(value); value
    }
    private fun checkOpen() { if (closed.get()) throw IOException("CLOSED") }
    private fun fail(code: String) { if (!closed.get()) failure = code; close() }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Closing raw sockets first interrupts any blocked protocol read/write.
        synchronized(resources) { resources.toList() }.forEach { try { it.close() } catch (_: Exception) { } }
        try { process?.outputStream?.close() } catch (_: Exception) { }
        try { process?.destroy() } catch (_: Exception) { }
        pending.values.forEach { it.result.completeExceptionally(IOException("HELPER_CLOSED")) }; pending.clear()
    }
}
