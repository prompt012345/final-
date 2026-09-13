package com.bubblesms.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bubblesms.app.data.Friend
import com.bubblesms.app.data.PrefsManager

class AddFriendActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friend)

        val phoneInput = findViewById<EditText>(R.id.editPhone)
        val pseudoInput = findViewById<EditText>(R.id.editFriendPseudo)
        val button = findViewById<Button>(R.id.buttonSendRequest)

        button.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val pseudo = pseudoInput.text.toString().trim()

            if (phone.isEmpty() || pseudo.isEmpty()) {
                Toast.makeText(this, "Remplis le numéro et le pseudo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Autorise l'envoi de SMS dans les paramètres du téléphone pour ajouter un ami", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val myPseudo = PrefsManager.getMyPseudo(this) ?: "Moi"
            PrefsManager.upsertFriend(this, Friend(phone, pseudo, confirmed = false))

            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, "BUB::REQ::$myPseudo", null, null)

            Toast.makeText(this, "Demande envoyée à $pseudo", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
