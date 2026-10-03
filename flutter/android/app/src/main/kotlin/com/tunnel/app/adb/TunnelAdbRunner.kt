package com.tunnel.app.adb

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.tunnel.app.adb.probe.BoundedProcessResult
import com.tunnel.app.adb.probe.BoundedProcessRunner
import com.tunnel.app.adb.probe.LocalAdbIdentityProbe
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/** Fixed local ADB operations. No ordinary sh, global server kill, hidden grants or settings writes. */
class TunnelAdbRunner(context: Context) {
    private val app = context.applicationContext
    private val spec = LocalAdbProcessSpec(app)
    private val output = StringBuilder()
    private val operation = AtomicReference<Thread?>(null)
    @Volatile private var selectedSerial: String? = null
    @Volatile private var pairing = false
    @Volatile private var paired = false
    @Volatile private var verified = false
    @Volatile private var lastError = ""
    @Volatile private var deadline = Long.MAX_VALUE
    val adbPath: String get() = spec.adbPath
    fun isBinaryAvailable(): Boolean = File(adbPath).let { it.isFile && it.canRead() }
    fun isBinaryExecutable(): Boolean = isBinaryAvailable() && File(adbPath).canExecute()
    fun processEnvironment(): Map<String, String> = spec.environment
    fun selectedLocalSerial(): String? = selectedSerial?.takeIf { LocalAdbTargetPolicy.validate(it) != null }

