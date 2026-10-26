package com.bubblesms.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bubblesms.app.data.PrefsManager
import com.google.android.material.imageview.ShapeableImageView

class PseudoSetupActivity : AppCompatActivity() {

    private lateinit var avatarView: ShapeableImageView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = PrefsManager.saveProfilePicture(this, it)
            if (path != null) {
                avatarView.setImageDrawable(null)
                avatarView.setImageURI(Uri.fromFile(java.io.File(path)))
            } else {
                Toast.makeText(this, "Impossible de charger cette image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si un pseudo existe déjà, on saute directement à la liste des amis
        val existing = PrefsManager.getMyPseudo(this)
        if (existing != null) {
            val pin = PrefsManager.getAppPin(this)
            if (!pin.isNullOrBlank() && !AppLock.unlocked) {
                askPin(pin)
            } else {
                startActivity(Intent(this, ContactsActivity::class.java)); finish()
            }
            return
        }

        setContentView(R.layout.activity_pseudo_setup)
        val input = findViewById<EditText>(R.id.editPseudo)
        val button = findViewById<Button>(R.id.buttonContinue)
        avatarView = findViewById(R.id.imageProfile)

        avatarView.setOnClickListener {
            pickImage.launch("image/*")
        }

        button.setOnClickListener {
            val pseudo = input.text.toString().trim()
            if (pseudo.isEmpty()) {
                Toast.makeText(this, "Choisis un pseudo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsManager.setMyPseudo(this, pseudo)
            startActivity(Intent(this, ContactsActivity::class.java))
            finish()
        }
    }

    /**
     * Avant : un mauvais PIN fermait la boîte de dialogue et laissait un écran vide sans
     * moyen de réessayer (il fallait relancer l'appli). On réaffiche maintenant la demande.
     */
    private fun askPin(pin: String) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Code PIN"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("BubbleSMS verrouillé")
            .setMessage("Entre ton code pour continuer.")
            .setView(input)
            .setCancelable(false)
            .setNegativeButton("Quitter") { _, _ -> finish() }
            .setPositiveButton("Déverrouiller") { _, _ ->
                if (input.text.toString() == pin) {
                    AppLock.unlocked = true
                    startActivity(Intent(this, ContactsActivity::class.java)); finish()
                } else {
                    Toast.makeText(this, "Code incorrect", Toast.LENGTH_SHORT).show()
                    askPin(pin)
                }
            }.show()
    }
}

/** Mémorise (tant que le processus vit) que le PIN a été saisi. */
object AppLock {
    @Volatile var unlocked = false
}
