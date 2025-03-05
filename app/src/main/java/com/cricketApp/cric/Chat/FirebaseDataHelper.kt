
package com.cricketApp.cric.Chat

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.GenericTypeIndicator

/**
 * Helper class for properly handling Firebase data conversion, especially for Lists vs Maps
 */
object FirebaseDataHelper {

    /**
     * Safely convert Firebase comments data to a MutableList of CommentMessage
     * This handles both the case where comments are stored as a List or as a Map
     */
    fun getCommentsFromSnapshot(snapshot: DataSnapshot): MutableList<CommentMessage> {
        val comments = mutableListOf<CommentMessage>()

        // Check if the comments node exists
        if (!snapshot.hasChild("comments")) {
            return comments
        }

        val commentsSnapshot = snapshot.child("comments")

        try {
            // First try to read as a List
            val listType = object : GenericTypeIndicator<List<CommentMessage>>() {}
            val commentsList = commentsSnapshot.getValue(listType)
            if (commentsList != null) {
                comments.addAll(commentsList.filterNotNull())
                return comments
            }
        } catch (e: Exception) {
            // If reading as List fails, it might be stored as a Map
        }

        try {
            // Try to read as a Map with String keys
            val mapType = object : GenericTypeIndicator<Map<String, CommentMessage>>() {}
            val commentsMap = commentsSnapshot.getValue(mapType)
            if (commentsMap != null) {
                comments.addAll(commentsMap.values)
                return comments
            }
        } catch (e: Exception) {
            // If both approaches fail, try to read comments one by one
        }

        // Finally, try to read each child individually
        for (childSnapshot in commentsSnapshot.children) {
            try {
                val comment = childSnapshot.getValue(CommentMessage::class.java)
                if (comment != null) {
                    // Make sure to set the ID if it's not already set
                    if (comment.id.isEmpty()) {
                        comment.id = childSnapshot.key ?: ""
                    }
                    comments.add(comment)
                }
            } catch (e: Exception) {
                // Skip invalid comments
            }
        }

        return comments
    }

    /**
     * Create a complete ChatMessage object from a DataSnapshot
     * This handles proper deserialization of all fields, including comments
     */
    fun getChatMessageFromSnapshot(snapshot: DataSnapshot): ChatMessage? {
        try {
            // Get the basic ChatMessage without comments
            val chat = snapshot.getValue(ChatMessage::class.java)

            if (chat != null) {
                // Make sure the ID is set
                chat.id = snapshot.key ?: chat.id

                // Handle comments separately
                chat.comments = getCommentsFromSnapshot(snapshot)

                return chat
            }
        } catch (e: Exception) {
            // Log error but don't crash
        }

        return null
    }

    /**
     * Create a complete PollMessage object from a DataSnapshot
     * This handles proper deserialization of all fields, including comments
     */
    fun getPollMessageFromSnapshot(snapshot: DataSnapshot): PollMessage? {
        try {
            // Get the basic PollMessage without comments
            val poll = snapshot.getValue(PollMessage::class.java)

            if (poll != null) {
                // Make sure the ID is set
                poll.id = snapshot.key ?: poll.id

                // Handle comments separately
                poll.comments = getCommentsFromSnapshot(snapshot)

                return poll
            }
        } catch (e: Exception) {
            // Log error but don't crash
        }

        return null
    }
}