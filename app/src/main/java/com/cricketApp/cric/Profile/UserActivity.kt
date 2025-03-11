package com.cricketApp.cric.Profile

/**
 * Enum representing the different types of activities that can be displayed in the profile
 */
enum class UserActivityType {
    CHAT,      // User posted a text message in chat
    MEME,      // User posted a meme
    POLL,      // User created a poll
    COMMENT    // User commented on a chat/meme/poll
}

/**
 * Data class representing a user activity for display in the profile activity feed
 */
data class UserActivity(
    val id: String,                           // Unique ID of the activity
    val type: UserActivityType,               // Type of activity
    val username: String,                     // User's display name
    val userId: String = "",                  // User's ID (for loading profile picture)
    val team: String,                         // User's team
    val content: String,                      // Content text
    val imageUrl: String = "",                // URL of image (if any)
    val timestamp: Long,                      // When the activity happened
    val hits: Int = 0,                        // Number of hits on this activity
    val misses: Int = 0,                      // Number of misses on this activity
    val reactions: Map<String, Int>? = null,  // Reactions to this activity
    val additionalData: Map<String, Any>? = null // Additional data specific to activity type
)