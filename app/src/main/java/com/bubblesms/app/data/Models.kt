package com.bubblesms.app.data

data class Friend(val phone: String, var pseudo: String, var confirmed: Boolean = false)
data class ChatMessage(val body: String, val isSent: Boolean, val timestamp: Long, val reaction: String = "", val pinned: Boolean = false)
data class Group(val id: String, var name: String, val members: MutableList<Friend> = mutableListOf(), var creatorPhone: String = "")
data class GroupMessage(val groupId: String, val body: String, val senderPseudo: String, val isSent: Boolean, val timestamp: Long, val reaction: String = "", val pinned: Boolean = false)
