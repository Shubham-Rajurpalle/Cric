package com.cricketApp.cric.Chat

data class ChatMessage(
    var id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val team: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    var reactions: MutableMap<String, Int> = mutableMapOf<String, Int>().apply {
        put("fire", 0)
        put("laugh", 0)
        put("cry", 0)
        put("troll", 0)
    },
    var hit: Int = 0,
    var miss: Int = 0,
    var comments: MutableList<CommentMessage> = mutableListOf()
)