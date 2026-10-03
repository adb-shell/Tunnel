package com.tunnel.app.adb.mirror

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.tunnel.app.BuildConfig
import com.tunnel.app.adb.TunnelAdbManager
import com.tunnel.app.adb.LocalAdbProcessSpec
import com.tunnel.app.adb.probe.BoundedProcessRunner
import com.tunnel.app.adb.probe.LocalAdbIdentityProbe
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import com.tunnel.adb.protocol.AdbWire
import com.tunnel.app.adb.mirror.PackagedAdbHelper.hex
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

/** Local diagnostic, not a capture provider. It never writes VIDEO_RAW or changes service state. */
object TunnelAdbPrototype {
    private val lock = Any()
    private var current: Run? = null
    private var unavailable = false
    private val random = SecureRandom()
    @Volatile private var snapshot: Map<String, Any> = emptyState()

    private fun emptyState(): Map<String, Any> = mapOf(
        "enabled" to BuildConfig.ADB_MIRROR_P0, "active" to false,
        "phase" to "IDLE", "reason" to "", "shellIdentityVerified" to false,
        "helperAuthenticated" to false, "videoCapabilities" to false,
        "configs" to 0L, "frames" to 0L, "keyFrames" to 0L, "bytes" to 0L,
        "width" to 0, "height" to 0, "decoded" to false, "rendered" to false,
    )

    fun status(): Map<String, Any> = snapshot
    fun isActive(): Boolean = synchronized(lock) { current != null }

    fun start(context: Context, serial: String): Map<String, Any> = synchronized(lock) {
        if (!BuildConfig.ADB_MIRROR_P0) return@synchronized emptyState()
        if (current != null) return@synchronized snapshot
        if (unavailable) return@synchronized emptyState() + ("reason" to "RESTART_APP_REQUIRED")
        if (Build.VERSION.SDK_INT !in 30..36) return@synchronized emptyState() + ("reason" to "UNSUPPORTED_ANDROID")
        val lease = TunnelAdbManager.acquireMirrorLease() ?: return@synchronized emptyState() + ("reason" to "LOCAL_ADB_BUSY")
        val run = Run(context.applicationContext, serial, lease)
        current = run
        snapshot = emptyState() + mapOf("active" to true, "phase" to "VERIFYING")
        run.worker = Thread({ execute(run) }, "tunnel-adb-p0").apply { isDaemon = true; start() }
        snapshot
    }

    /** Cancellation is bounded by the supervisor, not an ADB kill-server operation. */
    fun cancel(): Map<String, Any> {
        synchronized(lock) { current }?.stop("CANCELLED")
        return snapshot
    }

    private fun publish(run: Run, values: Map<String, Any>) = synchronized(lock) {
        if (current === run) snapshot = snapshot + values
    }

