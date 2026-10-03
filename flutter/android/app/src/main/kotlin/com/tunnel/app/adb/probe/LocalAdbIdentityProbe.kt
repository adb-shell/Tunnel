package com.tunnel.app.adb.probe

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import com.tunnel.app.adb.LocalAdbProcessSpec
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.Semaphore

enum class AdbProbeReason {
    VERIFIED_SHELL_ONLY, UNSUPPORTED_ANDROID, BACKGROUND_THREAD_REQUIRED, BUSY,
    ADB_MISSING, ADB_NOT_EXECUTABLE, TARGET_NOT_LOCAL, SERVER_UNAVAILABLE,
    TRANSPORT_UNAVAILABLE, PROCESS_FAILED, PROCESS_TIMEOUT, PROCESS_INTERRUPTED,
    OUTPUT_LIMIT, IDENTITY_INVALID, SHELL_UID_REQUIRED, PROBE_FAILED
}

/** A diagnostic snapshot, never an authorization or video/helper-ready token. */
class AdbIdentityProbeResult internal constructor(
    val supported: Boolean,
    val binaryAvailable: Boolean,
    val binaryExecutable: Boolean,
    val targetLocal: Boolean,
    val transportConnected: Boolean,
    val shellIdentityVerified: Boolean,
    val reason: AdbProbeReason,
    val elapsedMs: Long,
    val checkedAtElapsedRealtimeMs: Long,
    // Snapshot, not authority: revalidate locality/freshness before starting the helper.
    internal val trustedTarget: LocalAdbTarget?,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "supported" to supported,
        "binaryAvailable" to binaryAvailable,
        "binaryExecutable" to binaryExecutable,
        "targetLocal" to targetLocal,
        "transportConnected" to transportConnected,
        "shellIdentityVerified" to shellIdentityVerified,
        "helperVerified" to false,
        "captureAvailable" to false,
        "inputAvailable" to false,
        "reason" to reason.name,
        "elapsedMs" to elapsedMs,
        "checkedAtElapsedRealtimeMs" to checkedAtElapsedRealtimeMs,
    )

    override fun toString(): String = "AdbIdentityProbeResult(reason=$reason)"
}

/**
 * Fixed local identity preflight. Does not connect a transport, pair, grant, change settings or kill ADB.
 * Invoke on a worker thread. The bounded CLI may auto-start Tunnel's private daemon; get-state
 * never establishes an adbd transport. Do not add an unbounded raw Unix-socket connect here.
 */
class LocalAdbIdentityProbe(context: Context) {
    private val appContext = context.applicationContext

    fun probe(serial: String, cancelled: () -> Boolean = { false },
              timeoutMillis: Long = 5_000): AdbIdentityProbeResult {
        val startedAt = SystemClock.elapsedRealtime()
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val adb = File(appContext.applicationInfo.nativeLibraryDir, "libadb.so")
        val available = adb.isFile && adb.canRead()
        val executable = available && adb.canExecute()
        var target: LocalAdbTarget? = null
        var connected = false
        fun result(reason: AdbProbeReason, verified: Boolean = false) = AdbIdentityProbeResult(
            supported, available, executable, target != null, connected, verified, reason,
            SystemClock.elapsedRealtime() - startedAt, SystemClock.elapsedRealtime(),
            if (verified) target else null,
        )
        if (!supported) return result(AdbProbeReason.UNSUPPORTED_ANDROID)
        if (Looper.myLooper() == Looper.getMainLooper()) return result(AdbProbeReason.BACKGROUND_THREAD_REQUIRED)
        if (!available) return result(AdbProbeReason.ADB_MISSING)
        if (!executable) return result(AdbProbeReason.ADB_NOT_EXECUTABLE)
        if (!gate.tryAcquire()) return result(AdbProbeReason.BUSY)
        try {
            target = LocalAdbTargetPolicy.validate(serial) ?: return result(AdbProbeReason.TARGET_NOT_LOCAL)
            if (cancelled()) return result(AdbProbeReason.PROCESS_INTERRUPTED)
            // No discovery or implicit target selection; the local pairing runner owns transport.
            val spec = LocalAdbProcessSpec(appContext)
            val prefix = spec.command(target!!, emptyList())
            val environment = spec.environment
            val transport = BoundedProcessRunner.run(prefix + "get-state", appContext.filesDir, environment,
                timeoutMillis.coerceIn(1, 5_000), cancelled = cancelled)
            if (!transport.succeeded) return result(processReason(transport, AdbProbeReason.TRANSPORT_UNAVAILABLE))
            if (transport.stdout.toString(Charsets.US_ASCII).trim() != "device") {
                return result(AdbProbeReason.TRANSPORT_UNAVAILABLE)
            }
            connected = true
            // Interface addresses can disappear between preflight and the sensitive child command.
            if (LocalAdbTargetPolicy.validate(serial) == null) {
                target = null
                return result(AdbProbeReason.TARGET_NOT_LOCAL)
            }
            val nonce = ByteArray(16).also { random.nextBytes(it) }
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val identity = BoundedProcessRunner.run(
                prefix + listOf("shell", "-T", "-n", AdbIdentityOutputParser.command(nonce)),
                appContext.filesDir, environment,
                timeoutMillis.coerceIn(1, 5_000), cancelled = cancelled,
            )
            if (!identity.succeeded) return result(processReason(identity, AdbProbeReason.PROCESS_FAILED))
            val uid = AdbIdentityOutputParser.parseUid(identity.stdout, nonce)
                ?: return result(AdbProbeReason.IDENTITY_INVALID)
            if (uid != 2000) return result(AdbProbeReason.SHELL_UID_REQUIRED)
            return result(AdbProbeReason.VERIFIED_SHELL_ONLY, verified = true)
        } catch (_: Exception) {
            return result(AdbProbeReason.PROBE_FAILED)
        } finally {
            gate.release()
        }
    }

    private fun processReason(result: BoundedProcessResult, fallback: AdbProbeReason): AdbProbeReason =
        when (result.failure) {
            ProcessFailure.BUSY -> AdbProbeReason.BUSY
            ProcessFailure.TIMEOUT -> AdbProbeReason.PROCESS_TIMEOUT
            ProcessFailure.INTERRUPTED -> AdbProbeReason.PROCESS_INTERRUPTED
            ProcessFailure.OUTPUT_LIMIT -> AdbProbeReason.OUTPUT_LIMIT
            null -> fallback
            else -> AdbProbeReason.PROCESS_FAILED
        }

    companion object {
        private val gate = Semaphore(4)
        private val random = SecureRandom()
    }
}
