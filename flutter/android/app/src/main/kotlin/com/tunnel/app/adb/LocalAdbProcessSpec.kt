package com.tunnel.app.adb

import android.content.Context
import com.tunnel.app.adb.probe.LocalAdbTarget
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import java.io.Closeable
import java.io.File

/** Process configuration only. Callers own fixed verbs; remote callers never supply args/serial. */
internal class LocalAdbProcessSpec(context: Context) {
    private val app = context.applicationContext
    val adbPath: String = File(app.applicationInfo.nativeLibraryDir, "libadb.so").absolutePath
    val workingDirectory: File = app.filesDir
    // A TCP 5037 daemon can belong to LADB/Shizuku/another app and use that app's
    // keys. Keep Tunnel's daemon and its HOME in the same private app sandbox.
    val serverSocketPath: String = File(app.filesDir, "adb-server.sock").absolutePath
    val environment: Map<String, String> = mapOf(
        "HOME" to app.filesDir.absolutePath, "TMPDIR" to app.cacheDir.absolutePath,
        "PATH" to "/system/bin:/system/xbin", "ANDROID_ROOT" to "/system", "ANDROID_DATA" to "/data",
        "LD_LIBRARY_PATH" to app.applicationInfo.nativeLibraryDir,
        // Do not let the adb daemon auto-connect to arbitrary mDNS peers on the LAN.
        "ADB_MDNS_AUTO_CONNECT" to "0",
    )
    // AOSP classifies localfilesystem as local, so -L supports automatic daemon
    // startup as well as every subsequent client command. Never kill a shared server.
    fun baseCommand(): List<String> = listOf(adbPath, "-L", "localfilesystem:$serverSocketPath")
    fun command(target: LocalAdbTarget, args: List<String>): List<String> {
        require(LocalAdbTargetPolicy.validate(target.serial) != null) { "TARGET_NOT_LOCAL" }
        return baseCommand() + listOf("-s", target.serial) + args
    }
    fun processBuilder(target: LocalAdbTarget, args: List<String>): ProcessBuilder =
        ProcessBuilder(command(target, args)).directory(workingDirectory).redirectErrorStream(false).apply {
            environment().clear()
            environment().putAll(this@LocalAdbProcessSpec.environment)
        }
}

/** A single UiAutomation helper, plus independent bounded ADB client sessions. */
internal object LocalAdbAccess {
    private val lock = Any()
    private val owners = mutableSetOf<Lease>()
    fun acquire(mirror: Boolean): Closeable? = synchronized(lock) {
        if ((mirror && owners.any { it.mirror }) || (!mirror && owners.count { !it.mirror } >= 4)) null
        else Lease(mirror).also { owners.add(it) }
    }
    fun isMirrorActive(): Boolean = synchronized(lock) { owners.any { it.mirror } }
    private class Lease(val mirror: Boolean) : Closeable {
        override fun close() { synchronized(lock) { owners.remove(this) } }
    }
}
