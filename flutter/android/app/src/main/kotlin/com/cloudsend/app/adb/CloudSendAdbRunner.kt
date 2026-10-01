package com.cloudsend.app.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit

class CloudSendAdbRunner(context: Context) {
    companion object {
        private const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 16
        private const val MAX_SHELL_RESTART_ATTEMPTS = 3
    }

    private val appContext = context.applicationContext
    private val output = StringBuilder()
    private var shellProcess: Process? = null
    private var selectedSerial: String? = null
    private var preferredSerial: String? = null
    private var localShell = false
    private var shellGeneration = 0
    private var shellRestartAttempts = 0
    @Volatile
    private var restartOnShellExit = false

    @Volatile
    private var pairing = false

    @Volatile
    private var paired = false

    @Volatile
    private var connected = false

    @Volatile
    private var shellReady = false

    @Volatile
    private var lastError = ""

    val adbPath: String
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libadb.so").absolutePath

    fun isBinaryAvailable(): Boolean {
        val adb = File(adbPath)
        return adb.exists() && adb.isFile && adb.canRead()
    }

    fun isBinaryExecutable(): Boolean {
        val adb = File(adbPath)
        return adb.exists() && adb.isFile && adb.canExecute()
    }

    fun processEnvironment(): Map<String, String> {
        return mapOf(
            "HOME" to appContext.filesDir.path,
            "TMPDIR" to appContext.cacheDir.path,
        )
    }

    fun startServer() {
        shellRestartAttempts = 0
        startServerInternal()
    }

