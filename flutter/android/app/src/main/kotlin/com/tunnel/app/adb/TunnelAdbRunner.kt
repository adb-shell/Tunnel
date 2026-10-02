package com.tunnel.app.adb

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.tunnel.app.adb.probe.BoundedProcessResult
import com.tunnel.app.adb.probe.BoundedProcessRunner
import com.tunnel.app.adb.probe.LocalAdbIdentityProbe
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Local-user ADB actions. No ordinary sh, global server kill, hidden grants or settings writes. */
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
        for (endpoint in endpoints.take(4)) if (connectEndpoint(endpoint)) return@action
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
    fun pair(port: String, pairingCode: String) = action {
        paired = false
        requireReadyBinary()
        if (!Regex("[0-9]{6}").matches(pairingCode)) throw Failure("PAIR_CODE_INVALID")
        val endpoints = if (port.isBlank() || port == "auto")
            TunnelAdbDnsDiscover(app).discoverEndpoints(TunnelAdbDnsDiscover.Kind.PAIR, cancelled = ::cancelled)
        else endpointCandidates(port)
        if (endpoints.isEmpty()) throw Failure("PAIR_ADDRESS_REQUIRED")
        pairing = true
        paired = false
        requireSuccess(run(listOf("start-server"), 8_000), "SERVER_START_FAILED")
        val input = (pairingCode + "\n").toByteArray(Charsets.US_ASCII)
        try {
            for (endpoint in endpoints.take(4)) {
                checkCancelled()
                if (LocalAdbTargetPolicy.validate(endpoint) == null) continue
                val result = run(listOf("pair", endpoint), 12_000, input)
                val text = result.stdout.toString(Charsets.US_ASCII).lowercase()
                if (result.succeeded && text.contains("successfully paired")) {
                    paired = true
                    append("配对完成；请自动发现或手动输入独立的连接端口。")
                    return@action
                }
            }
            throw Failure("PAIR_FAILED")
        } finally { input.fill(0); pairing = false }
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
            timeout, 16 * 1024, stdin = input, cancelled = ::cancelled).also { checkCancelled() }
    }
    private fun verify(serial: String): Boolean {
        checkCancelled()
        val result = LocalAdbIdentityProbe(app).probe(serial)
        checkCancelled()
        if (!result.shellIdentityVerified) return false
        selectedSerial = result.trustedTarget?.serial
        verified = selectedSerial != null
        if (verified) append("已核实本机 ADB shell 身份（uid 2000）。")
        return verified
    }
    private fun connectEndpoint(endpoint: String): Boolean {
        if (LocalAdbTargetPolicy.validate(endpoint) == null) return false
        if (!run(listOf("connect", endpoint), 5_000).succeeded) return false
        return verify(endpoint)
    }
    private fun endpointCandidates(input: String): List<String> {
        if (Regex("[1-9][0-9]{0,4}").matches(input) && (input.toIntOrNull() ?: 0) in 1..65535) {
            return listOfNotNull("127.0.0.1:" + input, "localhost:" + input,
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
