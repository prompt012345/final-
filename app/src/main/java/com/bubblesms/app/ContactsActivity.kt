package com.bubblesms.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        friendsRecycler = findViewById(R.id.recyclerFriends)
        requestsRecycler = findViewById(R.id.recyclerRequests)
        requestsLabel = findViewById(R.id.labelRequests)
        avatarView = findViewById(R.id.imageMyProfile)
        friendsRecycler.layoutManager = LinearLayoutManager(this)
        requestsRecycler.layoutManager = LinearLayoutManager(this)

        avatarView.setOnClickListener {
            pickImage.launch("image/*")
        }

        val fab = findViewById<FloatingActionButton>(R.id.fabAddFriend)
        fab.setOnClickListener {
            startActivity(Intent(this, AddFriendActivity::class.java))
        }

        loadProfilePicture()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
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
        friendsRecycler.adapter = FriendAdapter(friends) { friend ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("phone", friend.phone)
            intent.putExtra("pseudo", friend.pseudo)
            startActivity(intent)
        }

        val requests = PrefsManager.getPendingRequests(this)
        requestsLabel.visibility = if (requests.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        requestsRecycler.visibility = requestsLabel.visibility
        requestsRecycler.adapter = RequestAdapter(requests) { req ->
            PrefsManager.upsertFriend(this, com.bubblesms.app.data.Friend(req.phone, req.pseudo, confirmed = true))
            PrefsManager.removePendingRequest(this, req.phone)

            if (hasSendSmsPermission()) {
                val myPseudo = PrefsManager.getMyPseudo(this) ?: "Moi"
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(req.phone, null, "BUB::ACC::$myPseudo", null, null)
            } else {
                Toast.makeText(this, "Autorise l'envoi de SMS pour confirmer l'ami", Toast.LENGTH_LONG).show()
            }

            refresh()
        }
    }
}
