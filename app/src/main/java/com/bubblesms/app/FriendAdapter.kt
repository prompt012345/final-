package com.bubblesms.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.Friend

class FriendAdapter(
    private val items: List<Friend>,
    private val onClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val pseudo: TextView = view.findViewById(R.id.itemPseudo)
        val status: TextView = view.findViewById(R.id.itemStatus)
        val avatar: TextView = view.findViewById(R.id.itemAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val friend = items[position]
        holder.pseudo.text = friend.pseudo
        holder.status.text = if (friend.confirmed) "" else "En attente d'acceptation…"
        holder.avatar.text = friend.pseudo.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.itemView.setOnClickListener { onClick(friend) }
    }

    override fun getItemCount() = items.size
}

class RequestAdapter(
    private val items: List<Friend>,
    private val onAccept: (Friend) -> Unit
) : RecyclerView.Adapter<RequestAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val pseudo: TextView = view.findViewById(R.id.reqPseudo)
        val accept: View = view.findViewById(R.id.buttonAccept)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = items[position]
        holder.pseudo.text = "${req.pseudo} veut t'ajouter"
        holder.accept.setOnClickListener { onAccept(req) }
    }

    override fun getItemCount() = items.size
}
