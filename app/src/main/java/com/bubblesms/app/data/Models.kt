package com.bubblesms.app.data

data class Friend(
    val phone: String,
    var pseudo: String,
    var confirmed: Boolean = false
)

data class ChatMessage(
    val body: String,
    val isSent: Boolean,
    val timestamp: Long
)
