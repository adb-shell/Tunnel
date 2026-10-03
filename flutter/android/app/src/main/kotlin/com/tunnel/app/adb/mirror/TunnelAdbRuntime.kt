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
        fun setAdbCaptureCommitted(committed: Boolean)
        fun setAdbCaptureFrozen() { setAdbCaptureCommitted(true) }
        fun setNormalScreenShare(enabled: Boolean): Boolean = false
        fun accessibilityNavigation(action: String): Boolean = false
        fun openLocalAccessibilitySettings() {}
        fun openUrl(url: String): Boolean = false
        fun setOverlay(blockTouch: Boolean, black: Boolean): Boolean = false
        fun clearOwnedOverlay() {}
    }

    private const val MAX_NUMBER = 9007199254740991L
    private val lock = Any()
    private var context: Context? = null
    private var hooks: Hooks? = null
    private var watchdogStarted = false
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
    private var pendingKey: AdbWire.Packet? = null
    private var lastConfig: AdbWire.Packet? = null
    private var configSent = false
    private var freezeFrames = false
    private var error = ""
    private var operationId = ""
    private var videoReady = false
    private var mask = 0
    private var pausedByUs = false
    private var lastHeartbeat = 0L
    private var lastInputSequence = 0L
    private var session: TunnelAdbSession? = null
    private var stopping = false
    private var committed = false
    private var baseLive = true
    private var frameOverride = 0
    private var capturePaused = false
    private var requestedMode = 0
    private var auxiliarySession: TunnelAdbSession? = null
    private var auxiliaryOwner = 0
    private var auxiliaryEpoch = 0L
    private var auxiliaryReady = false
    private var blackRequested = false
    private var blackEnabled = false
    private var actionBusy = false
    private val actionWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "tunnel-adb-scoped-actions").apply { isDaemon = true }
    }
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
        if (!watchdogStarted) {
            watchdogStarted = true
            Thread({
                while (true) {
                    Thread.sleep(500)
                    val unresponsive = synchronized(lock) {
                        if (session != null && !stopping && owner != 0 && SystemClock.elapsedRealtime() - lastHeartbeat > 15_000) owner else 0
                    }
                    if (unresponsive != 0) stopOwner(unresponsive, "HEARTBEAT_LOST", true)
                }
            }, "tunnel-adb-lease").apply { isDaemon = true; start() }
        }
    }

    /** Authenticated explicit action; video stop does not revoke the connection's grant. */
    @JvmStatic fun grantConsent(connId: Int, requestedScopes: Set<String>): String {
        synchronized(lock) {
            if (connId <= 0 || requestedScopes.isEmpty() || "video" !in requestedScopes ||
                !knownScopes.containsAll(requestedScopes) ||
                (owner != 0 && owner != connId) || (auxiliaryOwner != 0 && auxiliaryOwner != connId)) return snapshot("CONSENT_INVALID")
            val narrowing = owner != 0 && !requestedScopes.containsAll(scopes)
            consentOwner = connId
            scopes = requestedScopes.toSet(); pendingOwner = 0; pendingScopes = emptySet(); error = ""
            if (narrowing) stopLocked("CONSENT_SCOPE_CHANGED")
            return snapshot()
        }
    }

    @JvmStatic fun getConsentPending(): String = synchronized(lock) { snapshot() }
    @JvmStatic fun status(): String = synchronized(lock) { snapshot() }
    private fun hasConsent(connId: Int): Boolean = connId > 0 && consentOwner == connId && "video" in scopes
    private fun clearConsentLocked() {
        AdbScreenShareRequest.clear()
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
            if (auxiliaryOwner == connId) closeAuxiliaryLocked()
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
                completed[replayKey]?.let { previous ->
                    return@synchronized if (previous.first == signature) previous.second else snapshot("OPERATION_REPLAY")
                }
                if (op == "status") { requireKeys(payload, emptySet()); return@synchronized snapshot() }
                if (op == "stop") {
                    requireKeys(payload, emptySet())
                    if ((owner != 0 && owner != connId) ||
                        (owner == 0 && lastVideoOwner != connId && consentOwner != connId))
                        return@synchronized snapshot("NOT_OWNER")
                    stopLocked("STOPPED")
                    return@synchronized JSONObject(snapshot()).put("videoStopped", true).toString()
                }
                if (op == "start") {
                    if (actionBusy) return@synchronized snapshot("LOCAL_ADB_BUSY")
                    requireKeys(payload, setOf("initialSource"))
                    val initialMode = when (payload.optString("initialSource", "ADB_CAPTURE")) {
                        "ADB_CAPTURE" -> 0
                        "IGNORE_CAPTURE" -> 1
                        "HIERARCHY_CAPTURE" -> 2
                        else -> return@synchronized snapshot("REQUEST_INVALID")
                    }
                    val requestedEpoch = number(request, "epoch"); require(requestedEpoch > 0)
                    val requestedGeneration = number(request, "generation"); require(requestedGeneration > 0)
                    if (stopping && owner == connId && error == "STOPPED") {
                        session?.close(); session = null; stopping = false; owner = 0
                    }
                    if (stopping || (owner != 0 && owner != connId)) return@synchronized snapshot("OWNER_BUSY")
                    if (!hasConsent(connId)) {
                        pendingOwner = connId; pendingScopes = knownScopes
                        return@synchronized snapshot("SESSION_ADB_AUTHORIZATION_REQUIRED")
                    }
                    if ((initialMode == 1 && "snapshot" !in scopes) || (initialMode == 2 && "hierarchy" !in scopes))
                        return@synchronized snapshot("SCOPE_DENIED")
                    if (owner == connId) return@synchronized snapshot(if (requestedEpoch == epoch) "" else "OWNER_BUSY")
                    val app = context ?: return@synchronized snapshot("RUNTIME_UNAVAILABLE")
                    val serial = TunnelAdbManager.selectedLocalSerial() ?: return@synchronized snapshot("LOCAL_ADB_REQUIRED")
                    if (auxiliaryOwner != 0 && auxiliaryOwner != connId) return@synchronized snapshot("OWNER_BUSY")
                    auxiliarySession?.close()
                    owner = connId; lastVideoOwner = connId
                    epoch = requestedEpoch; generation = requestedGeneration; operationId = id
                    helperEpoch = requestedEpoch; freezeFrames = false; pendingConfig = null; pendingKey = null
                    lastConfig = null; configSent = false
                    baseLive = true; frameOverride = initialMode; capturePaused = false; requestedMode = initialMode
                    revision = 0; mask = 0; error = ""
                    phase = "PREPARING"; source = "NONE"; videoReady = false; committed = false
                    lastHeartbeat = SystemClock.elapsedRealtime(); lastInputSequence = 0; completed.clear()
                    val expectedEpoch = epoch
                    Thread({
                        val deadline = SystemClock.elapsedRealtime() + 5_000
                        var lease: java.io.Closeable? = null
                        while (lease == null && SystemClock.elapsedRealtime() < deadline) {
                            val allowed = synchronized(lock) { owner == connId && helperEpoch == expectedEpoch && !stopping && hasConsent(connId) }
                            if (!allowed) return@Thread
                            lease = TunnelAdbManager.acquireMirrorLease()
                            if (lease == null) Thread.sleep(50)
                        }
                        val notice = synchronized(lock) {
                            if (owner != connId || helperEpoch != expectedEpoch || stopping || !hasConsent(connId)) {
                                lease?.close(); return@Thread
                            }
                            if (lease == null) { failLocked("LOCAL_ADB_BUSY"); snapshot() }
                            else try {
                                val candidate = TunnelAdbSession(app, serial, expectedEpoch, lease!!, events(connId, expectedEpoch), initialMode)
                                session = candidate; candidate.start(); null
                            } catch (_: Exception) { lease?.close(); failLocked("HELPER_START_FAILED"); snapshot() }
                        }
                        notice?.let { hooks?.onState(connId, it) }
                    }, "tunnel-adb-video-start").apply { isDaemon = true; start() }
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
                            pendingKey?.let { if (forward(it)) { videoReady = true; phase = "READY" } }
                            pendingKey = null
                        }
                        "heartbeat" -> { requireKeys(payload, emptySet()); lastHeartbeat = SystemClock.elapsedRealtime() }
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
                val result = snapshot(); completed[replayKey] = signature to result; result
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
        if (owner != 0 || stopping) return@synchronized failure("STOP_ADB_VIDEO_BEFORE_PAIRING")
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
                if (owner != id || helperEpoch != expectedEpoch || stopping) return
                val lostInput = committed && mask and AdbWire.CAP_INPUT != 0 && bits and AdbWire.CAP_INPUT == 0
                val lostOverride = (frameOverride == 1 && mask and AdbWire.CAP_SCREENSHOT != 0 && bits and AdbWire.CAP_SCREENSHOT == 0) ||
                    (frameOverride == 2 && mask and AdbWire.CAP_TREE != 0 && bits and AdbWire.CAP_TREE == 0)
                mask = bits
                if (pausedByUs && bits and AdbWire.CAP_INPUT == 0) { hooks?.setAccessibilityPaused(false); pausedByUs = false }
                if (blackRequested && bits and AdbWire.CAP_OVERLAY != 0)
                    session?.let { blackOperation(it, id, "overlay-resume", true) }
                if (lostInput) failLocked("INPUT_CAPABILITY_LOST")
                if (lostOverride && !stopping) {
                    frameOverride = 0; requestedMode = if (baseLive) 0 else 3
                    if (!baseLive && phase != "COMMITTED") failLocked("FRAME_PROVIDER_LOST")
                    else if (!baseLive) {
                        capturePaused = true; videoReady = false; source = "NONE"
                        session?.setCapturePaused(true); session?.operation(AdbCommands.RELEASE_INPUT)
                    }
                }
                snapshot()
            }
            hooks?.onState(id, notice)
        }
        override fun packet(packet: AdbWire.Packet): Boolean {
            var notice: String? = null
            val accepted = synchronized(lock) {
                if (owner != id || helperEpoch != expectedEpoch || stopping) return false
                if (packet.kind == AdbWire.VIDEO_CONFIG) {
                    if (requestedMode == 3) return true
                    capturePaused = false; session?.setCapturePaused(false)
                    lastConfig = packet; configSent = false
                    if (revision != packet.configRevision) {
                        val replacement = revision != 0L
                        revision = packet.configRevision; width = packet.width; height = packet.height
                        candidateSource = when (packet.flags) { 2 -> "ADB_SNAPSHOT"; 4 -> "ADB_HIERARCHY"; else -> "ADB_LIVE" }
                        phase = "PREPARING"; videoReady = false
                        session?.operation(AdbCommands.RELEASE_INPUT)
                        if (replacement) {
                            phase = "RECONFIGURE"; freezeFrames = true; pendingConfig = packet; pendingKey = null
                            notice = snapshot()
                        }
                    }
                }
                if (capturePaused) true else if (freezeFrames) {
                    if (packet.kind == AdbWire.VIDEO_FRAME && packet.flags and AdbWire.FLAG_KEY_FRAME != 0) pendingKey = packet
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
                if (owner != id || helperEpoch != expectedEpoch) return
                val finalReason = if (stopping && error.isNotEmpty()) error else reason
                val explicitStop = finalReason in setOf("STOPPED", "DISCONNECTED", "CONSENT_REVOKED", "CONSENT_SCOPE_CHANGED")
                session = null; stopping = false
                if (explicitStop) {
                    restoreHooks(); owner = 0; phase = "IDLE"
                    source = if (hooks?.normalCaptureReady() == true) "MEDIA_PROJECTION" else "NONE"
                    if (finalReason == "STOPPED" && blackRequested && hasConsent(id)) ensureAuxiliaryLocked(id, "overlay-after-video-stop")
                } else {
                    // Preserve the selected source/final frame until an explicit stop.
                    phase = "FAILED"; hooks?.setAdbCaptureFrozen()
                }
                videoReady = false; mask = 0; error = if (cleanupComplete) finalReason else "CLEANUP_INCOMPLETE"
                completed.clear(); snapshot().let { JSONObject(it).put("ownerConnId", id).toString() }
            }
            hooks?.onState(id, notice)
        }
    }

    private fun input(payload: JSONObject): IntArray {
        return when (payload.getString("type")) {
            "touch" -> {
                requireKeys(payload, setOf("type", "action", "x", "y", "width", "height"))
                val action = listOf("down", "up", "move", "cancel").indexOf(payload.getString("action")); require(action >= 0)
                val x = payload.getDouble("x"); val y = payload.getDouble("y")
                require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
                require(number(payload, "width") == width.toLong() && number(payload, "height") == height.toLong())
                intArrayOf(AdbCommands.TOUCH, action, (x * 1_000_000).toInt(), (y * 1_000_000).toInt(), width, height, 0)
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

    private fun sideAction(id: String, payload: JSONObject): String {
        val action = payload.getString("action")
        requireKeys(payload, setOf("action"))
        return when (action) {
            "ignore_off", "hierarchy_off" -> {
                if ((action == "ignore_off" && source == "ADB_SNAPSHOT") ||
                    (action == "hierarchy_off" && source == "ADB_HIERARCHY")) changeMode(id, true, 0)
                ""
            }
            "ignore_on", "hierarchy_on" -> {
                val scope = if (action == "ignore_on") "snapshot" else "hierarchy"
                if (scope !in scopes) "SCOPE_DENIED"
                else { changeMode(id, true, if (action == "ignore_on") 1 else 2); "" }
            }
            else -> "OPERATION_UNSUPPORTED"
        }
    }

    private fun changeMode(id: String, nextBase: Boolean, nextOverride: Int) {
        val mode = if (nextOverride != 0) nextOverride else if (nextBase) 0 else 3
        requestedMode = mode
        submit(id, AdbCommands.CAPTURE_MODE, mode, if (nextBase) 0 else 3, actionResult = true, onSuccess = {
            baseLive = nextBase; frameOverride = nextOverride
            if (mode == 3) {
                capturePaused = true; videoReady = false; source = "NONE"
                session?.setCapturePaused(true); session?.operation(AdbCommands.RELEASE_INPUT)
            } else session?.setCapturePaused(false)
        }, onFailure = { requestedMode = if (frameOverride != 0) frameOverride else if (baseLive) 0 else 3 })
    }

    private fun submit(id: String, operation: Int, vararg args: Int, actionResult: Boolean = false,
                       onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) {
        val arguments = IntArray(6); args.copyInto(arguments)
        val expected = helperEpoch; val recipient = owner
        session?.operation(operation, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5])
            ?.whenComplete { result, failure ->
                val notice = synchronized(lock) {
                    if (owner != recipient || helperEpoch != expected || stopping) return@whenComplete
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
                        failLocked("INPUT_CHANNEL_FAILED")
                    }
                    if (code.isEmpty() && operation in setOf(AdbCommands.TOUCH, AdbCommands.KEY)) return@whenComplete
                    if (actionResult) actionNotice(recipient, id, code)
                    else JSONObject(if (stopping) snapshot() else snapshot(code))
                        .put("requestOperationId", id).put("operationComplete", true).toString()
                }
                hooks?.onState(recipient, notice)
            }
    }

    private fun inputReady(): Boolean = phase == "COMMITTED" && videoReady && !capturePaused && committed && "input" in scopes && mask and AdbWire.CAP_INPUT != 0
    private fun forward(packet: AdbWire.Packet): Boolean = hooks?.sendEncoded(epoch, packet.configRevision, packet.width, packet.height,
        packet.ptsUs, packet.flags and AdbWire.FLAG_KEY_FRAME != 0, packet.kind == AdbWire.VIDEO_CONFIG, packet.payloadCopy()) == true
    private fun stopOwner(id: Int, reason: String, revoke: Boolean) {
        val notice = synchronized(lock) {
            if (id <= 0 || owner != id) return
            if (revoke && reason != "HEARTBEAT_LOST" && consentOwner == id) clearConsentLocked()
            if (reason == "HEARTBEAT_LOST") failLocked(reason) else stopLocked(reason)
            snapshot()
        }
        hooks?.onState(id, notice)
    }
    private fun stopLocked(reason: String) {
        // Explicit video stop restores normal capture, but keeps the ADB grant.
        if (stopping) { error = reason; phase = "STOPPING"; restoreHooks(); return }
        stopping = true; phase = "STOPPING"; error = reason; videoReady = false
        pendingConfig = null; pendingKey = null; freezeFrames = false
        lastConfig = null; configSent = false
        restoreHooks(); session?.close()
        if (session == null) {
            stopping = false; owner = 0; phase = "IDLE"
            source = if (hooks?.normalCaptureReady() == true) "MEDIA_PROJECTION" else "NONE"
        }
    }
    private fun failLocked(reason: String) {
        stopping = session != null; phase = "FAILED"; error = reason; videoReady = false
        pendingConfig = null; pendingKey = null; freezeFrames = true
        hooks?.setAdbCaptureFrozen(); session?.close()
    }
    /** Private Rust entry: never a remotely accepted arbitrary operation. */
    @JvmStatic fun abortVideo(connId: Int) = synchronized(lock) {
        if (owner == connId && connId > 0) {
            if (stopping && error == "STOPPED") session?.close()
            else failLocked("VIDEO_ABORTED")
        }
    }
    private fun restoreHooks() {
        hooks?.setAdbCaptureCommitted(false); committed = false
        if (pausedByUs) { hooks?.setAccessibilityPaused(false); pausedByUs = false }
        hooks?.clearOwnedOverlay()
    }

    private fun actionNotice(connId: Int, id: String, code: String = "", complete: Boolean = true): String {
        val key = "$connId:$id"
        val previous = actionResults[key]
        if (!complete && previous != null && JSONObject(previous.second).optBoolean("operationComplete")) return previous.second
        val notice = JSONObject().put("v", 1).put("kind", "action").put("phase", "ACTION_RESULT")
            .put("operationId", id).put("requestOperationId", id).put("operationComplete", complete)
            .put("generation", 0).put("epoch", 0).put("code", code)
            .put("overlayBlack", blackEnabled)
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
            if ("overlay" !in scopes) return@synchronized actionNotice(connId, id, "SCOPE_DENIED")
            blackRequested = action == "overlay_black_on"
            val active = session
            if (active != null && !stopping && mask and AdbWire.CAP_OVERLAY != 0) {
                blackOperation(active, connId, id, blackRequested)
                return@synchronized actionNotice(connId, id, complete = false)
            }
            if (active != null) return@synchronized actionNotice(connId, id,
                if (phase == "COMMITTED") "OVERLAY_UNAVAILABLE" else "ADB_BUSY")
            if (!blackRequested && auxiliarySession == null) return@synchronized actionNotice(connId, id)
            return@synchronized ensureAuxiliaryLocked(connId, id)
        }
        if (action in setOf("ignore_on", "ignore_off", "hierarchy_on", "hierarchy_off")) {
            if (session == null || owner != connId || phase != "COMMITTED")
                return@synchronized actionNotice(connId, id, "ADB_VIDEO_REQUIRED")
            if ((action == "ignore_off" && source != "ADB_SNAPSHOT") ||
                (action == "hierarchy_off" && source != "ADB_HIERARCHY")) return@synchronized actionNotice(connId, id)
            val result = sideAction(id, payload)
            return@synchronized actionNotice(connId, id, result, complete = result.isNotEmpty())
        }
        if (action in setOf("touch_block_on", "touch_block_off"))
            return@synchronized actionNotice(connId, id, "OPERATION_UNSUPPORTED")
        if ((if (openSharePermission) "video" else "input") !in scopes) return@synchronized actionNotice(connId, id, "SCOPE_DENIED")
        if (owner != 0 && phase in setOf("PREPARING", "READY", "WAITING_PRESENTED", "RECONFIGURE", "STOPPING"))
            return@synchronized actionNotice(connId, id, "ADB_BUSY")
        if (actionBusy || (auxiliarySession != null && !auxiliaryReady))
            return@synchronized actionNotice(connId, id, "ADB_BUSY")
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
        actionBusy = true
        actionWorker.execute {
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
            val notice = synchronized(lock) { actionBusy = false; actionNotice(connId, id, code) }
            hooks?.onState(connId, notice)
        }
        actionNotice(connId, id, complete = false)
    }

    private fun blackOperation(active: TunnelAdbSession, connId: Int, id: String, enabled: Boolean) {
        active.operation(AdbCommands.OVERLAY_BLACK, if (enabled) 1 else 0).whenComplete { result, failure ->
            val code = if (failure == null && result.code == AdbCommands.OK) "" else "OVERLAY_UNAVAILABLE"
            val notice = synchronized(lock) {
                // A closed helper can complete its pending command after a new
                // helper has acquired the lease. It no longer owns overlay state.
                if ((session !== active || owner != connId || stopping) &&
                    (auxiliarySession !== active || auxiliaryOwner != connId))
                    return@synchronized actionNotice(connId, id, "ACTION_CANCELLED")
                if (code.isEmpty()) blackEnabled = enabled
                else blackRequested = blackEnabled
                if (code.isEmpty() && !enabled && auxiliarySession === active) active.close()
                actionNotice(connId, id, code)
            }
            hooks?.onState(connId, notice)
        }
    }

    private fun ensureAuxiliaryLocked(connId: Int, id: String): String {
        if (auxiliarySession != null) {
            if (auxiliaryOwner != connId || !auxiliaryReady) return actionNotice(connId, id, "ADB_BUSY")
            blackOperation(auxiliarySession!!, connId, id, blackRequested)
            return actionNotice(connId, id, complete = false)
        }
        if (session != null || stopping || actionBusy) return actionNotice(connId, id, "ADB_BUSY")
        val app = context ?: return actionNotice(connId, id, "RUNTIME_UNAVAILABLE")
        val serial = TunnelAdbManager.selectedLocalSerial() ?: return actionNotice(connId, id, "LOCAL_ADB_REQUIRED")
        val lease = TunnelAdbManager.acquireMirrorLease() ?: return actionNotice(connId, id, "LOCAL_ADB_BUSY")
        auxiliaryEpoch += 1; val expected = auxiliaryEpoch
        auxiliaryOwner = connId; auxiliaryReady = false
        var capabilityUpdates = 0
        val callbacks = object : TunnelAdbSession.Events {
            override fun packet(packet: AdbWire.Packet): Boolean = true
            override fun capabilities(bits: Int) {
                synchronized(lock) {
                    if (auxiliaryOwner != connId || auxiliaryEpoch != expected || auxiliaryReady) return
                    capabilityUpdates += 1
                    if (bits and AdbWire.CAP_OVERLAY == 0) {
                        if (capabilityUpdates >= 2) {
                            blackRequested = false; auxiliarySession?.close()
                            hooks?.onState(connId, actionNotice(connId, id, "OVERLAY_UNAVAILABLE"))
                        }
                        return
                    }
                    auxiliaryReady = true
                    auxiliarySession?.let { blackOperation(it, connId, id, blackRequested) }
                }
            }
            override fun ended(reason: String, cleanupComplete: Boolean) {
                val notice = synchronized(lock) {
                    if (auxiliaryEpoch != expected) return
                    val pending = !auxiliaryReady
                    auxiliarySession = null; auxiliaryOwner = 0; auxiliaryReady = false
                    if (owner == 0) blackEnabled = false
                    if (pending && reason != "STOPPED") actionNotice(connId, id, "OVERLAY_UNAVAILABLE") else null
                }
                notice?.let { hooks?.onState(connId, it) }
            }
        }
        try {
            auxiliarySession = TunnelAdbSession(app, serial, expected, lease, callbacks, 3).also { it.start() }
        } catch (_: Exception) { lease.close(); auxiliarySession = null; auxiliaryOwner = 0; return actionNotice(connId, id, "OVERLAY_UNAVAILABLE") }
        return actionNotice(connId, id, complete = false)
    }

    private fun closeAuxiliaryLocked() {
        blackRequested = false; blackEnabled = false; auxiliarySession?.close()
    }
    private fun snapshot(code: String? = null): String = JSONObject()
        .put("v", 1).put("phase", phase).put("operationId", operationId).put("ownerConnId", owner)
        .put("sourceOperationId", operationId)
        .put("generation", generation).put("epoch", epoch).put("revision", revision)
        .put("width", width).put("height", height).put("actualFrameSource", source)
        .put("candidateFrameSource", candidateSource)
        .put("capturePaused", capturePaused).put("baseMode", if (baseLive) "live" else "stopped")
        .put("videoStopped", phase == "IDLE" || (stopping && error == "STOPPED"))
        .put("overlayBlack", blackEnabled)
        .put("frameOverride", when (frameOverride) { 1 -> "screenshot"; 2 -> "hierarchy"; else -> "none" })
        .put("localAdbReady", TunnelAdbManager.snapshot().shellReady)
        .put("videoReady", videoReady).put("inputReady", inputReady()).put("code", code ?: error)
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
            .put("overlay", "overlay" in scopes && (mask and AdbWire.CAP_OVERLAY != 0 || auxiliaryReady)))
        .toString()

    private fun requireKeys(value: JSONObject, allowed: Set<String>) {
        val keys = value.keys(); while (keys.hasNext()) require(keys.next() in allowed)
    }
    private fun number(value: JSONObject, name: String): Long {
        val raw = value.get(name); require(raw is Number)
        val n = raw.toLong(); require(n in 0..MAX_NUMBER && raw.toDouble() == n.toDouble()); return n
    }
}
