package com.bubblesms.app

import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.PrefsManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ContactsActivity : AppCompatActivity() {

    private lateinit var friendsRecycler: RecyclerView
    private lateinit var requestsRecycler: RecyclerView
    private lateinit var requestsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        friendsRecycler = findViewById(R.id.recyclerFriends)
        requestsRecycler = findViewById(R.id.recyclerRequests)
        requestsLabel = findViewById(R.id.labelRequests)
        friendsRecycler.layoutManager = LinearLayoutManager(this)
        requestsRecycler.layoutManager = LinearLayoutManager(this)

        val fab = findViewById<FloatingActionButton>(R.id.fabAddFriend)
        fab.setOnClickListener {
            startActivity(Intent(this, AddFriendActivity::class.java))
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

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

            val myPseudo = PrefsManager.getMyPseudo(this) ?: "Moi"
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(req.phone, null, "BUB::ACC::$myPseudo", null, null)

            refresh()
        }
    }
}
