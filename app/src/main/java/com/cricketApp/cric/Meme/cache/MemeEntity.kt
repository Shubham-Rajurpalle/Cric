package com.cricketApp.cric.Meme.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cricketApp.cric.Chat.CommentMessage
import com.cricketApp.cric.Meme.MemeMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ─────────────────────────────────────────────────────────────────────────────
// Type Converters
// ─────────────────────────────────────────────────────────────────────────────

class MemeTypeConverters {
    private val gson = Gson()

    // reactions: MutableMap<String, Int> ↔ JSON string
    @TypeConverter
    fun fromReactionsMap(value: MutableMap<String, Int>): String =
        gson.toJson(value)

    @TypeConverter
    fun toReactionsMap(value: String): MutableMap<String, Int> {
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        return gson.fromJson(value, type) ?: mutableMapOf(
            "fire" to 0, "laugh" to 0, "cry" to 0, "troll" to 0
        )
    }

    // comments: MutableList<CommentMessage> ↔ JSON string
    @TypeConverter
    fun fromCommentList(value: MutableList<CommentMessage>): String =
        gson.toJson(value)

    @TypeConverter
    fun toCommentList(value: String): MutableList<CommentMessage> {
        val type = object : TypeToken<MutableList<CommentMessage>>() {}.type
        return gson.fromJson(value, type) ?: mutableListOf()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entity  — mirrors MemeMessage exactly (no caption field)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "memes")
@TypeConverters(MemeTypeConverters::class)
data class MemeEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val team: String,
    val memeUrl: String,
    val timestamp: Long,
    val hit: Int,
    val miss: Int,
    val commentCount: Int,
    val reactions: MutableMap<String, Int>,
    val comments: MutableList<CommentMessage>,
    /** Which filter bucket this was fetched under — e.g. "ALL", "TEAM_CSK", "TOP_HIT" */
    val filterKey: String,
    /** Epoch millis when this row was cached — used for TTL eviction */
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toMemeMessage(): MemeMessage = MemeMessage(
        id           = id,
        senderId     = senderId,
        senderName   = senderName,
        team         = team,
        memeUrl      = memeUrl,
        timestamp    = timestamp,
        hit          = hit,
        miss         = miss,
        commentCount = commentCount,
        reactions    = reactions,
        comments     = comments
    )

    companion object {
        fun fromMemeMessage(meme: MemeMessage, filterKey: String) = MemeEntity(
            id           = meme.id,
            senderId     = meme.senderId,
            senderName   = meme.senderName,
            team         = meme.team,
            memeUrl      = meme.memeUrl,
            timestamp    = meme.timestamp,
            hit          = meme.hit,
            miss         = meme.miss,
            commentCount = meme.commentCount,
            reactions    = meme.reactions,
            comments     = meme.comments,
            filterKey    = filterKey,
            cachedAt     = System.currentTimeMillis()
        )
    }
}