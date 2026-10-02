package com.tunnel.app.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.tunnel.app.adb.probe.LocalAdbTargetPolicy
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Pair/connect use different services. Unknown/foreign hosts are never fallbacks. */
class TunnelAdbDnsDiscover(context: Context) {
    enum class Kind(val serviceType: String) {
        CONNECT("_adb-tls-connect._tcp."), PAIR("_adb-tls-pairing._tcp.")
    }
    private val app = context.applicationContext

    fun discoverEndpoints(kind: Kind, timeoutMs: Long = 8_000, cancelled: () -> Boolean = { false }): List<String> {
        val manager = app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val active = AtomicBoolean(true)
        val wake = CountDownLatch(1)
        val lock = Any()
        val endpoints = linkedSetOf<String>()
        val queue = ArrayDeque<NsdServiceInfo>()
        val seen = mutableSetOf<String>()
        var resolving = false
        val multicast = try {
            (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createMulticastLock("tunnel:adb-mdns")?.apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) { null }

        fun resolveNext() {
            val service = synchronized(lock) {
                if (!active.get() || resolving || queue.isEmpty()) null
                else queue.removeFirst().also { resolving = true }
            } ?: return
            fun finished() { synchronized(lock) { resolving = false }; resolveNext() }
            try {
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) { finished() }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (active.get()) {
                            val host = info.host?.hostAddress
                            if (host != null && info.port in 1..65535) {
                                val endpoint = (if (host.contains(':')) "[" + host + "]" else host) + ":" + info.port
                                if (LocalAdbTargetPolicy.validate(endpoint) != null) {
                                    synchronized(lock) { endpoints.add(endpoint) }
                                    wake.countDown()
                                }
                            }
                        }
                        finished()
                    }
                })
            } catch (_: Exception) { finished() }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!active.get() || info.serviceType.trimEnd('.') != kind.serviceType.trimEnd('.')) return
                synchronized(lock) {
                    if (seen.size < 32 && seen.add(info.serviceName)) queue.add(info)
                }
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { wake.countDown() }
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
        }
        return try {
            manager.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceIn(1, 15_000))
            while (!cancelled() && !Thread.currentThread().isInterrupted && System.nanoTime() < deadline) {
                if (wake.await(100, TimeUnit.MILLISECONDS)) break
            }
            if (cancelled() || Thread.currentThread().isInterrupted) emptyList()
            else synchronized(lock) { endpoints.toList() }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt(); emptyList()
        } catch (_: Exception) { emptyList() }
        finally {
            active.set(false)
            synchronized(lock) { queue.clear() }
            try { manager.stopServiceDiscovery(listener) } catch (_: Exception) {}
            try { multicast?.release() } catch (_: Exception) {}
        }
    }

    fun discoverConnectPort(timeoutMs: Long = 8_000, log: (String) -> Unit): Int? {
        val found = discoverEndpoints(Kind.CONNECT, timeoutMs).firstOrNull()
        if (found == null) log("未发现本机连接端口，请手动输入无线调试主页地址。")
        return found?.substringAfterLast(':')?.toIntOrNull()
    }

    companion object {
        fun localIpv4Address(context: Context): String? = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.let { manager ->
                manager.allNetworks.firstNotNullOfOrNull { network ->
                    if (manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                        manager.getLinkProperties(network)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
                    else null
                }
            }
        } catch (_: Exception) { null }
    }
}
