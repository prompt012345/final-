package com.bubblesms.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.bubblesms.app.data.Friend
import com.bubblesms.app.data.Group
import com.bubblesms.app.data.PrefsManager
import java.util.UUID

class GroupCreateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_create)
        val name = findViewById<EditText>(R.id.editGroupName)
        val friends = PrefsManager.getFriends(this).filter { it.confirmed && !PrefsManager.isBlocked(this, it.phone) }
        val list = findViewById<LinearLayout>(R.id.membersList)
        val checks = mutableListOf<Pair<Friend, CheckBox>>()
        friends.forEach { f ->
            val cb = CheckBox(this).apply { text = f.pseudo; setTextColor(androidx.core.content.ContextCompat.getColor(this@GroupCreateActivity, R.color.text_dark)); textSize = 15f; buttonTintList = null }
            list.addView(cb)
            checks.add(f to cb)
        }
        findViewById<Button>(R.id.buttonCreateGroup).setOnClickListener {
            val n = name.text.toString().trim().replace("::", " ")
            if (n.isEmpty()) { name.error = "Donne un nom au groupe"; return@setOnClickListener }
            val members = checks.filter { it.second.isChecked }.map { it.first }.toMutableList()
            // Identifiant stable (ne dépend pas du pseudo, qui peut changer) : sert à savoir
            // qu'on est bien l'admin de ce groupe, même après un changement de pseudo.
            val creatorId = "self:" + PrefsManager.getMyId(this)
            val group = Group(UUID.randomUUID().toString().take(8), n, members, creatorId)
            PrefsManager.saveGroup(this, group)
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val me = PrefsManager.getMyPseudo(this) ?: "Moi"
                val sms = SmsManager.getDefault()
                // Chaque destinataire reçoit la liste des AUTRES membres uniquement :
                // sinon il se retrouverait listé comme membre de son propre groupe,
                // et s'enverrait un SMS à lui-même en répondant.
                group.members.forEach { recipient ->
                    val others = group.members.filter {
                        PrefsManager.normalizePhone(it.phone) != PrefsManager.normalizePhone(recipient.phone)
                    }
                    val memberData = others.joinToString("|") { "${it.phone}~${it.pseudo.replace("|", " ")}" }
                    val body = "BUB::GNEW::${group.id}::$n::$me::$memberData"
                    sms.sendMultipartTextMessage(recipient.phone, null, sms.divideMessage(body), null, null)
                }
            }
            startActivity(Intent(this, GroupChatActivity::class.java).putExtra("groupId", group.id))
            finish()
        }
    }
}
