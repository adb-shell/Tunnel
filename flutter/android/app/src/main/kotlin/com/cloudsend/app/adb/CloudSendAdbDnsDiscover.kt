package com.cloudsend.app.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CloudSendAdbDnsDiscover(context: Context) {
    companion object {
        private const val SERVICE_TYPE = "_adb-tls-connect._tcp"

        fun localIpv4Address(context: Context): String? {
            return CloudSendAdbDnsDiscover(context).getLocalIpAddress()
        }
    }

    private val appContext = context.applicationContext

    fun discoverConnectPort(
        timeoutMs: Long = 10_000,
        log: (String) -> Unit,
    ): Int? {
        val nsdManager =
            appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val pendingResolves = AtomicInteger(0)
        val finishScheduled = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val localIp = getLocalIpAddress()
        val lock = acquireMulticastLock()
        val best = BestPort()
        var discoveryStarted = false

        lateinit var discoveryListener: NsdManager.DiscoveryListener

        fun finishIfQuiet() {
            if (pendingResolves.get() == 0 && best.port != null && finishScheduled.compareAndSet(false, true)) {
                Thread {
                    // Match LADB's behavior: keep scanning briefly so newer broadcasts can win.
                    Thread.sleep(3_000)
                    done.countDown()
                }.start()
            }
        }

        fun resolve(service: NsdServiceInfo, attempt: Int = 0) {
            pendingResolves.incrementAndGet()
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    log("ADB mDNS resolve failed: $errorCode")
                    pendingResolves.decrementAndGet()
                    if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && attempt < 4) {
                        Thread {
                            Thread.sleep(250L * (attempt + 1))
                            resolve(serviceInfo, attempt + 1)
                        }.start()
                        return
                    }
                    finishIfQuiet()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    handleResolvedService(serviceInfo, localIp, best, log)
                    pendingResolves.decrementAndGet()
                    finishIfQuiet()
                }
            }

            try {
                nsdManager.resolveService(service, resolveListener)
            } catch (e: Exception) {
                log("ADB mDNS resolve error: ${e.message ?: e.javaClass.simpleName}")
                pendingResolves.decrementAndGet()
                finishIfQuiet()
            }
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                discoveryStarted = true
                log("ADB mDNS discovery started.")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val serviceType = serviceInfo.serviceType ?: ""
                if (!serviceType.contains("_adb-tls-connect")) return
                log("ADB mDNS service found: ${serviceInfo.serviceName}")
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                log("ADB mDNS service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                log("ADB mDNS discovery stopped.")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("ADB mDNS discovery failed: $errorCode")
                done.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("ADB mDNS stop failed: $errorCode")
            }
        }

        return try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
            best.port
        } catch (e: Exception) {
            log("ADB mDNS discovery error: ${e.message ?: e.javaClass.simpleName}")
            null
        } finally {
            if (discoveryStarted) {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (_: Exception) {
                }
            }
            try {
                lock?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun handleResolvedService(
        serviceInfo: NsdServiceInfo,
        localIp: String?,
        best: BestPort,
        log: (String) -> Unit,
    ) {
        val port = serviceInfo.port
        if (port <= 0) {
            log("ADB mDNS ignored zero port.")
            return
        }

        val hostAddress = try {
            serviceInfo.host?.hostAddress
        } catch (_: Exception) {
            null
        }
        val localMatch = hostAddress == null ||
            localIp == null ||
            hostAddress == localIp ||
            hostAddress == "127.0.0.1" ||
            hostAddress == "::1"
        if (!localMatch) {
            log("ADB mDNS found non-local host as fallback: $hostAddress")
        }

        val expirationTime = parseExpirationTime(serviceInfo.toString())
        if (best.shouldReplace(serviceInfo.serviceName, expirationTime, localMatch)) {
            best.port = port
            best.serviceName = serviceInfo.serviceName
            best.expirationTime = expirationTime
            best.localMatch = localMatch
            log("ADB connect port discovered: $port")
        }
    }

    private fun parseExpirationTime(raw: String): Long? {
        val expirationTimeStr =
            """expirationTime: (\S+)""".toRegex().find(raw)?.groupValues?.get(1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return try {
            dateFormat.parse(expirationTimeStr ?: "")?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun getLocalIpAddress(): String? {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        return try {
            wifiManager.createMulticastLock("cloudsend:adb-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            null
        }
    }

    private class BestPort {
        var port: Int? = null
        var serviceName: String? = null
        var expirationTime: Long? = null
        var localMatch: Boolean = false

        fun shouldReplace(
            nextServiceName: String,
            nextExpirationTime: Long?,
            nextLocalMatch: Boolean
        ): Boolean {
            if (port == null) return true
            if (nextLocalMatch != localMatch) return nextLocalMatch
            if (nextExpirationTime != null) {
                val currentExpiration = expirationTime
                return currentExpiration == null || nextExpirationTime > currentExpiration
            }
            val currentName = serviceName ?: return true
            return serviceIndex(nextServiceName) >= serviceIndex(currentName)
        }

        private fun serviceIndex(name: String): Int {
            return """\((\d+)\)""".toRegex()
                .find(name)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0
        }
    }
}
