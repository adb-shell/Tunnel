package com.tunnel.app.adb.mirror

import android.content.Context
import android.os.SystemClock
import com.tunnel.adb.protocol.AdbCommands
import com.tunnel.adb.protocol.AdbWire
import com.tunnel.app.adb.TunnelAdbManager
import com.tunnel.app.adb.RemoteAdbPairing
import com.tunnel.app.adb.LocalAdbProcessSpec
import com.tunnel.app.adb.probe.BoundedProcessRunner
import com.tunnel.app.adb.probe.LocalAdbIdentityProbe
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import org.json.JSONObject
import java.util.LinkedHashMap

/** Endpoint authority. Pair/authorize are explicit scoped actions by an authorized secure controller. */
object TunnelAdbRuntime {
    interface Hooks {
        fun sendEncoded(epoch: Long, revision: Long, width: Int, height: Int, ptsUs: Long,
                        key: Boolean, config: Boolean, bytes: ByteArray): Boolean
        fun onState(connId: Int, json: String)
        fun connectionAuthorized(connId: Int): Boolean = false
        fun setAccessibilityPaused(paused: Boolean): Boolean
        fun disableOwnAccessibility(): Boolean
        fun accessibilityBound(): Boolean
        fun normalCaptureReady(): Boolean
        fun normalGeometry(): Pair<Int, Int>
        fun setAdbCaptureCommitted(committed: Boolean)
        fun setNormalScreenShare(enabled: Boolean): Boolean = false
        fun accessibilityNavigation(action: String): Boolean = false
        fun openLocalAccessibilitySettings() {}
        fun openUrl(url: String): Boolean = false
    }

