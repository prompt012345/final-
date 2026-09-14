package com.bubblesms.app

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/** Découverte LAN par broadcast UDP : aucun serveur central. */
object LocalDiscoveryManager {
    private const val PORT = 47820
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var callback: ((String, String) -> Unit)? = null

    fun start(ctx: Context, name: String, onPeer: (String, String) -> Unit) {
        if (running) return
        callback = onPeer
        running = true
        executor.execute {
            try {
                socket = DatagramSocket(PORT).apply { broadcast = true; reuseAddress = true }
                val listen = socket!!
                executor.execute {
                    val data = ByteArray(1024)
                    while (running) {
                        try {
                            val p = DatagramPacket(data, data.size)
                            listen.receive(p)
                            val msg = String(p.data, 0, p.length)
                            if (msg.startsWith("BUBBLE_LOCAL::")) {
                                val peer = msg.removePrefix("BUBBLE_LOCAL::").split("::", limit = 2)
                                if (peer.size == 2 && peer[1] != localIp(ctx)) callback?.invoke(peer[0], peer[1])
                            }
                        } catch (_: Throwable) { if (!running) break }
                    }
                }
                while (running) {
                    try {
                        val msg = "BUBBLE_LOCAL::${name.replace("::", " ")}::${localIp(ctx)}".toByteArray()
                        val p = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), PORT)
                        listen.send(p)
                    } catch (_: Throwable) {}
                    Thread.sleep(2500)
                }
            } catch (_: Throwable) {} finally { try { socket?.close() } catch (_: Throwable) {}; socket = null }
        }
    }

    fun stop() { running = false; callback = null; try { socket?.close() } catch (_: Throwable) {} ; socket = null }

    private fun localIp(ctx: Context): String {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return listOf(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff).joinToString(".")
    }
}
