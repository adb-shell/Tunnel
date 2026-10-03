package com.tunnel.app.adb

data class TunnelAdbState(
    val supported: Boolean = false,
    val binaryAvailable: Boolean = false,
    val binaryExecutable: Boolean = false,
    val initialized: Boolean = false,
    val pairing: Boolean = false,
    val paired: Boolean = false,
    val pairedBefore: Boolean = false,
    val busy: Boolean = false,
    val mirrorActive: Boolean = false,
    val connected: Boolean = false,
    val shellReady: Boolean = false,
    val output: String = "",
    val adbPath: String = "",
    val environment: Map<String, String> = emptyMap(),
    val lastError: String = "",
    val phase: String = "IDLE",
    val terminalRunning: Boolean = false,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "supported" to supported,
        "binaryAvailable" to binaryAvailable,
        "binaryExecutable" to binaryExecutable,
        "initialized" to initialized,
        "pairing" to pairing,
        "paired" to paired,
        "pairedBefore" to pairedBefore,
        "busy" to busy,
        "mirrorActive" to mirrorActive,
        "connected" to connected,
        "shellReady" to shellReady,
        "output" to output,
        "lastError" to lastError,
        "phase" to phase,
        "terminalRunning" to terminalRunning,
    )
}
