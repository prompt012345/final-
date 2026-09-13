package com.bubblesms.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bubblesms.app.data.PrefsManager

class PseudoSetupActivity : AppCompatActivity() {

    private val permissions = mutableListOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si un pseudo existe déjà, on saute directement à la liste des amis
        val existing = PrefsManager.getMyPseudo(this)
        if (existing != null) {
            requestPermissionsIfNeeded()
            startActivity(Intent(this, ContactsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_pseudo_setup)
        val input = findViewById<EditText>(R.id.editPseudo)
        val button = findViewById<Button>(R.id.buttonContinue)

        button.setOnClickListener {
            val pseudo = input.text.toString().trim()
            if (pseudo.isEmpty()) {
                Toast.makeText(this, "Choisis un pseudo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsManager.setMyPseudo(this, pseudo)
            requestPermissionsIfNeeded()
            startActivity(Intent(this, ContactsActivity::class.java))
            finish()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }
}
