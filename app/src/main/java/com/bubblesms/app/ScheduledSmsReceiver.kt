package com.bubblesms.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.bubblesms.app.data.ChatMessage
import com.bubblesms.app.data.PrefsManager

class ScheduledSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra("phone") ?: return
        val text = intent.getStringExtra("text") ?: return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        val body = "BUB::MSG::${PrefsManager.getMyPseudo(context) ?: "Moi"}::$text"
        SmsManager.getDefault().sendTextMessage(phone, null, body, null, null)
        PrefsManager.addMessage(context, phone, ChatMessage(text, true, System.currentTimeMillis()))
    }
}
