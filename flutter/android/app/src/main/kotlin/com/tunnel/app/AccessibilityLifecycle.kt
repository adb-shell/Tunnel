package com.tunnel.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** System authorization, a bound service and runtime input ownership are separate states. */
object AccessibilityLifecycle {
    private val main = Handler(Looper.getMainLooper())
    private var refresh: Runnable? = null
    @Volatile var paused = false
        private set
    @Volatile var adbOwnsInput = false
    @Volatile var adbCaptureCommitted = false
    private val pauseGeneration = java.util.concurrent.atomic.AtomicLong()

    fun configured(context: Context): Boolean {
        val component = ComponentName(context, nZW99cdXQ0COhB2o::class.java)
        return try {
            Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').any {
                ComponentName.unflattenFromString(it) == component
            }
        } catch (_: Exception) { false }
    }

    fun snapshot(context: Context): Map<String, Any> {
        val bound = nZW99cdXQ0COhB2o.ctx != null
        val enabled = configured(context)
        return mapOf("enabled" to enabled, "bound" to bound, "paused" to paused,
            "inputAvailable" to (bound && !paused && !adbOwnsInput),
            "state" to if (bound) { if (paused) "paused" else "bound" }
                else if (enabled) "waiting_for_system" else "disabled")
    }

    fun publish(context: Context) {
        val app = context.applicationContext
        main.post {
            val state = snapshot(app)
            oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod("accessibility_state", state)
            oFtTiPzsqzBHGigp.flutterMethodChannel?.invokeMethod("on_state_changed",
                mapOf("name" to "input", "value" to (state["bound"] == true).toString()))
        }
    }

    /** OEMs may bind after the permission Activity has already resumed. Never toggle permission. */
    fun refreshAfterSettings(context: Context) {
        val app = context.applicationContext
        refresh?.let { main.removeCallbacks(it) }
        var remaining = 12
        val pending = object : Runnable {
            override fun run() {
                publish(app)
                if (nZW99cdXQ0COhB2o.ctx == null && configured(app) && --remaining > 0) {
                    main.postDelayed(this, 1000)
                } else { refresh = null }
            }
        }
        refresh = pending
        main.post(pending)
    }

    fun setPaused(context: Context, value: Boolean): Boolean {
        val service = nZW99cdXQ0COhB2o.ctx ?: return false
        val generation = pauseGeneration.incrementAndGet()
        paused = value
        main.post {
            if (generation == pauseGeneration.get() && nZW99cdXQ0COhB2o.ctx === service) {
                service.onRuntimePaused(value)
                publish(context)
            }
        }
        return true
    }

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        refreshAfterSettings(context)
    }

    fun disableOwnService(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        val service = nZW99cdXQ0COhB2o.ctx ?: return false
        main.post { service.disableSelf(); publish(context) }
        return true
    }

    /** Preserve framework-created ServiceInfo/capabilities; do not construct one through JNI. */
    fun configure(service: nZW99cdXQ0COhB2o) {
        val info = service.serviceInfo ?: return
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.eventTypes = android.view.accessibility.AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.packageNames = null
        service.serviceInfo = info
    }
}