    fun stopServer() {
        restartOnShellExit = false
        closeShellProcess()
        append("Stopping CloudSend ADB service...")
        try {
            val output = runAdb(listOf("kill-server"), 5)
            if (output.isNotBlank()) {
                append(output.trimEnd())
            }
        } catch (e: Exception) {
            append("ADB stop warning: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            selectedSerial = null
            preferredSerial = null
            localShell = false
            connected = false
            shellReady = false
            pairing = false
            append("CloudSend ADB service stopped.")
        }
    }

    private fun startServerInternal() {
        closeShellProcess()
        localShell = false
        restartOnShellExit = true
        selectedSerial = null
        preferredSerial = null
        connected = false
        shellReady = false
        lastError = ""
        append("Starting CloudSend ADB environment...")
        if (!isBinaryAvailable()) {
            fail("libadb.so is missing or unreadable")
            return
        }
        if (!isBinaryExecutable()) {
            fail("libadb.so is not executable")
            return
        }

        try {
            prepareWirelessDebuggingLikeLadb()
            waitForWirelessDebuggingIfNeeded()
            append("Scanning ADB wireless-debugging connect port...")
            val adbPort = CloudSendAdbDnsDiscover(appContext).discoverConnectPort { append(it) }
            if (adbPort != null) {
                append("Best ADB port discovered: $adbPort")
            } else {
                append("No ADB connect port discovered yet.")
            }

            val startOutput = runAdb(listOf("start-server"), 60)
            if (startOutput.isNotBlank()) {
                append(startOutput.trimEnd())
            }

            if (adbPort != null) {
                connectToDiscoveredPort(adbPort)
            } else {
                append("No connect port discovered, waiting for an already paired ADB device...")
                val waitOutput = runAdb(listOf("wait-for-device"), 60)
                if (waitOutput.isNotBlank()) {
                    append(waitOutput.trimEnd())
                }
            }

            val devicesOutput = runAdb(listOf("devices"), 10)
            if (devicesOutput.isNotBlank()) {
                append(devicesOutput.trimEnd())
            }

            val devices = parseConnectedDevices(devicesOutput)
            selectedSerial = selectDeviceSerial(devices)
            connected = selectedSerial != null
            if (connected) {
                append("Selected ADB device: $selectedSerial")
                openShell()
            } else {
                shellReady = false
                append("ADB server is ready, but no connected local device was found.")
            }
        } catch (e: Exception) {
            shellReady = false
            fail("ADB start failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun startLocalShell() {
        shellRestartAttempts = 0
        startLocalShellInternal()
    }

    private fun startLocalShellInternal() {
        closeShellProcess()
        localShell = true
        restartOnShellExit = true
        selectedSerial = null
        preferredSerial = null
        connected = false
        append("Pairing skipped. Entering non-ADB shell.")
        openShell()
    }

    fun pair(port: String, pairingCode: String) {
        val cleanPort = port.trim()
        val cleanCode = pairingCode.trim()
        if (cleanPort.isEmpty() || cleanCode.isEmpty()) {
            fail("Pairing port and pairing code are required")
            return
        }
        pairing = true
        paired = false
        lastError = ""
        try {
            runAdb(listOf("kill-server"), 3)
            val endpoints = adbEndpoints(cleanPort)
            var success = false
            var lastText = ""
            endpoints.forEach { endpoint ->
                if (success) return@forEach
                append("Trying to pair $endpoint ...")
                val process = adbProcess(listOf("pair", endpoint))
                // Match LADB's delay so the bundled adb process has time to ask for the pairing code.
                Thread.sleep(5_000)
                PrintStream(process.outputStream).use {
                    it.println(cleanCode)
                    it.flush()
                }
                val finished = process.waitFor(15, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor(3, TimeUnit.SECONDS)
                }
                val text = readProcessOutput(process)
                lastText = text
                if (text.isNotBlank()) {
                    append(text.trimEnd())
                }
                if (!finished) {
                    append("Pairing timed out for $endpoint")
                } else if (isPairSuccessful(text, process.exitValue())) {
                    success = true
                } else {
                    append("Pairing failed for $endpoint")
                    runAdb(listOf("kill-server"), 3)
                }
            }
            if (success) {
                paired = true
                lastError = ""
                append("Pairing succeeded. Waiting for wireless-debugging connect port discovery.")
            } else {
                fail(
                    if (lastText.isBlank()) {
                        "Pairing failed"
                    } else {
                        "Pairing failed: ${lastText.lineSequence().lastOrNull()?.trim().orEmpty()}"
                    }
                )
            }
            runAdb(listOf("kill-server"), 3)
        } catch (e: Exception) {
            fail("Pairing failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            pairing = false
        }
    }

    fun sendCommand(command: String) {
        val text = command.trim()
        if (text.isEmpty()) return
        if (!shellReady || shellProcess == null) {
            append("> $text")
            append("ADB shell is not ready yet.")
            return
        }
        append("> $text")
        try {
            PrintStream(shellProcess!!.outputStream).apply {
                println(text)
                flush()
            }
        } catch (e: Exception) {
            fail("Failed to send command: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun snapshotOutput(): String = synchronized(output) { output.toString() }

    fun state(): CloudSendAdbState = CloudSendAdbState(
        binaryAvailable = isBinaryAvailable(),
        binaryExecutable = isBinaryExecutable(),
        initialized = true,
        pairing = pairing,
        paired = paired,
        connected = connected,
        shellReady = shellReady,
        output = snapshotOutput(),
        adbPath = adbPath,
        environment = processEnvironment(),
        lastError = lastError,
    )

    private fun openShell() {
        try {
            val generation = ++shellGeneration
            val serial = selectedSerial
            shellProcess = if (localShell) {
                localShellProcess(listOf("sh", "-l"))
            } else {
                adbProcess(
                    if (serial.isNullOrBlank()) {
                        listOf("shell")
                    } else {
                        listOf("-s", serial, "shell")
                    },
                )
            }
            shellReady = true
            sendRawToShell("alias adb=\"$adbPath\"")
            append(
                if (localShell) {
                    "Entered non-ADB shell."
                } else {
                    "Entered adb shell."
                }
            )
            if (!localShell) {
                sendRawToShell(
                    "pm grant ${appContext.packageName} android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1"
                )
                sendRawToShell("echo 'ADB permission grant requested'")
                sendRawToShell("echo 'ADB shell ready'")
            }
            Thread {
                try {
                    val process = shellProcess ?: return@Thread
                    val reader = process.inputStream.bufferedReader()
                    reader.useLines { lines ->
                        lines.forEach { append(it) }
                    }
                    process.waitFor(3, TimeUnit.SECONDS)
                } catch (_: Exception) {
                } finally {
                    if (shellGeneration == generation) {
                        shellReady = false
                        connected = false
                        append("ADB shell closed.")
                        restartShellAfterDelay(generation)
                    }
                }
            }.start()
        } catch (e: Exception) {
            shellReady = false
            fail("Failed to open adb shell: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun runAdb(args: List<String>, timeoutSeconds: Long): String {
        val process = adbProcess(args)
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(3, TimeUnit.SECONDS)
            append("adb ${args.joinToString(" ")} timed out")
        }
        val text = readProcessOutput(process)
        return text
    }

    private fun connectToDiscoveredPort(port: Int): Boolean {
        adbEndpoints(port.toString()).forEach { endpoint ->
            append("Trying to connect $endpoint ...")
            val connectOutput = runAdb(listOf("connect", endpoint), 30)
            if (connectOutput.isNotBlank()) {
                append(connectOutput.trimEnd())
            }
            val devices = waitForConnectedDevices(port)
            if (devices.any { it == endpoint || it.endsWith(":$port") }) {
                preferredSerial = devices.first { it == endpoint || it.endsWith(":$port") }
                append("ADB device connected through $endpoint")
                return true
            }
        }
        return false
    }

    private fun waitForConnectedDevices(port: Int): List<String> {
        repeat(6) { attempt ->
            val devicesOutput = runAdb(listOf("devices"), 5)
            val devices = parseConnectedDevices(devicesOutput)
            if (devices.any { it.endsWith(":$port") }) return devices
            if (attempt < 5) Thread.sleep(1_000)
        }
        return emptyList()
    }

    private fun adbEndpoints(port: String): List<String> {
        val endpoints = LinkedHashSet<String>()
        endpoints.add("localhost:$port")
        endpoints.add("127.0.0.1:$port")
        CloudSendAdbDnsDiscover.localIpv4Address(appContext)?.let { ip ->
            endpoints.add("$ip:$port")
        }
        return endpoints.toList()
    }

    private fun isPairSuccessful(output: String, exitCode: Int): Boolean {
        val lower = output.lowercase()
        if (
            lower.contains("failed") ||
            lower.contains("unable") ||
            lower.contains("cannot") ||
            lower.contains("error") ||
            lower.contains("invalid") ||
            lower.contains("wrong")
        ) {
            return false
        }
        return exitCode == 0 && (
            lower.contains("successfully paired") ||
                lower.contains("pairing succeeded") ||
                lower.contains("paired")
            )
    }

    private fun adbProcess(args: List<String>): Process {
        return ProcessBuilder(listOf(adbPath) + args)
            .directory(appContext.filesDir)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(processEnvironment())
            }
            .start()
    }

    private fun localShellProcess(args: List<String>): Process {
        return ProcessBuilder(args)
            .directory(appContext.filesDir)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(processEnvironment())
                environment()["ADB"] = adbPath
            }
            .start()
    }

    private fun readProcessOutput(process: Process): String {
        return try {
            process.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun closeShellProcess() {
        shellGeneration++
        restartOnShellExit = false
        try {
            shellProcess?.destroy()
        } catch (_: Exception) {
        } finally {
            shellProcess = null
            shellReady = false
            connected = false
        }
    }

    private fun restartShellAfterDelay(generation: Int) {
        if (!restartOnShellExit) return
        Thread {
            try {
                Thread.sleep(3_000)
                if (shellGeneration != generation || !restartOnShellExit) return@Thread
                if (shellRestartAttempts >= MAX_SHELL_RESTART_ATTEMPTS) {
                    restartOnShellExit = false
                    append("Shell restarted too many times; please check wireless debugging and pair again.")
                    return@Thread
                }
                shellRestartAttempts++
                append("Shell is dead, resetting...")
                if (localShell) {
                    startLocalShellInternal()
                } else {
                    runAdb(listOf("kill-server"), 3)
                    startServerInternal()
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun sendRawToShell(command: String) {
        val process = shellProcess ?: return
        try {
            PrintStream(process.outputStream).apply {
                println(command)
                flush()
            }
        } catch (e: Exception) {
            fail("Failed to send startup shell command: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun parseConnectedDevices(devicesOutput: String): List<String> {
        return devicesOutput
            .lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2 && parts[1] == "device") parts[0] else null
            }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun selectDeviceSerial(devices: List<String>): String? {
        if (devices.isEmpty()) return null
        if (devices.size == 1) return devices.first()
        val preferred = preferredSerial
        return devices.firstOrNull { it == preferred }
            ?: devices.firstOrNull { it.startsWith("localhost:") || it.startsWith("127.0.0.1:") }
            ?: devices.firstOrNull { !it.startsWith("emulator-") }
            ?: devices.first()
    }

    private fun waitForWirelessDebuggingIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (isWirelessDebuggingEnabled()) return
        append("Wireless debugging is not enabled.")
        append("Settings -> Developer options -> Wireless debugging")
        append("Waiting for wireless debugging...")
        repeat(60) {
            Thread.sleep(1_000)
            if (isWirelessDebuggingEnabled()) {
                append("Wireless debugging is enabled.")
                return
            }
        }
        append("Wireless debugging was not enabled within 60 seconds; continuing with fallback.")
    }

    private fun isWirelessDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(appContext.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun prepareWirelessDebuggingLikeLadb() {
        if (!hasSecureSettingsPermission()) return
        disableMobileDataAlwaysOnIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cycleWirelessDebugging()
        }
    }

    private fun hasSecureSettingsPermission(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun disableMobileDataAlwaysOnIfNeeded() {
        try {
            if (Settings.Global.getInt(appContext.contentResolver, "mobile_data_always_on", 0) == 1) {
                append("Disabling 'Mobile data always on'...")
                Settings.Global.putInt(appContext.contentResolver, "mobile_data_always_on", 0)
                Thread.sleep(3_000)
            }
        } catch (_: Exception) {
        }
    }

    private fun cycleWirelessDebugging() {
        append("Cycling wireless debugging, please wait...")
        try {
            if (isWirelessDebuggingEnabled()) {
                append("Turning off wireless debugging...")
                Settings.Global.putInt(appContext.contentResolver, "adb_wifi_enabled", 0)
                Thread.sleep(3_000)
            }

            append("Turning on wireless debugging...")
            Settings.Global.putInt(appContext.contentResolver, "adb_wifi_enabled", 1)
            Thread.sleep(3_000)

            append("Turning off wireless debugging...")
            Settings.Global.putInt(appContext.contentResolver, "adb_wifi_enabled", 0)
            Thread.sleep(3_000)

            append("Turning on wireless debugging...")
            Settings.Global.putInt(appContext.contentResolver, "adb_wifi_enabled", 1)
            Thread.sleep(3_000)
        } catch (e: Exception) {
            append("Wireless debugging cycle failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fail(message: String) {
        lastError = message
        append(message)
    }

    private fun append(message: String) {
        synchronized(output) {
            output.append("* ").append(message).append(System.lineSeparator())
            if (output.length > MAX_OUTPUT_BUFFER_SIZE) {
                output.delete(0, output.length - MAX_OUTPUT_BUFFER_SIZE)
            }
        }
    }
}
