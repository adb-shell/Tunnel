package com.cloudsend.app

/**
 * Handle events from flutter
 * Request MediaProjection permission
 *
 * Inspired by [droidVNC-NG] https://github.com/bk138/droidVNC-NG
 */

import pkg2230.ClsFx9V0S

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import com.cloudsend.app.adb.CloudSendAdbManager
import com.hjq.permissions.XXPermissions
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread


class oFtTiPzsqzBHGigp : FlutterActivity() {
    companion object {
        var flutterMethodChannel: MethodChannel? = null
        private var _rdClipboardManager: ig2xH1U3RDNsb7CS? = null
        val rdClipboardManager: ig2xH1U3RDNsb7CS?
            get() = _rdClipboardManager;
    }

    private val channelTag = p50.a(byteArrayOf(-50, 8, 30, -51, 78, 44, 9, -49), byteArrayOf(-93, 75, 118, -84, 32, 66, 108))
    private var mainService: DFm8Y8iMScvB2YDw? = null
    private var mainServiceBound = false

    private var isAudioStart = false
 

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        if (DFm8Y8iMScvB2YDw.isReady) {
            ensureMainServiceStarted()
        }
        flutterMethodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            channelTag
        )
        initFlutterChannel(flutterMethodChannel!!)
        thread { setCodecInfo() }
    }

    override fun onResume() {
        super.onResume()
        DFm8Y8iMScvB2YDw.ctx?.flushPendingVoiceCallEvent()
        val inputPer = nZW99cdXQ0COhB2o.isOpen
        activity.runOnUiThread {
            flutterMethodChannel?.invokeMethod(
                p50.a(byteArrayOf(-15, -20, 80, -13, -117, -33, 35, -90, 14, 59, -119, -1, -20, 104, -27, -101), byteArrayOf(-98, -126, 15, -128, -1, -66, 87, -61, 81, 88, -31)),
                mapOf(p50.a(byteArrayOf(-84, -103, 52, 68), byteArrayOf(-62, -8, 89, 33, 6, -21, -28, 34)) to p50.a(byteArrayOf(-20, -6, 118, -3, 9), byteArrayOf(-123, -108, 6, -120, 125, 29, -84)), p50.a(byteArrayOf(60, 80, -114, 53, -62), byteArrayOf(74, 49, -30, 64, -89, 10)) to inputPer.toString())
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DFm8Y8iMScvB2YDw.ctx?.flushPendingVoiceCallEvent()
    }

    private fun requestMediaProjection() {
        if (!DFm8Y8iMScvB2YDw.beginScreenSharePermissionRequest("activity-request")) {
            return
        }
        val intent = Intent(this, XerQvgpGBzr8FDFr::class.java).apply {
            action = ACT_REQUEST_MEDIA_PROJECTION
        }
        try {
            startActivityForResult(intent, REQ_INVOKE_PERMISSION_ACTIVITY_MEDIA_PROJECTION)
        } catch (e: Exception) {
            DFm8Y8iMScvB2YDw.finishScreenSharePermissionRequest("activity-launch-failed")
            Log.e("MainActivity", "requestMediaProjection: launch failed", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_INVOKE_PERMISSION_ACTIVITY_MEDIA_PROJECTION && resultCode == RES_FAILED) {
            DFm8Y8iMScvB2YDw.finishScreenSharePermissionRequest("activity-permission-denied")
            flutterMethodChannel?.invokeMethod(p50.a(byteArrayOf(26, 8, -95, 53, 4, -23, 28, 7, -95, 40, 19, -30, 31, 3, -99, 44, 8, -30, 27, 57, -99, 57, 15, -18, 16, 10, -101, 60), byteArrayOf(117, 102, -2, 88, 97, -115)), null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
      /*   getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

         @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = windowManager.maximumWindowMetrics
            HomeWidth = m.bounds.width()
            HomeHeight = m.bounds.height()
        } else {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            HomeWidth = dm.widthPixels
            HomeHeight = dm.heightPixels
        } */

        ClsFx9V0S.qka8qpr4(this)
        super.onCreate(savedInstanceState)
        if (_rdClipboardManager == null) {
            _rdClipboardManager = ig2xH1U3RDNsb7CS(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            ClsFx9V0S.jSYL8DA3(_rdClipboardManager!!)
        }
    }

    override fun onDestroy() {
    
        if (mainServiceBound) {
            unbindService(serviceConnection)
            mainServiceBound = false
        }
        super.onDestroy()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {

            val binder = service as DFm8Y8iMScvB2YDw.LocalBinder
            mainService = binder.getService()
            mainServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
  
            mainService = null
            mainServiceBound = false
        }
    }

    private fun ensureMainServiceStarted() {
        val intent = Intent(activity, DFm8Y8iMScvB2YDw::class.java).apply {
            action = DFm8Y8iMScvB2YDw.ACT_ENSURE_CORE_SERVICE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "ensureMainServiceStarted failed", e)
        }
        if (!mainServiceBound) {
            try {
                mainServiceBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                mainServiceBound = false
                Log.e("MainActivity", "bind MainService failed", e)
            }
        }
    }

    private fun activeMainService(): DFm8Y8iMScvB2YDw? {
        return mainService ?: DFm8Y8iMScvB2YDw.ctx
    }

    private fun initFlutterChannel(flutterMethodChannel: MethodChannel) {
        flutterMethodChannel.setMethodCallHandler { call, result ->
            // make sure result will be invoked, otherwise flutter will await forever
            when (call.method) {
                "ensure_core_service", "init_service" -> {
                    ensureMainServiceStarted()
                    result.success(true)
                }
                "check_service" -> {
                    ensureMainServiceStarted()
                    result.success(DFm8Y8iMScvB2YDw.isReady)
                }
                "start_screen_share" -> {
                    ensureMainServiceStarted()
                    val service = activeMainService()
                    if (DFm8Y8iMScvB2YDw.isStart) {
                        // Already sharing. A repeated start request must not try
                        // to restore/reuse MediaProjection, otherwise Android 14+
                        // can reopen the permission dialog and clear the active share.
                        result.success(true)
                    } else if (!DFm8Y8iMScvB2YDw.isReady || service == null) {
                        requestMediaProjection()
                        result.success(true)
                    } else {
                        service.restoreMediaProjection(
                            reason = "flutter-start-screen-share",
                            allowPermissionPrompt = true
                        )
                        result.success(true)
                    }
                }
                "start_capture" -> {
                    ensureMainServiceStarted()
                    if (DFm8Y8iMScvB2YDw.isStart) {
                        activeMainService()?.forceVideoFrameRefresh("legacy-start-capture")
                    }
                    result.success(DFm8Y8iMScvB2YDw.isStart)
                }
                "stop_screen_share", "stop_service" -> {
                    activeMainService()?.stopScreenShareOnly("flutter-command")
                    result.success(true)
                }
                "flush_pending_voice_call_event" -> {
                    activeMainService()?.flushPendingVoiceCallEvent()
                    result.success(true)
                }
                "cloudsend_adb_init" -> {
                    result.success(CloudSendAdbManager.initialize(applicationContext).toMap())
                }
                "cloudsend_adb_status" -> {
                    result.success(CloudSendAdbManager.snapshot().toMap())
                }
                "cloudsend_adb_output" -> {
                    result.success(CloudSendAdbManager.output(applicationContext))
                }
                "cloudsend_adb_start" -> {
                    thread {
                        try {
                            val state = CloudSendAdbManager.start(applicationContext).toMap()
                            runOnUiThread { result.success(state) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ADB_START_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "cloudsend_adb_stop" -> {
                    thread {
                        try {
                            val state = CloudSendAdbManager.stop(applicationContext).toMap()
                            runOnUiThread { result.success(state) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ADB_STOP_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "cloudsend_adb_local_shell" -> {
                    thread {
                        try {
                            val state = CloudSendAdbManager.startLocalShell(applicationContext).toMap()
                            runOnUiThread { result.success(state) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ADB_LOCAL_SHELL_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "cloudsend_adb_pair" -> {
                    val args = call.arguments as? Map<*, *>
                    val port = args?.get("port")?.toString() ?: ""
                    val code = args?.get("code")?.toString() ?: ""
                    thread {
                        try {
                            val state = CloudSendAdbManager.pair(applicationContext, port, code).toMap()
                            runOnUiThread { result.success(state) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ADB_PAIR_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "cloudsend_adb_command" -> {
                    val command = call.arguments?.toString() ?: ""
                    thread {
                        try {
                            val state = CloudSendAdbManager.sendCommand(applicationContext, command).toMap()
                            runOnUiThread { result.success(state) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ADB_COMMAND_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "cloudsend_adb_wireless_debug_status" -> {
                    result.success(CloudSendAdbManager.wirelessDebugStatus(applicationContext))
                }
                "cloudsend_adb_wireless_debug_set" -> {
                    val args = call.arguments as? Map<*, *>
                    val enable = args?.get("enable") == true
                    result.success(CloudSendAdbManager.setWirelessDebugging(applicationContext, enable))
                }
                "cloudsend_adb_wireless_debug_cancel" -> {
                    result.success(CloudSendAdbManager.cancelWirelessDebugging(applicationContext))
                }
                p50.a(byteArrayOf(25, -111, -85, 2, -11, -126, 49, -44, 6, -106, -95, 19), byteArrayOf(112, -1, -62, 118, -86, -15, 84, -90)) -> {
                    // Legacy init_service is core-service initialization only.
                    // It must not request MediaProjection.
                    ensureMainServiceStarted()
                    result.success(true)
                }
                p50.a(byteArrayOf(46, 62, 72, 107, -102, 86, -108, -32, -47, -34, 40, 56, 76), byteArrayOf(93, 74, 41, 25, -18, 9, -9, -127, -95, -86)) -> {
                    ensureMainServiceStarted()
                    if (DFm8Y8iMScvB2YDw.isStart) {
                        activeMainService()?.forceVideoFrameRefresh("legacy-obfuscated-start-capture")
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                p50.a(byteArrayOf(116, 82, -111, -105, 68, -98, 108, 117, 80, -105, -124, 126), byteArrayOf(7, 38, -2, -25, 27, -19, 9)) -> {
                  
                    activeMainService()?.let {
                        it.stopScreenShareOnly("legacy-flutter-command")
                        result.success(true)
                    } ?: let {
                        result.success(false)
                    }
                }
                p50.a(byteArrayOf(-61, -124, -18, -94, -18, -87, 83, -59, -98, -26, -88, -10, -123, 74, -49, -126), byteArrayOf(-96, -20, -117, -63, -123, -10, 35)) -> {
                    if (call.arguments is String) {
                        result.success(XXPermissions.isGranted(context, call.arguments as String))
                    } else {
                        result.success(false)
                    }
                }
                p50.a(byteArrayOf(-73, 98, 2, -49, 91, -41, -120, -38, -123, 27, -73, 106, 26, -55, 77, -51, -109, -21), byteArrayOf(-59, 7, 115, -70, 62, -92, -4, -123, -11, 126)) -> {
                    if (call.arguments is String) {
                        requestPermission(context, call.arguments as String)
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                START_ACTION -> {
                    if (call.arguments is String) {
                        startAction(context, call.arguments as String)
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                "request_auto_start_permission" -> {
                    // 请求自启动权限
                    requestAutoStartPermission(context)
                    result.success(true)
                }
                p50.a(byteArrayOf(2, 3, 11, 30, -36, -23, 42, -31, 45, 101, -76, 62, 27, 11, 15, -38, -33, 47, -5, 32, 111, -75), byteArrayOf(97, 107, 110, 125, -73, -74, 92, -120, 73, 0, -37)) -> {
                    mainService?.let {
                        result.success(it.checkMediaPermission())
                    } ?: let {
                        result.success(false)
                    }
                }
                p50.a(byteArrayOf(-33, -113, -51, 102, 35, -114, -6, -39, -107, -34, 108, 43, -76), byteArrayOf(-68, -25, -88, 5, 72, -47, -119)) -> {
                    Companion.flutterMethodChannel?.invokeMethod(
                        p50.a(byteArrayOf(77, -92, 92, 65, 14, 76, -124, 71, -107, 96, 90, 27, 67, -105, 71, -82), byteArrayOf(34, -54, 3, 50, 122, 45, -16)),
                        mapOf(p50.a(byteArrayOf(13, 75, 20, -108), byteArrayOf(99, 42, 121, -15, -100, -5, -89, 65, 49, 111, -44)) to p50.a(byteArrayOf(65, 0, -87, 115, -99), byteArrayOf(40, 110, -39, 6, -23, -84, -74, -46, -107, -98, -76)), p50.a(byteArrayOf(26, -72, -93, -30, 13), byteArrayOf(108, -39, -49, -105, 104, 60, 3)) to nZW99cdXQ0COhB2o.isOpen.toString())
                    )
                    Companion.flutterMethodChannel?.invokeMethod(
                        p50.a(byteArrayOf(-86, 4, 123, 108, -14, -117, -54, -91, 31, -90, 2, 69, 113, -31, -113, -38), byteArrayOf(-59, 106, 36, 31, -122, -22, -66, -64, 64)),
                        mapOf(p50.a(byteArrayOf(-70, -71, -112, 27), byteArrayOf(-44, -40, -3, 126, 123, -76, -16, -4)) to p50.a(byteArrayOf(-96, 18, 121, 53, -92), byteArrayOf(-51, 119, 29, 92, -59, 118, -58, -124)), p50.a(byteArrayOf(90, 113, -76, -128, 9), byteArrayOf(44, 16, -40, -11, 108, 74)) to DFm8Y8iMScvB2YDw.isReady.toString())
                    )
                    result.success(true)
                }
                p50.a(byteArrayOf(-50, 61, -43, 15, -95, -124, -101, -51, 60, -50), byteArrayOf(-67, 73, -70, 127, -2, -19, -11)) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        nZW99cdXQ0COhB2o.ctx?.disableSelf()
                    }
                    nZW99cdXQ0COhB2o.ctx = null
                    Companion.flutterMethodChannel?.invokeMethod(
                        p50.a(byteArrayOf(65, -92, -124, -58, -103, 60, 111, 75, -107, -72, -35, -116, 51, 124, 75, -82), byteArrayOf(46, -54, -37, -75, -19, 93, 27)),
                        mapOf(p50.a(byteArrayOf(5, -110, 88, 116), byteArrayOf(107, -13, 53, 17, -89, 14, 95, -88, -65, 101, -5)) to p50.a(byteArrayOf(89, -32, -82, 25, -58), byteArrayOf(48, -114, -34, 108, -78, 57, -66)), p50.a(byteArrayOf(64, 29, 37, -86, -25), byteArrayOf(54, 124, 73, -33, -126, -117, 45)) to nZW99cdXQ0COhB2o.isOpen.toString())
                    )
                    result.success(true)
                }
                p50.a(byteArrayOf(-7, 113, -8, 6, 116, 5, 50, -12, 127, -30, 12, 119, 0, 14, -5, 100, -1, 10, 127), byteArrayOf(-102, 16, -106, 101, 17, 105, 109)) -> {
                    if (call.arguments is Int) {
                        val id = call.arguments as Int
                        mainService?.cancelNotification(id)
                    } else {
                        result.success(true)
                    }
                }
                p50.a(byteArrayOf(83, 86, -4, -51, -122, 116, -97, -22, 89, 94, -23, -16, -127, 116, -71, -5, 89, 89, -17, -53), byteArrayOf(54, 56, -99, -81, -22, 17, -64, -103)) -> {
                    // https://blog.csdn.net/hanye2020/article/details/105553780
                    if (call.arguments as Boolean) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                    } else {
                        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                    }
                    result.success(true)

                }
                p50.a(byteArrayOf(21, 16, -26, -76, 29, 122, -38, -4, -17, 19, -126, 8, 18, -3, -124, 15, 113, -48), byteArrayOf(97, 98, -97, -21, 110, 3, -76, -97, -80, 112, -18)) -> {
                    rdClipboardManager?.syncClipboard(true)
                    result.success(true)
                }
                GET_START_ON_BOOT_OPT -> {
                    val prefs = getSharedPreferences(KEY_SHARED_PREFERENCES, MODE_PRIVATE)
                    result.success(prefs.getBoolean(KEY_START_ON_BOOT_OPT, false))
                }
                SET_START_ON_BOOT_OPT -> {
                    if (call.arguments is Boolean) {
                        val prefs = getSharedPreferences(KEY_SHARED_PREFERENCES, MODE_PRIVATE)
                        val edit = prefs.edit()
                        edit.putBoolean(KEY_START_ON_BOOT_OPT, call.arguments as Boolean)
                        edit.apply()
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                SYNC_APP_DIR_CONFIG_PATH -> {
                    if (call.arguments is String) {
                        val appDir = call.arguments as String
                        val prefs = getSharedPreferences(KEY_SHARED_PREFERENCES, MODE_PRIVATE)
                        val edit = prefs.edit()
                        edit.putString(KEY_APP_DIR_CONFIG_PATH, appDir)
                        edit.apply()
                        DFm8Y8iMScvB2YDw.ctx?.let {
                            ClsFx9V0S.xt4P9mWE(appDir, "")
                        }
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                GET_VALUE -> {
                    if (call.arguments is String) {
                        if (call.arguments == KEY_IS_SUPPORT_VOICE_CALL) {
                            result.success(isSupportVoiceCall())
                        } else {
                            result.error(p50.a(byteArrayOf(120, -66), byteArrayOf(85, -113, 28, -32, 100, 13, -82, -50)), p50.a(byteArrayOf(-52, -87, 1, -82, 67, 26, -12, 47, 20, 65, -59), byteArrayOf(-126, -58, 33, -35, 54, 121, -100, 15, 127, 36, -68)), null)
                        }
                    } else {
                        result.success(null)
                    }
                }
                p50.a(byteArrayOf(-126, 78, 118, -8, 96, 69, 110, -120, 127, 74, -17, 99, 64, 82, -98, 84, 72, -4, 123, 73, 105), byteArrayOf(-19, 32, 41, -114, 15, 44, 13)) -> {
                    onVoiceCallStarted()
                }
                p50.a(byteArrayOf(-86, 14, -89, 8, 57, -119, -54, -28, -102, 3, -103, 18, 58, -65, -54, -19, -86, 19, -99, 26), byteArrayOf(-59, 96, -8, 126, 86, -32, -87, -127)) -> {
                    onVoiceCallClosed()
                }
                else -> {
                    result.error(p50.a(byteArrayOf(-85, 50), byteArrayOf(-122, 3, 17, -54, 92, -34, -101, 126)), p50.a(byteArrayOf(118, -26, 56, -29, 68, 47, 80, -87, 117, -11, 69, 36, 87, -19), byteArrayOf(56, -119, 24, -112, 49, 76)), null)
                }
            }
        }
    }

    private fun setCodecInfo() {
 
        
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecs = codecList.codecInfos
        val codecArray = JSONArray()

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val wh = getScreenSize(windowManager)
        var w = wh.first
        var h = wh.second
        val align = 64
        w = (w + align - 1) / align * align
        h = (h + align - 1) / align * align
        codecs.forEach { codec ->
            val codecObject = JSONObject()
            codecObject.put(p50.a(byteArrayOf(-108, 9, 3, -105), byteArrayOf(-6, 104, 110, -14, 82, 84)), codec.name)
            codecObject.put(p50.a(byteArrayOf(-56, -97, -113, 68, -96, -63, -23, 49, -75, -45), byteArrayOf(-95, -20, -48, 33, -50, -94, -122, 85, -48)), codec.isEncoder)
            var hw: Boolean? = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hw = codec.isHardwareAccelerated
            } else {
                // https://chromium.googlesource.com/external/webrtc/+/HEAD/sdk/android/src/java/org/webrtc/MediaCodecUtils.java#29
                // https://chromium.googlesource.com/external/webrtc/+/master/sdk/android/api/org/webrtc/HardwareVideoEncoderFactory.java#229
                if (listOf(p50.a(byteArrayOf(15, -76, 120, -123, -126, -1, 47, -98, 76, -50, -53), byteArrayOf(64, -7, 32, -85, -27, -112)), p50.a(byteArrayOf(-63, 66, 89, -35, -41, -116, 119, -96), byteArrayOf(-114, 15, 1, -13, -124, -55, 52)), p50.a(byteArrayOf(-49, 126, 114, 25, 84, -22, 9, 93, 75, 11), byteArrayOf(-84, 76, 92, 120, 58, -114, 123, 50, 34, 111))).any { codec.name.startsWith(it, true) }) {
                    hw = false
                } else if (listOf(p50.a(byteArrayOf(-17, -114, -30, -86, -13, -118), byteArrayOf(-116, -68, -52, -37, -121, -29, -72, -3, 79, 55, 43)), p50.a(byteArrayOf(91, -107, 116, 74, 51, 49, -126, 113, 12, 98, -79, 72, 1, 45), byteArrayOf(20, -40, 44, 100, 66, 82, -19, 28, 34)), p50.a(byteArrayOf(-67, 111, 13, 7, -103, -18, -11, -86, -87, -127), byteArrayOf(-14, 34, 85, 41, -36, -106, -116, -60, -58)), p50.a(byteArrayOf(116, 74, 91, -85, -87, 90, 9, 121), byteArrayOf(59, 7, 3, -123, -63, 51, 122, 16, -53)), p50.a(byteArrayOf(-86, -75, -40, -24, 5, -53, 70), byteArrayOf(-27, -8, -128, -58, 72, -97, 13, 41)), p50.a(byteArrayOf(-81, 121, 10, -107, -71, -92, -34, -123, 88), byteArrayOf(-32, 52, 82, -69, -16, -54, -86)), p50.a(byteArrayOf(17, 75, 122, 94, -60, 6, 68, 47, 101, -75), byteArrayOf(94, 6, 34, 112, -118, 112, 45, 75, 12, -44, -89))).any { codec.name.startsWith(it, true) }) {
                    hw = true
                }
            }
            if (hw != true) {
                return@forEach
            }
            codecObject.put(p50.a(byteArrayOf(102, -96), byteArrayOf(14, -41, -36, 81, 86, -74, -6, -95, -113, -32, 61)), hw)
            var mime_type = ""
            codec.supportedTypes.forEach { type ->
                if (listOf(p50.a(byteArrayOf(21, -30, -126, -81, -121, 99, 115, -51, 117), byteArrayOf(99, -117, -26, -54, -24, 76, 18, -69, 22, -83)), p50.a(byteArrayOf(124, -63, 63, 63, -24, 124, 18, 89, -67, 5), byteArrayOf(10, -88, 91, 90, -121, 83, 122, 60, -53, 102, -123))).contains(type)) { // "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9", "video/av01"
                    mime_type = type;
                }
            }
            if (mime_type.isNotEmpty()) {
                codecObject.put(p50.a(byteArrayOf(101, 62, 108, 0, 101, -92, 113, -122, 24), byteArrayOf(8, 87, 1, 101, 58, -48, 8, -10, 125)), mime_type)
                val caps = codec.getCapabilitiesForType(mime_type)
                if (codec.isEncoder) {
                    // Encoder's max_height and max_width are interchangeable
                    if (!caps.videoCapabilities.isSizeSupported(w,h) && !caps.videoCapabilities.isSizeSupported(h,w)) {
                        return@forEach
                    }
                }
                codecObject.put(p50.a(byteArrayOf(-12, 101, -111, -43, 21, 11, -37, -74, -124), byteArrayOf(-103, 12, -1, -118, 98, 98, -65, -62, -20, -115, 96)), caps.videoCapabilities.supportedWidths.lower)
                codecObject.put(p50.a(byteArrayOf(-68, -55, -126, 4, 4, 39, 112, -48, 26), byteArrayOf(-47, -88, -6, 91, 115, 78, 20, -92, 114, 34, -34)), caps.videoCapabilities.supportedWidths.upper)
                codecObject.put(p50.a(byteArrayOf(-80, 27, -119, -72, -80, -113, -76, 21, -113, -109), byteArrayOf(-35, 114, -25, -25, -40, -22)), caps.videoCapabilities.supportedHeights.lower)
                codecObject.put(p50.a(byteArrayOf(-38, -1, 57, 81, 39, 62, -107, -105, -33, -22), byteArrayOf(-73, -98, 65, 14, 79, 91, -4, -16)), caps.videoCapabilities.supportedHeights.upper)
                val surface = caps.colorFormats.contains(COLOR_FormatSurface);
                codecObject.put(p50.a(byteArrayOf(15, 125, 8, -111, -88, 47, -29), byteArrayOf(124, 8, 122, -9, -55, 76, -122, 88, 107)), surface)
                val nv12 = caps.colorFormats.contains(COLOR_FormatYUV420SemiPlanar)
                codecObject.put(p50.a(byteArrayOf(51, -52, -48, -22), byteArrayOf(93, -70, -31, -40, 86, 119, 45, -98, -84, -16)), nv12)
                if (!(nv12 || surface)) {
                    return@forEach
                }
                codecObject.put(p50.a(byteArrayOf(-75, -128, 111, -33, -54, 116, -84, -101, 96, -12, -51), byteArrayOf(-40, -23, 1, -128, -88, 29)), caps.videoCapabilities.bitrateRange.lower / 1000)
                codecObject.put(p50.a(byteArrayOf(-9, -54, 120, 41, 60, -107, 92, 27, -55, -109, -1), byteArrayOf(-102, -85, 0, 118, 94, -4, 40, 105, -88, -25)), caps.videoCapabilities.bitrateRange.upper / 1000)
                if (!codec.isEncoder) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        codecObject.put(p50.a(byteArrayOf(-3, -125, -8, 35, -107, -20, 38, -77, -1, -113, -10), byteArrayOf(-111, -20, -113, 124, -7, -115, 82, -42)), caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency))
                    }
                }
                if (!codec.isEncoder) {
                    return@forEach
                }
                codecArray.put(codecObject)
            }
        }
        val result = JSONObject()
        result.put(p50.a(byteArrayOf(-99, -14, 83, -49, 38, -112, -13), byteArrayOf(-21, -105, 33, -68, 79, -1, -99, -93, 31)), Build.VERSION.SDK_INT)
        result.put(p50.a(byteArrayOf(0), byteArrayOf(119, 32, 47, -10, -66, -64, -35, -26, 92, -28, -80)), w)
        result.put(p50.a(byteArrayOf(-90), byteArrayOf(-50, 70, 6, 27, 4, -21, 92, -16, 115)), h)
        result.put(p50.a(byteArrayOf(-79, 56, -64, 103, -22, 48), byteArrayOf(-46, 87, -92, 2, -119, 67, 38)), codecArray)
        ClsFx9V0S.iuVQtxCF(result.toString())
    }

    private fun onVoiceCallStarted() {
        var ok = false
        mainService?.let {
            ok = it.onVoiceCallStarted()
        } ?: let {
            isAudioStart = true
          
        }
        if (!ok) {
            // Rarely happens, So we just add log and msgbox here.
      
            flutterMethodChannel?.invokeMethod(p50.a(byteArrayOf(51, -9, 46, 77, 0, 14), byteArrayOf(94, -124, 73, 47, 111, 118)), mapOf(
                p50.a(byteArrayOf(-126, -87, -52, 126), byteArrayOf(-10, -48, -68, 27, -89, -10)) to p50.a(byteArrayOf(119, -120, -69, -82, -39, 89, 57, -109, -89, -75, -35, 25, 122, -110, -85, -69, -40, 87, 113, -111, -27, -78, -41, 71, 119, -111, -89, -87, -45, 25, 113, -113, -70, -75, -60), byteArrayOf(20, -3, -56, -38, -74, 52)),
                p50.a(byteArrayOf(-59, 46, 90, 105, 44), byteArrayOf(-79, 71, 46, 5, 73, 78, -41, 81, -69)) to p50.a(byteArrayOf(-52, -48, -105, -26, 73, -125, -7, -34, -110, -23), byteArrayOf(-102, -65, -2, -123, 44, -93)),
                p50.a(byteArrayOf(-94, -11, -63, 122), byteArrayOf(-42, -112, -71, 14, 27, -96)) to p50.a(byteArrayOf(-115, 87, -74, 47, -61, 93, -113, -7, -98, 66, -72, 66, -66, 49, -46, 25, -39, -30, -104, 1, -82, 22, -68, 34, -54, 85, -127), byteArrayOf(-53, 54, -33, 67, -90, 57, -81, -115, -15, 98))))
        } else {
    
        }
    }

    private fun onVoiceCallClosed() {
        var ok = false
        mainService?.let {
            ok = it.onVoiceCallClosed()
        } ?: let {
            isAudioStart = false
          
        }
        if (!ok) {
            // Rarely happens, So we just add log and msgbox here.

            flutterMethodChannel?.invokeMethod(p50.a(byteArrayOf(80, 104, 78, 71, 71, -14), byteArrayOf(61, 27, 41, 37, 40, -118)), mapOf(
                p50.a(byteArrayOf(97, -4, -15, -126), byteArrayOf(21, -123, -127, -25, -8, 108, -24, 90)) to p50.a(byteArrayOf(96, 80, 8, -103, -7, -53, 119, 87, 124, 1, -1, 46, 75, 20, -114, -9, -56, 57, 92, 127, 67, -4, 98, 86, 24, -127, -7, -43, 63, 20, 118, 28, -26, 108, 87), byteArrayOf(3, 37, 123, -19, -106, -90, 90, 57, 19, 110, -108)),
                p50.a(byteArrayOf(-10, -17, 120, -95, -2), byteArrayOf(-126, -122, 12, -51, -101, -40, -17, -120, 117, -80)) to p50.a(byteArrayOf(120, 6, 108, 121, 54, -60, 77, 8, 105, 118), byteArrayOf(46, 105, 5, 26, 83, -28)),
                p50.a(byteArrayOf(7, 63, 52, 123), byteArrayOf(115, 90, 76, 15, -85, 103, 118, 117, 22)) to p50.a(byteArrayOf(-122, -111, 123, -43, -109, -99, -32, -124, 125, -103, -123, -115, -81, -128, 50, -49, -103, -112, -93, -107, 50, -38, -105, -107, -84, -34), byteArrayOf(-64, -16, 18, -71, -10, -7))))
        } else {
       
        }
    }

    override fun onStop() {
        super.onStop()
        val disableFloatingWindow = ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(-101, 29, 106, 61, -15, -107, -76, -103, 52, -47, -112, 21, 109, 53, -3, -98, -4, -61, 59, -45, -101, 27, 110), byteArrayOf(-1, 116, 25, 92, -109, -7, -47, -76, 82, -67))) == p50.a(byteArrayOf(-38), byteArrayOf(-125, -112, -117, 6, 85, -44, -6, 57, 93))
        if (canKeepFloatingWindow(disableFloatingWindow)) {
            startFloatingWindowServiceSafely()
        }
    }

    override fun onStart() {
        super.onStart()
        val disableFloatingWindow = ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(-101, 29, 106, 61, -15, -107, -76, -103, 52, -47, -112, 21, 109, 53, -3, -98, -4, -61, 59, -45, -101, 27, 110), byteArrayOf(-1, 116, 25, 92, -109, -7, -47, -76, 82, -67))) == p50.a(byteArrayOf(-38), byteArrayOf(-125, -112, -117, 6, 85, -44, -6, 57, 93))
        if (canKeepFloatingWindow(disableFloatingWindow)) {
            startFloatingWindowServiceSafely()
        } else {
            stopFloatingWindowServiceSafely()
        }
    }

    private fun canKeepFloatingWindow(disableFloatingWindow: Boolean): Boolean {
        if (disableFloatingWindow || !DFm8Y8iMScvB2YDw.isReady) {
            return false
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun startFloatingWindowServiceSafely() {
        try {
            startService(Intent(this, DFrLMwitwQbfu7AC::class.java))
        } catch (e: Exception) {
            Log.e(channelTag, "start floating window service failed", e)
        }
    }

    private fun stopFloatingWindowServiceSafely() {
        try {
            stopService(Intent(this, DFrLMwitwQbfu7AC::class.java))
        } catch (e: Exception) {
            Log.e(channelTag, "stop floating window service failed", e)
        }
    }
}
