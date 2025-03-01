package com.cricketApp.cric.Chat

data class ChatMessage(
    var id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val team: String = "",
    val message: String = "",
    val type: String = "text", // "text" or "poll"
    val reactions: MutableMap<String, Int> = mutableMapOf(
        "fire" to 0,
        "laugh" to 0,
        "cry" to 0,
        "troll" to 0
    ),
    val hit: Int = 0,
    val miss: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val comments: MutableMap<String, CommentMessage> = mutableMapOf()
)