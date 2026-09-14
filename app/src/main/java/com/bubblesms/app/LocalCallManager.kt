package com.bubblesms.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Direct LAN media transport. No cloud/server is used. */
object LocalCallManager {
    private const val VIDEO_PORT = 47824
    private const val AUDIO_PORT = 47825
    private const val MAX_PACKET = 60000
    private const val MAGIC_V = "BUBV1"
    private const val MAGIC_A = "BUBA1"
    private val io = Executors.newCachedThreadPool()
    private var videoSocket: DatagramSocket? = null
    private var audioSocket: DatagramSocket? = null
    @Volatile private var running = false
    private val peers = ConcurrentHashMap.newKeySet<String>()
    private var audioTrack: AudioTrack? = null

    fun start(remoteIps: Collection<String>, onVideo: (String, ByteArray) -> Unit, onAudio: (String, ByteArray) -> Unit, onError: (Throwable) -> Unit = {}) {
        stop()
        peers.clear(); peers.addAll(remoteIps.filter { it.isNotBlank() })
        videoSocket = DatagramSocket(VIDEO_PORT).apply { reuseAddress = true }
        audioSocket = DatagramSocket(AUDIO_PORT).apply { reuseAddress = true }
        running = true
        io.execute { receiveVideo(onVideo, onError) }
        io.execute { receiveAudio(onAudio, onError) }
        startAudioPlayback()
    }

    fun updatePeers(remoteIps: Collection<String>) { peers.clear(); peers.addAll(remoteIps.filter { it.isNotBlank() }) }

    fun sendVideo(bytes: ByteArray) {
        if (!running || bytes.isEmpty()) return
        io.execute {
            try {
                val id = UUID.randomUUID().toString().substring(0, 8)
                val chunkSize = MAX_PACKET - 32
                val count = (bytes.size + chunkSize - 1) / chunkSize
                for (i in 0 until count) {
                    val end = minOf(bytes.size, (i + 1) * chunkSize)
                    val payload = ByteArrayOutputStream().apply {
                        write(MAGIC_V.toByteArray()); write('|'.code); write(id.toByteArray()); write('|'.code)
                        write(i.toString().toByteArray()); write('|'.code); write(count.toString().toByteArray()); write('|'.code)
                        write(bytes, i * chunkSize, end - i * chunkSize)
                    }.toByteArray()
                    peers.forEach { ip -> videoSocket?.send(DatagramPacket(payload, payload.size, InetAddress.getByName(ip), VIDEO_PORT)) }
                }
            } catch (t: Throwable) { }
        }
    }

    fun sendAudio(bytes: ByteArray) {
        if (!running || bytes.isEmpty()) return
        io.execute {
            try {
                val payload = ByteArrayOutputStream().apply { write(MAGIC_A.toByteArray()); write('|'.code); write(bytes) }.toByteArray()
                peers.forEach { ip -> audioSocket?.send(DatagramPacket(payload, payload.size, InetAddress.getByName(ip), AUDIO_PORT)) }
            } catch (_: Throwable) { }
        }
    }

    private fun receiveVideo(onVideo: (String, ByteArray) -> Unit, onError: (Throwable) -> Unit) {
        val frames = HashMap<String, Array<ByteArray?>>()
        val counts = HashMap<String, Int>()
        try {
            while (running) {
                val buf = ByteArray(65535); val p = DatagramPacket(buf, buf.size); videoSocket?.receive(p)
                val text = String(p.data, 0, p.length, Charsets.ISO_8859_1)
                val first = text.indexOf('|'); if (first < 0) continue
                if (!text.startsWith(MAGIC_V)) continue
                val second = text.indexOf('|', first + 1); val third = text.indexOf('|', second + 1); val fourth = text.indexOf('|', third + 1)
                if (fourth < 0) continue
                val id = text.substring(first + 1, second); val idx = text.substring(second + 1, third).toIntOrNull() ?: continue
                val count = text.substring(third + 1, fourth).toIntOrNull() ?: continue
                val payload = p.data.copyOfRange(fourth + 1, p.length)
                val arr = frames.getOrPut(id) { arrayOfNulls(count) }; counts[id] = count
                if (idx in arr.indices) arr[idx] = payload
                if (arr.all { it != null }) {
                    val out = ByteArrayOutputStream(); arr.forEach { out.write(it!!) }
                    frames.remove(id); counts.remove(id); onVideo(p.address.hostAddress ?: "", out.toByteArray())
                }
            }
        } catch (t: Throwable) { if (running) onError(t) }
    }

    private fun receiveAudio(onAudio: (String, ByteArray) -> Unit, onError: (Throwable) -> Unit) {
        try {
            while (running) {
                val buf = ByteArray(4096); val p = DatagramPacket(buf, buf.size); audioSocket?.receive(p)
                if (p.length <= 6) continue
                val header = String(p.data, 0, 5, Charsets.ISO_8859_1)
                if (header != MAGIC_A) continue
                val bytes = p.data.copyOfRange(6, p.length); onAudio(p.address.hostAddress ?: "", bytes)
                audioTrack?.write(bytes, 0, bytes.size)
            }
        } catch (t: Throwable) { if (running) onError(t) }
    }

    private fun startAudioPlayback() {
        try {
            val min = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioTrack = AudioTrack(AudioManager.STREAM_VOICE_CALL, 16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096), AudioTrack.MODE_STREAM)
            audioTrack?.play()
        } catch (_: Throwable) { }
    }

    fun stop() {
        running = false
        peers.clear()
        try { videoSocket?.close() } catch (_: Throwable) {}
        try { audioSocket?.close() } catch (_: Throwable) {}
        videoSocket = null; audioSocket = null
        try { audioTrack?.stop() } catch (_: Throwable) {}
        try { audioTrack?.release() } catch (_: Throwable) {}
        audioTrack = null
    }
}
