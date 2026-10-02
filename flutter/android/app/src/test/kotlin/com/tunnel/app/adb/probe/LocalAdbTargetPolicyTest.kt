package com.tunnel.app.adb.probe

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAdbTargetPolicyTest {
    private val assigned = setOf(InetAddress.getByAddress(byteArrayOf(10, 2, 3, 4)))

    @Test fun acceptsOnlyExplicitLoopbackOrAssignedAddress() {
        for (serial in listOf("localhost:37123", "127.0.0.1:37123", "[::1]:37123", "10.2.3.4:37123")) {
            val target = LocalAdbTarget.validate(serial, assigned)
            assertNotNull(target)
            assertEquals(serial, target!!.serial)
            assertEquals("LocalAdbTarget(redacted)", target.toString())
        }
        assertNull(LocalAdbTarget.validate("10.2.3.5:37123", assigned))
        assertNull(LocalAdbTarget.validate("10.2.3.4:37123", emptySet()))
    }

    @Test fun rejectsHostnameUsbDiscoveryInjectionAndAmbiguousNumbers() {
        for (serial in listOf(
            "example.com:37123", "device-serial", "adb-name._adb-tls-connect._tcp",
            "127.0.0.1:37123;id", "127.0.0.1:37123\n", " 127.0.0.1:37123",
            "-s", "127.1:37123", "127.00.0.1:37123", "127.0.0.1:00080",
            "127.0.0.1:0", "127.0.0.1:65536", "127.0.0.1:+80", "[::1%lo]:37123",
            "0.0.0.0:37123", "255.255.255.255:37123", "127.0.0.1", "::1:37123",
        )) assertNull(serial, LocalAdbTarget.validate(serial, assigned))
    }

    @Test fun rejectsWildcardAndMulticastEvenIfEnumerationIncludedThem() {
        val addresses = setOf(
            InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0)),
            InetAddress.getByAddress(byteArrayOf(224.toByte(), 0, 0, 1)),
        )
        assertNull(LocalAdbTarget.validate("0.0.0.0:37123", addresses))
        assertNull(LocalAdbTarget.validate("224.0.0.1:37123", addresses))
    }
}
