package com.cricketApp.cric.Chat

data class PollMessage(
    var id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val team: String = "",
    val question: String = "",
    val options: MutableMap<String, Int> = mutableMapOf(),
    var reactions: MutableMap<String, Int> = mutableMapOf(
        "fire" to 0,
        "laugh" to 0,
        "cry" to 0,
        "troll" to 0
    ),
    var hit: Int = 0,
    var miss: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    var comments: MutableList<CommentMessage> = mutableListOf()
)