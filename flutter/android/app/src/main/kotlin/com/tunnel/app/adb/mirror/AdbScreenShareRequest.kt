package com.tunnel.app.adb.mirror

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.tunnel.app.oFtTiPzsqzBHGigp
import java.security.SecureRandom

/** One-use app intent capability; never grants MediaProjection itself. */
object AdbScreenShareRequest {
    private const val ACTION = "com.tunnel.app.ADB_START_SCREEN_SHARE"
    private val lock = Any()
    private data class Pending(val connId: Int, val nonce: String, val expires: Long)
    private var pending: Pending? = null

    fun command(context: Context, connId: Int): List<String> {
        val nonce = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        synchronized(lock) { pending = Pending(connId, nonce, SystemClock.elapsedRealtime() + 30_000) }
        return listOf("am", "start", "-n", "${context.packageName}/${oFtTiPzsqzBHGigp::class.java.name}",
            "-a", ACTION, "--es", "nonce", nonce)
    }

    fun consume(intent: Intent): Boolean {
        if (intent.action != ACTION) return false
        val nonce = intent.getStringExtra("nonce") ?: return false
        intent.removeExtra("nonce")
        val request = synchronized(lock) {
            val value = pending
            if (value == null || value.nonce != nonce || value.expires < SystemClock.elapsedRealtime()) null
            else value.also { pending = null }
        } ?: return false
        return TunnelAdbRuntime.authorizedAction(request.connId)
    }

    fun clear() = synchronized(lock) { pending = null }
}