    private fun execute(run: Run) {
        val watchdog = Thread({
            try {
                while (!run.finished) {
                    if (run.failure.get() != null) { run.closeOwned(); run.worker?.interrupt(); return@Thread }
                    if (SystemClock.elapsedRealtime() - run.startedAt > 45_000) {
                        run.stop("DEADLINE_EXCEEDED"); run.closeOwned(); run.worker?.interrupt(); return@Thread
                    }
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) { }
        }, "tunnel-adb-p0-deadline").apply { isDaemon = true; start() }
        var artifact: PackagedAdbHelper.Artifact? = null
        var remoteDir: String? = null
        var remoteCreated = false
        var prefix: List<String>? = null
        val spec = LocalAdbProcessSpec(run.context)
        val environment = spec.environment
        var terminalReason = "PROTOTYPE_FAILED"
        try {
            val probe = LocalAdbIdentityProbe(run.context).probe(run.serial)
            publish(run, mapOf("shellIdentityVerified" to probe.shellIdentityVerified))
            val target = probe.trustedTarget ?: throw Failure(probe.reason.name)
            run.check()
            artifact = try { PackagedAdbHelper.load(run.context) } catch (_: Exception) {
                throw Failure("HELPER_ASSET_INVALID_OR_MISSING")
            }
            val adbPrefix = spec.command(target, emptyList())
            prefix = adbPrefix
            // The path is generated internally; no shell argument is supplied by Flutter/PC.
            val suffix = ByteArray(16).also { random.nextBytes(it) }.hex()
            remoteDir = "/data/local/tmp/tunnel-adb-p0-$suffix"
            val remoteJar = "$remoteDir/helper.jar"
            fun finite(args: List<String>): ByteArray {
                run.check()
                if (LocalAdbTargetPolicy.validate(run.serial) == null) throw Failure("TARGET_NOT_LOCAL")
                val result = BoundedProcessRunner.run(adbPrefix + args, run.context.filesDir, environment)
                if (!result.succeeded) throw Failure("HELPER_PREPARE_FAILED")
                run.check()
                return result.stdout
            }
            publish(run, mapOf("phase" to "PREPARING"))
            finite(listOf("shell", "-T", "-n", "umask 077; mkdir '$remoteDir'"))
            remoteCreated = true
            publish(run, mapOf("cleanupComplete" to false))
            finite(listOf("push", artifact.file.absolutePath, remoteJar))
            val digestOutput = finite(listOf("shell", "-T", "-n", "chmod 400 '$remoteJar' && sha256sum '$remoteJar'"))
                .toString(Charsets.US_ASCII).trim()
            if (digestOutput != "${artifact.sha256}  $remoteJar") throw Failure("HELPER_REMOTE_HASH_INVALID")
            val videoListener = run.own(ServerSocket().apply {
                bind(InetSocketAddress(InetAddress.getByName(AdbWire.LOOPBACK_HOST), 0), 1); soTimeout = 5_000
            })
            val controlListener = run.own(ServerSocket().apply {
                bind(InetSocketAddress(InetAddress.getByName(AdbWire.LOOPBACK_HOST), 0), 1); soTimeout = 5_000
            })
            // Revalidate shell identity immediately before executing the pinned asset.
            if (!LocalAdbIdentityProbe(run.context).probe(run.serial).shellIdentityVerified) throw Failure("IDENTITY_EXPIRED")
            run.check()
            val child = ProcessBuilder(adbPrefix + listOf("shell", "-T",
                "CLASSPATH='$remoteJar' exec app_process / ${PackagedAdbHelper.ENTRY_POINT}"))
                .directory(run.context.filesDir).redirectErrorStream(false).apply {
                    environment().clear(); environment().putAll(environment)
                }.start()
            run.process = child
            run.drain(child.inputStream); run.drain(child.errorStream)
            val secret = ByteArray(32).also { random.nextBytes(it) }
            val epoch = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1)
            try {
                AdbWire.Bootstrap(secret, epoch, videoListener.localPort, controlListener.localPort,
                    1280, 30, 4_000_000, 20).use { AdbWire.writeBootstrap(child.outputStream, it) }
                publish(run, mapOf("phase" to "AUTHENTICATING"))
                val video = run.accept(videoListener, secret, epoch, AdbWire.CHANNEL_VIDEO)
                val control = run.accept(controlListener, secret, epoch, AdbWire.CHANNEL_CONTROL)
                videoListener.close(); controlListener.close()
                publish(run, mapOf("helperAuthenticated" to true, "phase" to "CAPTURING"))
                run.startControl(control, epoch)
                var configs = 0L; var frames = 0L; var keys = 0L; var bytes = 0L
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    run.check()
                    val packet = try { video.read() } catch (e: SocketTimeoutException) {
                        // Only an idle timeout after the sample window may finish it. Never
                        // convert authentication/framing errors or early peer EOF into success.
                        if (SystemClock.elapsedRealtime() >= deadline && frames > 0 && keys > 0) break
                        throw e
                    }
                    if (packet.kind == AdbWire.VIDEO_CONFIG) configs++ else {
                        frames++
                        if (packet.flags and AdbWire.FLAG_KEY_FRAME != 0) keys++
                    }
                    bytes += packet.payloadLength()
                    publish(run, mapOf("configs" to configs, "frames" to frames, "keyFrames" to keys,
                        "bytes" to bytes, "width" to packet.width, "height" to packet.height))
                }
                run.check()
                if (frames == 0L || keys == 0L || snapshot["videoCapabilities"] != true) throw Failure("NO_ENCODED_VIDEO")
                terminalReason = "ENCODED_SAMPLE_RECEIVED"
            } finally { secret.fill(0) }
        } catch (e: Failure) { terminalReason = e.code
        } catch (_: Exception) { terminalReason = "HELPER_OR_TRANSPORT_FAILED"
        } finally {
            terminalReason = run.failure.get() ?: terminalReason
            run.finished = true
            run.closeOwned()
            // Clear cancellation before bounded teardown, then retain the terminal reason.
            Thread.interrupted()
            val child = run.process
            if (child != null) {
                try { child.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) { }
                if (child.isAlive) {
                    try {
                        child.destroyForcibly()
                        child.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (_: Exception) { }
                }
            }
            val threadsStopped = run.joinThreads()
            val processStopped = child == null || !child.isAlive
            if (!threadsStopped || !processStopped) terminalReason = "RESOURCE_CLEANUP_FAILED"
            // Cleanup only our random directory on a still-local selector. Never kill a shared server.
            if (remoteCreated && remoteDir != null && prefix != null && LocalAdbTargetPolicy.validate(run.serial) != null) {
                val cleanup = BoundedProcessRunner.run(prefix + listOf("shell", "-T", "-n",
                    "rm -f '$remoteDir/helper.jar'; rmdir '$remoteDir'"), run.context.filesDir, environment, 2_000)
                publish(run, mapOf("cleanupComplete" to cleanup.succeeded))
            }
            artifact?.file?.delete()
            if (threadsStopped && processStopped) run.lease.close()
            watchdog.interrupt()
            synchronized(lock) {
                if (!threadsStopped || !processStopped) unavailable = true
                if (current === run) {
                    snapshot = snapshot + mapOf("active" to false, "phase" to "FINISHED", "reason" to terminalReason)
                    current = null
                }
            }
        }
    }

