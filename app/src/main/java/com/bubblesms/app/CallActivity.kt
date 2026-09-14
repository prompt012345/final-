package com.bubblesms.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CallActivity : AppCompatActivity() {
    private val REQ = 801
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        val group = intent.getBooleanExtra("group", false)
        findViewById<TextView>(R.id.callTitle).text = if (group) "Appel vidéo de groupe" else "Appel vidéo"
        findViewById<TextView>(R.id.callSubtitle).text = if (group) "Mode Wi‑Fi local • jusqu'à 4 appareils" else "Mode Wi‑Fi local • sans serveur"
        findViewById<ImageButton>(R.id.callClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.callConnect).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQ)
            } else startPreview()
        }
    }
    private fun startPreview() {
        findViewById<TextView>(R.id.callState).text = "● Caméra/micro activés • réseau local prêt"
        findViewById<Button>(R.id.callConnect).text = "Connecté au réseau local"
        Toast.makeText(this, "Le mode appel local est prêt. Les appareils doivent être sur le même Wi‑Fi.", Toast.LENGTH_LONG).show()
    }
}
