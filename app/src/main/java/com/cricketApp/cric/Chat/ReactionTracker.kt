package com.cricketApp.cric.Utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/**
 * Utility class to track and manage user reactions to content
 * Ensures users can only react once per content item with one emoji type
 * and one hit/miss vote (not both)
 */
object ReactionTracker {

    private const val TAG = "ReactionTracker"

    // Database path constants
    private const val USER_REACTIONS_PATH = "UserReactions"
    private const val NOTIFICATIONS_PATH = "Notifications"

    /**
     * Track different types of content in the app
     */
    enum class ContentType {
        CHAT, POLL, COMMENT, MEME
    }

    /**
     * Track different reaction types
     */
    enum class ReactionType {
        EMOJI, HIT_MISS
    }

    /**
     * Add an emoji reaction to content
     * @param contentType The type of content (chat, poll, comment, meme)
     * @param contentId The ID of the content
     * @param parentId The parent ID (for comments)
     * @param reactionType The type of emoji reaction (fire, laugh, cry, troll)
     * @param onComplete Callback with success status and the updated reaction count
     */
    fun addEmojiReaction(
        contentType: ContentType,
        contentId: String,
        parentId: String? = null,
        reactionType: String,
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        // Path to check if user has already reacted
        val userReactionsRef = FirebaseDatabase.getInstance().getReference(
            "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/emoji"
        )

        // First check if user has already reacted with an emoji
        userReactionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If snapshot exists, user has already reacted - get previous reaction
                val previousReaction = snapshot.getValue(String::class.java)

                if (previousReaction != null && previousReaction == reactionType) {
                    // User clicked the same reaction again - remove it
                    removeEmojiReaction(contentType, contentId, parentId, previousReaction) { success, newCount ->
                        onComplete(success, newCount)
                    }
                    return
                } else if (previousReaction != null) {
                    // User already reacted with a different emoji - remove previous and add new
                    removeEmojiReaction(contentType, contentId, parentId, previousReaction) { success, _ ->
                        if (success) {
                            // Now add the new reaction
                            processEmojiReaction(contentType, contentId, parentId, reactionType, userReactionsRef, onComplete)
                        } else {
                            onComplete(false, 0)
                        }
                    }
                    return
                }

                // User hasn't reacted before - add the new reaction
                processEmojiReaction(contentType, contentId, parentId, reactionType, userReactionsRef, onComplete)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error checking existing reaction: ${error.message}")
                onComplete(false, 0)
            }
        })
    }

    /**
     * Process adding an emoji reaction
     */
    private fun processEmojiReaction(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        reactionType: String,
        userReactionsRef: com.google.firebase.database.DatabaseReference,
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        // Get content reference based on type
        val contentRef = getReactionContentRef(contentType, contentId, parentId, "reactions/$reactionType")

        // Use transaction to ensure atomic update
        contentRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    // Save the user's reaction choice
                    userReactionsRef.setValue(reactionType)
                        .addOnSuccessListener {
                            val newValue = currentData.getValue(Int::class.java) ?: 0
                            onComplete(true, newValue)
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Error saving user reaction: ${it.message}")
                            onComplete(false, 0)
                        }
                } else {
                    Log.e(TAG, "Error updating reaction: ${error?.message}")
                    onComplete(false, 0)
                }

                // Check if notification should be created (100+ threshold)
                if (committed && error == null) {
                    val newValue = currentData?.getValue(Int::class.java) ?: 0
                    if (newValue == 100) { // Exactly 100 (not over) to avoid multiple notifications
                        createMilestoneNotification(contentType, contentId, parentId, "reaction", reactionType, newValue)
                    }
                }
            }
        })
    }

    /**
     * Remove an emoji reaction
     */
    private fun removeEmojiReaction(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        reactionType: String,
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        // Get content reference based on type
        val contentRef = getReactionContentRef(contentType, contentId, parentId, "reactions/$reactionType")

        // Use transaction to ensure atomic update
        contentRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                // Ensure we don't go below 0
                currentData.value = if (currentValue > 0) currentValue - 1 else 0
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    // Remove the user's reaction choice
                    val userReactionsRef = FirebaseDatabase.getInstance().getReference(
                        "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/emoji"
                    )

                    userReactionsRef.removeValue()
                        .addOnSuccessListener {
                            val newValue = currentData.getValue(Int::class.java) ?: 0
                            onComplete(true, newValue)
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Error removing user reaction: ${it.message}")
                            onComplete(false, 0)
                        }
                } else {
                    Log.e(TAG, "Error updating reaction: ${error?.message}")
                    onComplete(false, 0)
                }
            }
        })
    }

    /**
     * Update hit or miss count for content
     * @param contentType The type of content (chat, poll, comment, meme)
     * @param contentId The ID of the content
     * @param parentId The parent ID (for comments)
     * @param isHit True for hit, false for miss
     * @param onComplete Callback with success status and the updated hit/miss count
     */
    fun updateHitOrMiss(
        contentType: ContentType,
        contentId: String,
        parentId: String? = null,
        isHit: Boolean,
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        // Path to check if user has already rated with hit/miss
        val userReactionsRef = FirebaseDatabase.getInstance().getReference(
            "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/hitMiss"
        )

        // First check if user has already rated with hit/miss
        userReactionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val previousVote = snapshot.getValue(String::class.java)

                if (previousVote != null) {
                    val previousIsHit = previousVote == "hit"

                    if (previousIsHit == isHit) {
                        // User clicked the same vote again - remove it
                        removeHitOrMiss(contentType, contentId, parentId, previousIsHit) { success, newCount ->
                            onComplete(success, newCount)
                        }
                    } else {
                        // User voted the opposite - remove previous and add new
                        removeHitOrMiss(contentType, contentId, parentId, previousIsHit) { success, _ ->
                            if (success) {
                                // Now add the new vote
                                processHitOrMiss(contentType, contentId, parentId, isHit, userReactionsRef, onComplete)
                            } else {
                                onComplete(false, 0)
                            }
                        }
                    }
                    return
                }

                // User hasn't voted before - add the new vote
                processHitOrMiss(contentType, contentId, parentId, isHit, userReactionsRef, onComplete)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error checking existing hit/miss: ${error.message}")
                onComplete(false, 0)
            }
        })
    }

    /**
     * Process adding a hit or miss vote
     */
    private fun processHitOrMiss(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        isHit: Boolean,
        userReactionsRef: com.google.firebase.database.DatabaseReference,
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        // Get content reference based on type
        val fieldName = if (isHit) "hit" else "miss"
        val contentRef = getReactionContentRef(contentType, contentId, parentId, fieldName)

        // Also update team stats (if team is available)
        val contentFullRef = getFullContentRef(contentType, contentId, parentId)
        var teamName = ""

        contentFullRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get team name from content
                teamName = snapshot.child("team").getValue(String::class.java) ?: ""

                // Now update the hit/miss count
                contentRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentValue = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = currentValue + 1
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed && error == null && currentData != null) {
                            // Save the user's hit/miss choice
                            userReactionsRef.setValue(if (isHit) "hit" else "miss")
                                .addOnSuccessListener {
                                    val newValue = currentData.getValue(Int::class.java) ?: 0

                                    // Also update team stats if team is available
                                    if (teamName.isNotEmpty()) {
                                        updateTeamStats(teamName, isHit)
                                    }

                                    onComplete(true, newValue)
                                }
                                .addOnFailureListener {
                                    Log.e(TAG, "Error saving user hit/miss: ${it.message}")
                                    onComplete(false, 0)
                                }
                        } else {
                            Log.e(TAG, "Error updating hit/miss: ${error?.message}")
                            onComplete(false, 0)
                        }

                        // Check if notification should be created (100+ threshold)
                        if (committed && error == null) {
                            val newValue = currentData?.getValue(Int::class.java) ?: 0
                            if (newValue == 100) { // Exactly 100 to avoid multiple notifications
                                createMilestoneNotification(contentType, contentId, parentId, "hitMiss", if (isHit) "hit" else "miss", newValue)
                            }
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting team info: ${error.message}")
                onComplete(false, 0)
            }
        })
    }

    /**
     * Remove a hit or miss vote
     */
    private fun removeHitOrMiss(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        isHit: Boolean,
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        // Get content reference based on type
        val fieldName = if (isHit) "hit" else "miss"
        val contentRef = getReactionContentRef(contentType, contentId, parentId, fieldName)

        // Also get team info to update team stats
        val contentFullRef = getFullContentRef(contentType, contentId, parentId)
        var teamName = ""

        contentFullRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get team name from content
                teamName = snapshot.child("team").getValue(String::class.java) ?: ""

                // Now update the hit/miss count
                contentRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentValue = currentData.getValue(Int::class.java) ?: 0
                        // Ensure we don't go below 0
                        currentData.value = if (currentValue > 0) currentValue - 1 else 0
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed && error == null && currentData != null) {
                            // Remove the user's hit/miss choice
                            val userReactionsRef = FirebaseDatabase.getInstance().getReference(
                                "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/hitMiss"
                            )

                            userReactionsRef.removeValue()
                                .addOnSuccessListener {
                                    val newValue = currentData.getValue(Int::class.java) ?: 0

                                    // Also update team stats if team is available (decrement)
                                    if (teamName.isNotEmpty()) {
                                        updateTeamStats(teamName, isHit, true)
                                    }

                                    onComplete(true, newValue)
                                }
                                .addOnFailureListener {
                                    Log.e(TAG, "Error removing user hit/miss: ${it.message}")
                                    onComplete(false, 0)
                                }
                        } else {
                            Log.e(TAG, "Error updating hit/miss: ${error?.message}")
                            onComplete(false, 0)
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting team info: ${error.message}")
                onComplete(false, 0)
            }
        })
    }

    /**
     * Create notification when a content item reaches 100+ reactions/hit/miss
     */
    private fun createMilestoneNotification(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        reactionCategory: String,
        reactionValue: String,
        count: Int
    ) {
        if (count < 100) return
        Log.d(TAG, "Creating milestone notification for content reaching $count")

        // Get full content info to create rich notification
        val contentRef = getFullContentRef(contentType, contentId, parentId)

        contentRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Gather content info
                val senderId = snapshot.child("senderId").getValue(String::class.java) ?: ""
                val senderName = snapshot.child("senderName").getValue(String::class.java) ?: ""
                val team = snapshot.child("team").getValue(String::class.java) ?: ""

                // Different content types have different message fields
                val message = when (contentType) {
                    ContentType.POLL -> snapshot.child("question").getValue(String::class.java) ?: ""
                    ContentType.MEME -> "Meme" // Memes don't have text, just use the content type
                    else -> snapshot.child("message").getValue(String::class.java) ?: ""
                }

                // Create notification in Firebase
                val notificationsRef = FirebaseDatabase.getInstance().getReference(NOTIFICATIONS_PATH).push()

                val notification = mapOf(
                    "contentType" to contentType.toString(),
                    "contentId" to contentId,
                    "parentId" to (parentId ?: ""),
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "team" to team,
                    "message" to message,
                    "reactionCategory" to reactionCategory,
                    "reactionValue" to reactionValue,
                    "count" to count,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                )

                notificationsRef.setValue(notification)
                    .addOnSuccessListener {
                        Log.d(TAG, "Created milestone notification for $contentType with ID $contentId")

                        // IMPORTANT ADDITION: Send cloud message to all users
                        sendCloudNotification(
                            contentType = contentType.toString(),
                            contentId = contentId,
                            senderName = senderName,
                            team = team,
                            message = message,
                            reactionCategory = reactionCategory,
                            reactionValue = reactionValue,
                            count = count
                        )
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Failed to create notification: ${it.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting content for notification: ${error.message}")
            }
        })
    }


    private fun sendCloudNotification(
        contentType: String,
        contentId: String,
        senderName: String,
        team: String,
        message: String,
        reactionCategory: String,
        reactionValue: String,
        count: Int
    ) {
        // Create a cloud function trigger or use Firebase Admin SDK from your server
        // Here we'll add the notification to a special "cloud_notifications" node that will be processed
        // by a Cloud Function

        val notificationMessage = when (contentType) {
            "CHAT" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s message received ${count} ${reactionValue.capitalize()}!"
                    else -> "${senderName}'s message is trending!"
                }
            }
            "POLL" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s poll received ${count} ${reactionValue.capitalize()}!"
                    else -> "${senderName}'s poll is trending!"
                }
            }
            "MEME" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s meme received ${count} ${reactionValue.capitalize()}!"
                    else -> "${senderName}'s meme is trending!"
                }
            }
            "COMMENT" -> {
                when (reactionCategory) {
                    "hitMiss" -> "${senderName}'s comment received ${count} ${reactionValue.capitalize()}!"
                    else -> "${senderName}'s comment is trending!"
                }
            }
            else -> "Content is trending!"
        }

        val title = "Trending Content"

        // Add to a node that will trigger a cloud function
        val cloudNotificationRef = FirebaseDatabase.getInstance().getReference("CloudNotifications").push()
        val notificationData = mapOf(
            "contentType" to contentType,
            "contentId" to contentId,
            "title" to title,
            "message" to notificationMessage,
            "team" to team,
            "timestamp" to System.currentTimeMillis()
        )

        cloudNotificationRef.setValue(notificationData)
            .addOnSuccessListener {
                Log.d(TAG, "Cloud notification created for $contentType with ID $contentId")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to create cloud notification: ${it.message}")
            }
    }


    /**
     * Update team stats when content is rated
     */
    private fun updateTeamStats(team: String, isHit: Boolean, isRemoval: Boolean = false) {
        if (team.isEmpty()) return
        var lowerCaseTeam= team.lowercase(Locale.getDefault())

        val teamStatsRef = FirebaseDatabase.getInstance().getReference("teams/$lowerCaseTeam")
        val field = if (isHit) "hits" else "misses"

        teamStatsRef.child(field).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0

                // Increment or decrement based on whether this is a new vote or removing a vote
                val newValue = if (isRemoval) {
                    if (currentValue > 0) currentValue - 1 else 0
                } else {
                    currentValue + 1
                }

                currentData.value = newValue
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Error updating team stats: ${error.message}")
                }
            }
        })
    }

    /**
     * Get Firebase reference to the reaction field in content
     */
    private fun getReactionContentRef(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        fieldPath: String
    ): com.google.firebase.database.DatabaseReference {
        val database = FirebaseDatabase.getInstance()

        return when (contentType) {
            ContentType.CHAT -> database.getReference("NoBallZone/chats/$contentId/$fieldPath")
            ContentType.POLL -> database.getReference("NoBallZone/polls/$contentId/$fieldPath")
            ContentType.MEME -> database.getReference("NoBallZone/memes/$contentId/$fieldPath")
            ContentType.COMMENT -> {
                if (parentId != null) {
                    // Determine parent type from the enum value
                    val parentTypePath = when {
                        parentId.startsWith("chat_") -> "chats"
                        parentId.startsWith("poll_") -> "polls"
                        parentId.startsWith("meme_") -> "memes"
                        else -> "chats" // Default to chats if unclear
                    }

                    // Extract actual parent ID (remove prefix)
                    val actualParentId = parentId.substringAfter("_")
                    database.getReference("NoBallZone/$parentTypePath/$actualParentId/comments/$contentId/$fieldPath")
                } else {
                    // This should not happen, comments should always have a parent
                    database.getReference("NoBallZone/chats/$contentId/$fieldPath")
                }
            }
        }
    }

    /**
     * Get full Firebase reference to content
     */
    private fun getFullContentRef(
        contentType: ContentType,
        contentId: String,
        parentId: String?
    ): com.google.firebase.database.DatabaseReference {
        val database = FirebaseDatabase.getInstance()

        return when (contentType) {
            ContentType.CHAT -> database.getReference("NoBallZone/chats/$contentId")
            ContentType.POLL -> database.getReference("NoBallZone/polls/$contentId")
            ContentType.MEME -> database.getReference("NoBallZone/memes/$contentId")
            ContentType.COMMENT -> {
                if (parentId != null) {
                    // Determine parent type from the enum value
                    val parentTypePath = when {
                        parentId.startsWith("chat_") -> "chats"
                        parentId.startsWith("poll_") -> "polls"
                        parentId.startsWith("meme_") -> "memes"
                        else -> "chats" // Default to chats if unclear
                    }

                    // Extract actual parent ID (remove prefix)
                    val actualParentId = parentId.substringAfter("_")
                    database.getReference("NoBallZone/$parentTypePath/$actualParentId/comments/$contentId")
                } else {
                    // This should not happen, comments should always have a parent
                    database.getReference("NoBallZone/chats/$contentId")
                }
            }
        }
    }
}