    private class Failure(val code: String) : IOException()

    private class Run(val context: Context, val serial: String, val lease: Closeable) {
        val startedAt = SystemClock.elapsedRealtime()
        val failure = AtomicReference<String?>(null)
        @Volatile var worker: Thread? = null
        @Volatile var process: Process? = null
        @Volatile var finished = false
        private val resources = mutableListOf<Closeable>()
        private val threads = mutableListOf<Thread>()
        fun check() { failure.get()?.let { throw Failure(it) }; if (finished) throw Failure("CANCELLED") }
        fun stop(reason: String) { failure.compareAndSet(null, reason) }
        fun <T : Closeable> own(resource: T): T {
            synchronized(resources) {
                if (failure.get() != null || finished) { resource.close(); throw Failure("CANCELLED") }
                resources.add(resource)
            }
            return resource
        }
        fun closeOwned() {
            val copy = synchronized(resources) { resources.toList() }
            copy.reversed().forEach { try { it.close() } catch (_: Exception) { } }
            try { process?.outputStream?.close() } catch (_: Exception) { }
            try { process?.destroy() } catch (_: Exception) { }
        }
        fun accept(listener: ServerSocket, secret: ByteArray, epoch: Long, channel: Int): AdbWire.Session {
            val socket = own(listener.accept())
            socket.soTimeout = 5_000; socket.tcpNoDelay = true
            // Client is the APK protocol role, independent of which side initiated TCP.
            return own(AdbWire.authenticateClient(socket.getInputStream(), socket.getOutputStream(), secret, epoch, channel, random))
        }
        fun drain(input: InputStream) = launch("drain") {
            try {
                val buffer = ByteArray(1024); var total = 0
                while (!finished) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > 8192) { stop("HELPER_OUTPUT_LIMIT"); break }
                }
            } catch (_: Exception) { if (!finished) stop("HELPER_IO_FAILED")
            } finally { try { input.close() } catch (_: Exception) { } }
        }
        fun startControl(control: AdbWire.Session, epoch: Long) = launch("control") {
            try {
                var sequence = 1L
                control.write(AdbWire.Packet.command(AdbWire.REQUEST_KEYFRAME, epoch, sequence++))
                while (!finished && failure.get() == null) {
                    control.write(AdbWire.Packet.command(AdbWire.PING, epoch, sequence++))
                    var pong = false
                    var packets = 0
                    while (!pong && ++packets <= 4) {
                        val packet = control.read()
                        if (packet.kind == AdbWire.PONG) pong = true else {
                            val caps = ByteBuffer.wrap(packet.payloadCopy())
                            val codecs = caps.int; val bits = caps.int
                            publish(this, mapOf("videoCapabilities" to
                                (codecs and AdbWire.CODEC_H264 != 0 && bits and AdbWire.CAP_VIDEO != 0)))
                        }
                    }
                    if (!pong) throw IOException()
                    Thread.sleep(1_000)
                }
            } catch (_: Exception) { if (!finished) stop("CONTROL_CHANNEL_FAILED") }
        }
        private fun launch(name: String, body: () -> Unit) {
            threads.add(Thread(body, "tunnel-adb-p0-$name").apply { isDaemon = true; start() })
        }
        fun joinThreads(): Boolean {
            threads.forEach { it.interrupt() }
            threads.forEach { try { it.join(300) } catch (_: InterruptedException) { Thread.interrupted() } }
            return threads.none { it.isAlive }
        }
    }
}
