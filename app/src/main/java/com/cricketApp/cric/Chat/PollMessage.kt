package com.cricketApp.cric.Chat

data class PollMessage(
    var id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val team: String = "",
    val question: String = "",
    var options: MutableMap<String, Int> = mutableMapOf(),
    var reactions: MutableMap<String, Int> = mutableMapOf(
        "fire" to 0,
        "laugh" to 0,
        "cry" to 0,
        "troll" to 0
    ),
    var hit: Int = 0,
    var miss: Int = 0,
    var commentCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    var comments: MutableList<CommentMessage> = mutableListOf(),
    var voters: MutableMap<String, String>? = mutableMapOf() // Maps userId to selected option
) {
    // Add copy function for creating a local working copy
    fun copy(): PollMessage {
        return PollMessage(
            id = this.id,
            senderId = this.senderId,
            senderName = this.senderName,
            team = this.team,
            question = this.question,
            options = this.options.toMutableMap(),
            reactions = this.reactions.toMutableMap(),
            hit = this.hit,
            miss = this.miss,
            commentCount = this.commentCount,
            timestamp = this.timestamp,
            comments = this.comments.toMutableList(),
            voters = this.voters?.toMutableMap()
        )
    }
}