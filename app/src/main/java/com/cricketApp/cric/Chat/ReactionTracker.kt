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

object ReactionTracker {

    private const val TAG = "ReactionTracker"
    private const val USER_REACTIONS_PATH = "UserReactions"
    private const val NOTIFICATIONS_PATH = "Notifications"

    enum class ContentType {
        CHAT, POLL, COMMENT, MEME
    }

    enum class ReactionType {
        EMOJI, HIT_MISS
    }

    // ── Public: addEmojiReaction ──────────────────────────────────────────────

    fun addEmojiReaction(
        contentType: ContentType,
        contentId: String,
        parentId: String? = null,
        reactionType: String,
        roomBasePath: String = "NoBallZone",        // ← NEW
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        val userReactionsRef = FirebaseDatabase.getInstance().getReference(
            "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/emoji"
        )

        userReactionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val previousReaction = snapshot.getValue(String::class.java)

                if (previousReaction != null && previousReaction == reactionType) {
                    removeEmojiReaction(contentType, contentId, parentId, previousReaction, roomBasePath) { success, newCount ->
                        onComplete(success, newCount)
                    }
                    return
                } else if (previousReaction != null) {
                    removeEmojiReaction(contentType, contentId, parentId, previousReaction, roomBasePath) { success, _ ->
                        if (success) {
                            processEmojiReaction(contentType, contentId, parentId, reactionType, userReactionsRef, roomBasePath, onComplete)
                        } else {
                            onComplete(false, 0)
                        }
                    }
                    return
                }

                processEmojiReaction(contentType, contentId, parentId, reactionType, userReactionsRef, roomBasePath, onComplete)
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(false, 0)
            }
        })
    }

    // ── Private: processEmojiReaction ─────────────────────────────────────────

    private fun processEmojiReaction(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        reactionType: String,
        userReactionsRef: com.google.firebase.database.DatabaseReference,
        roomBasePath: String,                        // ← NEW
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val contentRef = getReactionContentRef(contentType, contentId, parentId, "reactions/$reactionType", roomBasePath)

        contentRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    userReactionsRef.setValue(reactionType)
                        .addOnSuccessListener {
                            val newValue = currentData.getValue(Int::class.java) ?: 0
                            onComplete(true, newValue)
                        }
                        .addOnFailureListener {
                            onComplete(false, 0)
                        }
                } else {
                    onComplete(false, 0)
                }

                if (committed && error == null) {
                    val newValue = currentData?.getValue(Int::class.java) ?: 0
                    if (newValue >= 100) {
                        createMilestoneNotification(contentType, contentId, parentId, "reaction", reactionType, newValue, roomBasePath)
                    }
                }
            }
        })
    }

    // ── Private: removeEmojiReaction ──────────────────────────────────────────

    private fun removeEmojiReaction(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        reactionType: String,
        roomBasePath: String,                        // ← NEW
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        val contentRef = getReactionContentRef(contentType, contentId, parentId, "reactions/$reactionType", roomBasePath)

        contentRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = if (currentValue > 0) currentValue - 1 else 0
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    val userReactionsRef = FirebaseDatabase.getInstance().getReference(
                        "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/emoji"
                    )

                    userReactionsRef.removeValue()
                        .addOnSuccessListener {
                            val newValue = currentData.getValue(Int::class.java) ?: 0
                            onComplete(true, newValue)
                        }
                        .addOnFailureListener {
                            onComplete(false, 0)
                        }
                } else {
                    onComplete(false, 0)
                }
            }
        })
    }

    // ── Public: updateHitOrMiss ───────────────────────────────────────────────

    fun updateHitOrMiss(
        contentType: ContentType,
        contentId: String,
        parentId: String? = null,
        isHit: Boolean,
        roomBasePath: String = "NoBallZone",         // ← NEW
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        val userReactionsRef = FirebaseDatabase.getInstance().getReference(
            "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/hitMiss"
        )

        userReactionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val previousVote = snapshot.getValue(String::class.java)

                if (previousVote != null) {
                    val previousIsHit = previousVote == "hit"

                    if (previousIsHit == isHit) {
                        removeHitOrMiss(contentType, contentId, parentId, previousIsHit, roomBasePath) { success, newCount ->
                            onComplete(success, newCount)
                        }
                    } else {
                        removeHitOrMiss(contentType, contentId, parentId, previousIsHit, roomBasePath) { success, _ ->
                            if (success) {
                                processHitOrMiss(contentType, contentId, parentId, isHit, userReactionsRef, roomBasePath, onComplete)
                            } else {
                                onComplete(false, 0)
                            }
                        }
                    }
                    return
                }

                processHitOrMiss(contentType, contentId, parentId, isHit, userReactionsRef, roomBasePath, onComplete)
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(false, 0)
            }
        })
    }

    // ── Private: processHitOrMiss ─────────────────────────────────────────────

    private fun processHitOrMiss(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        isHit: Boolean,
        userReactionsRef: com.google.firebase.database.DatabaseReference,
        roomBasePath: String,                        // ← NEW
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val fieldName = if (isHit) "hit" else "miss"
        val contentRef = getReactionContentRef(contentType, contentId, parentId, fieldName, roomBasePath)
        val contentFullRef = getFullContentRef(contentType, contentId, parentId, roomBasePath)
        var teamName = ""

        contentFullRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                teamName = snapshot.child("team").getValue(String::class.java) ?: ""

                contentRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentValue = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = currentValue + 1
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed && error == null && currentData != null) {
                            userReactionsRef.setValue(if (isHit) "hit" else "miss")
                                .addOnSuccessListener {
                                    val newValue = currentData.getValue(Int::class.java) ?: 0
                                    if (teamName.isNotEmpty()) updateTeamStats(teamName, isHit)
                                    onComplete(true, newValue)
                                }
                                .addOnFailureListener {
                                    onComplete(false, 0)
                                }
                        } else {
                            onComplete(false, 0)
                        }

                        if (committed && error == null) {
                            val newValue = currentData?.getValue(Int::class.java) ?: 0
                            if (newValue >= 100) {
                                createMilestoneNotification(contentType, contentId, parentId, "hitMiss", if (isHit) "hit" else "miss", newValue, roomBasePath)
                            }
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(false, 0)
            }
        })
    }

    // ── Private: removeHitOrMiss ──────────────────────────────────────────────

    private fun removeHitOrMiss(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        isHit: Boolean,
        roomBasePath: String,                        // ← NEW
        onComplete: (success: Boolean, newValue: Int) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        val fieldName = if (isHit) "hit" else "miss"
        val contentRef = getReactionContentRef(contentType, contentId, parentId, fieldName, roomBasePath)
        val contentFullRef = getFullContentRef(contentType, contentId, parentId, roomBasePath)
        var teamName = ""

        contentFullRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                teamName = snapshot.child("team").getValue(String::class.java) ?: ""

                contentRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentValue = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = if (currentValue > 0) currentValue - 1 else 0
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed && error == null && currentData != null) {
                            val userReactionsRef = FirebaseDatabase.getInstance().getReference(
                                "$USER_REACTIONS_PATH/$userId/$contentType/${if (parentId != null) "$parentId/" else ""}$contentId/hitMiss"
                            )

                            userReactionsRef.removeValue()
                                .addOnSuccessListener {
                                    val newValue = currentData.getValue(Int::class.java) ?: 0
                                    if (teamName.isNotEmpty()) updateTeamStats(teamName, isHit, true)
                                    onComplete(true, newValue)
                                }
                                .addOnFailureListener {
                                    onComplete(false, 0)
                                }
                        } else {
                            onComplete(false, 0)
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(false, 0)
            }
        })
    }

    // ── getReactionContentRef — now room-aware ────────────────────────────────

    private fun getReactionContentRef(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        fieldPath: String,
        roomBasePath: String = "NoBallZone"          // ← NEW
    ): com.google.firebase.database.DatabaseReference {
        val database = FirebaseDatabase.getInstance()

        return when (contentType) {
            ContentType.CHAT    -> database.getReference("$roomBasePath/chats/$contentId/$fieldPath")
            ContentType.POLL    -> database.getReference("$roomBasePath/polls/$contentId/$fieldPath")
            ContentType.MEME    -> database.getReference("$roomBasePath/memes/$contentId/$fieldPath")
            ContentType.COMMENT -> {
                if (parentId != null) {
                    val parentTypePath = when {
                        parentId.startsWith("chat_") -> "chats"
                        parentId.startsWith("poll_") -> "polls"
                        parentId.startsWith("meme_") -> "memes"
                        else -> "chats"
                    }
                    val actualParentId = parentId.substringAfter("_")
                    database.getReference("$roomBasePath/$parentTypePath/$actualParentId/comments/$contentId/$fieldPath")
                } else {
                    database.getReference("$roomBasePath/chats/$contentId/$fieldPath")
                }
            }
        }
    }

    // ── getFullContentRef — now room-aware ────────────────────────────────────

    private fun getFullContentRef(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        roomBasePath: String = "NoBallZone"          // ← NEW
    ): com.google.firebase.database.DatabaseReference {
        val database = FirebaseDatabase.getInstance()

        return when (contentType) {
            ContentType.CHAT    -> database.getReference("$roomBasePath/chats/$contentId")
            ContentType.POLL    -> database.getReference("$roomBasePath/polls/$contentId")
            ContentType.MEME    -> database.getReference("$roomBasePath/memes/$contentId")
            ContentType.COMMENT -> {
                if (parentId != null) {
                    val parentTypePath = when {
                        parentId.startsWith("chat_") -> "chats"
                        parentId.startsWith("poll_") -> "polls"
                        parentId.startsWith("meme_") -> "memes"
                        else -> "chats"
                    }
                    val actualParentId = parentId.substringAfter("_")
                    database.getReference("$roomBasePath/$parentTypePath/$actualParentId/comments/$contentId")
                } else {
                    database.getReference("$roomBasePath/chats/$contentId")
                }
            }
        }
    }

    // ── createMilestoneNotification — now room-aware ──────────────────────────

    private fun createMilestoneNotification(
        contentType: ContentType,
        contentId: String,
        parentId: String?,
        reactionCategory: String,
        reactionValue: String,
        count: Int,
        roomBasePath: String = "NoBallZone"
    ) {
        if (count < 100) return

        val contentRef = getFullContentRef(contentType, contentId, parentId, roomBasePath)

        // ADD THIS - verify the path is correct
        Log.d("MilestoneDebug", "Fetching content at: ${contentRef.path}")

        contentRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                // ADD THIS - verify data exists
                Log.d("MilestoneDebug", "Snapshot exists: ${snapshot.exists()}, value: ${snapshot.value}")

                val senderId   = snapshot.child("senderId").getValue(String::class.java) ?: ""
                val senderName = snapshot.child("senderName").getValue(String::class.java) ?: ""

                val notificationsRef = FirebaseDatabase.getInstance()
                    .getReference(NOTIFICATIONS_PATH).push()
                val notification = mapOf(
                    "contentType"      to contentType.toString(),
                    "contentId"        to contentId,
                    "parentId"         to (parentId ?: ""),
                    "senderId"         to senderId,
                    "senderName"       to senderName,
                    "team"             to (snapshot.child("team").getValue(String::class.java) ?: ""),
                    "message"          to when (contentType) {
                        ContentType.POLL -> snapshot.child("question").getValue(String::class.java) ?: ""
                        ContentType.MEME -> "Meme"
                        else             -> snapshot.child("message").getValue(String::class.java) ?: ""
                    },
                    "reactionCategory" to reactionCategory,
                    "reactionValue"    to reactionValue,
                    "count"            to count,
                    "timestamp"        to System.currentTimeMillis(),
                    "read"             to false,
                    "roomBasePath"     to roomBasePath
                )

                notificationsRef.setValue(notification)
                    .addOnSuccessListener {
                        // ADD THIS
                        Log.d("MilestoneDebug", "✅ Notification written successfully")
                        sendCloudNotification(
                            contentType.toString(), contentId,
                            senderId, snapshot.child("team").getValue(String::class.java) ?: "",
                            notification["message"] as String,
                            reactionCategory, reactionValue, count
                        )
                    }
                    .addOnFailureListener { e ->
                        // ADD THIS - this reveals Firebase Rules rejections
                        Log.e("MilestoneDebug", "❌ Notification write FAILED: ${e.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                // ADD THIS
                Log.e("MilestoneDebug", "❌ Content fetch cancelled: ${error.message}, code: ${error.code}")
            }
        })
    }

    // ── sendCloudNotification — unchanged ────────────────────────────────────

    private fun sendCloudNotification(
        contentType: String, contentId: String, senderName: String, team: String,
        message: String, reactionCategory: String, reactionValue: String, count: Int
    ) {
        val notificationMessage = when (contentType) {
            "CHAT"    -> if (reactionCategory == "hitMiss") "${senderName}'s message received $count ${reactionValue.replaceFirstChar { it.uppercase() }}!" else "${senderName}'s message is trending!"
            "POLL"    -> if (reactionCategory == "hitMiss") "${senderName}'s poll received $count ${reactionValue.replaceFirstChar { it.uppercase() }}!"    else "${senderName}'s poll is trending!"
            "MEME"    -> if (reactionCategory == "hitMiss") "${senderName}'s meme received $count ${reactionValue.replaceFirstChar { it.uppercase() }}!"    else "${senderName}'s meme is trending!"
            "COMMENT" -> if (reactionCategory == "hitMiss") "${senderName}'s comment received $count ${reactionValue.replaceFirstChar { it.uppercase() }}!" else "${senderName}'s comment is trending!"
            else      -> "Content is trending!"
        }

        val cloudNotificationRef = FirebaseDatabase.getInstance().getReference("CloudNotifications").push()
        val notificationData = mapOf(
            "contentType" to contentType, "contentId" to contentId,
            "title"       to "Trending Content", "message" to notificationMessage,
            "team"        to team, "timestamp" to System.currentTimeMillis()
        )
        cloudNotificationRef.setValue(notificationData)
    }

    // ── updateTeamStats — unchanged ───────────────────────────────────────────

    private fun updateTeamStats(team: String, isHit: Boolean, isRemoval: Boolean = false) {
        if (team.isEmpty()) return
        val lowerCaseTeam = team.lowercase(Locale.getDefault())
        val teamStatsRef  = FirebaseDatabase.getInstance().getReference("teams/$lowerCaseTeam")
        val field         = if (isHit) "hits" else "misses"

        teamStatsRef.child(field).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = if (isRemoval) {
                    if (currentValue > 0) currentValue - 1 else 0
                } else {
                    currentValue + 1
                }
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
        })
    }
}