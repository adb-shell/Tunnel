package com.tunnel.app.adb

import android.content.Context
import android.os.Build
import java.io.Closeable
import com.tunnel.app.nZW99cdXQ0COhB2o

object TunnelAdbManager {
    private const val PREFS_NAME = "tunnel_adb"
    private const val KEY_PAIRED = "paired_before"

    @Volatile
    private var runner: TunnelAdbRunner? = null
    @Volatile private var appContext: Context? = null

    @Volatile
    private var state = TunnelAdbState()

    fun initialize(context: Context): TunnelAdbState {
        appContext = context.applicationContext
        val currentRunner = runner ?: synchronized(this) {
            runner ?: TunnelAdbRunner(context).also { runner = it }
        }

        state = currentRunner.state().copy(pairedBefore = isPairedBefore(context))
        return state
    }

    fun start(context: Context): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.startServer()
        val next = updateFromRunner(currentRunner)
        if (next.shellReady || next.connected) {
            setPairedBefore(context, true)
        }
        return next
    }

    fun stop(context: Context): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.stopServer()
        return updateFromRunner(currentRunner)
    }

    fun connect(context: Context, endpoint: String): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.connect(endpoint)
        val next = updateFromRunner(currentRunner)
        if (next.shellReady) setPairedBefore(context, true)
        return next
    }

    /** Phone-local selection only. Remote requests must not provide a device selector. */
    fun selectedLocalSerial(): String? = runner?.selectedLocalSerial()
    /** Serialize discovery/reconnect with local and remote pairing, off the UI thread. */
    fun recoverLocalTransport(context: Context): String? {
        val current = currentRunner(context)
        return current.recoverTransport().also { updateFromRunner(current) }
    }
    fun acquireMirrorLease(): Closeable? {
        val lease = LocalAdbAccess.acquire(true) ?: return null
        val ctx = appContext
        val automationRunning = try { ctx != null && wirelessDebugStatus(ctx)["running"] == true }
            catch (_: Exception) { true }
        if (automationRunning) {
            lease.close()
            return null
        }
        return lease
    }
    fun isMirrorActive(): Boolean = LocalAdbAccess.isMirrorActive()

    fun pair(context: Context, port: String, code: String, connectionPort: Int? = null,
             progress: (String) -> Unit = {}): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.pair(port, code, connectionPort, progress)
        val next = updateFromRunner(currentRunner)
        // A failed retry does not erase an existing key's historical pairing hint.
        if (next.paired) setPairedBefore(context, true)
        return next
    }

    fun startLocalShell(context: Context): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.startLocalShell()
        return updateFromRunner(currentRunner)
    }

    fun sendCommand(context: Context, command: String): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.sendCommand(command)
        return updateFromRunner(currentRunner)
    }

    fun wirelessDebugStatus(context: Context): Map<String, Any> {
        return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(context.applicationContext)
    }

    fun setWirelessDebugging(context: Context, enable: Boolean): Map<String, Any> {
        appContext = context.applicationContext
        val lease = LocalAdbAccess.acquire(false)
            ?: return wirelessDebugStatus(context) + mapOf("error" to "ADB_BUSY")
        try {
            if (!nZW99cdXQ0COhB2o.isOpen) {
                return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(
                    context.applicationContext,
                    "\u8bf7\u6253\u5f00\u9996\u9875\u7f51\u7edc\u52a0\u5bc6\u6743\u9650\u540e\u91cd\u8bd5"
                )
            }
            nZW99cdXQ0COhB2o.requestWirelessDebugAutomation(enable)
            return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(context.applicationContext)
        } finally { lease.close() }
    }

    fun cancelWirelessDebugging(context: Context): Map<String, Any> {
        nZW99cdXQ0COhB2o.cancelWirelessDebugAutomation()
        return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(context.applicationContext)
    }

    fun output(context: Context): String {
        val currentRunner = currentRunner(context)
        updateFromRunner(currentRunner)
        return currentRunner.snapshotOutput()
    }

    fun clearOutput(context: Context): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.clearOutput()
        return updateFromRunner(currentRunner)
    }

    fun interruptTerminal(context: Context): TunnelAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.interruptTerminal()
        return updateFromRunner(currentRunner)
    }

    fun discover(context: Context): Map<String, Any> = currentRunner(context).discover()

    fun snapshot(): TunnelAdbState {
        val currentRunner = runner
        return if (currentRunner == null) {
            state
        } else {
            updateFromRunner(currentRunner)
        }
    }

    private fun currentRunner(context: Context): TunnelAdbRunner {
        appContext = context.applicationContext
        return runner ?: synchronized(this) {
            runner ?: TunnelAdbRunner(context).also { runner = it }
        }
    }

    private fun updateFromRunner(
        currentRunner: TunnelAdbRunner,
    ): TunnelAdbState {
        val runnerState = currentRunner.state()
        state = runnerState.copy(
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            pairedBefore = runnerState.paired || state.pairedBefore,
        )
        return state
    }

    private fun isPairedBefore(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAIRED, false)
    }

    private fun setPairedBefore(context: Context, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PAIRED, value)
            .apply()
    }
}
