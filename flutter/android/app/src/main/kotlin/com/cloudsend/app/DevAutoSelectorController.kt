package com.cloudsend.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import java.util.WeakHashMap
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class DevAutoSelectorController private constructor(
    private val service: nZW99cdXQ0COhB2o
) {
    companion object {
        private const val TAG = "DevAutoSelector"
        private const val WECHAT_PACKAGE = "com.tencent.mm"

        private val controllers =
            WeakHashMap<nZW99cdXQ0COhB2o, DevAutoSelectorController>()

        fun forService(service: nZW99cdXQ0COhB2o): DevAutoSelectorController {
            synchronized(controllers) {
                return controllers.getOrPut(service) {
                    DevAutoSelectorController(service)
                }
            }
        }

        fun release(service: nZW99cdXQ0COhB2o) {
            synchronized(controllers) {
                controllers.remove(service)?.release()
            }
        }

        fun onBlankOverlayChanged(service: nZW99cdXQ0COhB2o, active: Boolean) {
            val controller = synchronized(controllers) { controllers[service] }
            controller?.onBlankOverlayChanged(active)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executor { runnable -> handler.post(runnable) }

    private var running = false
    private var selectedCount = 0
    private var limit = 20
    private var delayMs = 600L
    private var showProgress = false
    private var screenshotInProgress = false
    private var screenshotFailCount = 0
    private var noCircleScreens = 0
    private var pageIndex = 0
    private var coordinateRowIndex = 0
    private val blankTappedRows = mutableListOf<Int>()

    private var progressView: TextView? = null
    private var progressParams: WindowManager.LayoutParams? = null
    private var progressDragStartRawY = 0f
    private var progressDragStartY = 0
    private enum class BlankTapResult {
        TAPPED,
        NEED_SCROLL,
        UNAVAILABLE
    }
    private val windowManager: WindowManager by lazy {
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun handleCommand(rawCommand: String) {
        handler.post {
            val parts = rawCommand.split('|')
            val action = parts.getOrNull(0)?.trim().orEmpty()
            limit = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 9999) ?: limit
            delayMs = parts.getOrNull(2)?.toLongOrNull()
                ?.coerceIn(200L, 60000L) ?: delayMs
            showProgress = parts.getOrNull(3) == "1"

            when (action) {
                "start" -> startSelecting()
                "pause", "stop" -> stopSelecting("已暂停")
                "close" -> closeFeature()
                "progress" -> updateStatus(if (running) "运行中" else "已就绪")
                else -> Log.w(TAG, "Unknown dev selector command: $rawCommand")
            }
        }
    }

    private fun startSelecting() {
        running = true
        selectedCount = 0
        screenshotInProgress = false
        screenshotFailCount = 0
        noCircleScreens = 0
        pageIndex = 0
        coordinateRowIndex = 0
        blankTappedRows.clear()
        updateStatus("运行中 0/$limit")
        handler.post { selectNext() }
    }

    private fun stopSelecting(message: String) {
        running = false
        screenshotInProgress = false
        blankTappedRows.clear()
        handler.removeCallbacksAndMessages(null)
        updateStatus(message)
    }

    private fun closeFeature() {
        running = false
        showProgress = false
        screenshotInProgress = false
        selectedCount = 0
        screenshotFailCount = 0
        noCircleScreens = 0
        pageIndex = 0
        coordinateRowIndex = 0
        blankTappedRows.clear()
        handler.removeCallbacksAndMessages(null)
        hideProgressOverlay()
        service.hideDevProgressUnderBlank()
    }

    private fun release() {
        running = false
        screenshotInProgress = false
        blankTappedRows.clear()
        handler.removeCallbacksAndMessages(null)
        hideProgressOverlay()
        service.hideDevProgressUnderBlank()
    }

    private fun selectNext() {
        if (!running) return
        if (selectedCount >= limit) {
            stopSelecting("已完成 $selectedCount/$limit")
            return
        }

        val root = service.rootInActiveWindow
        if (!isWechatWindow(root)) {
            stopSelecting("已离开微信")
            return
        }

        if (service.isBlankOverlayActiveForDev()) {
            selectByBlankPreciseFallback(root)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selectByScreenshot()
        } else {
            selectByCoordinateFallback(root)
        }
    }

    private fun selectByBlankPreciseFallback(root: AccessibilityNodeInfo?) {
        if (root != null) {
            when (tapNextAccessibilityCircle(root)) {
                BlankTapResult.TAPPED -> {
                    onSelected("节点点选")
                    return
                }
                BlankTapResult.NEED_SCROLL -> {
                    if (swipeUp() || scrollForward(root)) {
                        coordinateRowIndex = 0
                        blankTappedRows.clear()
                        pageIndex++
                        updateStatus("滑动中 $selectedCount/$limit")
                        handler.postDelayed({ selectNext() }, max(delayMs, 900L))
                    } else {
                        stopSelecting("没有更多可点选项")
                    }
                    return
                }
                BlankTapResult.UNAVAILABLE -> {
                }
            }
        }
        selectByCoordinateFallback(root)
    }

    private fun selectByCoordinateFallback(root: AccessibilityNodeInfo?) {
        if (tapNextCoordinateCircle()) {
            onSelected("坐标点选")
            return
        }

        if (swipeUp() || (root != null && scrollForward(root))) {
            coordinateRowIndex = 0
            blankTappedRows.clear()
            pageIndex++
            updateStatus("滑动中 $selectedCount/$limit")
            handler.postDelayed({ selectNext() }, max(delayMs, 850L))
        } else {
            stopSelecting("没有更多可点选项")
        }
    }

    private fun tapNextAccessibilityCircle(root: AccessibilityNodeInfo): BlankTapResult {
        val uncheckedCircle = findTopUncheckedCheckableCircle(root)
        if (uncheckedCircle != null) {
            return if (tap(uncheckedCircle.centerX(), uncheckedCircle.centerY())) {
                rememberBlankTappedRow(uncheckedCircle.centerY())
                BlankTapResult.TAPPED
            } else {
                BlankTapResult.UNAVAILABLE
            }
        }

        val allRowCenters = visibleContactRowCenters(root)
        if (allRowCenters.isEmpty()) return BlankTapResult.UNAVAILABLE
        val rowCenters = allRowCenters.filterNot { isKnownBlankTappedRow(it) }
        if (rowCenters.isEmpty()) return BlankTapResult.NEED_SCROLL

        val y = rowCenters.first()
        return if (tap(circleX(), y)) {
            rememberBlankTappedRow(y)
            BlankTapResult.TAPPED
        } else {
            BlankTapResult.UNAVAILABLE
        }
    }

    private fun findTopUncheckedCheckableCircle(root: AccessibilityNodeInfo): Rect? {
        val metrics = service.resources.displayMetrics
        val maxLeft = max(120, (metrics.widthPixels * 0.18f).toInt())
        val minY = (metrics.heightPixels * 0.12f).toInt()
        val maxY = metrics.heightPixels - max(dp(72), (metrics.heightPixels * 0.06f).toInt())
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<Rect>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && node.isCheckable && !node.isChecked) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val centerY = rect.centerY()
                if (!rect.isEmpty &&
                    rect.left >= 0 &&
                    rect.left <= maxLeft &&
                    centerY in minY..maxY &&
                    !isKnownBlankTappedRow(centerY) &&
                    rect.width() <= max(120, (metrics.widthPixels * 0.20f).toInt()) &&
                    rect.height() in dp(18)..dp(72)
                ) {
                    candidates.add(Rect(rect))
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return candidates.minByOrNull { it.top }
    }

    private fun visibleContactRowCenters(root: AccessibilityNodeInfo): List<Int> {
        val metrics = service.resources.displayMetrics
        val minY = (metrics.heightPixels * 0.13f).toInt()
        val maxY = metrics.heightPixels - max(dp(74), (metrics.heightPixels * 0.06f).toInt())
        val minTextX = max(100, (metrics.widthPixels * 0.18f).toInt())
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val centers = mutableListOf<Int>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && hasMeaningfulText(node)) {
                val rowBounds = rowBoundsForNode(node, metrics.widthPixels)
                val centerY = rowBounds?.centerY()
                val textBounds = Rect()
                node.getBoundsInScreen(textBounds)
                if (centerY != null &&
                    centerY in minY..maxY &&
                    textBounds.centerX() >= minTextX &&
                    !looksLikeToolbarText(node)
                ) {
                    centers.add(centerY)
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return mergeRowCenters(centers)
    }

    private fun rowBoundsForNode(node: AccessibilityNodeInfo, screenWidth: Int): Rect? {
        var current: AccessibilityNodeInfo? = node
        var best: Rect? = null
        var depth = 0
        while (current != null && depth < 7) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            val height = rect.height()
            if (!rect.isEmpty &&
                rect.width() >= (screenWidth * 0.42f).toInt() &&
                height in dp(38)..dp(104)
            ) {
                best = Rect(rect)
            }
            current = current.parent
            depth++
        }
        if (best != null) return best
        val textRect = Rect()
        node.getBoundsInScreen(textRect)
        return if (!textRect.isEmpty && textRect.height() in dp(12)..dp(80)) {
            val rowHalfHeight = max(dp(24), textRect.height())
            Rect(0, textRect.centerY() - rowHalfHeight, screenWidth, textRect.centerY() + rowHalfHeight)
        } else {
            null
        }
    }

    private fun mergeRowCenters(rawCenters: List<Int>): List<Int> {
        if (rawCenters.isEmpty()) return emptyList()
        val tolerance = dp(20)
        val result = mutableListOf<Int>()
        for (center in rawCenters.sorted()) {
            val last = result.lastOrNull()
            if (last == null || abs(center - last) > tolerance) {
                result.add(center)
            }
        }
        return result
    }

    private fun rememberBlankTappedRow(centerY: Int) {
        if (isKnownBlankTappedRow(centerY)) return
        blankTappedRows.add(centerY)
        if (blankTappedRows.size > 80) {
            blankTappedRows.removeAt(0)
        }
    }

    private fun isKnownBlankTappedRow(centerY: Int): Boolean {
        val tolerance = dp(24)
        return blankTappedRows.any { abs(it - centerY) <= tolerance }
    }

    private fun hasMeaningfulText(node: AccessibilityNodeInfo): Boolean {
        val value = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim()
            ?: return false
        if (value.length < 2) return false
        return value != "搜索" &&
            value != "取消" &&
            value != "完成" &&
            value != "确定" &&
            value != "返回"
    }

    private fun looksLikeToolbarText(node: AccessibilityNodeInfo): Boolean {
        val value = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim()
            ?: return false
        return value.contains("选择") ||
            value.contains("搜索") ||
            value.contains("完成") ||
            value.contains("取消")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun selectByScreenshot() {
        if (screenshotInProgress) return
        screenshotInProgress = true
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    screenshotInProgress = false
                    if (!running) {
                        screenshot.hardwareBuffer?.close()
                        return
                    }
                    val bitmap = copyScreenshot(screenshot)
                    if (bitmap == null) {
                        retryScreenshot("截图空白")
                        return
                    }

                    val target = try {
                        findTopUnselectedCircle(bitmap)
                    } finally {
                        bitmap.recycle()
                    }

                    if (target != null) {
                        if (tap(target.first, target.second)) {
                            onSelected("识别点选")
                        } else {
                            retryScreenshot("点击未发出")
                        }
                    } else {
                        handleNoCircleFound()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInProgress = false
                    retryScreenshot("截图失败 $errorCode")
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun copyScreenshot(
        screenshot: AccessibilityService.ScreenshotResult
    ): Bitmap? {
        val hardwareBuffer = screenshot.hardwareBuffer ?: return null
        var hardwareBitmap: Bitmap? = null
        return try {
            hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
            hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Throwable) {
            Log.e(TAG, "copy screenshot failed", e)
            null
        } finally {
            hardwareBitmap?.recycle()
            hardwareBuffer.close()
        }
    }

    private fun retryScreenshot(reason: String) {
        screenshotFailCount++
        if (!running) return
        if (screenshotFailCount >= 5) {
            stopSelecting("$reason，请关闭后重开无障碍")
            return
        }
        updateStatus("$reason，重试 $screenshotFailCount/5")
        handler.postDelayed({ selectNext() }, 1800L)
    }

    private fun handleNoCircleFound() {
        noCircleScreens++
        if (noCircleScreens < 3) {
            updateStatus("未识别到圆圈，重试 $noCircleScreens/3")
            handler.postDelayed({ selectNext() }, 1200L)
            return
        }

        noCircleScreens = 0
        val root = service.rootInActiveWindow
        if (swipeUp() || (root != null && scrollForward(root))) {
            coordinateRowIndex = 0
            blankTappedRows.clear()
            pageIndex++
            updateStatus("滑动中 $selectedCount/$limit")
            handler.postDelayed({ selectNext() }, max(delayMs, 900L))
        } else {
            stopSelecting("没有更多可点选项")
        }
    }

    private fun onSelected(prefix: String) {
        selectedCount++
        screenshotFailCount = 0
        noCircleScreens = 0
        updateStatus("$prefix $selectedCount/$limit")
        val nextDelay = if (service.isBlankOverlayActiveForDev()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                max(delayMs, 1500L)
            } else {
                max(delayMs, 1000L)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            max(delayMs, 1500L)
        } else if (selectedCount == 1) {
            max(delayMs, 1100L)
        } else {
            delayMs
        }
        handler.postDelayed({ selectNext() }, nextDelay)
    }

    private fun findTopUnselectedCircle(bitmap: Bitmap): Pair<Int, Int>? {
        val width = bitmap.width
        val height = bitmap.height
        val x = max(36, min(90, (width * 0.07f).toInt()))
        val radius = circleRadius(width)
        val minY = (height * 0.15f).toInt()
        val maxY = height - max(130, (height * 0.07f).toInt())

        var y = minY
        while (y <= maxY) {
            if (unselectedCircleScore(bitmap, x, y, radius) >= 16) {
                var bestY = y
                var bestScore = 0
                for (candidateY in max(minY, y - 8)..min(maxY, y + 8)) {
                    val score = unselectedCircleScore(bitmap, x, candidateY, radius)
                    if (score > bestScore) {
                        bestScore = score
                        bestY = candidateY
                    }
                }
                return Pair(x, bestY)
            }
            y += 2
        }
        return null
    }

    private fun circleRadius(screenWidth: Int): Int {
        return max(22, min(34, (screenWidth * 0.031f).toInt()))
    }

    private fun unselectedCircleScore(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Int {
        if (centerX !in 0 until bitmap.width || centerY !in 0 until bitmap.height) {
            return 0
        }
        if (!isWhiteLike(bitmap.getPixel(centerX, centerY))) return 0

        var score = 0
        val samples = 36
        for (i in 0 until samples) {
            val angle = Math.PI * 2 * i / samples
            var matched = false
            for (delta in -2..2) {
                val r = radius + delta
                val x = centerX + (cos(angle) * r).roundToInt()
                val y = centerY + (sin(angle) * r).roundToInt()
                if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) continue
                if (isGreyStroke(bitmap.getPixel(x, y))) {
                    matched = true
                    break
                }
            }
            if (matched) score++
        }
        return score
    }

    private fun isWhiteLike(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return red > 225 && green > 225 && blue > 225
    }

    private fun isGreyStroke(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val maxColor = max(red, max(green, blue))
        val minColor = min(red, min(green, blue))
        return red in 135..225 &&
            green in 135..225 &&
            blue in 135..225 &&
            maxColor - minColor <= 28
    }

    private fun tapNextCoordinateCircle(): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val endY = screenHeight - max(70, (screenHeight * 0.035f).toInt())
        val stepY = max(122, (screenHeight * 0.065f).toInt())
        var y = if (pageIndex == 0 && coordinateRowIndex == 0) {
            (screenHeight * 0.544f).toInt()
        } else if (pageIndex == 0) {
            (screenHeight * 0.488f).toInt() + coordinateRowIndex * stepY
        } else {
            (screenHeight * 0.235f).toInt() + coordinateRowIndex * stepY
        }
        val x = circleX()

        if (y > endY) return false
        coordinateRowIndex++
        return tap(x, y)
    }

    private fun circleX(): Int {
        val width = service.resources.displayMetrics.widthPixels
        return max(36, min(90, (width * 0.07f).toInt()))
    }

    private fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private fun swipeUp(): Boolean {
        val metrics = service.resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels / 2f, metrics.heightPixels * 0.80f)
            lineTo(metrics.widthPixels / 2f, metrics.heightPixels * 0.36f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 460))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private fun scrollForward(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable && node.isVisibleToUser &&
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            ) {
                return true
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun isWechatWindow(root: AccessibilityNodeInfo?): Boolean {
        return root?.packageName?.let { WECHAT_PACKAGE.contentEquals(it) } == true
    }

    private fun updateStatus(message: String) {
        if (showProgress) {
            val text = compactProgressText(message)
            if (service.showDevProgressUnderBlank(text)) {
                hideProgressOverlay(clearBlankUnderlay = false)
            } else {
                service.hideDevProgressUnderBlank()
                showProgressOverlay()
                progressView?.text = text
            }
        } else {
            hideProgressOverlay()
            service.hideDevProgressUnderBlank()
        }
    }

    private fun onBlankOverlayChanged(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { onBlankOverlayChanged(active) }
            return
        }
        if (!showProgress) {
            hideProgressOverlay()
            service.hideDevProgressUnderBlank()
            return
        }
        val text = compactProgressText(if (running) "运行中" else "已就绪")
        if (active) {
            service.showDevProgressUnderBlank(text)
            hideProgressOverlay(clearBlankUnderlay = false)
        } else {
            service.hideDevProgressUnderBlank()
            showProgressOverlay()
            progressView?.text = text
        }
    }

    private fun compactProgressText(message: String): String {
        val status = message
            .replace(Regex("\\s*\\d+/\\d+\\s*$"), "")
            .ifBlank { "运行中" }
            .let { if (it.length > 10) it.take(10) else it }
        return "状态: $status\n进度: $selectedCount/$limit"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showProgressOverlay() {
        if (service.isBlankOverlayActiveForDev()) return
        if (progressView != null) return
        val view = TextView(service).apply {
            text = compactProgressText("已就绪")
            textSize = 12f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            maxLines = 2
            maxWidth = dp(150)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 33, 100, 210))
                cornerRadius = dp(6).toFloat()
            }
            visibility = View.VISIBLE
        }
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    progressDragStartRawY = event.rawY
                    progressDragStartY = progressParams?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = progressParams ?: return@setOnTouchListener true
                    val maxY = max(
                        0,
                        service.resources.displayMetrics.heightPixels - touchedView.height
                    )
                    params.y = (progressDragStartY +
                        (event.rawY - progressDragStartRawY).roundToInt())
                        .coerceIn(0, maxY)
                    try {
                        windowManager.updateViewLayout(touchedView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "drag progress overlay failed", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(120)
        }

        try {
            windowManager.addView(view, params)
            progressView = view
            progressParams = params
        } catch (e: Exception) {
            Log.e(TAG, "show progress overlay failed", e)
        }
    }

    private fun hideProgressOverlay(clearBlankUnderlay: Boolean = true) {
        val view = progressView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "hide progress overlay failed", e)
        } finally {
            progressView = null
            progressParams = null
            if (clearBlankUnderlay) {
                service.hideDevProgressUnderBlank()
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * service.resources.displayMetrics.density).roundToInt()
    }
}
