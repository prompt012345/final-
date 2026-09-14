package com.bubblesms.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class LocalShareActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var ipView: TextView
    private var selectedBytes: ByteArray? = null
    private var selectedName = "fichier"
    private var selectedMime = "application/octet-stream"
    private val peers = linkedMapOf<String, String>()

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) prepare(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_share)
        status = findViewById(R.id.localStatus)
        ipView = findViewById(R.id.localIp)
        findViewById<ImageButton>(R.id.localBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.localPick).setOnClickListener { pick.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.localSend).setOnClickListener { askHostAndSend() }
        findViewById<Button>(R.id.localCall).setOnClickListener { openCall(false) }
        findViewById<Button>(R.id.localGroupCall).setOnClickListener { openCall(true) }
        startReceiver()
        startDiscovery()
        updateNetworkState()
        intent.getStringExtra("selected_uri")?.let { prepare(Uri.parse(it)) }
    }

    private fun updateNetworkState() {
        if (LocalNetworkManager.hasWifi(this)) {
            ipView.text = "Adresse locale : ${LocalNetworkManager.localIp(this)}\nMême Wi‑Fi requis • aucun serveur"
            status.text = "● Mode local prêt"
        } else {
            ipView.text = "Wi‑Fi désactivé\nActive le Wi‑Fi pour utiliser le mode local"
            status.text = "● En attente du Wi‑Fi"
        }
    }

    private fun startReceiver() {
        LocalNetworkManager.startReceiver({ name, mime, bytes ->
            runOnUiThread {
                val file = File(filesDir, "received_$name")
                file.outputStream().use { it.write(bytes) }
                status.text = "✓ Reçu : $name"
                Toast.makeText(this, "Fichier reçu : $name", Toast.LENGTH_LONG).show()
            }
        }) { runOnUiThread { status.text = "● Mode local actif" } }
    }

    private fun prepare(uri: Uri) {
        try {
            selectedBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            selectedName = queryName(uri) ?: "fichier"
            selectedMime = contentResolver.getType(uri) ?: "application/octet-stream"
            status.text = "✓ Prêt : $selectedName"
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible de lire ce fichier", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askHostAndSend() {
        val bytes = selectedBytes ?: run { Toast.makeText(this, "Choisis d'abord un fichier", Toast.LENGTH_SHORT).show(); return }
        if (peers.isNotEmpty()) {
            val names = peers.entries.map { "${it.key}  •  ${it.value}" }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Appareils sur le Wi‑Fi").setItems(names) { _, which ->
                val host = peers.values.elementAt(which)
                sendTo(host, bytes)
            }.setNegativeButton("Entrer une IP", null).show()
        } else {
            val input = EditText(this).apply { hint = "IP du téléphone (ex. 192.168.1.25)"; setSingleLine(true) }
            AlertDialog.Builder(this).setTitle("Envoyer en Wi‑Fi local").setMessage("Aucun appareil détecté. Le destinataire doit ouvrir le mode local.").setView(input)
                .setNegativeButton("Annuler", null).setPositiveButton("Envoyer") { _, _ -> sendTo(input.text.toString().trim(), bytes) }.show()
        }
    }

    private fun sendTo(host: String, bytes: ByteArray) {
        status.text = "↗ Envoi en cours…"
        LocalNetworkManager.sendFile(host, selectedName, selectedMime, bytes,
            { runOnUiThread { status.text = "✓ Fichier envoyé à $host" } },
            { runOnUiThread { status.text = "× Envoi impossible — même Wi‑Fi requis" } })
    }

    private fun startDiscovery() {
        LocalDiscoveryManager.start(this, PrefsName()) { name, ip ->
            runOnUiThread { peers[name] = ip; status.text = "● ${peers.size} appareil(s) sur le réseau local" }
        }
    }

    private fun PrefsName(): String = com.bubblesms.app.data.PrefsManager.getMyPseudo(this) ?: "BubbleSMS"


    private fun openCall(group: Boolean) {
        startActivity(android.content.Intent(this, CallActivity::class.java).putExtra("group", group))
    }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    override fun onDestroy() { super.onDestroy(); LocalNetworkManager.stopReceiver(); LocalDiscoveryManager.stop() }
}
