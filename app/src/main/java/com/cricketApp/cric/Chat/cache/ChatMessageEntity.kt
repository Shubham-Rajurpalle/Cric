package com.cricketApp.cric.Chat.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cricketApp.cric.Chat.ChatMessage
import com.cricketApp.cric.Chat.CommentMessage
import com.cricketApp.cric.Chat.PollMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ─────────────────────────────────────────────────────────────────────────────
// Type Converters — shared by both entities
// ─────────────────────────────────────────────────────────────────────────────

class ChatTypeConverters {
    private val gson = Gson()

    // Handles ALL MutableMap<String, Int> fields (reactions, options, etc.)
    @TypeConverter fun fromStringIntMap(v: MutableMap<String, Int>): String = gson.toJson(v)
    @TypeConverter fun toStringIntMap(v: String): MutableMap<String, Int> {
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        return gson.fromJson(v, type) ?: mutableMapOf()
    }

    @TypeConverter fun fromCommentList(v: MutableList<CommentMessage>): String = gson.toJson(v)
    @TypeConverter fun toCommentList(v: String): MutableList<CommentMessage> {
        val type = object : TypeToken<MutableList<CommentMessage>>() {}.type
        return gson.fromJson(v, type) ?: mutableListOf()
    }

    @TypeConverter fun fromStringStringMap(v: MutableMap<String, String>): String = gson.toJson(v)
    @TypeConverter fun toStringStringMap(v: String): MutableMap<String, String> {
        val type = object : TypeToken<MutableMap<String, String>>() {}.type
        return gson.fromJson(v, type) ?: mutableMapOf()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChatMessageEntity
// roomId:    "global" | "CSK" | "<liveRoomId>"
// filterKey: "ALL" | "TOP_HIT" | "TOP_MISS" | "TEAM_CSK" | "POLLS_ONLY"
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "chat_messages")
@TypeConverters(ChatTypeConverters::class)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val filterKey: String,
    val senderId: String,
    val senderName: String,
    val team: String,
    val message: String,
    val imageUrl: String,
    val timestamp: Long,
    val hit: Int,
    val miss: Int,
    val commentCount: Int,
    val reactions: MutableMap<String, Int>,
    val comments: MutableList<CommentMessage>,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toChatMessage() = ChatMessage(
        id           = id,
        senderId     = senderId,
        senderName   = senderName,
        team         = team,
        message      = message,
        imageUrl     = imageUrl,
        timestamp    = timestamp,
        hit          = hit,
        miss         = miss,
        commentCount = commentCount,
        reactions    = reactions,
        comments     = comments
    )

    companion object {
        fun from(msg: ChatMessage, roomId: String, filterKey: String) = ChatMessageEntity(
            id           = msg.id,
            roomId       = roomId,
            filterKey    = filterKey,
            senderId     = msg.senderId,
            senderName   = msg.senderName,
            team         = msg.team,
            message      = msg.message,
            imageUrl     = msg.imageUrl,
            timestamp    = msg.timestamp,
            hit          = msg.hit,
            miss         = msg.miss,
            commentCount = msg.commentCount,
            reactions    = msg.reactions,
            comments     = msg.comments,
            cachedAt     = System.currentTimeMillis()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PollEntity
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "poll_messages")
@TypeConverters(ChatTypeConverters::class)
data class PollEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val filterKey: String,
    val senderId: String,
    val senderName: String,
    val team: String,
    val question: String,
    val timestamp: Long,
    val hit: Int,
    val miss: Int,
    val commentCount: Int,
    val reactions: MutableMap<String, Int>,
    val options: MutableMap<String, Int>,
    val voters: MutableMap<String, String>,
    val comments: MutableList<CommentMessage>,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toPollMessage() = PollMessage(
        id           = id,
        senderId     = senderId,
        senderName   = senderName,
        team         = team,
        question     = question,
        timestamp    = timestamp,
        hit          = hit,
        miss         = miss,
        commentCount = commentCount,
        reactions    = reactions,
        options      = options,
        voters       = voters,
        comments     = comments
    )

    companion object {
        fun from(poll: PollMessage, roomId: String, filterKey: String) = PollEntity(
            id           = poll.id,
            roomId       = roomId,
            filterKey    = filterKey,
            senderId     = poll.senderId,
            senderName   = poll.senderName,
            team         = poll.team,
            question     = poll.question,
            timestamp    = poll.timestamp,
            hit          = poll.hit,
            miss         = poll.miss,
            commentCount = poll.commentCount,
            reactions    = poll.reactions,
            options      = poll.options,
            voters       = poll.voters ?: mutableMapOf(),
            comments     = poll.comments,
            cachedAt     = System.currentTimeMillis()
        )
    }
}