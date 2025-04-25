package com.cricketApp.cric.Chat

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.cricketApp.cric.Meme.MemeMessage
import com.cricketApp.cric.R
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * Utility class to handle message actions like deletion and reporting
 */
object MessageActionsHandler {
    private const val TAG = "MessageActionsHandler"

    // Map to track user's image claim attempts
    private val imageClaims = mutableMapOf<String, Int>() // messageId -> count

    // Map to track copyright claims
    private val copyrightClaims = mutableMapOf<String, Int>() // messageId -> count

    // Constants for reports threshold - reduced from 50 to more reasonable numbers
    private const val MAX_REPORTS_THRESHOLD = 50  // Lowered from 50 to 5
    private const val INAPPROPRIATE_REPORTS_THRESHOLD = 30
    private const val SPAM_REPORTS_THRESHOLD = 30
    private const val HARASSMENT_REPORTS_THRESHOLD = 20
    private const val FALSE_INFO_REPORTS_THRESHOLD = 40

    private const val IMAGE_CLAIM_THRESHOLD = 5
    private const val COPYRIGHT_CLAIM_THRESHOLD = 3  // Reduced from 10 to 3

    /**
     * Show message options bottom sheet when a message is long-pressed
     */
    fun showMessageOptionsBottomSheet(
        context: Context,
        message: Any,
        position: Int,
        onDelete: (Any, Int, String) -> Unit  // Updated to include messageId
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val bottomSheetDialog = BottomSheetDialog(context)
        val bottomSheetView = View.inflate(context, R.layout.bottom_sheet_message_options, null)

        // Get the message info
        val messageId: String
        val senderId: String
        val isOwnMessage: Boolean
        val hasImage: Boolean
        val parentInfo: Pair<String, String>? // (parentId, parentType) for comments

        when (message) {
            is ChatMessage -> {
                messageId = message.id
                senderId = message.senderId
                isOwnMessage = message.senderId == currentUser.uid
                hasImage = message.imageUrl.isNotEmpty()
                parentInfo = null
            }
            is PollMessage -> {
                messageId = message.id
                senderId = message.senderId
                isOwnMessage = message.senderId == currentUser.uid
                hasImage = false
                parentInfo = null
            }
            is CommentMessage -> {
                messageId = message.id
                senderId = message.senderId
                isOwnMessage = message.senderId == currentUser.uid
                hasImage = message.imageUrl.isNotEmpty()

                // For comments, we'll need to get the parent info from the adapter later
                parentInfo = null
            }
            is MemeMessage -> {
                messageId = message.id
                senderId = message.senderId
                isOwnMessage = message.senderId == currentUser.uid
                hasImage = message.memeUrl.isNotEmpty()
                parentInfo = null
            }
            else -> return
        }

        // Set up the UI based on whether it's the user's own message
        val btnDelete = bottomSheetView.findViewById<Button>(R.id.btnDelete)
        val btnReport = bottomSheetView.findViewById<Button>(R.id.btnReport)
        val btnClaimImage = bottomSheetView.findViewById<Button>(R.id.btnClaimImage)

        // Add copyright report button
        val btnCopyrightReport = bottomSheetView.findViewById<Button>(R.id.btnCopyrightReport)

        // Only show delete option for own messages
        btnDelete.visibility = if (isOwnMessage) View.VISIBLE else View.GONE

        // Only show report option for messages from other users
        btnReport.visibility = if (!isOwnMessage) View.VISIBLE else View.GONE

        // Only show claim image option if the message has an image and it's not the user's message
        btnClaimImage.visibility = if (hasImage && !isOwnMessage) View.VISIBLE else View.GONE

        // Only show copyright report option if the message has an image and it's not the user's message
        btnCopyrightReport.visibility = if (hasImage && !isOwnMessage) View.VISIBLE else View.GONE

        // Set up click listeners
        btnDelete.setOnClickListener {
            bottomSheetDialog.dismiss()

            // For comments, we need to extract extra info from context
            if (message is CommentMessage) {
                // Use the context to get the CommentActivity's messageId and messageType
                val activity = context as? CommentActivity
                if (activity != null) {
                    val parentId = activity.messageId
                    val parentType = activity.messageType

                    showDeleteConfirmationDialog(context, message, position, messageId, onDelete, parentId, parentType)
                } else {
                    showDeleteConfirmationDialog(context, message, position, messageId, onDelete)
                }
            } else {
                showDeleteConfirmationDialog(context, message, position, messageId, onDelete)
            }
        }

        btnReport.setOnClickListener {
            bottomSheetDialog.dismiss()
            showReportDialog(context, messageId, senderId, getMessageType(message))
        }

        btnClaimImage.setOnClickListener {
            bottomSheetDialog.dismiss()
            handleImageClaim(context, messageId, message)
        }

        btnCopyrightReport.setOnClickListener {
            bottomSheetDialog.dismiss()
            handleCopyrightClaim(context, messageId, message)
        }

        bottomSheetDialog.setContentView(bottomSheetView)
        bottomSheetDialog.show()
    }

