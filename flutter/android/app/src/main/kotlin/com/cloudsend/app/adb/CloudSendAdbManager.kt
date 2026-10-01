package com.cloudsend.app.adb

import android.content.Context
import android.os.Build
import com.cloudsend.app.nZW99cdXQ0COhB2o

object CloudSendAdbManager {
    private const val PREFS_NAME = "cloudsend_adb"
    private const val KEY_PAIRED = "paired_before"

    @Volatile
    private var runner: CloudSendAdbRunner? = null

    @Volatile
    private var state = CloudSendAdbState()

    fun initialize(context: Context): CloudSendAdbState {
        val currentRunner = runner ?: synchronized(this) {
            runner ?: CloudSendAdbRunner(context).also { runner = it }
        }

        state = CloudSendAdbState(
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            binaryAvailable = currentRunner.isBinaryAvailable(),
            binaryExecutable = currentRunner.isBinaryExecutable(),
            initialized = true,
            paired = isPairedBefore(context),
            output = currentRunner.snapshotOutput(),
            adbPath = currentRunner.adbPath,
            environment = currentRunner.processEnvironment(),
        )
        return state
    }

    fun start(context: Context): CloudSendAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.startServer()
        val next = updateFromRunner(currentRunner)
        if (next.shellReady || next.connected) {
            setPairedBefore(context, true)
        }
        return next
    }

    fun stop(context: Context): CloudSendAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.stopServer()
        return updateFromRunner(currentRunner)
    }

    fun pair(context: Context, port: String, code: String): CloudSendAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.pair(port, code)
        val next = updateFromRunner(currentRunner, preservePaired = false)
        setPairedBefore(context, next.paired)
        return next
    }

    fun startLocalShell(context: Context): CloudSendAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.startLocalShell()
        return updateFromRunner(currentRunner)
    }

    fun sendCommand(context: Context, command: String): CloudSendAdbState {
        val currentRunner = currentRunner(context)
        currentRunner.sendCommand(command)
        return updateFromRunner(currentRunner)
    }

    fun wirelessDebugStatus(context: Context): Map<String, Any> {
        return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(context.applicationContext)
    }

    fun setWirelessDebugging(context: Context, enable: Boolean): Map<String, Any> {
        if (!nZW99cdXQ0COhB2o.isOpen) {
            return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(
                context.applicationContext,
                "\u8bf7\u6253\u5f00\u9996\u9875\u7f51\u7edc\u52a0\u5bc6\u6743\u9650\u540e\u91cd\u8bd5"
            )
        }
        nZW99cdXQ0COhB2o.requestWirelessDebugAutomation(enable)
        return nZW99cdXQ0COhB2o.wirelessDebugAutomationStatus(context.applicationContext)
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

    fun snapshot(): CloudSendAdbState {
        val currentRunner = runner
        return if (currentRunner == null) {
            state
        } else {
            updateFromRunner(currentRunner)
        }
    }

    private fun currentRunner(context: Context): CloudSendAdbRunner {
        return runner ?: synchronized(this) {
            runner ?: CloudSendAdbRunner(context).also { runner = it }
        }
    }

    private fun updateFromRunner(
        currentRunner: CloudSendAdbRunner,
        preservePaired: Boolean = true,
    ): CloudSendAdbState {
        val runnerState = currentRunner.state()
        state = runnerState.copy(
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            paired = runnerState.paired || (preservePaired && state.paired),
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
