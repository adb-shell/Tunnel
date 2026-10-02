package com.tunnel.app.adb.probe

import java.net.InetAddress
import java.net.NetworkInterface

/** Address-local selector only; binding the actual device still requires the helper handshake. */
internal class LocalAdbTarget private constructor(val serial: String) {
    companion object {
        internal fun validate(serial: String, localAddresses: Set<InetAddress>): LocalAdbTarget? {
            // Do not trim, resolve arbitrary hostnames, accept USB serials, or guess a device.
            if (serial.isEmpty() || serial.length > 80 || serial.any { it.isWhitespace() }) return null
            val match = Regex("^(localhost|[0-9.]+|\\[[0-9a-fA-F:]+\\]):([1-9][0-9]{0,4})$")
                .matchEntire(serial) ?: return null
            val host = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            if (host == "localhost") return LocalAdbTarget(serial)
            val address = parseLiteral(host) ?: return null
            val loopback = address == InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)) ||
                address == InetAddress.getByAddress(ByteArray(16).also { it[15] = 1 })
            if (!loopback && (address.isAnyLocalAddress || address.isMulticastAddress ||
                    !localAddresses.contains(address))) return null
            // Keep the exact validated selector: adb -s matches its transport serial, not DNS.
            return LocalAdbTarget(serial)
        }

        private fun parseLiteral(host: String): InetAddress? = try {
            if (host.startsWith("[")) {
                val literal = host.substring(1, host.length - 1)
                if (!literal.contains(':')) null else InetAddress.getByName(literal)
            } else {
                val parts = host.split('.')
                if (parts.size != 4 || parts.any {
                        it.isEmpty() || it.length > 3 || (it.length > 1 && it[0] == '0') ||
                            (it.toIntOrNull() ?: -1) !in 0..255
                    }) null
                else InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
            }
        } catch (_: Exception) {
            null
        }
    }

    // Avoid accidental device address disclosure through result/debug interpolation.
    override fun toString(): String = "LocalAdbTarget(redacted)"
}

internal object LocalAdbTargetPolicy {
    fun validate(serial: String): LocalAdbTarget? =
        LocalAdbTarget.validate(serial, currentInterfaceAddresses())

    private fun currentInterfaceAddresses(): Set<InetAddress> = try {
        val addresses = mutableSetOf<InetAddress>()
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces != null && interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp) continue
            val candidates = network.inetAddresses
            while (candidates.hasMoreElements()) addresses.add(candidates.nextElement())
        }
        addresses
    } catch (_: Exception) {
        // Enumeration failure never permits an unverified LAN target; loopback still works.
        emptySet()
    }
}
