package com.bubblesms.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.ImageButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.ChatMessage
import com.bubblesms.app.data.PrefsManager

class ChatActivity : AppCompatActivity() {

    private lateinit var phone: String
    private lateinit var pseudo: String
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessageAdapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val incomingPhone = intent.getStringExtra(EXTRA_PHONE) ?: return
            if (PrefsManager.normalizePhone(incomingPhone) == PrefsManager.normalizePhone(phone)) {
                loadMessages()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        phone = intent.getStringExtra("phone") ?: ""
        pseudo = intent.getStringExtra("pseudo") ?: phone

        findViewById<TextView>(R.id.chatTitle).text = pseudo
        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonBlock).setOnClickListener {
            val blocked = PrefsManager.isBlocked(this, phone)
            val action = if (blocked) "débloquer" else "bloquer"
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(if (blocked) "Débloquer $pseudo ?" else "Bloquer $pseudo ?")
                .setMessage(if (blocked) "Ses messages pourront à nouveau apparaître." else "Ses messages seront ignorés par BubbleSMS.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton(action.replaceFirstChar { it.uppercase() }) { _, _ ->
                    PrefsManager.setBlocked(this, phone, !blocked)
                    Toast.makeText(this, if (blocked) "Utilisateur débloqué" else "Utilisateur bloqué", Toast.LENGTH_SHORT).show()
                }.show()
        }

        recycler = findViewById(R.id.recyclerMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(mutableListOf()) { position -> showMessageActions(position) }
        recycler.adapter = adapter

        val input = findViewById<EditText>(R.id.editMessage)
        val sendButton = findViewById<ImageButton>(R.id.buttonSend)

        sendButton.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (PrefsManager.isBlocked(this, phone)) { Toast.makeText(this, "Utilisateur bloqué", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Autorise l'envoi de SMS dans les paramètres du téléphone", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val myPseudo = PrefsManager.getMyPseudo(this) ?: "Moi"
            val fullBody = "BUB::MSG::$myPseudo::$text"

            val smsManager = SmsManager.getDefault()
            // Découpe automatique si le message est long (multi-SMS)
            val parts = smsManager.divideMessage(fullBody)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            PrefsManager.addMessage(this, phone, ChatMessage(text, isSent = true, timestamp = System.currentTimeMillis()))
            input.setText("")
            loadMessages()
        }

        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(ACTION_NEW_MESSAGE))
        loadMessages()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    private fun showMessageActions(position: Int) {
        val messages = PrefsManager.getMessages(this, phone)
        if (position !in messages.indices) return
        val current = messages[position]
        val options = arrayOf("↩ Répondre", "❤ Réagir", "📌 Épingler / désépingler", "📋 Copier", "🗑 Supprimer", "🔕 Sourdine")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Message").setItems(options) { _, which ->
            when (which) {
                0 -> findViewById<EditText>(R.id.editMessage).setText("↩ ${current.body} — ")
                1 -> { val r=arrayOf("❤","😂","👍","😮","😢","🔥"); androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Réaction").setItems(r){_,i->PrefsManager.updateMessage(this,phone,position,current.copy(reaction=r[i]));loadMessages()}.show() }
                2 -> { PrefsManager.updateMessage(this,phone,position,current.copy(pinned=!current.pinned));loadMessages() }
                3 -> { val cm=getSystemService(android.content.ClipboardManager::class.java);cm.setPrimaryClip(android.content.ClipData.newPlainText("Message",current.body));Toast.makeText(this,"Copié",Toast.LENGTH_SHORT).show() }
                4 -> { PrefsManager.deleteMessage(this,phone,position);loadMessages() }
                5 -> { val muted=!PrefsManager.isMuted(this,phone);PrefsManager.setMuted(this,phone,muted);Toast.makeText(this,if(muted)"Conversation en sourdine" else "Notifications réactivées",Toast.LENGTH_SHORT).show() }
            }
        }.show()
    }

    private fun loadMessages() {
        val messages = PrefsManager.getMessages(this, phone)
        adapter.update(messages)
        if (messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.size - 1)
        }
    }
}
