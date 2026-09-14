package com.bubblesms.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.GroupMessage
import com.bubblesms.app.data.PrefsManager

class GroupChatActivity : AppCompatActivity() {
    private lateinit var groupId: String
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: GroupMessageAdapter
    private var group = com.bubblesms.app.data.Group("", "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_chat)
        groupId = intent.getStringExtra("groupId") ?: ""
        group = PrefsManager.getGroup(this, groupId) ?: run { finish(); return }
        findViewById<TextView>(R.id.groupTitle).text = group.name
        findViewById<TextView>(R.id.groupMembers).text = "${group.members.size + 1} membres"
        recycler = findViewById(R.id.recyclerGroupMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = GroupMessageAdapter(mutableListOf()) { showMessageActions(it) }
        recycler.adapter = adapter
        findViewById<ImageButton>(R.id.buttonGroupBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonGroupInfo).setOnClickListener { showMembers() }
        findViewById<ImageButton>(R.id.buttonGroupVideo).setOnClickListener { startActivity(android.content.Intent(this, CallActivity::class.java).putExtra("group", true)) }
        val input = findViewById<EditText>(R.id.editGroupMessage)
        findViewById<ImageButton>(R.id.buttonGroupSend).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) { Toast.makeText(this, "Autorise les SMS pour envoyer", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val me = PrefsManager.getMyPseudo(this) ?: "Moi"
            val body = "BUB::GRP::$groupId::${group.name}::$me::$text"
            val sms = SmsManager.getDefault()
            group.members.filter { !PrefsManager.isBlocked(this, it.phone) }.forEach { member ->
                sms.sendMultipartTextMessage(member.phone, null, sms.divideMessage(body), null, null)
            }
            PrefsManager.addGroupMessage(this, GroupMessage(groupId, text, me, true, System.currentTimeMillis()))
            input.setText(""); load()
        }
        load()
    }
    private fun showMessageActions(position:Int){
        val list=PrefsManager.getGroupMessages(this,groupId);if(position !in list.indices)return;val m=list[position]
        val options=arrayOf("❤ Réagir","📌 Épingler / désépingler","📋 Copier","🗑 Supprimer")
        AlertDialog.Builder(this).setTitle("Message de ${m.senderPseudo}").setItems(options){_,w->when(w){
            0->{val r=arrayOf("❤","😂","👍","😮","😢","🔥");AlertDialog.Builder(this).setTitle("Réaction").setItems(r){_,i->PrefsManager.updateGroupMessage(this,groupId,position,m.copy(reaction=r[i]));load()}.show()}
            1->{PrefsManager.updateGroupMessage(this,groupId,position,m.copy(pinned=!m.pinned));load()}
            2->{val cm=getSystemService(android.content.ClipboardManager::class.java);cm.setPrimaryClip(android.content.ClipData.newPlainText("Message",m.body));Toast.makeText(this,"Copié",Toast.LENGTH_SHORT).show()}
            3->{PrefsManager.deleteGroupMessage(this,groupId,position);load()}
        }}.show()
    }

    private fun load() { val m=PrefsManager.getGroupMessages(this,groupId); adapter.update(m); if(m.isNotEmpty()) recycler.scrollToPosition(m.size-1) }
    private fun syncGroup(){
        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)return
        val me=PrefsManager.getMyPseudo(this) ?: "Moi"
        val sms=SmsManager.getDefault()
        // Comme à la création : chaque destinataire reçoit la liste des AUTRES membres,
        // jamais son propre numéro (sinon il s'enverrait des SMS à lui-même par la suite).
        group.members.forEach { recipient ->
            val others = group.members.filter {
                PrefsManager.normalizePhone(it.phone) != PrefsManager.normalizePhone(recipient.phone)
            }
            val data = others.joinToString("|"){ "${it.phone}~${it.pseudo.replace("|"," ")}" }
            val body = "BUB::GNEW::${group.id}::${group.name.replace("::"," ")}::$me::$data"
            sms.sendMultipartTextMessage(recipient.phone, null, sms.divideMessage(body), null, null)
        }
    }

    private fun showMembers() {
        val me = PrefsManager.getMyPseudo(this) ?: "Moi"
        val friends = PrefsManager.getFriends(this).filter { it.confirmed && !PrefsManager.isBlocked(this, it.phone) }
        val checked = friends.map { f -> group.members.any { PrefsManager.normalizePhone(it.phone) == PrefsManager.normalizePhone(f.phone) } }.toBooleanArray()
        val names = friends.map { it.pseudo }.toTypedArray()
        val isAdmin = group.creatorPhone.isBlank() || group.creatorPhone == "self:" + PrefsManager.getMyId(this)
        val choices = arrayOf("Gérer les membres", "Renommer le groupe", "Quitter le groupe")
        AlertDialog.Builder(this).setTitle("${group.name} • ${group.members.size + 1} membres").setItems(choices) { _, which ->
            when(which) {
                0 -> if(isAdmin) manageMembers(friends, checked, names) else Toast.makeText(this,"Seul l'admin peut gérer les membres",Toast.LENGTH_SHORT).show()
                1 -> if(isAdmin) renameGroup() else Toast.makeText(this,"Seul l'admin peut renommer le groupe",Toast.LENGTH_SHORT).show()
                2 -> { PrefsManager.deleteGroup(this, groupId); finish() }
            }
        }.show()
    }

    private fun manageMembers(friends: List<com.bubblesms.app.data.Friend>, checked: BooleanArray, names: Array<String>) {
        AlertDialog.Builder(this).setTitle("Membres").setMultiChoiceItems(names, checked){_,which,isChecked->checked[which]=isChecked}.setNegativeButton("Annuler",null).setPositiveButton("Enregistrer"){_,_->
            group.members.clear(); group.members.addAll(friends.filterIndexed{i,_->checked[i]})
            PrefsManager.saveGroup(this,group)
            syncGroup(); findViewById<TextView>(R.id.groupMembers).text="${group.members.size+1} membres"
        }.show()
    }

    private fun renameGroup() {
        val input=EditText(this); input.setText(group.name); input.setSingleLine(true)
        AlertDialog.Builder(this).setTitle("Nom du groupe").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer"){_,_->
            val n=input.text.toString().trim().replace("::", " "); if(n.isNotEmpty()){group.name=n;PrefsManager.saveGroup(this,group);syncGroup();findViewById<TextView>(R.id.groupTitle).text=n}
        }.show()
    }

}