    /**
     * Get the database type string for a message type
     */
    private fun getMessageType(message: Any): String {
        return when (message) {
            is ChatMessage -> "chats"
            is PollMessage -> "polls"
            is CommentMessage -> "comments"
            is MemeMessage -> "memes"
            else -> ""
        }
    }

    /**
     * Show dialog to confirm message deletion
     */
    private fun showDeleteConfirmationDialog(
        context: Context,
        message: Any,
        position: Int,
        messageId: String,
        onDelete: (Any, Int, String) -> Unit,  // Updated to include messageId
        parentId: String? = null,
        parentType: String? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
            //    Log.d(TAG, "User confirmed deletion of message: $messageId at position: $position")

                deleteMessage(message, parentId, parentType) { success ->
                    if (success) {
                        // Pass both position and messageId to onDelete
                    //    Log.d(TAG, "Firebase deletion successful, now removing from UI: $messageId")
                        onDelete(message, position, messageId)
                        Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to delete message", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Delete a message from Firebase
     */
    private fun deleteMessage(
        message: Any,
        parentId: String? = null,
        parentType: String? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val database = FirebaseDatabase.getInstance()

        // Get message type and ID
        when (message) {
            is ChatMessage -> {
                val messageRef = database.getReference("NoBallZone/chats/${message.id}")
                messageRef.removeValue()
                    .addOnSuccessListener {
                    //    Log.d(TAG, "Successfully deleted chat message from Firebase: ${message.id}")
                        onComplete(true)
                    }
                    .addOnFailureListener {
                    //    Log.e(TAG, "Error deleting chat message: ${it.message}")
                        onComplete(false)
                    }
            }
            is PollMessage -> {
                val messageRef = database.getReference("NoBallZone/polls/${message.id}")
                messageRef.removeValue()
                    .addOnSuccessListener {
                    //    Log.d(TAG, "Successfully deleted poll message from Firebase: ${message.id}")
                        onComplete(true)
                    }
                    .addOnFailureListener {
                    //    Log.e(TAG, "Error deleting poll message: ${it.message}")
                        onComplete(false)
                    }
            }
            is MemeMessage -> {
                val messageRef = database.getReference("NoBallZone/memes/${message.id}")
                messageRef.removeValue()
                    .addOnSuccessListener {
                    //    Log.d(TAG, "Successfully deleted meme from Firebase: ${message.id}")
                        onComplete(true)
                    }
                    .addOnFailureListener {
                    //    Log.e(TAG, "Error deleting meme: ${it.message}")
                        onComplete(false)
                    }
            }
            is CommentMessage -> {
                // For comments, we need to know the parent message type and ID
                if (parentId != null && parentType != null) {
                    // Build the correct reference path based on parent type
                    val dbPath = when (parentType) {
                        "chat" -> "NoBallZone/chats/$parentId/comments/${message.id}"
                        "poll" -> "NoBallZone/polls/$parentId/comments/${message.id}"
                        "meme" -> "NoBallZone/memes/$parentId/comments/${message.id}"
                        else -> ""
                    }

                    if (dbPath.isNotEmpty()) {
                        val commentRef = database.getReference(dbPath)
                        commentRef.removeValue()
                            .addOnSuccessListener {
                            //    Log.d(TAG, "Successfully deleted comment from Firebase: ${message.id}")

                                // Update the comment count in the parent message
                                updateParentCommentCount(parentId, parentType)

                                onComplete(true)
                            }
                            .addOnFailureListener {
                            //    Log.e(TAG, "Error deleting comment: ${it.message}")
                                onComplete(false)
                            }
                    } else {
                    //    Log.e(TAG, "Invalid parent type for comment")
                        onComplete(false)
                    }
                } else {
                //    Log.e(TAG, "Comment parent information not available")
                    onComplete(false)
                }
            }
            else -> {
                onComplete(false)
            }
        }
    }

    /**
     * Update the comment count in the parent message after a comment is deleted
     */
    private fun updateParentCommentCount(parentId: String, parentType: String) {
        val database = FirebaseDatabase.getInstance()

        // Get the comments reference
        val commentsPath = when (parentType) {
            "chat" -> "NoBallZone/chats/$parentId/comments"
            "poll" -> "NoBallZone/polls/$parentId/comments"
            "meme" -> "NoBallZone/memes/$parentId/comments"
            else -> return
        }

        val parentRef = when (parentType) {
            "chat" -> database.getReference("NoBallZone/chats/$parentId")
            "poll" -> database.getReference("NoBallZone/polls/$parentId")
            "meme" -> database.getReference("NoBallZone/memes/$parentId")
            else -> return
        }

        // Count the comments
        val commentsRef = database.getReference(commentsPath)
        commentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val commentsCount = snapshot.childrenCount.toInt()

                // Update the parent message
                parentRef.child("commentCount").setValue(commentsCount)
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e(TAG, "Error updating comment count: ${error.message}")
            }
        })
    }

