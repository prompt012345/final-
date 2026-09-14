package com.bubblesms.app

import android.content.Context
import android.net.wifi.WifiManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Transport local uniquement : aucun serveur distant.
 * Les données passent directement entre appareils présents sur le même réseau Wi‑Fi.
 */
object LocalNetworkManager {
    private const val PORT = 47821
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null

    fun localIp(ctx: Context): String {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return listOf(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff).joinToString(".")
    }

    fun startReceiver(onFile: (name: String, mime: String, bytes: ByteArray) -> Unit, onError: (Throwable) -> Unit = {}) {
        if (server != null) return
        executor.execute {
            try {
                server = ServerSocket(PORT)
                while (!Thread.currentThread().isInterrupted) {
                    val socket = server!!.accept()
                    executor.execute {
                        try {
                            DataInputStream(BufferedInputStream(socket.getInputStream())).use { input ->
                                val name = input.readUTF()
                                val mime = input.readUTF()
                                val size = input.readInt()
                                if (size < 0 || size > 100 * 1024 * 1024) return@use
                                val bytes = ByteArray(size)
                                input.readFully(bytes)
                                onFile(name, mime, bytes)
                            }
                        } catch (t: Throwable) { onError(t) } finally { socket.close() }
                    }
                }
            } catch (t: Throwable) { onError(t) }
        }
    }

    fun stopReceiver() {
        try { server?.close() } catch (_: Throwable) {}
        server = null
    }

    fun sendFile(host: String, name: String, mime: String, bytes: ByteArray, onDone: () -> Unit, onError: (Throwable) -> Unit) {
        executor.execute {
            try {
                Socket(host, PORT).use { socket ->
                    DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { out ->
                        out.writeUTF(name)
                        out.writeUTF(mime)
                        out.writeInt(bytes.size)
                        out.write(bytes)
                        out.flush()
                    }
                }
                onDone()
            } catch (t: Throwable) { onError(t) }
        }
    }

    fun hasWifi(ctx: Context): Boolean {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.isWifiEnabled
    }
}
