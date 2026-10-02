package com.tunnel.app.adb.mirror

import android.content.Context
import android.os.SystemClock
import com.tunnel.adb.protocol.AdbCommands
import com.tunnel.adb.protocol.AdbWire
import com.tunnel.app.adb.TunnelAdbManager
import org.json.JSONObject
import java.util.LinkedHashMap

/** Endpoint authority. A connected peer is not consent to run the local shell helper. */
object TunnelAdbRuntime {
    interface Hooks {
        fun sendEncoded(epoch: Long, revision: Long, width: Int, height: Int, ptsUs: Long,
                        key: Boolean, config: Boolean, bytes: ByteArray): Boolean
        fun onState(connId: Int, json: String)
        fun setAccessibilityPaused(paused: Boolean): Boolean
        fun disableOwnAccessibility(): Boolean
        fun accessibilityBound(): Boolean
        fun normalCaptureReady(): Boolean
        fun setAdbCaptureCommitted(committed: Boolean)
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
    private var consentOwner = 0
    private var consentUntil = 0L
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
    private val knownScopes = setOf("video", "input", "accessibility", "display", "snapshot", "hierarchy", "overlay")
    private val completed = object : LinkedHashMap<String, Pair<String, String>>(128, .75f, true) {
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
                    val expired = synchronized(lock) {
                        if (owner != 0 && (SystemClock.elapsedRealtime() >= consentUntil ||
                                    SystemClock.elapsedRealtime() - lastHeartbeat > 15_000)) owner else 0
                    }
                    if (expired != 0) stopOwner(expired, "LEASE_EXPIRED", true)
                }
            }, "tunnel-adb-lease").apply { isDaemon = true; start() }
        }
    }

    /** Local Activity only; MainService verifies connId is currently authenticated. */
    @JvmStatic fun grantConsent(connId: Int, requestedScopes: Set<String>, ttlSeconds: Int): String {
        synchronized(lock) {
            if (connId <= 0 || requestedScopes.isEmpty() || "video" !in requestedScopes ||
                !knownScopes.containsAll(requestedScopes) || ttlSeconds !in 30..3600 ||
                (owner != 0 && owner != connId)) return snapshot("CONSENT_INVALID")
            val narrowing = owner != 0 && !requestedScopes.containsAll(scopes)
            consentOwner = connId; consentUntil = SystemClock.elapsedRealtime() + ttlSeconds * 1000L
            scopes = requestedScopes.toSet(); pendingOwner = 0; pendingScopes = emptySet(); error = ""
            if (narrowing) stopLocked("CONSENT_SCOPE_CHANGED")
            return snapshot()
        }
    }

    @JvmStatic fun getConsentPending(): String = synchronized(lock) { snapshot() }
    @JvmStatic fun status(): String = synchronized(lock) { snapshot() }
    @JvmStatic fun revokeConsent() {
        val id = synchronized(lock) { consentOwner = 0; consentUntil = 0; scopes = emptySet(); pendingOwner = 0; pendingScopes = emptySet(); owner }
        if (id != 0) stopOwner(id, "CONSENT_REVOKED", true)
    }
    @JvmStatic fun onDisconnected(connId: Int) {
        synchronized(lock) {
            if (consentOwner == connId) { consentOwner = 0; consentUntil = 0; scopes = emptySet() }
            if (pendingOwner == connId) { pendingOwner = 0; pendingScopes = emptySet() }
        }
        stopOwner(connId, "DISCONNECTED", true)
    }
    @JvmStatic fun shutdown() { revokeConsent(); synchronized(lock) { hooks = null; context = null } }

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
            synchronized(lock) {
                val signature = request.toString()
                val replayKey = "$connId:$id:$op:${request.optLong("epoch", 0)}"
                completed[replayKey]?.let { previous ->
                    return@synchronized if (previous.first == signature) previous.second else snapshot("OPERATION_REPLAY")
                }
                if (op == "status") { requireKeys(payload, emptySet()); return@synchronized snapshot() }
                if (op == "start") {
                    requireKeys(payload, emptySet())
                    val requestedEpoch = number(request, "epoch"); require(requestedEpoch > 0)
                    val requestedGeneration = number(request, "generation"); require(requestedGeneration > 0)
                    if (stopping || (owner != 0 && owner != connId)) return@synchronized snapshot("OWNER_BUSY")
                    if (consentOwner != connId || SystemClock.elapsedRealtime() >= consentUntil || "video" !in scopes) {
                        pendingOwner = connId; pendingScopes = knownScopes
                        return@synchronized snapshot("LOCAL_CONSENT_REQUIRED")
                    }
                    if (owner == connId) return@synchronized snapshot(if (requestedEpoch == epoch) "" else "OWNER_BUSY")
                    val app = context ?: return@synchronized snapshot("RUNTIME_UNAVAILABLE")
                    val serial = TunnelAdbManager.selectedLocalSerial() ?: return@synchronized snapshot("LOCAL_ADB_REQUIRED")
                    val lease = TunnelAdbManager.acquireMirrorLease() ?: return@synchronized snapshot("LOCAL_ADB_BUSY")
                    owner = connId; epoch = requestedEpoch; generation = requestedGeneration; operationId = id
                    helperEpoch = requestedEpoch; freezeFrames = false; pendingConfig = null; pendingKey = null
                    lastConfig = null; configSent = false
                    baseLive = true; frameOverride = 0; capturePaused = false; requestedMode = 0
                    revision = 0; mask = 0; error = ""
                    phase = "PREPARING"; source = "NONE"; videoReady = false; committed = false
                    lastHeartbeat = SystemClock.elapsedRealtime(); lastInputSequence = 0; completed.clear()
                    try {
                        val candidate = TunnelAdbSession(app, serial, epoch, lease, events(connId, epoch))
                        session = candidate; candidate.start()
                    } catch (_: Exception) {
                        session = null; owner = 0; phase = "FAILED"; error = "HELPER_START_FAILED"; lease.close()
                        return@synchronized snapshot(error)
                    }
                } else {
                    if (owner != connId || owner == 0) return@synchronized snapshot("NOT_OWNER")
                    if (number(request, "generation") != generation) return@synchronized snapshot("STALE_GENERATION")
                    if ((op != "reconfigure" && number(request, "epoch") != epoch) ||
                        (op !in setOf("heartbeat", "stop", "reconfigure") && number(request, "revision") != revision))
                        return@synchronized snapshot("STALE_FRAME")
                    if (SystemClock.elapsedRealtime() >= consentUntil) return@synchronized snapshot("CONSENT_EXPIRED")
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
                        "stop" -> { requireKeys(payload, emptySet()); stopLocked("STOPPED") }
                        "input" -> {
                            if (!inputReady()) return@synchronized snapshot("INPUT_NOT_READY")
                            val sequence = number(request, "sequence")
                            if (sequence <= lastInputSequence) return@synchronized snapshot("INPUT_REPLAY")
                            val arguments = input(payload)
                            lastInputSequence = sequence
                            submit(id, arguments[0], *arguments.copyOfRange(1, arguments.size))
                        }
                        "side_action" -> {
                            if (phase != "COMMITTED") return@synchronized snapshot("NOT_READY")
                            val result = sideAction(id, payload)
                            if (result.isNotEmpty()) return@synchronized snapshot(result)
                        }
                        "accessibility_pause", "accessibility_disable" -> {
                            requireKeys(payload, emptySet())
                            if ("accessibility" !in scopes) return@synchronized snapshot("SCOPE_DENIED")
                            if (!inputReady()) return@synchronized snapshot("REPLACEMENT_NOT_COMMITTED")
                            val ok = if (op == "accessibility_pause") hooks?.setAccessibilityPaused(true) == true
                                else hooks?.disableOwnAccessibility() == true
                            if (!ok) return@synchronized snapshot("LOCAL_ACTION_REQUIRED")
                            if (op == "accessibility_pause") pausedByUs = true
                        }
                        "accessibility_resume" -> {
                            requireKeys(payload, emptySet())
                            if ("accessibility" !in scopes) return@synchronized snapshot("SCOPE_DENIED")
                            if (hooks?.setAccessibilityPaused(false) != true) return@synchronized snapshot("LOCAL_ACTION_REQUIRED")
                            pausedByUs = false
                        }
                        "accessibility_enable" -> {
                            requireKeys(payload, emptySet())
                            if ("accessibility" !in scopes) return@synchronized snapshot("SCOPE_DENIED")
                            hooks?.openLocalAccessibilitySettings()
                            return@synchronized snapshot("LOCAL_ACTION_REQUIRED")
                        }
                        else -> return@synchronized snapshot("OPERATION_UNSUPPORTED")
                    }
                }
                val result = snapshot(); completed[replayKey] = signature to result; result
            }
        } catch (_: Exception) { synchronized(lock) { snapshot("REQUEST_INVALID") } }
        return try {
            val output = JSONObject(response)
            output.put("requestOperationId", request.optString("operationId"))
                .put("requestEpoch", request.optLong("epoch", 0)).put("requestGeneration", request.optLong("generation", 0))
            if (output.optBoolean("operationRejected", false)) {
                output.put("operationId", request.optString("operationId"))
                    .put("epoch", request.optLong("epoch", 0)).put("generation", request.optLong("generation", 0))
            }
            output.toString()
        } catch (_: Exception) { response }
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
                if (lostInput) stopLocked("INPUT_CAPABILITY_LOST")
                if (lostOverride && !stopping) {
                    frameOverride = 0; requestedMode = if (baseLive) 0 else 3
                    if (!baseLive && phase != "COMMITTED") stopLocked("FRAME_PROVIDER_LOST")
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
                restoreHooks(); session = null; stopping = false; owner = 0
                phase = if (finalReason == "STOPPED") "IDLE" else "FAILED"
                source = if (hooks?.normalCaptureReady() == true) "MEDIA_PROJECTION" else "NONE"
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
        requireKeys(payload, if (action == "open_url") setOf("action", "url") else setOf("action"))
        val navigation = listOf("back", "home", "recents", "volume_up", "volume_down").indexOf(action)
        if (navigation >= 0) {
            if (!inputReady()) return "INPUT_NOT_READY"
            submit(id, AdbCommands.NAVIGATE, navigation + 1); return ""
        }
        return when (action) {
            "display_on", "display_off" -> {
                if ("display" !in scopes) "SCOPE_DENIED"
                else { submit(id, AdbCommands.DISPLAY, if (action == "display_on") 1 else 0); "" }
            }
            "touch_block_on" -> if ("overlay" !in scopes) "SCOPE_DENIED"
                else if (hooks?.setOverlay(true, false) == true) "" else "LOCAL_ACTION_REQUIRED"
            "touch_block_off" -> { hooks?.clearOwnedOverlay(); "" }
            "share_start" -> {
                if (baseLive && frameOverride == 0 && !capturePaused) session?.keyframe()
                else changeMode(id, true, 0)
                ""
            }
            "share_stop" -> { changeMode(id, false, frameOverride); "" }
            "ignore_off", "hierarchy_off" -> {
                if ((action == "ignore_off" && source == "ADB_SNAPSHOT") || (action == "hierarchy_off" && source == "ADB_HIERARCHY"))
                    changeMode(id, baseLive, 0)
                "" // Closing a non-current override never cancels the active override.
            }
            "ignore_on", "hierarchy_on" -> {
                val scope = if (action == "ignore_on") "snapshot" else "hierarchy"
                if (scope !in scopes) "SCOPE_DENIED"
                else { changeMode(id, baseLive, if (action == "ignore_on") 1 else 2); "" }
            }
            "open_url" -> {
                if ("input" !in scopes) "SCOPE_DENIED" else {
                    val uri = android.net.Uri.parse(payload.getString("url"))
                    if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrEmpty() || uri.userInfo != null || uri.toString().length > 2048) "REQUEST_INVALID"
                    else if (hooks?.openUrl(uri.toString()) == true) "" else "LOCAL_ACTION_REQUIRED"
                }
            }
            else -> "OPERATION_UNSUPPORTED"
        }
    }

    private fun changeMode(id: String, nextBase: Boolean, nextOverride: Int) {
        val mode = if (nextOverride != 0) nextOverride else if (nextBase) 0 else 3
        requestedMode = mode
        submit(id, AdbCommands.CAPTURE_MODE, mode, if (nextBase) 0 else 3, onSuccess = {
            baseLive = nextBase; frameOverride = nextOverride
            if (mode == 3) {
                capturePaused = true; videoReady = false; source = "NONE"
                session?.setCapturePaused(true); session?.operation(AdbCommands.RELEASE_INPUT)
            } else session?.setCapturePaused(false)
        }, onFailure = { requestedMode = if (frameOverride != 0) frameOverride else if (baseLive) 0 else 3 })
    }

    private fun submit(id: String, operation: Int, vararg args: Int, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) {
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
                        stopLocked("INPUT_CHANNEL_FAILED")
                    }
                    if (code.isEmpty() && operation in setOf(AdbCommands.TOUCH, AdbCommands.KEY)) return@whenComplete
                    JSONObject(if (stopping) snapshot() else snapshot(code))
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
            if (revoke) { consentOwner = 0; scopes = emptySet(); consentUntil = 0 }
            stopLocked(reason); snapshot()
        }
        hooks?.onState(id, notice)
    }
    private fun stopLocked(reason: String) {
        if (stopping) return
        stopping = true; phase = "STOPPING"; error = reason; videoReady = false
        pendingConfig = null; pendingKey = null; freezeFrames = false
        lastConfig = null; configSent = false
        restoreHooks(); session?.close()
    }
    private fun restoreHooks() {
        hooks?.setAdbCaptureCommitted(false); committed = false
        if (pausedByUs) { hooks?.setAccessibilityPaused(false); pausedByUs = false }
        hooks?.clearOwnedOverlay()
    }
    private fun snapshot(code: String? = null): String = JSONObject()
        .put("v", 1).put("phase", phase).put("operationId", operationId).put("ownerConnId", owner)
        .put("sourceOperationId", operationId)
        .put("generation", generation).put("epoch", epoch).put("revision", revision)
        .put("width", width).put("height", height).put("actualFrameSource", source)
        .put("candidateFrameSource", candidateSource)
        .put("capturePaused", capturePaused).put("baseMode", if (baseLive) "live" else "stopped")
        .put("frameOverride", when (frameOverride) { 1 -> "screenshot"; 2 -> "hierarchy"; else -> "none" })
        .put("localAdbReady", TunnelAdbManager.snapshot().shellReady)
        .put("videoReady", videoReady).put("inputReady", inputReady()).put("code", code ?: error)
        .put("operationRejected", !code.isNullOrEmpty())
        .put("accessibilityBound", hooks?.accessibilityBound() == true).put("accessibilityPaused", pausedByUs)
        .put("pendingConsentConnId", pendingOwner).put("pendingScopes", org.json.JSONArray(pendingScopes.toList()))
        .put("consentConnId", consentOwner).put("consentRemainingSeconds", ((consentUntil - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0))
        .put("capabilities", JSONObject().put("video", mask and AdbWire.CAP_VIDEO != 0)
            .put("input", "input" in scopes && mask and AdbWire.CAP_INPUT != 0)
            .put("keyframe", mask and AdbWire.CAP_KEYFRAME != 0)
            .put("snapshot", "snapshot" in scopes && mask and AdbWire.CAP_SCREENSHOT != 0)
            .put("hierarchy", "hierarchy" in scopes && mask and AdbWire.CAP_TREE != 0)
            .put("snapshotSelectable", "snapshot" in scopes).put("hierarchySelectable", "hierarchy" in scopes)
            .put("display", "display" in scopes && mask and AdbWire.CAP_DISPLAY != 0)
            .put("accessibility", "accessibility" in scopes).put("overlay", false))
        .toString()

    private fun requireKeys(value: JSONObject, allowed: Set<String>) {
        val keys = value.keys(); while (keys.hasNext()) require(keys.next() in allowed)
    }
    private fun number(value: JSONObject, name: String): Long {
        val raw = value.get(name); require(raw is Number)
        val n = raw.toLong(); require(n in 0..MAX_NUMBER && raw.toDouble() == n.toDouble()); return n
    }
}
