package com.tunnel.app.adb

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Finite local pairing/connection transaction. Caller must authorize the encrypted peer first.
 * Only local port numbers and a six-digit code are accepted; no peer command/host/shell text.
 * Pairing code is transient stdin, never a command argument, preference or status field.
 */
object RemoteAdbPairing {
    private const val RESULT_TTL_MS = 120_000L
    private val lock = Any()
    private var active: Job? = null
    private val results = linkedMapOf<Int, Cached>()

    private class Cached(val updatedAt: Long, val json: String)
    private class Job(val connId: Int, val operationId: String,
                      val callback: (JSONObject) -> Unit) {
        val cancelled = AtomicBoolean(false)
        val disconnected = AtomicBoolean(false)
        var worker: Thread? = null
    }

    fun busy(): Boolean = synchronized(lock) { active != null }

    fun snapshot(connId: Int): JSONObject? = synchronized(lock) {
        prune()
        results[connId]?.let { JSONObject(it.json) }
    }

    fun start(context: Context, connId: Int, operationId: String, pairingPort: Int,
              pairingCode: String, connectionPort: Int? = null,
              callback: (JSONObject) -> Unit): Boolean {
        if (pairingPort !in 1..65535 || !Regex("[0-9]{6}").matches(pairingCode) ||
            (connectionPort != null && connectionPort !in 1..65535)) {
            callback(JSONObject().put("operationId", operationId).put("phase", "PAIR_FAILED")
                .put("code", "PAIR_INPUT_INVALID").put("terminal", true).put("localAdbReady", false))
            return true
        }
        // Clear the only retained reference once consumed. JVM String memory is not zeroizable;
        // the runner's mutable stdin byte array is wiped in finally.
        val secret = AtomicReference<String?>(pairingCode)
        return launch(context, connId, operationId, "PAIR_STARTING", callback) { app, progress ->
            val code = secret.getAndSet(null) ?: return@launch TunnelAdbManager.snapshot()
            try { TunnelAdbManager.pair(app, pairingPort.toString(), code, connectionPort, progress) }
            finally { secret.set(null) }
        }.also { if (!it) secret.set(null) }
    }

    /** Reuse a key already authorized in Android settings. No pairing code or permission bypass. */
    fun authorize(context: Context, connId: Int, operationId: String, connectionPort: Int? = null,
                  callback: (JSONObject) -> Unit): Boolean {
        if (connectionPort != null && connectionPort !in 1..65535) {
            callback(JSONObject().put("operationId", operationId).put("phase", "PAIR_FAILED")
                .put("code", "CONNECT_PORT_INVALID").put("terminal", true).put("localAdbReady", false))
            return true
        }
        return launch(context, connId, operationId, "CONNECTING", callback) { app, _ ->
            if (connectionPort == null) TunnelAdbManager.start(app)
            else TunnelAdbManager.connect(app, connectionPort.toString())
        }
    }

    fun cancel(connId: Int, operationId: String? = null): Boolean = synchronized(lock) {
        val job = active ?: return@synchronized false
        if (job.connId != connId || (operationId != null && operationId != job.operationId))
            return@synchronized false
        job.cancelled.set(true)
        job.worker?.interrupt() // Only our worker; never cancel a phone-local operation or daemon.
        true
    }

    fun disconnect(connId: Int) = synchronized(lock) {
        results.remove(connId)
        active?.takeIf { it.connId == connId }?.let {
            it.disconnected.set(true)
            it.cancelled.set(true)
            it.worker?.interrupt()
        }
    }

    private fun launch(context: Context, connId: Int, operationId: String, initialPhase: String,
                       callback: (JSONObject) -> Unit,
                       action: (Context, (String) -> Unit) -> TunnelAdbState): Boolean {
        val app = context.applicationContext
        val job = synchronized(lock) {
            if (active != null) return false
            Job(connId, operationId, callback).also { active = it }
        }
        val initialState = try { TunnelAdbManager.initialize(app) } catch (_: Exception) {
            synchronized(lock) { if (active === job) active = null }
            emit(job, "PAIR_FAILED", "ADB_INITIALIZE_FAILED", true, TunnelAdbManager.snapshot())
            return true
        }
        emit(job, initialPhase, "", false, initialState)
        val worker = Thread({
            try {
                if (job.cancelled.get()) return@Thread
                val state = action(app) { phase ->
                    if (!job.cancelled.get()) emit(job, phase, "", false, TunnelAdbManager.snapshot())
                }
                if (!job.cancelled.get()) {
                    val phase = when {
                        state.lastError == "CANCELLED" -> "CANCELLED"
                        state.shellReady && state.lastError.isEmpty() -> "VERIFIED"
                        state.paired && state.lastError in setOf("PAIRED_CONNECT_REQUIRED",
                            "PAIRED_CONNECT_FAILED", "CONNECT_ADDRESS_REQUIRED", "CONNECT_FAILED",
                            "OPERATION_TIMEOUT") -> "PAIRED_CONNECT_REQUIRED"
                        else -> "PAIR_FAILED"
                    }
                    val code = if (phase == "VERIFIED") "" else state.lastError.ifEmpty { "ADB_NOT_VERIFIED" }
                    emit(job, phase, code, true, state)
                }
            } catch (_: Exception) {
                if (!job.cancelled.get()) emit(job, "PAIR_FAILED", "ADB_OPERATION_FAILED", true,
                    TunnelAdbManager.snapshot())
            } finally {
                if (job.cancelled.get()) emit(job, "CANCELLED", "CANCELLED", true, TunnelAdbManager.snapshot())
                synchronized(lock) { if (active === job) active = null }
            }
        }, "tunnel-remote-adb-pair").apply { isDaemon = true }
        synchronized(lock) { job.worker = worker; if (job.cancelled.get()) worker.interrupt() }
        try { worker.start() } catch (_: Exception) {
            synchronized(lock) { if (active === job) active = null }
            emit(job, "PAIR_FAILED", "ADB_WORKER_START_FAILED", true, TunnelAdbManager.snapshot())
        }
        return true
    }

    private fun emit(job: Job, phase: String, code: String, terminal: Boolean, state: TunnelAdbState) {
        if (job.disconnected.get()) return
        val ready = state.shellReady && (!terminal || (phase == "VERIFIED" && code.isEmpty()))
        val value = JSONObject().put("operationId", job.operationId).put("phase", phase)
            .put("code", code).put("terminal", terminal).put("paired", state.paired)
            .put("connected", state.connected && ready).put("localAdbReady", ready)
            .put("binaryAvailable", state.binaryAvailable).put("binaryExecutable", state.binaryExecutable)
        synchronized(lock) {
            if (job.disconnected.get()) return
            prune()
            results[job.connId] = Cached(SystemClock.elapsedRealtime(), value.toString())
            while (results.size > 4) results.remove(results.keys.first())
        }
        try { job.callback(value) } catch (_: Exception) { /* Callback must not leak secret/raw failure text. */ }
    }

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        val iterator = results.entries.iterator()
        while (iterator.hasNext()) if (now - iterator.next().value.updatedAt > RESULT_TTL_MS) iterator.remove()
    }
}
