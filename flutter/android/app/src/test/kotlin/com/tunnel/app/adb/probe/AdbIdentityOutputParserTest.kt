package com.tunnel.app.adb.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AdbIdentityOutputParserTest {
    private val nonce = "0123456789abcdef0123456789abcdef"
    private fun response(uid: String, separator: String = "\n") =
        "TUNNEL_ID_BEGIN:$nonce${separator}$uid${separator}TUNNEL_ID_END:$nonce${separator}"

    @Test fun parsesOnlyACompleteCurrentNonceResponse() {
        assertEquals(2000, AdbIdentityOutputParser.parseUid(response("2000").toByteArray(), nonce))
        assertEquals(2000, AdbIdentityOutputParser.parseUid(response("2000", "\r\n").toByteArray(), nonce))
        assertNull(AdbIdentityOutputParser.parseUid("2000\n".toByteArray(), nonce))
        assertNull(AdbIdentityOutputParser.parseUid(response("2000").toByteArray(), "f".repeat(32)))
        assertNull(AdbIdentityOutputParser.parseUid(("banner\n" + response("2000")).toByteArray(), nonce))
        assertNull(AdbIdentityOutputParser.parseUid((response("2000") + "extra\n").toByteArray(), nonce))
        assertNull(AdbIdentityOutputParser.parseUid(response("2000").trimEnd().toByteArray(), nonce))
        assertNull(AdbIdentityOutputParser.parseUid(response("9999999999").toByteArray(), nonce))
        assertNull(AdbIdentityOutputParser.parseUid(ByteArray(1025), nonce))
    }

    @Test fun doesNotConfuseAppUidOrRootWithShellUid() {
        assertEquals(0, AdbIdentityOutputParser.parseUid(response("0").toByteArray(), nonce))
        assertEquals(10123, AdbIdentityOutputParser.parseUid(response("10123").toByteArray(), nonce))
        assertFalse(AdbIdentityOutputParser.parseUid(response("10123").toByteArray(), nonce) == 2000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCommandInjectionAsNonce() {
        AdbIdentityOutputParser.command("x'; id; #")
    }
}
