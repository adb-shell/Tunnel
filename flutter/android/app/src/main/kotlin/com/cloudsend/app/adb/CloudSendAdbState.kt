package com.cloudsend.app.adb

data class CloudSendAdbState(
    val supported: Boolean = false,
    val binaryAvailable: Boolean = false,
    val binaryExecutable: Boolean = false,
    val initialized: Boolean = false,
    val pairing: Boolean = false,
    val paired: Boolean = false,
    val connected: Boolean = false,
    val shellReady: Boolean = false,
    val output: String = "",
    val adbPath: String = "",
    val environment: Map<String, String> = emptyMap(),
    val lastError: String = "",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "supported" to supported,
        "binaryAvailable" to binaryAvailable,
        "binaryExecutable" to binaryExecutable,
        "initialized" to initialized,
        "pairing" to pairing,
        "paired" to paired,
        "connected" to connected,
        "shellReady" to shellReady,
        "output" to output,
        "adbPath" to adbPath,
        "environment" to environment,
        "lastError" to lastError,
    )
}
