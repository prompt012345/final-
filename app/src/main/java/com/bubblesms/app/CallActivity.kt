package com.bubblesms.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bubblesms.app.data.PrefsManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CallActivity : AppCompatActivity() {
    private val REQ = 801
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var audioRecord: AudioRecord? = null
    private val mediaRunning = AtomicBoolean(false)
    private var selectedIps = mutableSetOf<String>()
    private lateinit var remoteImage: ImageView
    private lateinit var preview: PreviewView
    private lateinit var peersList: LinearLayout
    private lateinit var state: TextView
    private var group = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        group = intent.getBooleanExtra("group", false)
        findViewById<TextView>(R.id.callTitle).text = if (group) "Appel vidéo de groupe" else "Appel vidéo"
        findViewById<TextView>(R.id.callSubtitle).text = "Wi‑Fi local • direct • sans serveur"
        remoteImage = findViewById(R.id.remoteVideo)
        preview = findViewById(R.id.localPreview)
        peersList = findViewById(R.id.peersList)
        state = findViewById(R.id.callState)
        findViewById<ImageButton>(R.id.callClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.callConnect).setOnClickListener { requestAndStart() }
        discoverPeers()
    }

    private fun discoverPeers() {
        peersList.removeAllViews()
        val me = PrefsManager.getMyPseudo(this) ?: "BubbleSMS"
        LocalDiscoveryManager.stop()
        LocalDiscoveryManager.start(this, me) { name, ip -> runOnUiThread {
            if (ip == LocalNetworkManager.localIp(this)) return@runOnUiThread
            if (peersList.findViewWithTag<View>(ip) != null) return@runOnUiThread
            val cb = CheckBox(this).apply { text = "$name  •  $ip"; tag = ip; setTextColor(0xFFFFFFFF.toInt()) }
            cb.setOnCheckedChangeListener { _, checked -> if (checked) selectedIps.add(ip) else selectedIps.remove(ip) }
            peersList.addView(cb)
        }}
    }

    private fun requestAndStart() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (needed.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, needed, REQ); return
        }
        if (selectedIps.isEmpty()) { Toast.makeText(this, "Sélectionne au moins un appareil du même réseau Wi‑Fi.", Toast.LENGTH_LONG).show(); return }
        startMedia()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startMedia()
    }

    private fun startMedia() {
        mediaRunning.set(true)
        state.text = "● Appel local actif • ${selectedIps.size} appareil(s)"
        findViewById<Button>(R.id.callConnect).text = "Appel en cours"
        LocalCallManager.start(selectedIps, { _, bytes ->
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@start
            runOnUiThread { remoteImage.setImageBitmap(bmp); remoteImage.visibility = View.VISIBLE }
        }, { _, _ -> }, { })
        startCamera(); startAudio()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val previewUseCase = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setTargetResolution(android.util.Size(640, 480)).build()
            analysis.setAnalyzer(cameraExecutor) { image -> encodeAndSend(image) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, previewUseCase, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun encodeAndSend(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val y = ByteArray(buffer.remaining()); buffer.get(y)
            // Fast local preview frame: JPEG from the Y plane as grayscale is reliable across devices.
            val bmp = android.graphics.Bitmap.createBitmap(image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
            val pixels = IntArray(image.width * image.height)
            for (i in pixels.indices) { val v = y[minOf(i, y.lastIndex)].toInt() and 255; pixels[i] = -0x1000000 or (v shl 16) or (v shl 8) or v }
            bmp.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val out = ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 45, out); bmp.recycle()
            LocalCallManager.sendVideo(out.toByteArray())
        } catch (_: Throwable) { } finally { image.close() }
    }

    private fun startAudio() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096))
        audioRecord?.startRecording()
        Thread {
            val buf = ByteArray(2048)
            while (mediaRunning.get()) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: 0
                if (n > 0) LocalCallManager.sendAudio(buf.copyOf(n))
            }
        }.start()
    }

    override fun onDestroy() {
        mediaRunning.set(false)
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        LocalCallManager.stop(); LocalDiscoveryManager.stop(); cameraExecutor.shutdown()
        super.onDestroy()
    }
}
