    package com.cricketApp.cric.Chat

    import android.util.Log
    import com.cricketApp.cric.Meme.MemeMessage
    import com.google.firebase.database.DataSnapshot

    /**
     * Helper class for parsing Firebase data into proper model objects
     */
    object FirebaseDataHelper {

        private const val TAG = "FirebaseDataHelper"

        /**
         * Safely convert Firebase comments data to a MutableList of CommentMessage
         * This handles both the case where comments are stored as a List or as a Map
         * Added support for image URLs in comments
         */
        fun getCommentsFromSnapshot(snapshot: DataSnapshot): MutableList<CommentMessage> {
            val comments = mutableListOf<CommentMessage>()

            try {
                // Check if the comments node exists
                if (!snapshot.hasChild("comments")) {
                    return comments
                }

                val commentsSnapshot = snapshot.child("comments")

                // Log how many children we're dealing with for debugging
                // Log.d(TAG, "Comments node has ${commentsSnapshot.childrenCount} children")

                // Iterate through each comment directly - this works for both Map and List structures
                for (childSnapshot in commentsSnapshot.children) {
                    try {
                        // Try to convert the data to a CommentMessage object
                        val commentMap = childSnapshot.value as? Map<*, *>
                        if (commentMap != null) {
                            val id = childSnapshot.key ?: ""
                            val senderId = commentMap["senderId"] as? String ?: ""
                            val senderName = commentMap["senderName"] as? String ?: ""
                            val team = commentMap["team"] as? String ?: ""
                            val message = commentMap["message"] as? String ?: ""
                            val imageUrl = commentMap["imageUrl"] as? String ?: ""  // Added image URL support
                            val timestamp = (commentMap["timestamp"] as? Number)?.toLong() ?: 0L

                            // Handle reactions map
                            val reactionsMap = mutableMapOf(
                                "fire" to 0,
                                "laugh" to 0,
                                "cry" to 0,
                                "troll" to 0
                            )

                            if (commentMap.containsKey("reactions")) {
                                val reactions = commentMap["reactions"] as? Map<*, *>
                                if (reactions != null) {
                                    for (key in reactionsMap.keys) {
                                        reactionsMap[key] = (reactions[key] as? Number)?.toInt() ?: 0
                                    }
                                }
                            }

                            // Handle hit and miss counts
                            val hit = (commentMap["hit"] as? Number)?.toInt() ?: 0
                            val miss = (commentMap["miss"] as? Number)?.toInt() ?: 0

                            // Create the CommentMessage object
                            val comment = CommentMessage(
                                id = id,
                                senderId = senderId,
                                senderName = senderName,
                                team = team,
                                message = message,
                                imageUrl = imageUrl,  // Added image URL
                                reactions = reactionsMap,
                                hit = hit,
                                miss = miss,
                                timestamp = timestamp
                            )

                            comments.add(comment)
                        } else {
                            // Try the standard getValue approach as a fallback
                            val comment = childSnapshot.getValue(CommentMessage::class.java)
                            if (comment != null) {
                                // Make sure to set the ID if it's not already set
                                if (comment.id.isEmpty()) {
                                    comment.id = childSnapshot.key ?: ""
                                }
                                comments.add(comment)
                            }
                        }
                    } catch (e: Exception) {
                      //  Log.e(TAG, "Error parsing individual comment: ${e.message}")
                    }
                }

                // Sort comments by timestamp for consistency
                comments.sortBy { it.timestamp }

            //    Log.d(TAG, "Successfully parsed ${comments.size} comments")

            } catch (e: Exception) {
            //    Log.e(TAG, "Error parsing comments: ${e.message}")
            }

            return comments
        }

        /**
         * Create a complete ChatMessage object from a DataSnapshot
         * This handles proper deserialization of all fields, including comments
         */
        fun getChatMessageFromSnapshot(snapshot: DataSnapshot): ChatMessage? {
            try {
                // Get the basic data as a Map
                val messageMap = snapshot.value as? Map<*, *>
                if (messageMap != null) {
                    val id = snapshot.key ?: ""
                    val senderId = messageMap["senderId"] as? String ?: ""
                    val senderName = messageMap["senderName"] as? String ?: ""
                    val team = messageMap["team"] as? String ?: ""
                    val message = messageMap["message"] as? String ?: ""
                    val imageUrl = messageMap["imageUrl"] as? String ?: ""
                    val timestamp = (messageMap["timestamp"] as? Number)?.toLong() ?: 0L

                    // Get comment count
                    val commentCount = (messageMap["commentCount"] as? Number)?.toInt() ?: 0

                    // Handle reactions map
                    val reactionsMap = mutableMapOf(
                        "fire" to 0,
                        "laugh" to 0,
                        "cry" to 0,
                        "troll" to 0
                    )

                    if (messageMap.containsKey("reactions")) {
                        val reactions = messageMap["reactions"] as? Map<*, *>
                        if (reactions != null) {
                            for (key in reactionsMap.keys) {
                                reactionsMap[key] = (reactions[key] as? Number)?.toInt() ?: 0
                            }
                        }
                    }

                    // Handle hit and miss counts
                    val hit = (messageMap["hit"] as? Number)?.toInt() ?: 0
                    val miss = (messageMap["miss"] as? Number)?.toInt() ?: 0

                    // Create the message
                    val chat = ChatMessage(
                        id = id,
                        senderId = senderId,
                        senderName = senderName,
                        team = team,
                        message = message,
                        imageUrl = imageUrl,
                        timestamp = timestamp,
                        reactions = reactionsMap,
                        hit = hit,
                        miss = miss,
                        commentCount = commentCount
                    )

                    // Handle comments separately
                    chat.comments = getCommentsFromSnapshot(snapshot)

                    return chat
                } else {
                    // Try standard getValue as fallback
                    val chat = snapshot.getValue(ChatMessage::class.java)
                    if (chat != null) {
                        chat.id = snapshot.key ?: chat.id
                        chat.comments = getCommentsFromSnapshot(snapshot)
                        return chat
                    }
                }
            } catch (e: Exception) {
             //   Log.e(TAG, "Error parsing chat message: ${e.message}")
            }

            return null
        }

        /**
         * Create a complete PollMessage object from a DataSnapshot
         * This handles proper deserialization of all fields, including comments
         */
        fun getPollMessageFromSnapshot(snapshot: DataSnapshot): PollMessage? {
            try {
                // Get the basic data as a Map
                val messageMap = snapshot.value as? Map<*, *>
                if (messageMap != null) {
                    val id = snapshot.key ?: ""
                    val senderId = messageMap["senderId"] as? String ?: ""
                    val senderName = messageMap["senderName"] as? String ?: ""
                    val team = messageMap["team"] as? String ?: ""
                    val question = messageMap["question"] as? String ?: ""
                    val timestamp = (messageMap["timestamp"] as? Number)?.toLong() ?: 0L

                    // Get comment count
                    val commentCount = (messageMap["commentCount"] as? Number)?.toInt() ?: 0

                    // Handle options map
                    val optionsMap = mutableMapOf<String, Int>()
                    if (messageMap.containsKey("options")) {
                        val options = messageMap["options"] as? Map<*, *>
                        if (options != null) {
                            for (key in options.keys) {
                                if (key is String) {
                                    optionsMap[key] = (options[key] as? Number)?.toInt() ?: 0
                                }
                            }
                        }
                    }

                    // Handle reactions map
                    val reactionsMap = mutableMapOf(
                        "fire" to 0,
                        "laugh" to 0,
                        "cry" to 0,
                        "troll" to 0
                    )

                    if (messageMap.containsKey("reactions")) {
                        val reactions = messageMap["reactions"] as? Map<*, *>
                        if (reactions != null) {
                            for (key in reactionsMap.keys) {
                                reactionsMap[key] = (reactions[key] as? Number)?.toInt() ?: 0
                            }
                        }
                    }

                    // Handle voters map
                    val votersMap = mutableMapOf<String, String>()
                    if (messageMap.containsKey("voters")) {
                        val voters = messageMap["voters"] as? Map<*, *>
                        if (voters != null) {
                            for (key in voters.keys) {
                                if (key is String && voters[key] is String) {
                                    votersMap[key] = voters[key] as String
                                }
                            }
                        }
                    }

                    // Handle hit and miss counts
                    val hit = (messageMap["hit"] as? Number)?.toInt() ?: 0
                    val miss = (messageMap["miss"] as? Number)?.toInt() ?: 0

                    // Create the poll message
                    val poll = PollMessage(
                        id = id,
                        senderId = senderId,
                        senderName = senderName,
                        team = team,
                        question = question,
                        options = optionsMap,
                        reactions = reactionsMap,
                        hit = hit,
                        miss = miss,
                        timestamp = timestamp,
                        commentCount = commentCount,
                        voters = votersMap
                    )

                    // Handle comments separately
                    poll.comments = getCommentsFromSnapshot(snapshot)

                    return poll
                } else {
                    // Try standard getValue as fallback
                    val poll = snapshot.getValue(PollMessage::class.java)
                    if (poll != null) {
                        poll.id = snapshot.key ?: poll.id
                        poll.comments = getCommentsFromSnapshot(snapshot)
                        return poll
                    }
                }
            } catch (e: Exception) {
            //    Log.e(TAG, "Error parsing poll message: ${e.message}")
            }

            return null
        }

        /**
         * Create a complete MemeMessage object from a DataSnapshot
         * This handles proper deserialization of all fields, including comments
         */
        fun getMemeMessageFromSnapshot(snapshot: DataSnapshot): MemeMessage? {
            try {
                // Get the basic data as a Map
                val messageMap = snapshot.value as? Map<*, *>
                if (messageMap != null) {
                    val id = snapshot.key ?: ""
                    val senderId = messageMap["senderId"] as? String ?: ""
                    val senderName = messageMap["senderName"] as? String ?: ""
                    val team = messageMap["team"] as? String ?: ""
                    val memeUrl = messageMap["memeUrl"] as? String ?: ""
                    val timestamp = (messageMap["timestamp"] as? Number)?.toLong() ?: 0L

                    // Get comment count
                    val commentCount = (messageMap["commentCount"] as? Number)?.toInt() ?: 0

                    // Handle reactions map
                    val reactionsMap = mutableMapOf(
                        "fire" to 0,
                        "laugh" to 0,
                        "cry" to 0,
                        "troll" to 0
                    )

                    if (messageMap.containsKey("reactions")) {
                        val reactions = messageMap["reactions"] as? Map<*, *>
                        if (reactions != null) {
                            for (key in reactionsMap.keys) {
                                reactionsMap[key] = (reactions[key] as? Number)?.toInt() ?: 0
                            }
                        }
                    }

                    // Handle hit and miss counts
                    val hit = (messageMap["hit"] as? Number)?.toInt() ?: 0
                    val miss = (messageMap["miss"] as? Number)?.toInt() ?: 0

                    // Create the meme message
                    val meme = MemeMessage(
                        id = id,
                        senderId = senderId,
                        senderName = senderName,
                        team = team,
                        memeUrl = memeUrl,
                        timestamp = timestamp,
                        reactions = reactionsMap,
                        hit = hit,
                        miss = miss,
                        commentCount = commentCount
                    )

                    // Handle comments separately
                    meme.comments = getCommentsFromSnapshot(snapshot)

                    return meme
                } else {
                    // Try standard getValue as fallback
                    val meme = snapshot.getValue(MemeMessage::class.java)
                    if (meme != null) {
                        meme.id = snapshot.key ?: meme.id
                        meme.comments = getCommentsFromSnapshot(snapshot)
                        return meme
                    }
                }
            } catch (e: Exception) {
            //    Log.e(TAG, "Error parsing meme message: ${e.message}")
            }

            return null
        }
    }