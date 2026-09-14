package com.bubblesms.app

import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.telephony.SmsManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.ChatMessage
import com.bubblesms.app.data.PrefsManager
import java.io.File

class ChatActivity : AppCompatActivity() {
    private lateinit var phone: String
    private lateinit var pseudo: String
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var wallpaper: ImageView
    private lateinit var wallpaperOverlay: View

    private val pickAttachment = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleAttachment(uri)
    }
    private val pickWallpaper = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && PrefsManager.saveChatWallpaper(this, phone, uri) != null) applyWallpaper()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val incomingPhone = intent.getStringExtra(EXTRA_PHONE) ?: return
            if (PrefsManager.normalizePhone(incomingPhone) == PrefsManager.normalizePhone(phone)) loadMessages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        phone = intent.getStringExtra("phone") ?: ""
        pseudo = intent.getStringExtra("pseudo") ?: phone

        wallpaper = findViewById(R.id.chatWallpaper)
        wallpaperOverlay = findViewById(R.id.chatWallpaperOverlay)
        findViewById<TextView>(R.id.chatTitle).text = pseudo
        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonCall).setOnClickListener { startPhoneCall() }
        findViewById<ImageButton>(R.id.buttonVideo).setOnClickListener { openLocalCall(false) }
        findViewById<ImageButton>(R.id.buttonMore).setOnClickListener { showChatOptions() }
        findViewById<ImageButton>(R.id.buttonAttach).setOnClickListener { pickAttachment.launch(arrayOf("image/*","video/*","audio/*","application/pdf","text/*","application/zip","image/gif")) }

        val quickPanel=findViewById<LinearLayout>(R.id.quickPanel)
        findViewById<ImageButton>(R.id.buttonSticker).setOnClickListener { quickPanel.visibility=if(quickPanel.visibility==View.VISIBLE) View.GONE else View.VISIBLE }
        listOf(R.id.sticker1,R.id.sticker2,R.id.sticker3,R.id.sticker4,R.id.sticker5).forEach { id ->
            findViewById<TextView>(id).setOnClickListener { sendText((it as TextView).text.toString()); quickPanel.visibility=View.GONE }
        }

        findViewById<ImageButton>(R.id.buttonBlock).setOnClickListener { showBlockDialog() }
        recycler = findViewById(R.id.recyclerMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(mutableListOf()) { position -> showMessageActions(position) }
        recycler.adapter = adapter

        val input=findViewById<EditText>(R.id.editMessage)
        findViewById<ImageButton>(R.id.buttonSend).setOnClickListener {
            val text=input.text.toString().trim()
            if(text.isNotEmpty()){ sendText(text); input.setText("") }
        }
        applyWallpaper()
        loadMessages()
    }

    private fun sendText(text:String){
        if(PrefsManager.isBlocked(this,phone)){ Toast.makeText(this,"Utilisateur bloqué",Toast.LENGTH_SHORT).show(); return }
        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){ Toast.makeText(this,"Autorise l'envoi de SMS",Toast.LENGTH_LONG).show(); return }
        val fullBody="BUB::MSG::${PrefsManager.getMyPseudo(this) ?: "Moi"}::$text"
        val sms=SmsManager.getDefault(); val parts=sms.divideMessage(fullBody)
        sms.sendMultipartTextMessage(phone,null,parts,null,null)
        PrefsManager.addMessage(this,phone,ChatMessage(text,true,System.currentTimeMillis()))
        loadMessages()
    }

    private fun handleAttachment(uri:Uri){
        // Les médias ne passent pas par SMS : on ouvre le transport local Wi‑Fi.
        startActivity(Intent(this, LocalShareActivity::class.java).apply {
            putExtra("selected_uri", uri.toString())
            putExtra("chat_phone", phone)
            putExtra("chat_pseudo", pseudo)
        })
    }

    private fun queryName(uri:Uri):String?=try{contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else null}}catch(_:Exception){null}

    private fun startPhoneCall(){
        val intent=Intent(Intent.ACTION_DIAL,Uri.parse("tel:${phone.replace("#","%23")}"))
        startActivity(intent)
    }

    private fun showChatOptions(){
        val items=arrayOf("🎨 Personnaliser le fond","🔄 Fond noir","📷 Choisir une image","📞 Appel audio (réseau opérateur)","📹 Appel vidéo local (Wi‑Fi)","📎 Partage local Wi‑Fi")
        AlertDialog.Builder(this).setTitle("Options de $pseudo").setItems(items){_,which->
            when(which){
                0->showWallpaperColors()
                1->{PrefsManager.setChatWallpaper(this,phone,null);applyWallpaper()}
                2->pickWallpaper.launch("image/*")
                3->startPhoneCall()
                4->openLocalCall(false)
                5->startActivity(Intent(this, LocalShareActivity::class.java))
            }
        }.show()
    }

    private fun openLocalCall(group: Boolean){
        startActivity(Intent(this, CallActivity::class.java).putExtra("group", group))
    }

    private fun showWallpaperColors(){
        val names=arrayOf("Noir","Gris","Bleu nuit","Violet","Rouge sombre","Vert sombre")
        val values=arrayOf("#090909","#202020","#101827","#24132F","#2A1014","#10251C")
        AlertDialog.Builder(this).setTitle("Fond de conversation").setItems(names){_,i->PrefsManager.setChatWallpaper(this,phone,"color:${values[i]}");applyWallpaper()}.show()
    }

    private fun applyWallpaper(){
        val value=PrefsManager.getChatWallpaper(this,phone)
        if(value.isNullOrBlank()){
            wallpaper.visibility=View.GONE; wallpaperOverlay.visibility=View.GONE
            findViewById<View>(R.id.chatRoot).setBackgroundColor(Color.parseColor("#090909")); return
        }
        if(value.startsWith("color:")){
            wallpaper.visibility=View.GONE; wallpaperOverlay.visibility=View.GONE
            findViewById<View>(R.id.chatRoot).setBackgroundColor(Color.parseColor(value.removePrefix("color:")))
        }else{
            wallpaper.setImageURI(Uri.fromFile(File(value))); wallpaper.visibility=View.VISIBLE; wallpaperOverlay.visibility=View.VISIBLE
        }
    }

    private fun showBlockDialog(){
        val blocked=PrefsManager.isBlocked(this,phone); val action=if(blocked)"débloquer" else "bloquer"
        AlertDialog.Builder(this).setTitle(if(blocked)"Débloquer $pseudo ?" else "Bloquer $pseudo ?").setNegativeButton("Annuler",null).setPositiveButton(action.replaceFirstChar{it.uppercase()}){_,_->PrefsManager.setBlocked(this,phone,!blocked);Toast.makeText(this,if(blocked)"Utilisateur débloqué" else "Utilisateur bloqué",Toast.LENGTH_SHORT).show()}.show()
    }

    private fun showMessageActions(position:Int){
        val messages=PrefsManager.getMessages(this,phone); if(position !in messages.indices)return
        val current=messages[position]
        val options=arrayOf("↩ Répondre","❤ Réagir","📌 Épingler / désépingler","📋 Copier","🗑 Supprimer","🔕 Sourdine")
        AlertDialog.Builder(this).setTitle("Message").setItems(options){_,which->when(which){
            0->findViewById<EditText>(R.id.editMessage).setText("↩ ${current.body} — ")
            1->{val r=arrayOf("❤","😂","👍","😮","😢","🔥");AlertDialog.Builder(this).setTitle("Réaction").setItems(r){_,i->PrefsManager.updateMessage(this,phone,position,current.copy(reaction=r[i]));loadMessages()}.show()}
            2->{PrefsManager.updateMessage(this,phone,position,current.copy(pinned=!current.pinned));loadMessages()}
            3->{val cm=getSystemService(android.content.ClipboardManager::class.java);cm.setPrimaryClip(android.content.ClipData.newPlainText("Message",current.body));Toast.makeText(this,"Copié",Toast.LENGTH_SHORT).show()}
            4->{PrefsManager.deleteMessage(this,phone,position);loadMessages()}
            5->{val muted=!PrefsManager.isMuted(this,phone);PrefsManager.setMuted(this,phone,muted);Toast.makeText(this,if(muted)"Conversation en sourdine" else "Notifications réactivées",Toast.LENGTH_SHORT).show()}
        }}.show()
    }

    private fun loadMessages(){val messages=PrefsManager.getMessages(this,phone);adapter.update(messages);if(messages.isNotEmpty())recycler.scrollToPosition(messages.size-1)}
    override fun onResume(){super.onResume();LocalBroadcastManager.getInstance(this).registerReceiver(receiver,IntentFilter(ACTION_NEW_MESSAGE));loadMessages()}
    override fun onPause(){super.onPause();LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)}
}