    /**
     * Show dialog with report options
     */
    private fun showReportDialog(context: Context, messageId: String, senderId: String, messageType: String) {
        val options = arrayOf(
            "Inappropriate content",
            "Harassment or bullying",
            "Spam",
            "False information",
            "Offensive language",
            "Other"
        )

        AlertDialog.Builder(context,R.style.CustomAlertDialogTheme)
            .setTitle("Report Message")
            .setItems(options) { dialog, which ->
                val reportReason = options[which]
                submitReport(context, messageId, senderId, reportReason, messageType)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Submit a report to Firebase
     */
    private fun submitReport(context: Context, messageId: String, senderId: String, reason: String, messageType: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance()

        // Create a report entry
        val reportsRef = database.getReference("Reports").push()
        val report = mapOf(
            "messageId" to messageId,
            "messageType" to messageType,
            "reportedUserId" to senderId,
            "reporterUserId" to currentUser.uid,
            "reason" to reason,
            "timestamp" to System.currentTimeMillis()
        )

        // Submit the report
        reportsRef.setValue(report)
            .addOnSuccessListener {
                Toast.makeText(context, "Report submitted. Thank you!", Toast.LENGTH_SHORT).show()

                // Get the count of reports for this message by reason
                getMessageReportCount(messageId, reason) { reportCount ->
                    // Check if the message has reached the threshold for automatic removal based on reason
                    val threshold = when (reason) {
                        "Inappropriate content" -> INAPPROPRIATE_REPORTS_THRESHOLD
                        "Harassment or bullying" -> HARASSMENT_REPORTS_THRESHOLD
                        "Spam" -> SPAM_REPORTS_THRESHOLD
                        "False information" -> FALSE_INFO_REPORTS_THRESHOLD
                        else -> MAX_REPORTS_THRESHOLD
                    }

                    if (reportCount >= threshold) {
                        // Remove the message if report threshold is crossed
                        val messageRef = database.getReference("NoBallZone/$messageType/$messageId")
                        messageRef.removeValue()
                            .addOnSuccessListener {
                            //    Log.d(TAG, "Automatically removed message due to ${reportCount} reports for '${reason}'")
                            }
                    }
                }

                // Update report count for the user
                updateReportCount(senderId) { reportCount ->
                    // Check if the user has reached the threshold for automatic message removal
                    if (reportCount >= MAX_REPORTS_THRESHOLD) {
                        // Remove the message if report threshold is crossed
                        val messageRef = database.getReference("NoBallZone/$messageType/$messageId")
                        messageRef.removeValue()
                            .addOnSuccessListener {
                            //    Log.d(TAG, "Automatically removed message due to high report count for user")
                            }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to submit report. Please try again.", Toast.LENGTH_SHORT).show()
            //    Log.e(TAG, "Error submitting report: ${it.message}")
            }
    }

    /**
     * Get the count of reports for a specific message and reason
     */
    private fun getMessageReportCount(messageId: String, reason: String, onComplete: (Int) -> Unit) {
        val reportsRef = FirebaseDatabase.getInstance().getReference("Reports")
        reportsRef.orderByChild("messageId").equalTo(messageId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var count = 0
                    for (reportSnapshot in snapshot.children) {
                        val reportReason = reportSnapshot.child("reason").getValue(String::class.java)
                        if (reportReason == reason) {
                            count++
                        }
                    }
                    onComplete(count)
                }

                override fun onCancelled(error: DatabaseError) {
               //     Log.e(TAG, "Error getting report count: ${error.message}")
                    onComplete(0)
                }
            })
    }

    /**
     * Update the report count for a user
     */
    private fun updateReportCount(userId: String, onComplete: (Int) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val userReportsRef = database.getReference("Users/$userId/reportCount")

        userReportsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentValue = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = currentValue + 1
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
                if (committed) {
                    val reportCount = dataSnapshot?.getValue(Int::class.java) ?: 0
                    onComplete(reportCount)
                } else {
                //    Log.e(TAG, "Error updating report count: ${error?.message}")
                    onComplete(0)
                }
            }
        })
    }

    /**
     * Handle image claim functionality
     */
    private fun handleImageClaim(context: Context, messageId: String, message: Any) {
        // Get the current claim count for this message
        val currentClaimCount = imageClaims.getOrDefault(messageId, 0)

        // Increment the claim count
        imageClaims[messageId] = currentClaimCount + 1

        if (currentClaimCount + 1 >= IMAGE_CLAIM_THRESHOLD) {
            // User has claimed enough times, let them remove the image
            showImageClaimSuccessDialog(context, messageId, message)
        } else {
            // Show progress to the user
            Toast.makeText(
                context,
                "Image claim registered (${currentClaimCount + 1}/$IMAGE_CLAIM_THRESHOLD). Claim this image ${IMAGE_CLAIM_THRESHOLD - (currentClaimCount + 1)} more times to remove it.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Handle copyright claim functionality
     */
    private fun handleCopyrightClaim(context: Context, messageId: String, message: Any) {
        // Get the current claim count for this message
        val currentClaimCount = copyrightClaims.getOrDefault(messageId, 0)

        // Increment the claim count
        copyrightClaims[messageId] = currentClaimCount + 1

        if (currentClaimCount + 1 >= COPYRIGHT_CLAIM_THRESHOLD) {
            // User has claimed enough times, let them remove the image
            showCopyrightClaimSuccessDialog(context, messageId, message)
        } else {
            // Show progress to the user
            Toast.makeText(
                context,
                "Copyright claim registered (${currentClaimCount + 1}/$COPYRIGHT_CLAIM_THRESHOLD). Report this image ${COPYRIGHT_CLAIM_THRESHOLD - (currentClaimCount + 1)} more times to remove it.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Show success dialog for image claim
     */
    private fun showImageClaimSuccessDialog(context: Context, messageId: String, message: Any) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Image Claim Successful")
            .setMessage("You've successfully claimed this image. Would you like to remove it?")
            .setPositiveButton("Remove") { dialog, _ ->
                removeClaimedImage(context, messageId, message)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show success dialog for copyright claim
     */
    private fun showCopyrightClaimSuccessDialog(context: Context, messageId: String, message: Any) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Copyright Claim Successful")
            .setMessage("You've successfully reported this copyrighted content. Would you like to remove it?")
            .setPositiveButton("Remove") { dialog, _ ->
                removeClaimedImage(context, messageId, message)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Remove a claimed image from a message
     */
    private fun removeClaimedImage(context: Context, messageId: String, message: Any) {
        val database = FirebaseDatabase.getInstance()

        when (message) {
            is ChatMessage -> {
                // For chat messages
                val imageRef = database.getReference("NoBallZone/chats/$messageId/imageUrl")
                imageRef.setValue("")
                    .addOnSuccessListener {
                        Toast.makeText(context, "Image removed successfully", Toast.LENGTH_SHORT).show()
                        // Clear the claim counts for this message
                        imageClaims.remove(messageId)
                        copyrightClaims.remove(messageId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to remove image", Toast.LENGTH_SHORT).show()
                    //    Log.e(TAG, "Error removing image: ${it.message}")
                    }
            }
            is MemeMessage -> {
                // For memes, we remove the meme URL (this effectively removes the image)
                val imageRef = database.getReference("NoBallZone/memes/$messageId/memeUrl")
                imageRef.setValue("")
                    .addOnSuccessListener {
                        Toast.makeText(context, "Image removed successfully", Toast.LENGTH_SHORT).show()
                        // Clear the claim counts for this message
                        imageClaims.remove(messageId)
                        copyrightClaims.remove(messageId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to remove image", Toast.LENGTH_SHORT).show()
                   //     Log.e(TAG, "Error removing image: ${it.message}")
                    }
            }
            is CommentMessage -> {
                // For comments, we need to check the parent type
                val activity = context as? CommentActivity
                if (activity != null) {
                    val parentId = activity.messageId
                    val parentType = activity.messageType

                    if (parentId.isNotEmpty() && parentType.isNotEmpty()) {
                        val dbType = when (parentType) {
                            "chat" -> "chats"
                            "poll" -> "polls"
                            "meme" -> "memes"
                            else -> ""
                        }

                        // If we have valid parent info, remove the image
                        if (dbType.isNotEmpty()) {
                            val path = "NoBallZone/$dbType/$parentId/comments/$messageId/imageUrl"
                            val commentImageRef = database.getReference(path)
                            commentImageRef.setValue("")
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Image removed successfully", Toast.LENGTH_SHORT).show()
                                    // Clear the claim counts for this message
                                    imageClaims.remove(messageId)
                                    copyrightClaims.remove(messageId)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to remove image", Toast.LENGTH_SHORT).show()
                                //    Log.e(TAG, "Error removing image: ${it.message}")
                                }
                        } else {
                            Toast.makeText(context, "Could not locate the image", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Could not locate the image", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Could not locate the image", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(context, "Unsupported message type", Toast.LENGTH_SHORT).show()
            }
        }
    }
}