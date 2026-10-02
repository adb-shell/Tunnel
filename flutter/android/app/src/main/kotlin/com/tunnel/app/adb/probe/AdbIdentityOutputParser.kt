package com.tunnel.app.adb.probe

internal object AdbIdentityOutputParser {
    private val noncePattern = Regex("^[0-9a-f]{32}$")

    fun command(nonce: String): String {
        require(noncePattern.matches(nonce))
        // nonce is generated locally, validated hex; no UI, peer, target or user text is inserted.
        return "printf 'TUNNEL_ID_BEGIN:$nonce\\n'; /system/bin/id -u; " +
            "probe_status=\$?; printf 'TUNNEL_ID_END:$nonce\\n'; exit \$probe_status"
    }

    fun parseUid(stdout: ByteArray, nonce: String): Int? {
        if (!noncePattern.matches(nonce) || stdout.size > 1024) return null
        val text = stdout.toString(Charsets.US_ASCII)
        val expected = Regex(
            "\\ATUNNEL_ID_BEGIN:$nonce\\r?\\n(0|[1-9][0-9]{0,9})\\r?\\n" +
                "TUNNEL_ID_END:$nonce\\r?\\n\\z"
        )
        return expected.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()
    }
}
