package com.bubblesms.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
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

        recycler = findViewById(R.id.recyclerMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(mutableListOf())
        recycler.adapter = adapter

        val input = findViewById<EditText>(R.id.editMessage)
        val sendButton = findViewById<Button>(R.id.buttonSend)

        sendButton.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

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

    private fun loadMessages() {
        val messages = PrefsManager.getMessages(this, phone)
        adapter.update(messages)
        if (messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.size - 1)
        }
    }
}
