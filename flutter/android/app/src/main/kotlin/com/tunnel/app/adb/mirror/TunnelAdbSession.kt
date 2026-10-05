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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
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
    private val initialMode: Int = 0,
) : Closeable {
    interface Events {
        fun packet(packet: AdbWire.Packet): Boolean
        fun capabilities(mask: Int)
        fun videoState(taskId: Int, state: Int)
        fun videoDiagnostic(taskId: Int, code: String)
        fun effectsState(applied: Int)
        fun ended(reason: String, cleanupComplete: Boolean)
    }

    private val random = SecureRandom()
    private val closed = AtomicBoolean()
    private val finished = AtomicBoolean()
    private val channelsClosedAt = AtomicLong()
    private val resources = mutableListOf<Closeable>()
    private val threads = mutableListOf<Thread>()
    private val writeLock = Any()
    private val ids = AtomicLong()
    private val lastKeyframeRequest = AtomicLong()
    private val keyframePending = AtomicBoolean()
    private val videoStalled = AtomicBoolean()
    // Never let a slow JNI/remote consumer back up the authenticated loopback
    // reader. Four bounded records; overflow keeps only config + a fresh IDR.
    private val videoQueue = ArrayBlockingQueue<AdbWire.Packet>(4)
    private val videoQueueLock = Any()
    private var deliveryConfig: AdbWire.Packet? = null
    private var awaitingKeyframe = true
    private var needsConfiguration = true
    private data class Pending(val since: Long, val result: CompletableFuture<AdbCommands.Result>)
    private val pending = ConcurrentHashMap<Long, Pending>()
    @Volatile private var control: AdbWire.Session? = null
    @Volatile private var process: Process? = null
    @Volatile private var authenticated = false
    @Volatile private var capturePaused = initialMode == 3
    @Volatile private var videoTaskId = 0
    @Volatile private var lastVideoAt = 0L
    @Volatile private var writeSince = 0L
    @Volatile private var failure = "STOPPED"
    private var sequence = 0L
    private val startedAt = SystemClock.elapsedRealtime()
    // Only locally defined identifiers may cross the status channel. Never expose
    // adb stderr, shell arguments, socket addresses or exception messages.
    private class SessionFailure(val code: String) : IOException(code)

    fun start() { launch("supervisor") { execute() } }
    fun setCapturePaused(paused: Boolean) { if (!paused) lastVideoAt = SystemClock.elapsedRealtime(); capturePaused = paused }

    fun videoTask(mode: Int, taskId: Int): CompletableFuture<AdbCommands.Result> {
        require(mode in 0..4 && taskId > 0)
        synchronized(videoQueueLock) {
            // A retry uses a new operation ID but the same video identity. Keep
            // its decoder configuration, queued frames and freshness intact.
            // A stale retry must never rewind the currently selected task either.
            if (taskId > videoTaskId) {
                videoTaskId = taskId
                videoQueue.clear()
                deliveryConfig = null
                awaitingKeyframe = true
                needsConfiguration = true
                videoStalled.set(false)
                setCapturePaused(mode == 3)
            }
        }
        return operation(AdbCommands.VIDEO_TASK, mode, taskId)
    }

    fun keyframe() {
        keyframePending.set(true)
        flushKeyframeRequest()
    }

    private fun flushKeyframeRequest() {
        if (!keyframePending.get() || control == null || closed.get() || capturePaused) return
        val now = SystemClock.elapsedRealtime()
        val last = lastKeyframeRequest.get()
        if (now - last < 500 || !lastKeyframeRequest.compareAndSet(last, now)) return
        if (!keyframePending.getAndSet(false)) return
        try { write(AdbWire.REQUEST_KEYFRAME) } catch (_: Exception) { fail("CONTROL_FAILED") }
    }

    private fun enqueueVideo(packet: AdbWire.Packet) {
        var requestKey = false
        synchronized(videoQueueLock) {
            if (packet.taskId != videoTaskId || capturePaused) return
            if (packet.kind == AdbWire.VIDEO_CONFIG) {
                videoQueue.clear()
                deliveryConfig = packet
                needsConfiguration = true
                awaitingKeyframe = true
                // Configuration and its first IDR enter the delivery queue together.
                // This prevents overflow from retaining an undecodable P-frame tail.
                requestKey = true
            } else {
                val config = deliveryConfig
                if (config == null || config.configRevision != packet.configRevision) return
                val key = packet.flags and AdbWire.FLAG_KEY_FRAME != 0
                if (awaitingKeyframe && !key) { requestKey = true }
                else {
                    val required = if (needsConfiguration) 2 else 1
                    val queuedBytes = videoQueue.sumOf { it.payloadLength().toLong() }
                    val nextBytes = packet.payloadLength().toLong() + if (needsConfiguration) config.payloadLength() else 0
                    if (videoQueue.remainingCapacity() < required
                        || queuedBytes + nextBytes > AdbWire.MAX_PAYLOAD.toLong() + AdbWire.MAX_CONFIG) {
                        videoQueue.clear()
                        needsConfiguration = true
                        awaitingKeyframe = true
                    }
                    if (!awaitingKeyframe || key) {
                        if (needsConfiguration) videoQueue.offer(config)
                        videoQueue.offer(packet)
                        needsConfiguration = false
                        awaitingKeyframe = false
                    } else requestKey = true
                }
            }
        }
        if (requestKey) keyframe()
    }

    private fun startVideoDelivery() = launch("video-delivery") {
        while (!closed.get()) {
            val packet = videoQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (packet.taskId != videoTaskId || capturePaused) continue
            // A temporarily unavailable JNI/remote consumer has no authority to
            // tear down the local shell, input or producer.
            val accepted = try { events.packet(packet) } catch (_: Exception) { false }
            if (accepted) {
                if (packet.taskId == videoTaskId && packet.kind == AdbWire.VIDEO_FRAME
                    && videoStalled.compareAndSet(true, false))
                    events.videoState(packet.taskId, 1)
            } else {
                synchronized(videoQueueLock) {
                    if (packet.taskId != videoTaskId) return@synchronized
                    videoQueue.clear()
                    awaitingKeyframe = true
                    needsConfiguration = true
                }
                keyframe()
            }
        }
    }

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
        var failureStage = "ADB_TRANSPORT_UNAVAILABLE"
        launch("watchdog") {
            while (!finished.get()) {
                val now = SystemClock.elapsedRealtime()
                // The writer may clear this volatile marker between two reads.
                // Subtracting a second read of zero falsely kills a healthy helper.
                val writeStarted = writeSince
                if (!authenticated && now - startedAt > 45_000) fail("HELPER_START_TIMEOUT")
                if (writeStarted != 0L && now - writeStarted > 2_000) fail("HELPER_WRITE_TIMEOUT")
                if (authenticated && !capturePaused && now - lastVideoAt > 15_000) {
                    val task = videoTaskId
                    // Loss of frames is telemetry, never an implicit OFF. The
                    // helper retains this task and retries its provider/codec.
                    if (videoStalled.compareAndSet(false, true)) events.videoState(task, 2)
                    keyframePending.set(true)
                }
                pending.entries.filter { now - it.value.since > 10_000 }.forEach { entry ->
                    if (pending.remove(entry.key, entry.value))
                        entry.value.result.completeExceptionally(IOException("HELPER_OPERATION_TIMEOUT"))
                }
                Thread.sleep(100)
            }
        }
        try {
            val spec = LocalAdbProcessSpec(context)
            processSpec = spec
            checkOpen()
            val identity = LocalAdbIdentityProbe(context).probe(serial)
            val trusted = identity.trustedTarget ?: throw SessionFailure("ADB_PROBE_" + identity.reason.name)
            target = trusted
            failureStage = "HELPER_ASSET_INVALID"
            artifact = PackagedAdbHelper.load(context)
            remoteDirectory = "/data/local/tmp/tunnel-adb-${ByteArray(16).also(random::nextBytes).hex()}"
            val directory = remoteDirectory
            val remoteJar = "$directory/helper.jar"
            fun finite(arguments: List<String>, timeout: Long = 10_000): ByteArray {
                checkOpen()
                if (LocalAdbTargetPolicy.validate(serial) == null) throw SessionFailure("TARGET_NOT_LOCAL")
                val response = BoundedProcessRunner.run(spec.command(trusted, arguments), spec.workingDirectory, spec.environment, timeout)
                if (!response.succeeded) throw SessionFailure(failureStage)
                checkOpen(); return response.stdout
            }
            failureStage = "HELPER_DIRECTORY_FAILED"
            finite(listOf("shell", "-T", "-n", "umask 077; mkdir '$directory'"))
            created = true
            failureStage = "HELPER_PUSH_FAILED"
            finite(listOf("push", artifact.file.absolutePath, remoteJar))
            failureStage = "HELPER_HASH_INVALID"
            val digest = finite(listOf("shell", "-T", "-n", "chmod 400 '$remoteJar' && sha256sum '$remoteJar'"))
                .toString(Charsets.US_ASCII).trim()
            if (digest != "${artifact.sha256}  $remoteJar") throw SessionFailure("HELPER_HASH_INVALID")
            failureStage = "HELPER_LISTENER_FAILED"
            val videoListener = listener()
            val controlListener = listener()
            val beforeLaunch = LocalAdbIdentityProbe(context).probe(serial)
            if (!beforeLaunch.shellIdentityVerified) throw SessionFailure("ADB_PROBE_" + beforeLaunch.reason.name)
            checkOpen()
            failureStage = "HELPER_PROCESS_START_FAILED"
            val child = spec.processBuilder(trusted, listOf("shell", "-T",
                "CLASSPATH='$remoteJar' exec app_process / ${PackagedAdbHelper.ENTRY_POINT}")).start()
            process = child
            checkOpen()
            drain(child.inputStream); drain(child.errorStream, diagnostics = true)
            val secret = ByteArray(32).also(random::nextBytes)
            try {
                failureStage = "HELPER_BOOTSTRAP_FAILED"
                AdbWire.Bootstrap(secret, epoch, videoListener.localPort, controlListener.localPort, 1280, 30, 4_000_000, AdbWire.SESSION_DURATION, initialMode)
                    .use { AdbWire.writeBootstrap(child.outputStream, it) }
                failureStage = "HELPER_VIDEO_HANDSHAKE_FAILED"
                val video = accept(videoListener, secret, AdbWire.CHANNEL_VIDEO)
                failureStage = "HELPER_CONTROL_HANDSHAKE_FAILED"
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
                            AdbWire.VIDEO_STATE -> {
                                val state = ByteBuffer.wrap(packet.payloadCopy())
                                val task = state.int; val status = state.int
                                if (task == videoTaskId) {
                                    if (status == 0) capturePaused = true
                                    if (status == 2) videoStalled.set(true)
                                }
                                events.videoState(task, status)
                            }
                            AdbWire.PONG -> Unit
                            AdbWire.EFFECTS_STATE -> events.effectsState(ByteBuffer.wrap(packet.payloadCopy()).int)
                            else -> throw IOException("CONTROL_INVALID")
                        }
                    }
                }
                launch("heartbeat") {
                    var nextPing = 0L
                    while (!closed.get()) {
                        val now = SystemClock.elapsedRealtime()
                        if (now >= nextPing) { write(AdbWire.PING); nextPing = now + 1_000 }
                        // The watchdog never waits for writeLock/socket I/O. This
                        // existing writer also coalesces throttled keyframe requests.
                        flushKeyframeRequest()
                        Thread.sleep(100)
                    }
                }
                keyframe()
                failureStage = "HELPER_VIDEO_CHANNEL_FAILED"
                startVideoDelivery()
                while (!closed.get()) {
                    val packet = video.read()
                    if (packet.taskId == videoTaskId && packet.kind == AdbWire.VIDEO_FRAME)
                        lastVideoAt = SystemClock.elapsedRealtime()
                    enqueueVideo(packet)
                }
            } finally { secret.fill(0) }
        } catch (e: Exception) { if (!closed.get()) fail(if (e is SessionFailure) e.code else failureStage)
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
            val threadsStopped = children.none { it.isAlive }
            if (processStopped) releaseLeaseAfterRemoteGrace()
            else {
                // Do not overlap two UiAutomation owners. A slow OEM process may
                // exit after our teardown budget; release its lease when it does,
                // rather than poisoning all future starts for this app lifetime.
                Thread({
                    while (child?.isAlive == true) {
                        try { child?.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) }
                        catch (_: InterruptedException) { Thread.interrupted() }
                    }
                    releaseLeaseAfterRemoteGrace()
                }, "tunnel-adb-process-reaper").apply { isDaemon = true; start() }
            }
            events.ended(failure, cleaned && processStopped && threadsStopped)
        }
    }

    private fun releaseLeaseAfterRemoteGrace() {
        // The child is adb's client, not app_process. Allow the remote helper's
        // 2-second hard-exit guard to finish after its sockets are closed before
        // admitting another UiAutomation owner. This wait never runs on the UI.
        val closedAt = channelsClosedAt.get().takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
        val deadline = closedAt + 2_500
        var interrupted = false
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            try { Thread.sleep(remaining) } catch (_: InterruptedException) { interrupted = true }
        }
        lease.close()
        if (interrupted) Thread.currentThread().interrupt()
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
    private fun drain(input: InputStream, diagnostics: Boolean = false) = launch("drain") {
        try {
            val buffer = ByteArray(2048); var window = SystemClock.elapsedRealtime(); var bytes = 0
            val line = StringBuilder(128)
            var discardLine = false
            while (!closed.get()) {
                val n = input.read(buffer); if (n < 0) break
                if (SystemClock.elapsedRealtime() - window > 60_000) { window = SystemClock.elapsedRealtime(); bytes = 0 }
                bytes += n; if (bytes > 64 * 1024) throw IOException("HELPER_OUTPUT_LIMIT")
                if (diagnostics) for (i in 0 until n) {
                    val ch = buffer[i].toInt() and 255
                    if (ch == 10) {
                        if (!discardLine) readVideoDiagnostic(line.toString())
                        line.setLength(0); discardLine = false
                    } else if (ch != 13) {
                        // Never retain arbitrary native logs, identifiers or screen text.
                        if (ch !in 32..126 || line.length >= 127) {
                            line.setLength(0); discardLine = true
                        } else if (!discardLine) line.append(ch.toChar())
                    }
                }
            }
        } finally { try { input.close() } catch (_: Exception) { } }
    }
    private fun readVideoDiagnostic(line: String) {
        val parts = line.split(':')
        if (parts.size != 3 || parts[0] != "TUNNEL_ADB_VIDEO") return
        val task = parts[1].toIntOrNull() ?: return
        if (task <= 0 || task != videoTaskId || capturePaused || closed.get()) return
        val code = parts[2]
        if (code !in setOf("FRAME_ACQUIRE_FAILED", "BITMAP_ENCODER_UNSUPPORTED",
                "ENCODER_OUTPUT_STALLED", "ENCODER_FAILED", "HARDWARE_H264_UNAVAILABLE",
                "ENCODER_SIZE_UNSUPPORTED", "CODEC_CONFIG_TOO_LARGE", "NAL_FORMAT_UNSUPPORTED",
                "NAL_FORMAT_INVALID", "PARTIAL_ACCESS_UNIT_UNSUPPORTED", "CODEC_BUFFER_INVALID",
                "CODEC_PTS_INVALID", "CODEC_DIMENSIONS_CHANGED", "ENCODER_ENDED")) return
        events.videoDiagnostic(task, code)
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
        synchronized(videoQueueLock) { videoQueue.clear(); deliveryConfig = null }
        // Closing raw sockets first interrupts any blocked protocol read/write.
        val owned = synchronized(resources) { resources.toList() }
        // Socket.close() interrupts streams without waiting on protocol monitors.
        owned.filterIsInstance<java.net.Socket>().forEach { try { it.close() } catch (_: Exception) { } }
        channelsClosedAt.compareAndSet(0, SystemClock.elapsedRealtime())
        owned.filterNot { it is java.net.Socket }.forEach { try { it.close() } catch (_: Exception) { } }
        try { process?.outputStream?.close() } catch (_: Exception) { }
        try { process?.destroy() } catch (_: Exception) { }
        pending.values.forEach { it.result.completeExceptionally(IOException("HELPER_CLOSED")) }; pending.clear()
    }
}
