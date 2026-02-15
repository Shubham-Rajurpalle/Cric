package com.cricketApp.cric.Notifications

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class for notification items
 */
data class NotificationItem(
    val id: String,
    val contentType: String,
    val contentId: String,
    val senderName: String,
    val team: String,
    val message: String,
    val reactionCategory: String,
    val reactionValue: String,
    val count: Int,
    val timestamp: Long,
    var read: Boolean
) {
    fun getFormattedTimestamp(): String {
        val now = System.currentTimeMillis()
        return when {
            DateUtils.isToday(timestamp) -> {
                // If it's today, show time only
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
            now - timestamp < 24 * 60 * 60 * 1000 -> {
                // If within the last 24 hours but not today
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Yesterday, ${timeFormat.format(Date(timestamp))}"
            }
            else -> {
                // For older notifications
                SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    fun getNotificationText(): String {
        return when (contentType) {
            "CHAT" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s message received $count ${reactionValue.capitalize()}"
                    "reaction" -> "${senderName}'s message got $count ${getEmojiName(reactionValue)} reactions"
                    else -> "${senderName}'s message is trending!"
                }
            }
            "POLL" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s poll received $count ${reactionValue.capitalize()}"
                    "reaction" -> "${senderName}'s poll got $count ${getEmojiName(reactionValue)} reactions"
                    else -> "${senderName}'s poll is trending!"
                }
            }
            "MEME" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s meme received $count ${reactionValue.capitalize()}"
                    "reaction" -> "${senderName}'s meme got $count ${getEmojiName(reactionValue)} reactions"
                    else -> "${senderName}'s meme is trending!"
                }
            }
            "COMMENT" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s comment received $count ${reactionValue.capitalize()}"
                    "reaction" -> "${senderName}'s comment got $count ${getEmojiName(reactionValue)} reactions"
                    else -> "${senderName}'s comment is trending!"
                }
            }
            else -> "Content is trending!"
        }
    }

    fun getTypeIcon(): Int {
        return when (contentType) {
            "CHAT" -> R.drawable.chat_icon
            "POLL" -> R.drawable.poll_btn
            "MEME" -> R.drawable.meme_icon
            "COMMENT" -> R.drawable.comment_arrow
            else -> R.drawable.bell_icon
        }
    }

    fun getReactionIcon(): Int {
        return when {
            reactionCategory == "hitMiss" && reactionValue == "hit" -> R.drawable.ic_hit
            reactionCategory == "hitMiss" && reactionValue == "miss" -> R.drawable.ic_miss
            reactionCategory == "reaction" && reactionValue == "fire" -> R.drawable.ic_fire
            reactionCategory == "reaction" && reactionValue == "laugh" -> R.drawable.ic_laugh
            reactionCategory == "reaction" && reactionValue == "cry" -> R.drawable.ic_cry
            reactionCategory == "reaction" && reactionValue == "troll" -> R.drawable.ic_broken_heart_emoji
            else -> R.drawable.ic_trending
        }
    }

    private fun getEmojiName(reactionType: String): String {
        return when (reactionType) {
            "fire" -> "Fire"
            "laugh" -> "Laugh"
            "cry" -> "Crying"
            "troll" -> "Broken Heart"
            else -> reactionType.capitalize()
        }
    }
}

