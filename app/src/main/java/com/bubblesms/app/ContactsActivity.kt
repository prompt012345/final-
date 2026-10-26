package com.bubblesms.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.PrefsManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import java.io.File

class ContactsActivity : AppCompatActivity() {

    private lateinit var friendsRecycler: RecyclerView
    private lateinit var requestsRecycler: RecyclerView
    private lateinit var requestsLabel: TextView
    private lateinit var avatarView: ShapeableImageView
    private lateinit var groupsRecycler: RecyclerView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = PrefsManager.saveProfilePicture(this, it)
            if (path != null) {
                loadProfilePicture()
            } else {
                Toast.makeText(this, "Impossible de charger cette image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Rafraîchit la liste (nouvelle demande d'ami, ami confirmé, groupe reçu...) sans relancer l'appli
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (PrefsManager.isContactsKey(key)) refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Le verrou PIN ne doit pas pouvoir être contourné en ouvrant l'appli depuis une notification
        val pin = PrefsManager.getAppPin(this)
        if (!pin.isNullOrBlank() && !AppLock.unlocked) {
            startActivity(Intent(this, PseudoSetupActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_contacts)

        friendsRecycler = findViewById(R.id.recyclerFriends)
        requestsRecycler = findViewById(R.id.recyclerRequests)
        groupsRecycler = findViewById(R.id.recyclerGroups)
        requestsLabel = findViewById(R.id.labelRequests)
        avatarView = findViewById(R.id.imageMyProfile)
        friendsRecycler.layoutManager = LinearLayoutManager(this)
        requestsRecycler.layoutManager = LinearLayoutManager(this)
        groupsRecycler.layoutManager = LinearLayoutManager(this)

        avatarView.setOnClickListener {
            pickImage.launch("image/*")
        }

        val fab = findViewById<FloatingActionButton>(R.id.fabAddFriend)
        fab.setOnClickListener {
            startActivity(Intent(this, AddFriendActivity::class.java))
        }

        requestMissingPermissions()
        findViewById<FloatingActionButton>(R.id.fabAddGroup).setOnClickListener { startActivity(Intent(this, GroupCreateActivity::class.java)) }
        findViewById<TextView>(R.id.buttonSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<EditText>(R.id.searchInput).addTextChangedListener(object: TextWatcher { override fun beforeTextChanged(s: CharSequence?, st:Int,c:Int,a:Int){} override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){ filterConversations(s?.toString().orEmpty()) } override fun afterTextChanged(e:Editable?){}})

        loadProfilePicture()
        findViewById<TextView>(R.id.myStatusText).text = "${PrefsManager.getStatus(this)} • Conversations privées"
        refresh()
    }

    override fun onResume() {
        super.onResume()
        PrefsManager.registerListener(this, prefsListener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        PrefsManager.unregisterListener(this, prefsListener)
    }

    /**
     * Demandé ici (et plus dans l'écran de pseudo, qui se fermait aussitôt) : sans RECEIVE_SMS
     * l'appli ne reçoit aucun message, sans SEND_SMS elle ne peut rien envoyer.
     */
    private fun requestMissingPermissions() {
        val wanted = mutableListOf(android.Manifest.permission.SEND_SMS, android.Manifest.permission.RECEIVE_SMS)
        if (android.os.Build.VERSION.SDK_INT >= 33) wanted.add(android.Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) androidx.core.app.ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 100) return
        val smsDenied = permissions.indices.any { i ->
            (permissions[i] == android.Manifest.permission.SEND_SMS || permissions[i] == android.Manifest.permission.RECEIVE_SMS) &&
                grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED
        }
        if (smsDenied) Toast.makeText(this, "Sans la permission SMS, BubbleSMS ne peut ni envoyer ni recevoir de messages.", Toast.LENGTH_LONG).show()
    }

    private fun loadProfilePicture() {
        val path = PrefsManager.getProfilePicturePath(this) ?: return
        val file = File(path)
        if (file.exists()) {
            avatarView.setImageDrawable(null)
            avatarView.setImageURI(Uri.fromFile(file))
        }
    }

    /** Vérifie que la permission d'envoi de SMS est bien accordée avant d'appeler SmsManager. */
    private fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val friends = PrefsManager.getFriends(this)
        val groups = PrefsManager.getGroups(this)
        val query = findViewById<EditText>(R.id.searchInput).text.toString().trim().lowercase()
        val visibleGroups = if(query.isBlank()) groups else groups.filter { it.name.lowercase().contains(query) }
        val visibleFriends = if(query.isBlank()) friends else friends.filter { it.pseudo.lowercase().contains(query) || it.phone.contains(query) }
        groupsRecycler.adapter = GroupAdapter(visibleGroups) { g ->
            startActivity(Intent(this, GroupChatActivity::class.java).putExtra("groupId", g.id))
        }

        friendsRecycler.adapter = FriendAdapter(visibleFriends) { friend ->
            val intent = Intent(this, ChatActivity::class.java)
            if (PrefsManager.isChatLocked(this, friend.phone)) {
                val input=EditText(this); input.inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Conversation verrouillée").setMessage("Entre le PIN de l’application.").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Ouvrir"){_,_-> if(input.text.toString()==PrefsManager.getAppPin(this)){ startActivity(Intent(this,ChatActivity::class.java).putExtra("phone",friend.phone).putExtra("pseudo",friend.pseudo)) } else Toast.makeText(this,"PIN incorrect",Toast.LENGTH_SHORT).show() }.show()
            } else {
                intent.putExtra("phone", friend.phone)
                intent.putExtra("pseudo", friend.pseudo)
                startActivity(intent)
            }
        }

        val requests = PrefsManager.getPendingRequests(this)
        requestsLabel.visibility = if (requests.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        requestsRecycler.visibility = requestsLabel.visibility
        requestsRecycler.adapter = RequestAdapter(requests) { req ->
            PrefsManager.upsertFriend(this, com.bubblesms.app.data.Friend(req.phone, req.pseudo, confirmed = true))
            PrefsManager.removePendingRequest(this, req.phone)

            if (hasSendSmsPermission()) {
                val myPseudo = PrefsManager.getMyPseudo(this) ?: "Moi"
                BubbleSms.send(this, req.phone, "BUB::ACC::$myPseudo")
            } else {
                Toast.makeText(this, "Autorise l'envoi de SMS pour confirmer l'ami", Toast.LENGTH_LONG).show()
            }

            refresh()
        }
    }
    private fun filterConversations(query: String) {
        val q=query.trim().lowercase()
        val groups=PrefsManager.getGroups(this).filter{q.isBlank() || it.name.lowercase().contains(q)}
        val friends=PrefsManager.getFriends(this).filter{q.isBlank() || it.pseudo.lowercase().contains(q) || it.phone.contains(q)}
        groupsRecycler.adapter=GroupAdapter(groups){g->startActivity(Intent(this,GroupChatActivity::class.java).putExtra("groupId",g.id))}
        friendsRecycler.adapter=FriendAdapter(friends){f-> if(PrefsManager.isChatLocked(this,f.phone)){ val input=EditText(this); input.inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD; androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Conversation verrouillée").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Ouvrir"){_,_->if(input.text.toString()==PrefsManager.getAppPin(this))startActivity(Intent(this,ChatActivity::class.java).putExtra("phone",f.phone).putExtra("pseudo",f.pseudo)) else Toast.makeText(this,"PIN incorrect",Toast.LENGTH_SHORT).show()}.show() } else startActivity(Intent(this,ChatActivity::class.java).putExtra("phone",f.phone).putExtra("pseudo",f.pseudo))}
    }

}
