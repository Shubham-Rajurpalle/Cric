package com.cricketApp.cric.Chat

data class CommentMessage(
    var id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val reactions: MutableMap<String, Int> = mutableMapOf(
        "fire" to 0,
        "laugh" to 0,
        "cry" to 0,
        "troll" to 0
    ),
    val hit: Int = 0,
    val miss: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)