    fun startServer() = action {
        requireReadyBinary()
        requireSuccess(run(listOf("start-server"), 8_000), "SERVER_START_FAILED")
        val previous = selectedLocalSerial()
        val devices = run(listOf("devices"))
        val candidates = if (devices.succeeded) devices.stdout.toString(Charsets.US_ASCII).lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it[0] }.filter { LocalAdbTargetPolicy.validate(it) != null }.toList() else emptyList()
        if (previous != null && verify(previous)) return@action
        for (candidate in candidates.take(4)) if (verify(candidate)) return@action
        val endpoints = TunnelAdbDnsDiscover(app).discoverEndpoints(TunnelAdbDnsDiscover.Kind.CONNECT, cancelled = ::cancelled)
        for (endpoint in endpoints.take(12)) if (connectEndpoint(endpoint)) return@action
        throw Failure("CONNECT_ADDRESS_REQUIRED")
    }

    /** Explicit connection-port entry, never the pairing-code dialog's port. */
    fun connect(endpoint: String) = action {
        requireReadyBinary()
        requireSuccess(run(listOf("start-server"), 8_000), "SERVER_START_FAILED")
        for (candidate in endpointCandidates(endpoint)) if (connectEndpoint(candidate)) return@action
        throw Failure("CONNECT_FAILED")
    }

    /** Port, exact local host:port, or 'auto' for _adb-tls-pairing (code still required). */
    fun pair(port: String, pairingCode: String, connectionPort: Int? = null,
             progress: (String) -> Unit = {}) = action {
        paired = false
        verified = false
        selectedSerial = null
        requireReadyBinary()
        if (!Regex("[0-9]{6}").matches(pairingCode)) throw Failure("PAIR_CODE_INVALID")
        if (connectionPort != null && connectionPort !in 1..65535) throw Failure("CONNECT_PORT_INVALID")
        val endpoints = if (port.isBlank() || port == "auto")
            TunnelAdbDnsDiscover(app).discoverEndpoints(TunnelAdbDnsDiscover.Kind.PAIR, cancelled = ::cancelled)
        else endpointCandidates(port)
        if (endpoints.isEmpty()) throw Failure("PAIR_ADDRESS_REQUIRED")
        pairing = true
        paired = false
        requireSuccess(run(listOf("start-server"), 8_000), "SERVER_START_FAILED")
        progress("PAIRING")
        val input = (pairingCode + "\n").toByteArray(Charsets.US_ASCII)
        var pairFailure = "PAIR_FAILED"
        try {
            for (endpoint in endpoints.take(4)) {
                checkCancelled()
                if (LocalAdbTargetPolicy.validate(endpoint) == null) continue
                val result = run(listOf("pair", endpoint), 12_000, input)
                val text = (result.stdout.toString(Charsets.US_ASCII) +
                    result.stderr.toString(Charsets.US_ASCII)).lowercase()
                if (result.succeeded && text.contains("successfully paired")) {
                    paired = true
                    break
                }
                if (text.contains("wrong password") || text.contains("incorrect pairing") ||
                    text.contains("authentication failed")) throw Failure("PAIR_CODE_REJECTED")
                pairFailure = when {
                    result.failure != null -> "PAIR_PROCESS_" + result.failure.name
                    text.contains("failed to read response") -> "PAIR_RESPONSE_REJECTED"
                    text.contains("connection refused") || text.contains("failed to connect") -> "PAIR_PORT_UNREACHABLE"
                    else -> "PAIR_FAILED"
                }
            }
            if (!paired) throw Failure(pairFailure)
        } finally { input.fill(0); pairing = false }
        // Pairing stores the RSA key; it does not create an adb transport. Never connect
        // to the pairing port. The independent _adb-tls-connect port is required.
        progress("CONNECTING")
        if (connectionPort != null) {
            for (endpoint in endpointCandidates(connectionPort.toString())) {
                if (connectEndpoint(endpoint) { progress("VERIFYING") }) return@action
            }
            throw Failure("PAIRED_CONNECT_FAILED")
        }
        // An already authorized transport may have appeared before the NSD callback.
        if (verifyExistingDevices { progress("VERIFYING") }) return@action
        repeat(3) {
            checkCancelled()
            val found = TunnelAdbDnsDiscover(app).discoverEndpoints(
                TunnelAdbDnsDiscover.Kind.CONNECT, remaining(4_000), ::cancelled)
            for (endpoint in found.take(12)) {
                if (connectEndpoint(endpoint) { progress("VERIFYING") }) return@action
            }
            if (verifyExistingDevices { progress("VERIFYING") }) return@action
        }
        throw Failure("PAIRED_CONNECT_REQUIRED")
    }

    /** Compatibility entry refuses to impersonate a shell-UID ADB connection. */
    fun startLocalShell() = action { throw Failure("NON_ADB_SHELL_DISABLED") }

    /** Cancel only our finite operation. Shared adb daemon and helper are left alive. */
    fun cancelPending() { operation.get()?.interrupt() }

    fun stopServer() {
        cancelPending()
        action { selectedSerial = null; verified = false; append("本地终端已停止；ADB 服务保持运行。") }
    }

    /** Only explicit phone-local terminal text. Never route peer messages here. */
    fun sendCommand(command: String) = action {
        if (command.isBlank() || command.length > 8192 || command.contains('\u0000')) throw Failure("COMMAND_INVALID")
        val serial = selectedLocalSerial() ?: throw Failure("TRANSPORT_UNAVAILABLE")
        if (!verify(serial)) throw Failure("IDENTITY_EXPIRED")
        val target = LocalAdbTargetPolicy.validate(serial) ?: throw Failure("TARGET_NOT_LOCAL")
        val result = BoundedProcessRunner.run(spec.command(target, listOf("shell", "-T", "-n", command)),
            spec.workingDirectory, spec.environment, 15_000, 16 * 1024, cancelled = ::cancelled)
        checkCancelled()
        // Local in-memory terminal only; never persisted or sent to peer status.
        append(result.stdout.toString(Charsets.UTF_8))
        append(result.stderr.toString(Charsets.UTF_8))
        if (!result.succeeded) throw Failure(result.failure?.name ?: "COMMAND_FAILED")
    }

    fun snapshotOutput(): String = synchronized(output) { output.toString() }
    fun state(): TunnelAdbState = TunnelAdbState(
        supported = Build.VERSION.SDK_INT >= 30, initialized = true,
        binaryAvailable = isBinaryAvailable(), binaryExecutable = isBinaryExecutable(),
        pairing = pairing, paired = paired, connected = verified && selectedLocalSerial() != null,
        shellReady = verified && selectedLocalSerial() != null,
        busy = operation.get() != null, mirrorActive = LocalAdbAccess.isMirrorActive(),
        output = snapshotOutput(), lastError = lastError,
    )

    private fun action(body: () -> Unit) {
        val lease = LocalAdbAccess.acquire(false) ?: run { lastError = "ADB_BUSY"; append("ADB_BUSY"); return }
        val thread = Thread.currentThread()
        operation.set(thread)
        deadline = SystemClock.elapsedRealtime() + 45_000
        try {
            lastError = ""
            if (Build.VERSION.SDK_INT < 30) throw Failure("UNSUPPORTED_ANDROID")
            checkCancelled()
            body()
        } catch (_: InterruptedException) { fail("CANCELLED")
        } catch (e: Failure) { fail(e.code)
        } catch (_: Exception) { fail("ADB_OPERATION_FAILED")
        } finally {
            pairing = false
            operation.compareAndSet(thread, null)
            Thread.interrupted()
            lease.close()
        }
    }
    private fun run(args: List<String>, timeout: Long = 5_000, input: ByteArray? = null): BoundedProcessResult {
        checkCancelled()
        return BoundedProcessRunner.run(spec.baseCommand() + args, spec.workingDirectory, spec.environment,
            remaining(timeout), 16 * 1024, stdin = input, cancelled = ::cancelled).also { checkCancelled() }
    }
    private fun verify(serial: String): Boolean {
        checkCancelled()
        val result = LocalAdbIdentityProbe(app).probe(serial, cancelled = ::cancelled,
            timeoutMillis = remaining(5_000))
        checkCancelled()
        if (!result.shellIdentityVerified) return false
        selectedSerial = result.trustedTarget?.serial
        verified = selectedSerial != null
        if (verified) append("已核实本机 ADB shell 身份（uid 2000）。")
        return verified
    }
    private fun connectEndpoint(endpoint: String, beforeVerify: () -> Unit = {}): Boolean {
        if (LocalAdbTargetPolicy.validate(endpoint) == null) return false
        if (!endpointListening(endpoint)) return false
        if (!run(listOf("connect", endpoint), 5_000).succeeded) return false
        beforeVerify()
        // adbd/TLS can finish accepting before the transport is reported online.
        // Give the exact local selector a short settle window; never select an
        // unrelated device or claim success merely from adb connect's exit code.
        val readyBy = SystemClock.elapsedRealtime() + remaining(3_000)
        do {
            if (verify(endpoint)) return true
            if (SystemClock.elapsedRealtime() >= readyBy) break
            Thread.sleep(150)
        } while (!cancelled())
        checkCancelled()
        return false
    }
    private fun endpointListening(endpoint: String): Boolean {
        checkCancelled()
        if (LocalAdbTargetPolicy.validate(endpoint) == null) return false
        val host = endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = endpoint.substringAfterLast(':').toIntOrNull() ?: return false
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), remaining(400).toInt()) }
            checkCancelled()
            true
        } catch (_: Exception) { checkCancelled(); false }
    }
    private fun verifyExistingDevices(beforeVerify: () -> Unit): Boolean {
        val devices = run(listOf("devices"))
        if (!devices.succeeded) return false
        val candidates = devices.stdout.toString(Charsets.US_ASCII).lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it[0] }.filter { LocalAdbTargetPolicy.validate(it) != null }.take(4)
        for (serial in candidates) { beforeVerify(); if (verify(serial)) return true }
        return false
    }
    private fun endpointCandidates(input: String): List<String> {
        if (Regex("[1-9][0-9]{0,4}").matches(input) && (input.toIntOrNull() ?: 0) in 1..65535) {
            return listOfNotNull("localhost:" + input, "127.0.0.1:" + input,
                TunnelAdbDnsDiscover.localIpv4Address(app)?.let { it + ":" + input })
        }
        return listOf(LocalAdbTargetPolicy.validate(input)?.serial ?: throw Failure("TARGET_NOT_LOCAL"))
    }
    private fun requireReadyBinary() {
        if (!isBinaryAvailable()) throw Failure("ADB_BINARY_MISSING")
        if (!isBinaryExecutable()) throw Failure("ADB_BINARY_NOT_EXECUTABLE")
    }
    private fun requireSuccess(result: BoundedProcessResult, code: String) {
        if (!result.succeeded) throw Failure(code)
    }
    private fun cancelled(): Boolean = Thread.currentThread().isInterrupted || SystemClock.elapsedRealtime() >= deadline
    private fun remaining(limit: Long): Long {
        checkCancelled()
        return (deadline - SystemClock.elapsedRealtime()).coerceIn(1, limit)
    }
    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) throw Failure("CANCELLED")
        if (SystemClock.elapsedRealtime() >= deadline) throw Failure("OPERATION_TIMEOUT")
    }
    private class Failure(val code: String) : Exception()
    private fun fail(code: String) { lastError = code; verified = false; append(code) }
    private fun append(text: String) = synchronized(output) {
        if (text.isNotBlank()) output.append(text.take(32 * 1024)).append('\n')
        if (output.length > 32 * 1024) output.delete(0, output.length - 32 * 1024)
    }
}
