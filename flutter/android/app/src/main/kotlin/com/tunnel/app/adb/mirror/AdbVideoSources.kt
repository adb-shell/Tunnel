package com.tunnel.app.adb.mirror

/** Desired sources, independent of codec/helper health and PC presentation.
 * Runtime replaces this value under its monitor; no worker can toggle a source.
 */
internal data class AdbVideoSources(
    val live: Boolean = false,
    val snapshot: Boolean = false,
    val hierarchy: Boolean = false,
) {
    // VIDEO_TASK wire modes. The two overlays compose; neither disables live intent.
    val mode: Int get() = when {
        snapshot && hierarchy -> 4
        hierarchy -> 2
        snapshot -> 1
        live -> 0
        else -> 3
    }

    fun apply(action: String): AdbVideoSources = when (action) {
        "live_on" -> copy(live = true)
        "live_off" -> copy(live = false)
        "ignore_on" -> copy(snapshot = true)
        "ignore_off" -> copy(snapshot = false)
        "hierarchy_on" -> copy(hierarchy = true)
        "hierarchy_off" -> copy(hierarchy = false)
        "resume" -> this // Recovery retries only the user's existing selection.
        else -> throw IllegalArgumentException("SOURCE_ACTION_INVALID")
    }
}