    private const val MAX_NUMBER = 9007199254740991L
    private val lock = Any()
    private var context: Context? = null
    private var hooks: Hooks? = null
    private var generation = 1L
    private var owner = 0
    private var lastVideoOwner = 0
    private var consentOwner = 0
    private var scopes: Set<String> = emptySet()
    private var pendingOwner = 0
    private var pendingScopes: Set<String> = emptySet()
    private var epoch = 0L
    private var helperEpoch = 0L
    private var revision = 0L
    private var width = 0
    private var height = 0
    private var phase = "IDLE"
    private var source = "NONE"
    private var candidateSource = "ADB_LIVE"
    private var pendingConfig: AdbWire.Packet? = null
    private var lastConfig: AdbWire.Packet? = null
    private var configSent = false
    private var freezeFrames = false
    private var error = ""
    private var videoDiagnostic = ""
    private var operationId = ""
    private var videoReady = false
    private var mask = 0
    private var pausedByUs = false
    private var lastEncodedFrameAt = 0L
    private var lastInputSequence = 0L
    private var session: TunnelAdbSession? = null
    private var stopping = false
    private var committed = false
    private var videoSources = AdbVideoSources()
    private val baseLive get() = videoSources.live
    private val snapshotEnabled get() = videoSources.snapshot
    private val hierarchyEnabled get() = videoSources.hierarchy
    private var capturePaused = false
    private var requestedMode = 3
    // Control is owned by the authorized connection, not by any video task.
    private var helperOwner = 0
    private var helperStarting = false
    private var controlReady = false
    private var videoTaskId = 0
    private var videoTaskCounter = 0
    private var issuedTaskId = 0
    private var videoDispatchRetries = 0
    private var videoRecoveryPending = false
    private var scheduledRecoveryEpoch = -1L
    private var videoRequested = false
    private var geometryRevision = 0L
    private var geometryKey = ""
    private var displayedGeometry: Pair<Int, Int>? = null
    private var globalInputSequence = 0L
    private var globalTouchGeneration = 0L
    private data class InputWork(val active: TunnelAdbSession, val geometry: Long, val args: IntArray,
                                 val touchGeneration: Long, val operationId: String)
    // Keep the newest not-yet-dispatched MOVE, but never merge across a DOWN,
    // UP, key, geometry revision or helper replacement.
    private var queuedMove: InputTask? = null
    private class InputTask(val connId: Int, var work: InputWork) : Runnable {
        var started = false // guarded by lock, like work and queuedMove
        override fun run() {
            val selected = synchronized(lock) {
                started = true
                if (queuedMove === this) queuedMove = null
                work
            }
            executeInputWork(connId, selected)
        }
    }
    private var controlRetryCount = 0
    private var controlRetryWindow = 0L
    private val inputWorker = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
        java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.ArrayBlockingQueue<Runnable>(256),
        java.util.concurrent.ThreadFactory { task -> Thread(task, "tunnel-adb-input").apply { isDaemon = true } })
    private var blackRequested = false
    private var blackEnabled = false
    private var blackVersion = 0L
    private var blackSentVersion = -1L
    private val actionWorker = java.util.concurrent.ThreadPoolExecutor(3, 3, 0L,
        java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.ArrayBlockingQueue<Runnable>(32),
        java.util.concurrent.ThreadFactory { task -> Thread(task, "tunnel-adb-scoped-actions").apply { isDaemon = true } })
    private val knownScopes = setOf("video", "input", "accessibility", "display", "snapshot", "hierarchy", "overlay")
    private val pairingOperations = mutableMapOf<Int, String>()
    private val cancelledPairing = mutableSetOf<String>()
    private var pairingRevision = 0L
    private var consentOperationId = ""
    // Only redacted results: never retain the pairing request or its code/signature.
    private val pairingResults = object : LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 64
    }
    private val completed = object : LinkedHashMap<String, Pair<String, String>>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>?): Boolean = size > 128
    }
    private val actionResults = object : LinkedHashMap<String, Pair<String, String>>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>?): Boolean = size > 128
    }

    @JvmStatic fun initialize(value: Context, callbacks: Hooks) = synchronized(lock) {
        context = value.applicationContext
        hooks = callbacks
        // The authenticated connection owns this lifetime. Delayed PC video
        // heartbeats are not a stop command. Disconnect/revoke still clean up.
    }

    /** Authenticated explicit action; video stop does not revoke the connection's grant. */
    @JvmStatic fun grantConsent(connId: Int, requestedScopes: Set<String>): String {
        synchronized(lock) {
            if (connId <= 0 || requestedScopes.isEmpty() || "video" !in requestedScopes ||
                !knownScopes.containsAll(requestedScopes) ||
                (owner != 0 && owner != connId) || (helperOwner != 0 && helperOwner != connId)) return snapshot("CONSENT_INVALID")
            val narrowing = owner != 0 && !requestedScopes.containsAll(scopes)
            consentOwner = connId
            scopes = requestedScopes.toSet(); pendingOwner = 0; pendingScopes = emptySet(); error = ""
            if (narrowing) stopLocked("CONSENT_SCOPE_CHANGED")
            ensureControlLocked(connId)
            return snapshot()
        }
    }

    @JvmStatic fun getConsentPending(): String = synchronized(lock) { snapshot() }
    @JvmStatic fun status(): String = synchronized(lock) { snapshot() }
    private fun hasConsent(connId: Int): Boolean = connId > 0 && consentOwner == connId && "video" in scopes
    private fun clearConsentLocked() {
        AdbScreenShareRequest.clear()
        videoSources = AdbVideoSources()
        consentOwner = 0; scopes = emptySet(); consentOperationId = ""
        pendingOwner = 0; pendingScopes = emptySet()
    }
    @JvmStatic fun revokeConsent() {
        val id = synchronized(lock) { clearConsentLocked(); closeAuxiliaryLocked(); owner }
        if (id != 0) stopOwner(id, "CONSENT_REVOKED", true)
    }
    fun authorizedAction(connId: Int): Boolean = synchronized(lock) {
        hasConsent(connId) && hooks?.connectionAuthorized(connId) == true
    }
    @JvmStatic fun onDisconnected(connId: Int) {
        synchronized(lock) {
            pairingOperations.remove(connId)
            cancelledPairing.removeAll { it.startsWith("$connId:") }
            pairingResults.keys.removeAll { it.startsWith("$connId:") }
            actionResults.keys.removeAll { it.startsWith("$connId:") }
            if (consentOwner == connId) clearConsentLocked()
            if (helperOwner == connId) closeAuxiliaryLocked()
            if (pendingOwner == connId) { pendingOwner = 0; pendingScopes = emptySet() }
        }
        RemoteAdbPairing.disconnect(connId)
        stopOwner(connId, "DISCONNECTED", true)
    }
    @JvmStatic fun shutdown() {
        val pending = synchronized(lock) {
            pairingOperations.keys.toList().also { pairingOperations.clear(); cancelledPairing.clear() }
        }
        pending.forEach { RemoteAdbPairing.disconnect(it) }
        revokeConsent(); synchronized(lock) { pairingResults.clear(); actionResults.clear(); hooks = null; context = null }
    }

    /** Rust has already checked relay encryption, authentication, permissions and exclusive owner. */
    @JvmStatic fun handleRequest(connId: Int, json: String): String {
        if (json.length > 8192 || connId <= 0) return synchronized(lock) { snapshot("REQUEST_INVALID") }
        val request = try { JSONObject(json) } catch (_: Exception) { return synchronized(lock) { snapshot("REQUEST_INVALID") } }
        val response = try {
            requireKeys(request, setOf("v", "op", "operationId", "generation", "epoch", "revision", "sequence", "inputFrozen", "payload"))
            require(number(request, "v") == 1L)
            val op = request.getString("op")
            val id = request.getString("operationId")
            require(id.length in 1..64 && id.all { it.isLetterOrDigit() && it.code < 128 || it in "-_.:" })
            val payload = request.getJSONObject("payload")
            if (op in setOf("pair", "authorize", "pair_cancel", "revoke")) {
                // This early return deliberately avoids completed[signature], which would retain the code.
                return pairingRequest(connId, id, op, payload)
            }
            if (op == "side_action" || op.startsWith("accessibility_")) {
                return try { actionRequest(connId, id, op, payload) }
                catch (_: Exception) { synchronized(lock) { actionNotice(connId, id, "REQUEST_INVALID") } }
            }
            synchronized(lock) {
                val signature = request.toString()
                val replayKey = "$connId:$id:$op:${request.optLong("epoch", 0)}"
                // These are repeatable signals, not one-shot mutations. Caching
                // a heartbeat or keyframe request previously disabled all later
                // calls with the same video operation identity.
                val cacheable = op !in setOf("status", "heartbeat", "keyframe", "input")
                (if (cacheable) completed[replayKey] else null)?.let { previous ->
                    return@synchronized if (previous.first == signature) previous.second else snapshot("OPERATION_REPLAY")
                }
                if (op == "status") { requireKeys(payload, emptySet()); return@synchronized snapshot() }
                if (op == "stop") {
                    requireKeys(payload, emptySet())
                    if ((owner != 0 && owner != connId) ||
                        (owner == 0 && lastVideoOwner != connId && consentOwner != connId))
                        return@synchronized snapshot("NOT_OWNER")
                    // Stop is its own video transaction. Its acknowledgement
                    // must match the PC's newer generation, not the old stream.
                    generation = number(request, "generation")
                    epoch = number(request, "epoch")
                    operationId = id
                    stopLocked("STOPPED")
                    return@synchronized JSONObject(snapshot()).put("videoStopped", true).toString()
                }
                if (op == "start") {
                    requireKeys(payload, setOf("initialSource", "sourceAction"))
                    require(!(payload.has("initialSource") && payload.has("sourceAction")))
                    val requestedEpoch = number(request, "epoch"); require(requestedEpoch > 0)
                    val requestedGeneration = number(request, "generation"); require(requestedGeneration > 0)
                    if ((owner != 0 && owner != connId) || (helperOwner != 0 && helperOwner != connId))
                        return@synchronized snapshot("OWNER_BUSY")
                    if (!hasConsent(connId)) {
                        pendingOwner = connId; pendingScopes = knownScopes
                        return@synchronized snapshot("SESSION_ADB_AUTHORIZATION_REQUIRED")
                    }
                    val action = payload.optString("sourceAction")
                    val sourceAction = if (action.isNotEmpty()) {
                        if (action !in setOf("ignore_on", "ignore_off", "hierarchy_on", "hierarchy_off", "live_off", "resume"))
                            return@synchronized snapshot("REQUEST_INVALID")
                        action
                    } else {
                        when (payload.optString("initialSource", "ADB_CAPTURE")) {
                            "ADB_CAPTURE" -> "live_on"
                            "IGNORE_CAPTURE" -> "ignore_on"
                            "HIERARCHY_CAPTURE" -> "hierarchy_on"
                            else -> return@synchronized snapshot("REQUEST_INVALID")
                        }
                    }
                    if ((sourceAction == "ignore_on" && "snapshot" !in scopes) ||
                        (sourceAction == "hierarchy_on" && "hierarchy" !in scopes))
                        return@synchronized snapshot("SCOPE_DENIED")
                    videoSources = videoSources.apply(sourceAction)
                    videoRecoveryPending = false
                    controlRetryCount = 0; controlRetryWindow = SystemClock.elapsedRealtime()
                    owner = connId; lastVideoOwner = connId
                    epoch = requestedEpoch; generation = requestedGeneration; operationId = id
                    freezeFrames = false; pendingConfig = null
                    lastConfig = null; configSent = false
                    capturePaused = false; requestedMode = videoSources.mode
                    videoRequested = requestedMode != 3
                    videoTaskId = nextTaskId(); issuedTaskId = 0; videoDispatchRetries = 0
                    revision = 0; error = ""; videoDiagnostic = ""; stopping = false
                    phase = "PREPARING"; videoReady = false
                    // Keep the currently displayed source until the replacement's first real frame.
                    lastEncodedFrameAt = 0; lastInputSequence = 0
                    if (requestedMode == 3) stopLocked("STOPPED")
                    else if (context == null || (session == null && TunnelAdbManager.selectedLocalSerial() == null)) {
                        // An off action must clear intent even while ADB is offline.
                        // Keep only the remaining explicitly enabled providers recoverable.
                        failLocked(if (context == null) "RUNTIME_UNAVAILABLE" else "LOCAL_ADB_REQUIRED")
                        videoRecoveryPending = videoRequested
                        helperOwner = connId
                        scheduleControlRecoveryLocked(connId, helperEpoch)
                    }
                    else { ensureControlLocked(connId); dispatchVideoTaskLocked() }
                } else {
                    if (owner != connId || owner == 0) return@synchronized snapshot("NOT_OWNER")
                    if (number(request, "generation") != generation) return@synchronized snapshot("STALE_GENERATION")
                    if ((op != "reconfigure" && number(request, "epoch") != epoch) ||
                        (op !in setOf("heartbeat", "stop", "reconfigure") && number(request, "revision") != revision))
                        return@synchronized snapshot("STALE_FRAME")
                    if (!hasConsent(connId)) return@synchronized snapshot("SESSION_ADB_AUTHORIZATION_REQUIRED")
                    when (op) {
                        // Private Rust -> endpoint operation. Remote Rust parser rejects this op.
                        "reconfigure" -> {
                            requireKeys(payload, emptySet())
                            val next = number(request, "epoch")
                            if (!freezeFrames || phase != "RECONFIGURE" || next <= epoch || id != operationId)
                                return@synchronized snapshot("RECONFIGURE_INVALID")
                            epoch = next; phase = "PREPARING"; freezeFrames = false
                            val configuration = pendingConfig ?: return@synchronized snapshot("RECONFIGURE_INVALID")
                            pendingConfig = null
                            configSent = forward(configuration)
                            if (!configSent) { session?.keyframe(); return@synchronized snapshot("VIDEO_BACKPRESSURE") }
                            // Frames received while frozen were discarded. Replaying
                            // only their IDR followed by today's P-frames loses the
                            // intermediate references. Start a fresh GOP instead.
                            session?.keyframe()
                        }
                        "heartbeat" -> { requireKeys(payload, emptySet()) }
                        "keyframe" -> { requireKeys(payload, emptySet()); session?.keyframe() }
                        "activate" -> {
                            requireKeys(payload, emptySet())
                            if (!request.optBoolean("inputFrozen", false) || phase != "READY" || !videoReady)
                                return@synchronized snapshot("NOT_READY")
                            phase = "WAITING_PRESENTED"
                        }
                        "presented" -> {
                            requireKeys(payload, emptySet())
                            if (phase != "WAITING_PRESENTED" || !videoReady) return@synchronized snapshot("NOT_READY")
                            phase = "COMMITTED"; committed = true; hooks?.setAdbCaptureCommitted(true)
                            source = candidateSource
                            displayedGeometry = width to height
                            inputGeometry()
                        }
                        "video_failed" -> { requireKeys(payload, emptySet()); failLocked("PC_VIDEO_FAILED") }
                        "input" -> {
                            if (!inputReady()) return@synchronized snapshot("INPUT_NOT_READY")
                            val sequence = number(request, "sequence")
                            if (sequence <= lastInputSequence) return@synchronized snapshot("INPUT_REPLAY")
                            val arguments = input(payload)
                            lastInputSequence = sequence
                            submit(id, arguments[0], *arguments.copyOfRange(1, arguments.size))
                        }
                        else -> return@synchronized snapshot("OPERATION_UNSUPPORTED")
                    }
                }
                val result = snapshot()
                if (cacheable) completed[replayKey] = signature to result
                result
            }
        } catch (_: Exception) { synchronized(lock) { snapshot("REQUEST_INVALID") } }
        return try {
            val output = JSONObject(response)
            synchronized(lock) {
                // Cached operation outcomes cannot revive a grant that has since ended.
                output.put("consentConnId", if (consentOwner == connId) connId else 0)
                    .put("consentActive", hasConsent(connId)).put("consentLifetime", "session")
            }
            output.put("requestOperationId", request.optString("operationId"))
                .put("requestEpoch", request.optLong("epoch", 0)).put("requestGeneration", request.optLong("generation", 0))
            if (output.optBoolean("operationRejected", false)) {
                output.put("operationId", request.optString("operationId"))
                    .put("epoch", request.optLong("epoch", 0)).put("generation", request.optLong("generation", 0))
            }
            output.toString()
        } catch (_: Exception) { response }
    }

    private fun pairingNotice(connId: Int, id: String, value: JSONObject): String {
        pairingRevision += 1
        return value.put("v", 1).put("kind", "pairing").put("operationId", id)
            .put("generation", 0).put("epoch", 0).put("pairRevision", pairingRevision)
            .put("consentConnId", if (consentOwner == connId) connId else 0)
            .put("consentActive", hasConsent(connId)).put("consentLifetime", "session")
            .toString()
    }

    private fun pairingRequest(connId: Int, id: String, op: String, payload: JSONObject): String = synchronized(lock) {
        fun failure(code: String, targetId: String = id): String = pairingNotice(connId, targetId, JSONObject()
            .put("phase", "PAIR_FAILED").put("code", code).put("terminal", true)
            .put("localAdbReady", TunnelAdbManager.snapshot().shellReady))
        if (hooks?.connectionAuthorized(connId) != true) return@synchronized failure("ANDROID_ADB_SESSION_NOT_REGISTERED")
        if (op == "pair_cancel") {
            requireKeys(payload, setOf("pairOperationId"))
            val target = payload.getString("pairOperationId")
            val pending = pairingOperations[connId] == target
            if (pending) cancelledPairing.add("$connId:$target")
            RemoteAdbPairing.cancel(connId, target)
            if (consentOwner == connId && consentOperationId == target) {
                clearConsentLocked()
                closeAuxiliaryLocked()
                if (owner == connId) stopLocked("CONSENT_REVOKED")
            }
            val result = pairingNotice(connId, target, JSONObject().put("phase", if (pending) "CANCELLING" else "CANCELLED")
                .put("code", if (pending) "" else "CANCELLED").put("terminal", !pending)
                .put("localAdbReady", TunnelAdbManager.snapshot().shellReady))
            if (!pending) pairingResults["$connId:$target"] = result
            return@synchronized result
        }
        if (op == "revoke") {
            requireKeys(payload, emptySet())
            pairingOperations.remove(connId); cancelledPairing.removeAll { it.startsWith("$connId:") }
            RemoteAdbPairing.disconnect(connId)
            if (consentOwner == connId) {
                clearConsentLocked()
                closeAuxiliaryLocked()
                if (owner == connId) stopLocked("CONSENT_REVOKED")
            }
            return@synchronized pairingNotice(connId, id, JSONObject().put("phase", "CANCELLED")
                .put("code", "CONSENT_REVOKED").put("terminal", true)
                .put("localAdbReady", TunnelAdbManager.snapshot().shellReady))
        }
        pairingResults["$connId:$id"]?.let {
            // A replay reports the original outcome but never restores an ended/revoked grant.
            return@synchronized pairingNotice(connId, id, JSONObject(it))
        }
        if (pairingOperations[connId] == id) {
            return@synchronized pairingNotice(connId, id, RemoteAdbPairing.snapshot(connId)
                ?: JSONObject().put("phase", "PAIR_STARTING").put("terminal", false))
        }
        if ((owner != 0 && owner != connId) || (helperOwner != 0 && helperOwner != connId) ||
            (consentOwner != 0 && consentOwner != connId)) return@synchronized failure("OWNER_BUSY")
        if (RemoteAdbPairing.busy()) return@synchronized failure("ADB_BUSY")
        requireKeys(payload, if (op == "pair") setOf("port", "code", "connectPort") else setOf("connectPort"))
        fun port(name: String): Int {
            val value = payload.get(name); require(value is String && value.length in 1..5 && value.all { it in '0'..'9' })
            return value.toInt().also { require(it in 1..65535) }
        }
        val connectPort = if (payload.has("connectPort")) port("connectPort") else null
        val pairingPort = if (op == "pair") port("port") else null
        val pairingCode = if (op == "pair") payload.getString("code").also {
            require(Regex("[0-9]{6}").matches(it))
        } else null
        val app = context ?: return@synchronized failure("RUNTIME_UNAVAILABLE")
        pairingOperations[connId] = id
        val callback: (JSONObject) -> Unit = { update ->
            synchronized(lock) {
                val cancelled = "$connId:$id" in cancelledPairing
                if (pairingOperations[connId] == id && hooks?.connectionAuthorized(connId) == true && context != null
                    && (!cancelled || update.optString("phase") == "CANCELLED")) {
                    if (update.optString("phase") == "VERIFIED" && update.optBoolean("localAdbReady")
                        && update.optString("code").isEmpty()) {
                        // Explicit pair/authorize is authority for this connection, never a persisted global grant.
                        if (owner != 0 && owner != connId) {
                            update.put("phase", "PAIR_FAILED").put("code", "OWNER_BUSY").put("terminal", true)
                        } else {
                            grantConsent(connId, knownScopes); consentOperationId = id
                        }
                    }
                    val result = pairingNotice(connId, id, update)
                    if (update.optBoolean("terminal")) {
                        cancelledPairing.remove("$connId:$id")
                        pairingOperations.remove(connId); pairingResults["$connId:$id"] = result
                    }
                    hooks?.onState(connId, result)
                }
            }
        }
        val accepted = if (op == "pair") {
            RemoteAdbPairing.start(app, connId, id, pairingPort!!, pairingCode!!, connectPort, callback)
        } else RemoteAdbPairing.authorize(app, connId, id, connectPort, callback)
        if (!accepted) { pairingOperations.remove(connId); return@synchronized failure("ADB_BUSY") }
        pairingResults["$connId:$id"] ?: pairingNotice(connId, id, RemoteAdbPairing.snapshot(connId)
            ?: JSONObject().put("phase", "PAIR_STARTING").put("terminal", false))
    }

    private fun events(id: Int, expectedEpoch: Long) = object : TunnelAdbSession.Events {
        override fun capabilities(bits: Int) {
            val notice = synchronized(lock) {
                if (helperOwner != id || helperEpoch != expectedEpoch) return
                mask = bits; controlReady = true
                if (pausedByUs && bits and AdbWire.CAP_INPUT == 0) { hooks?.setAccessibilityPaused(false); pausedByUs = false }
                session?.let { active ->
                    if (blackSentVersion != blackVersion)
                        effectOperation(active, id, "overlay-resume", blackRequested, blackVersion)
                }
                dispatchVideoTaskLocked()
                val resume = videoRecoveryPending && bits != 0
                JSONObject(actionNotice(id, "control-ready"))
                    .put("resumeVideo", resume).put("resumeGeneration", generation)
                    .put("baseLiveRequested", baseLive).toString()
            }
            hooks?.onState(id, notice)
        }
        override fun videoState(taskId: Int, state: Int) {
            val notice = synchronized(lock) {
                if (helperOwner != id || helperEpoch != expectedEpoch || taskId != videoTaskId || owner == 0) return
                // Helper retries the same desired task after releasing its old
                // codec. A temporary provider failure must not cancel that task.
                if (state == 2) { lastEncodedFrameAt = 0; snapshot() } else null
            }
            notice?.let { hooks?.onState(id, it) }
        }
        override fun videoDiagnostic(taskId: Int, code: String) {
            val notice = synchronized(lock) {
                if (helperOwner != id || helperEpoch != expectedEpoch || taskId != videoTaskId || owner != id || requestedMode == 3) return
                videoDiagnostic = code
                snapshot()
            }
            // Diagnostic only: keep the requested source, authorization and input alive.
            hooks?.onState(id, notice)
        }
        override fun effectsState(applied: Int) {
            val notice = synchronized(lock) {
                if (helperOwner != id || helperEpoch != expectedEpoch) return
                blackEnabled = applied and 1 != 0
                actionNotice(id, "effects-state")
            }
            hooks?.onState(id, notice)
        }
        override fun packet(packet: AdbWire.Packet): Boolean {
            var notice: String? = null
            val accepted = synchronized(lock) {
                if (helperOwner != id || helperEpoch != expectedEpoch || owner != id || packet.taskId != videoTaskId || stopping || requestedMode == 3) return true
                if (packet.kind == AdbWire.VIDEO_FRAME) {
                    lastEncodedFrameAt = SystemClock.elapsedRealtime()
                    if (videoDiagnostic.isNotEmpty()) { videoDiagnostic = ""; notice = snapshot() }
                }
                if (packet.kind == AdbWire.VIDEO_CONFIG) {
                    if (requestedMode == 3) return true
                    capturePaused = false; session?.setCapturePaused(false)
                    lastConfig = packet; configSent = false
                    if (revision != packet.configRevision) {
                        val replacement = revision != 0L
                        revision = packet.configRevision; width = packet.width; height = packet.height
                        candidateSource = when (packet.flags) { 2 -> "ADB_SNAPSHOT"; 4 -> "ADB_HIERARCHY"; 8 -> "ADB_SNAPSHOT_HIERARCHY"; else -> "ADB_LIVE" }
                        phase = "PREPARING"; videoReady = false
                        if (replacement) {
                            phase = "RECONFIGURE"; freezeFrames = true; pendingConfig = packet
                            notice = snapshot()
                        }
                    }
                }
                if (capturePaused) true else if (freezeFrames) {
                    true
                } else if (packet.kind == AdbWire.VIDEO_FRAME && !videoReady &&
                    packet.flags and AdbWire.FLAG_KEY_FRAME == 0) {
                    // Reconfiguration dropped a GOP tail. Wait for its requested
                    // fresh IDR instead of feeding undecodable dependent frames.
                    true
                } else {
                    val sent = if (packet.kind == AdbWire.VIDEO_CONFIG) {
                        forward(packet).also { configSent = it }
                    } else {
                        if (!configSent && packet.flags and AdbWire.FLAG_KEY_FRAME != 0)
                            configSent = lastConfig?.let { forward(it) } == true
                        configSent && forward(packet)
                    }
                    if (sent && packet.kind == AdbWire.VIDEO_FRAME && packet.flags and AdbWire.FLAG_KEY_FRAME != 0 && !videoReady) {
                        videoReady = true; phase = "READY"; notice = snapshot()
                    }
                    sent
                }
            }
            notice?.let { hooks?.onState(id, it) }
            return accepted
        }
        override fun ended(reason: String, cleanupComplete: Boolean) {
            val notice = synchronized(lock) {
                if (helperOwner != id || helperEpoch != expectedEpoch) return
                // Pending command failures may already have stopped the old task.
                // Recovery follows user intent, not that failed task's mode.
                val resume = owner == id && videoRequested
                session = null; helperStarting = false; controlReady = false; mask = 0; blackEnabled = false
                blackSentVersion = -1
                if (owner == id && requestedMode != 3) failLocked(reason)
                videoRecoveryPending = resume
                // Transport failure removes readiness, never the connection's grant.
                error = if (cleanupComplete) reason else "CLEANUP_INCOMPLETE"
                scheduleControlRecoveryLocked(id, expectedEpoch)
                snapshot()
            }
            hooks?.onState(id, notice)
        }
    }

    private fun input(payload: JSONObject, inputWidth: Int = width, inputHeight: Int = height): IntArray {
        return when (payload.getString("type")) {
            "touch" -> {
                requireKeys(payload, setOf("type", "action", "x", "y", "width", "height"))
                val action = listOf("down", "up", "move", "cancel").indexOf(payload.getString("action")); require(action >= 0)
                val x = payload.getDouble("x"); val y = payload.getDouble("y")
                require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
                require(number(payload, "width") == inputWidth.toLong() && number(payload, "height") == inputHeight.toLong())
                intArrayOf(AdbCommands.TOUCH, action, (x * 1_000_000).toInt(), (y * 1_000_000).toInt(), inputWidth, inputHeight, 0)
            }
            "key" -> {
                requireKeys(payload, setOf("type", "action", "keyCode", "metaState"))
                val action = listOf("down", "up").indexOf(payload.getString("action")); require(action >= 0)
                val code = number(payload, "keyCode"); val meta = number(payload, "metaState")
                require(code in 1..288 && meta in 0..0x7fffff)
                intArrayOf(AdbCommands.KEY, action, code.toInt(), meta.toInt(), 0, 0, 0)
            }
            else -> throw IllegalArgumentException()
        }
    }

    private fun submit(id: String, operation: Int, vararg args: Int, actionResult: Boolean = false,
                       onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) {
        val arguments = IntArray(6); args.copyInto(arguments)
        val expected = helperEpoch; val recipient = helperOwner
        session?.operation(operation, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5])
            ?.whenComplete { result, failure ->
                val notice = synchronized(lock) {
                    if (helperOwner != recipient || helperEpoch != expected) return@whenComplete
                    val code = when {
                        failure != null -> "HELPER_OPERATION_FAILED"
                        result.code == AdbCommands.OK -> ""
                        result.code == AdbCommands.UNSUPPORTED -> "OPERATION_UNSUPPORTED"
                        result.code == AdbCommands.STALE_GEOMETRY -> "STALE_FRAME"
                        result.code == AdbCommands.BUSY -> "HELPER_BUSY"
                        else -> "OPERATION_REJECTED"
                    }
                    if (code.isEmpty()) onSuccess() else onFailure()
                    if ((failure != null || result.code == AdbCommands.FAILED) &&
                        operation in setOf(AdbCommands.TOUCH, AdbCommands.KEY, AdbCommands.NAVIGATE)) {
                        session?.operation(AdbCommands.RELEASE_INPUT)
                    }
                    if (code.isEmpty() && operation in setOf(AdbCommands.TOUCH, AdbCommands.KEY)) return@whenComplete
                    if (actionResult) actionNotice(recipient, id, code)
                    else JSONObject(if (stopping) snapshot() else snapshot(code))
                        .put("requestOperationId", id).put("operationComplete", true).toString()
                }
                hooks?.onState(recipient, notice)
            }
    }

    private fun inputReady(): Boolean = controlReady && session != null && hasConsent(helperOwner) &&
        hooks?.connectionAuthorized(helperOwner) == true && "input" in scopes && mask and AdbWire.CAP_INPUT != 0
    private fun forward(packet: AdbWire.Packet): Boolean = hooks?.sendEncoded(epoch, packet.configRevision, packet.width, packet.height,
        packet.ptsUs, packet.flags and AdbWire.FLAG_KEY_FRAME != 0, packet.kind == AdbWire.VIDEO_CONFIG, packet.payloadCopy()) == true
    private fun stopOwner(id: Int, reason: String, revoke: Boolean) {
        val notice = synchronized(lock) {
            if (id <= 0 || owner != id) return
            if (revoke && consentOwner == id) clearConsentLocked()
            stopLocked(reason)
            snapshot()
        }
        hooks?.onState(id, notice)
    }
    private fun stopLocked(reason: String) {
        videoRecoveryPending = false
        videoRequested = false
        lastEncodedFrameAt = 0
        phase = "IDLE"; error = reason; videoDiagnostic = ""; videoReady = false; stopping = false
        pendingConfig = null; freezeFrames = false; lastConfig = null; configSent = false
        // Explicit legacy stop/revoke closes every source. The live menu sends
        // live_off, retaining snapshot/hierarchy until their own OFF commands.
        videoSources = AdbVideoSources()
        requestedMode = 3; videoTaskId = nextTaskId(); issuedTaskId = 0
        dispatchVideoTaskLocked()
        restoreHooks()
        owner = 0
        source = if (hooks?.normalCaptureReady() == true) "MEDIA_PROJECTION" else "NONE"
    }
    private fun failLocked(reason: String) {
        lastEncodedFrameAt = 0
        phase = "FAILED"; error = reason; videoDiagnostic = ""; videoReady = false; stopping = false
        pendingConfig = null; freezeFrames = false; lastConfig = null; configSent = false
        requestedMode = 3; videoTaskId = nextTaskId(); issuedTaskId = 0
        dispatchVideoTaskLocked()
        restoreHooks()
        source = if (hooks?.normalCaptureReady() == true) "MEDIA_PROJECTION" else "NONE"
    }
    /** Private Rust entry: abort only the failed video task, never ADB control/consent. */
    @JvmStatic fun abortVideo(connId: Int) = synchronized(lock) {
        if (owner == connId && connId > 0 && requestedMode != 3) failLocked("VIDEO_ABORTED")
    }
    private fun restoreHooks() {
        hooks?.setAdbCaptureCommitted(false); committed = false
        // Explicit accessibility pause and black overlay have their own lifetimes.
    }

    private fun actionNotice(connId: Int, id: String, code: String = "", complete: Boolean = true): String {
        val key = "$connId:$id"
        val previous = actionResults[key]
        if (!complete && previous != null && JSONObject(previous.second).optBoolean("operationComplete")) return previous.second
        val notice = JSONObject().put("v", 1).put("kind", "action").put("phase", "ACTION_RESULT")
            .put("operationId", id).put("requestOperationId", id).put("operationComplete", complete)
            .put("generation", 0).put("epoch", 0).put("code", code)
            .put("sourceGeneration", generation)
            .put("overlayBlack", blackEnabled).put("overlayBlackRequested", blackRequested)
            .put("baseLiveRequested", baseLive).put("snapshotEnabled", snapshotEnabled).put("hierarchyEnabled", hierarchyEnabled)
            .put("resumeVideo", videoRecoveryPending && controlReady && mask != 0).put("resumeGeneration", generation)
            .put("localAdbReady", TunnelAdbManager.snapshot().shellReady)
            .put("adbVideoPresent", framePresent(0)).put("adbSpecialPresent", framePresent(1, 4))
            .put("inputReady", inputReady())
            .put("consentConnId", if (hasConsent(connId)) connId else 0)
            .put("consentActive", hasConsent(connId)).put("consentLifetime", "session").toString()
        previous?.let { actionResults[key] = it.first to notice }
        return notice
    }

    private fun actionRequest(connId: Int, id: String, op: String, payload: JSONObject): String = synchronized(lock) {
        // accessibility_action arrives only through Rust's private, authenticated
        // control-permission gate. It does not require any ADB pairing/grant.
        if (hooks == null || (op != "accessibility_action" && hooks?.connectionAuthorized(connId) != true))
            return@synchronized actionNotice(connId, id, "UNAUTHORIZED")
        val replayKey = "$connId:$id"
        // Cache only a digest and redacted result, never a URL or shell command.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((op + payload.toString()).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        actionResults[replayKey]?.let { previous ->
            return@synchronized if (previous.first == digest) JSONObject(previous.second)
                .put("consentActive", hasConsent(connId)).put("consentConnId", if (hasConsent(connId)) connId else 0).toString()
                else actionNotice(connId, id, "OPERATION_REPLAY")
        }
        actionResults[replayKey] = digest to actionNotice(connId, id, complete = false)
        if (op == "accessibility_action") {
            val action = payload.getString("action")
            requireKeys(payload, if (action == "open_url") setOf("action", "url") else setOf("action"))
            if (action == "open_url") {
                val uri = android.net.Uri.parse(payload.getString("url"))
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrEmpty() || uri.userInfo != null || uri.toString().length > 2048)
                    return@synchronized actionNotice(connId, id, "REQUEST_INVALID")
                return@synchronized actionNotice(connId, id,
                    if (hooks?.accessibilityBound() == true && hooks?.openUrl(uri.toString()) == true) "" else "ACCESSIBILITY_NOT_READY")
            }
            if (action !in setOf("back", "home", "recents")) return@synchronized actionNotice(connId, id, "REQUEST_INVALID")
            return@synchronized actionNotice(connId, id,
                if (hooks?.accessibilityNavigation(action) == true) "" else "ACCESSIBILITY_NOT_READY")
        }
        if (!hasConsent(connId)) return@synchronized actionNotice(connId, id, "SESSION_ADB_AUTHORIZATION_REQUIRED")
        if (owner != 0 && owner != connId) return@synchronized actionNotice(connId, id, "OWNER_BUSY")
        if (op.startsWith("accessibility_")) {
            requireKeys(payload, emptySet())
            if ("accessibility" !in scopes) return@synchronized actionNotice(connId, id, "SCOPE_DENIED")
            val ok = when (op) {
                "accessibility_pause" -> (hooks?.setAccessibilityPaused(true) == true).also { if (it) pausedByUs = true }
                "accessibility_resume" -> (hooks?.setAccessibilityPaused(false) == true).also { if (it) pausedByUs = false }
                "accessibility_disable" -> hooks?.disableOwnAccessibility() == true
                "accessibility_enable" -> { hooks?.openLocalAccessibilitySettings(); false }
                else -> false
            }
            return@synchronized actionNotice(connId, id, if (ok) "" else "LOCAL_ACTION_REQUIRED")
        }
        val action = payload.getString("action")
        requireKeys(payload, if (action == "open_url") setOf("action", "url") else setOf("action"))
        val openSharePermission = action == "share_start" && hooks?.normalCaptureReady() != true
        if (action in setOf("share_start", "share_stop") && !openSharePermission) {
            if ("video" !in scopes) return@synchronized actionNotice(connId, id, "SCOPE_DENIED")
            return@synchronized actionNotice(connId, id,
                if (hooks?.setNormalScreenShare(action == "share_start") == true) "" else "LOCAL_ACTION_REQUIRED")
        }
        if (action in setOf("overlay_black_on", "overlay_black_off")) {
            if ("overlay" !in scopes)
                return@synchronized actionNotice(connId, id, "SCOPE_DENIED")
            val enabled = action.endsWith("_on")
            blackRequested = enabled
            val version = ++blackVersion
            if (!enabled && session == null && !helperStarting) {
                blackEnabled = false
                return@synchronized actionNotice(connId, id)
            }
            ensureControlLocked(connId)
            val active = session
            if (active != null && controlReady) {
                effectOperation(active, connId, id, enabled, version)
                return@synchronized actionNotice(connId, id, complete = false)
            }
            return@synchronized actionNotice(connId, id, "ADB_CONTROL_STARTING")
        }
        if (action in setOf("ignore_on", "ignore_off", "hierarchy_on", "hierarchy_off"))
            return@synchronized actionNotice(connId, id, "SOURCE_TRANSACTION_REQUIRED")
        if ((if (openSharePermission) "video" else "input") !in scopes) return@synchronized actionNotice(connId, id, "SCOPE_DENIED")
        val app = context ?: return@synchronized actionNotice(connId, id, "RUNTIME_UNAVAILABLE")
        val keys = mapOf("back" to 4, "home" to 3, "recents" to 187, "volume_up" to 24, "volume_down" to 25)
        val args = if (openSharePermission) AdbScreenShareRequest.command(app, connId)
            else if (action in keys) listOf("input", "keyevent", keys.getValue(action).toString())
            else if (action == "open_url") {
                val uri = android.net.Uri.parse(payload.getString("url"))
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrEmpty() || uri.userInfo != null || uri.toString().length > 2048)
                    return@synchronized actionNotice(connId, id, "REQUEST_INVALID")
                listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", uri.toString())
            } else return@synchronized actionNotice(connId, id, "OPERATION_UNSUPPORTED")
        val serial = TunnelAdbManager.selectedLocalSerial() ?: return@synchronized actionNotice(connId, id, "LOCAL_ADB_REQUIRED")
        try { actionWorker.execute {
            val code = try {
                val target = LocalAdbIdentityProbe(app).probe(serial).trustedTarget
                if (target == null) "LOCAL_ADB_REQUIRED" else {
                    val permitted = synchronized(lock) { hasConsent(connId) && hooks?.connectionAuthorized(connId) == true }
                    if (!permitted) "CONSENT_REVOKED" else {
                        val spec = LocalAdbProcessSpec(app)
                        // ADB joins shell args; quote every literal, including the URL.
                        val command = args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
                        if (LocalAdbTargetPolicy.validate(serial) == null) "LOCAL_ADB_REQUIRED"
                        else if (BoundedProcessRunner.run(spec.command(target, listOf("shell", "-T", "-n", command)),
                                spec.workingDirectory, spec.environment, 5_000,
                                cancelled = { !authorizedAction(connId) }).succeeded)
                            { if (openSharePermission) "CAPTURE_PERMISSION_REQUIRED" else "" } else "ADB_ACTION_FAILED"
                    }
                }
            } catch (_: Exception) { "ADB_ACTION_FAILED" }
            val notice = synchronized(lock) { actionNotice(connId, id, code) }
            hooks?.onState(connId, notice)
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            return@synchronized actionNotice(connId, id, "ADB_BUSY")
        }
        actionNotice(connId, id, complete = false)
    }

    private fun effectOperation(active: TunnelAdbSession, connId: Int, id: String,
                                enabled: Boolean, version: Long) {
        blackSentVersion = version
        active.operation(AdbCommands.OVERLAY_BLACK, if (enabled) 1 else 0).whenComplete { result, failure ->
            val code = if (failure == null && result.code == AdbCommands.OK) ""
                else "OVERLAY_UNAVAILABLE"
            val notice = synchronized(lock) {
                // Old helper/old toggle results cannot overwrite the latest intent.
                if (session !== active || helperOwner != connId ||
                    version != blackVersion)
                    return@synchronized actionNotice(connId, id, "ACTION_CANCELLED")
                if (code.isEmpty()) {
                    blackEnabled = enabled
                }
                actionNotice(connId, id, code)
            }
            hooks?.onState(connId, notice)
        }
    }

    private fun nextTaskId(): Int {
        check(videoTaskCounter < Int.MAX_VALUE) { "VIDEO_TASK_LIMIT" }
        return ++videoTaskCounter
    }
    private fun ensureControlLocked(connId: Int) {
        if (!hasConsent(connId) || hooks?.connectionAuthorized(connId) != true || session != null || helperStarting) return
        val app = context ?: return
        val serial = TunnelAdbManager.selectedLocalSerial() ?: return
        helperOwner = connId; helperStarting = true; controlReady = false
        helperEpoch += 1; val expected = helperEpoch
        Thread({
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var lease: java.io.Closeable? = null
            while (lease == null && SystemClock.elapsedRealtime() < deadline) {
                val allowed = synchronized(lock) { helperOwner == connId && helperEpoch == expected && hasConsent(connId) }
                if (!allowed) return@Thread
                lease = TunnelAdbManager.acquireMirrorLease()
                if (lease == null) Thread.sleep(50)
            }
            val notice = synchronized(lock) {
                if (helperOwner != connId || helperEpoch != expected || !hasConsent(connId)) {
                    lease?.close(); return@Thread
                }
                helperStarting = false
                if (lease == null) {
                    videoRecoveryPending = videoRequested
                    scheduleControlRecoveryLocked(connId, expected)
                    if (owner == connId) { failLocked("LOCAL_ADB_BUSY"); snapshot() }
                    else actionNotice(connId, "control-start", "LOCAL_ADB_BUSY")
                } else try {
                    session = TunnelAdbSession(app, serial, expected, lease!!, events(connId, expected), 3).also { it.start() }
                    null
                } catch (_: Exception) {
                    lease?.close(); session = null
                    videoRecoveryPending = videoRequested
                    scheduleControlRecoveryLocked(connId, expected)
                    if (owner == connId) { failLocked("HELPER_START_FAILED"); snapshot() }
                    else actionNotice(connId, "control-start", "HELPER_START_FAILED")
                }
            }
            notice?.let { hooks?.onState(connId, it) }
        }, "tunnel-adb-control-start").apply { isDaemon = true; start() }
    }
    private fun dispatchVideoTaskLocked() {
        val active = session ?: return
        if (!controlReady || videoTaskId <= 0 || issuedTaskId == videoTaskId) return
        val task = videoTaskId; val mode = requestedMode; val recipient = helperOwner
        issuedTaskId = task
        active.videoTask(mode, task).whenComplete { result, failure ->
            synchronized(lock) {
                if (session !== active || task != videoTaskId) return@whenComplete
                if (failure == null && result.code == AdbCommands.OK) {
                    videoDispatchRetries = 0
                    return@whenComplete
                }
                // Retry the same idempotent task, including OFF. Congested
                // control must not change providers or strand a running encoder.
                issuedTaskId = 0
                val delay = longArrayOf(250, 500, 1000, 2000)[videoDispatchRetries.coerceAtMost(3)]
                videoDispatchRetries = (videoDispatchRetries + 1).coerceAtMost(3)
                Thread({
                    Thread.sleep(delay)
                    synchronized(lock) {
                        if (session === active && task == videoTaskId && issuedTaskId == 0 &&
                            helperOwner == recipient && hasConsent(recipient)) dispatchVideoTaskLocked()
                    }
                }, "tunnel-adb-video-request").apply { isDaemon = true; start() }
            }
        }
    }
    private fun closeAuxiliaryLocked() {
        // Historical name retained for revoke callers: only authority loss closes control.
        blackRequested = false; blackEnabled = false; controlReady = false
        videoRecoveryPending = false
        videoRequested = false
        blackVersion++; blackSentVersion = -1
        helperEpoch += 1; helperOwner = 0; helperStarting = false
        val old = session; session = null; mask = 0; old?.close()
        globalInputSequence = 0; geometryKey = ""; displayedGeometry = null
        queuedMove = null
        if (pausedByUs) { hooks?.setAccessibilityPaused(false); pausedByUs = false }
    }
    private fun inputGeometry(): Pair<Int, Int> {
        val geometry = displayedGeometry ?: hooks?.normalGeometry() ?: (0 to 0)
        val physical = hooks?.normalGeometry() ?: (0 to 0)
        // Source labels do not change normalized coordinates. Only real geometry
        // changes cancel a held gesture when video/screenshot/layout is replaced.
        val key = "${geometry.first}:${geometry.second}:${physical.first}:${physical.second}"
        if (key != geometryKey) { geometryKey = key; geometryRevision += 1; globalTouchGeneration += 1; session?.operation(AdbCommands.RELEASE_INPUT) }
        return geometry
    }
    @JvmStatic fun normalPresented(connId: Int) = synchronized(lock) {
        if (connId != consentOwner && connId != lastVideoOwner) return@synchronized
        displayedGeometry = null; inputGeometry()
    }
    @JvmStatic fun inputStatus(connId: Int): String = synchronized(lock) {
        val size = inputGeometry()
        JSONObject().put("ready", connId == helperOwner && inputReady())
            .put("width", size.first).put("height", size.second).put("revision", geometryRevision).toString()
    }
    @JvmStatic fun globalInput(connId: Int, json: String) {
        synchronized(lock) {
            if (connId != helperOwner || !inputReady() || json.length > 8192) return
            val work = try {
                val request = JSONObject(json)
                requireKeys(request, setOf("v", "op", "operationId", "sequence", "geometryRevision", "payload"))
                require(number(request, "v") == 1L && request.getString("op") == "input")
                val size = inputGeometry()
                require(number(request, "geometryRevision") == geometryRevision)
                val sequence = number(request, "sequence"); require(sequence > globalInputSequence)
                val args = input(request.getJSONObject("payload"), size.first, size.second)
                globalInputSequence = sequence
                InputWork(session!!, geometryRevision, args, globalTouchGeneration, request.optString("operationId").take(64))
            } catch (_: Exception) {
                session?.operation(AdbCommands.RELEASE_INPUT); return
            }
            val isMove = work.args[0] == AdbCommands.TOUCH && work.args[1] == android.view.MotionEvent.ACTION_MOVE
            val previous = queuedMove
            if (isMove && previous != null && !previous.started && previous.connId == connId &&
                previous.work.active === work.active && previous.work.geometry == work.geometry &&
                previous.work.touchGeneration == work.touchGeneration) {
                previous.work = work
                return
            }
            val task = InputTask(connId, work)
            queuedMove = if (isMove) task else null
            // Enqueue under the same lock as sequence validation so callbacks
            // from different receive threads cannot reorder a DOWN and its UP.
            try { inputWorker.execute(task) }
            catch (_: java.util.concurrent.RejectedExecutionException) {
                if (queuedMove === task) queuedMove = null
                cancelInputWork(connId, work, "HELPER_BUSY")
            }
        }
    }
    private fun executeInputWork(connId: Int, work: InputWork) {
        val active = work.active; val args = work.args
        val touch = args[0] == AdbCommands.TOUCH
        try {
            val result = synchronized(lock) {
                if (session !== active || connId != helperOwner || !inputReady() || work.geometry != geometryRevision ||
                    (touch && work.touchGeneration != globalTouchGeneration)) {
                    return
                }
                active.operation(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
            }.get(3, java.util.concurrent.TimeUnit.SECONDS)
            if (result.code != AdbCommands.OK) {
                cancelInputWork(connId, work, when (result.code) {
                    AdbCommands.STALE_GEOMETRY -> "STALE_FRAME"
                    AdbCommands.BUSY -> "HELPER_BUSY"
                    else -> "INPUT_REJECTED"
                })
            }
        } catch (_: Exception) {
            cancelInputWork(connId, work, "INPUT_OPERATION_TIMEOUT")
        }
    }
    private fun cancelInputWork(connId: Int, work: InputWork, code: String) {
        val notice = synchronized(lock) {
            if (session !== work.active) return
            if (work.touchGeneration == globalTouchGeneration) globalTouchGeneration += 1
            work.active.operation(AdbCommands.RELEASE_INPUT)
            actionNotice(connId, work.operationId, code)
        }
        hooks?.onState(connId, notice)
    }
    private fun scheduleControlRecoveryLocked(connId: Int, expectedEpoch: Long) {
        if (!hasConsent(connId) || hooks?.connectionAuthorized(connId) != true) return
        if (scheduledRecoveryEpoch == expectedEpoch) return
        scheduledRecoveryEpoch = expectedEpoch
        val now = SystemClock.elapsedRealtime()
        if (now - controlRetryWindow > 60_000) { controlRetryWindow = now; controlRetryCount = 0 }
        val delays = longArrayOf(500, 1000, 2000, 5000, 10000)
        val delay = delays[controlRetryCount.coerceAtMost(delays.lastIndex)]
        controlRetryCount = (controlRetryCount + 1).coerceAtMost(delays.lastIndex)
        Thread({
            Thread.sleep(delay)
            synchronized(lock) {
                if (scheduledRecoveryEpoch == expectedEpoch) scheduledRecoveryEpoch = -1
                if (helperEpoch == expectedEpoch && session == null && !helperStarting && hasConsent(connId)) {
                    ensureControlLocked(connId)
                    // Wireless ADB may temporarily have no selected serial.
                    // Keep one backed-off retry; never create parallel helpers.
                    if (session == null && !helperStarting) scheduleControlRecoveryLocked(connId, expectedEpoch)
                }
            }
        }, "tunnel-adb-control-recovery").apply { isDaemon = true; start() }
    }
    private fun framePresent(vararg modes: Int): Boolean = session != null && requestedMode in modes &&
        lastEncodedFrameAt != 0L && SystemClock.elapsedRealtime() - lastEncodedFrameAt < 5_000

    private fun snapshot(code: String? = null): String = JSONObject()
        .put("v", 1).put("phase", phase).put("operationId", operationId).put("ownerConnId", owner)
        .put("sourceOperationId", operationId)
        .put("generation", generation).put("epoch", epoch).put("revision", revision)
        .put("width", width).put("height", height).put("actualFrameSource", source)
        .put("candidateFrameSource", candidateSource)
        .put("capturePaused", capturePaused).put("baseMode", if (baseLive) "live" else "stopped")
        .put("baseLiveRequested", baseLive)
        .put("resumeVideo", videoRecoveryPending && controlReady && mask != 0).put("resumeGeneration", generation)
        .put("adbVideoPresent", framePresent(0)).put("adbSpecialPresent", framePresent(1, 4))
        .put("videoStopped", phase == "IDLE" || (stopping && error == "STOPPED"))
        .put("overlayBlack", blackEnabled).put("overlayBlackRequested", blackRequested)
        .put("snapshotEnabled", snapshotEnabled).put("hierarchyEnabled", hierarchyEnabled)
        .put("frameOverride", when (videoSources.mode) { 1 -> "screenshot"; 2 -> "hierarchy"; 4 -> "screenshot_hierarchy"; else -> "none" })
        .put("localAdbReady", TunnelAdbManager.snapshot().shellReady)
        .put("videoReady", videoReady).put("inputReady", inputReady())
        .put("code", code ?: error.ifEmpty { videoDiagnostic })
        .put("operationRejected", !code.isNullOrEmpty())
        .put("accessibilityBound", hooks?.accessibilityBound() == true).put("accessibilityPaused", pausedByUs)
        .put("pendingConsentConnId", pendingOwner).put("pendingScopes", org.json.JSONArray(pendingScopes.toList()))
        .put("consentConnId", consentOwner).put("consentActive", hasConsent(consentOwner)).put("consentLifetime", "session")
        .put("capabilities", JSONObject().put("video", mask and AdbWire.CAP_VIDEO != 0)
            .put("input", "input" in scopes && mask and AdbWire.CAP_INPUT != 0)
            .put("keyframe", mask and AdbWire.CAP_KEYFRAME != 0)
            .put("snapshot", "snapshot" in scopes && mask and AdbWire.CAP_SCREENSHOT != 0)
            .put("hierarchy", "hierarchy" in scopes && mask and AdbWire.CAP_TREE != 0)
            .put("snapshotSelectable", "snapshot" in scopes).put("hierarchySelectable", "hierarchy" in scopes)
            .put("display", "display" in scopes && mask and AdbWire.CAP_DISPLAY != 0)
            .put("accessibility", "accessibility" in scopes)
            .put("overlay", "overlay" in scopes && (mask and AdbWire.CAP_OVERLAY != 0)))
        .toString()

    private fun requireKeys(value: JSONObject, allowed: Set<String>) {
        val keys = value.keys(); while (keys.hasNext()) require(keys.next() in allowed)
    }
    private fun number(value: JSONObject, name: String): Long {
        val raw = value.get(name); require(raw is Number)
        val n = raw.toLong(); require(n in 0..MAX_NUMBER && raw.toDouble() == n.toDouble()); return n
    }
}
