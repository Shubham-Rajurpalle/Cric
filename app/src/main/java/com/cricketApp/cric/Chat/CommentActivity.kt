package com.cricketApp.cric.Chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Meme.CloudVisionSafetyChecker
import com.cricketApp.cric.Moderation.ChatModerationService
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivityCommentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentBinding
    private lateinit var adapter: CommentAdapter
    private lateinit var comments: ArrayList<CommentMessage>
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: StorageReference
    private lateinit var moderationService: ChatModerationService
    private lateinit var safetyChecker: CloudVisionSafetyChecker

    // Make these properties public so MessageActionsHandler can access them
    var messageId: String = ""
    var messageType: String = ""

    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    // Login request code
    private val LOGIN_REQUEST_CODE = 1001

    // Map to keep track of comment positions for efficient updates
    private val commentPositions = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize moderation service
        moderationService = ChatModerationService(this)

        // Initialize safety checker for images
        safetyChecker = CloudVisionSafetyChecker(this)

        // Get message details from intent
        messageId = intent.getStringExtra("MESSAGE_ID") ?: ""
        messageType = intent.getStringExtra("MESSAGE_TYPE") ?: ""

        if (messageId.isEmpty() || messageType.isEmpty()) {
            Toast.makeText(this, "Error: Invalid message data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("CommentActivity", "Loading comments for $messageType ID: $messageId")

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Initialize Firebase Storage
        val storage = FirebaseStorage.getInstance()
        storageRef = storage.reference

        // Setup RecyclerView
        comments = ArrayList()
        adapter = CommentAdapter(comments, messageId, messageType)
        binding.recyclerViewComments.apply {
            layoutManager = LinearLayoutManager(this@CommentActivity)
            adapter = this@CommentActivity.adapter
        }

        // Setup real-time comments listener
        setupCommentsListener()

        // Load initial comments
        loadInitialComments()

        // Update UI based on login status
        updateUIForLoginStatus()

        // Setup send button
        binding.buttonSendComment.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to add comments")
                return@setOnClickListener
            }

            if (selectedImageUri != null) {
                checkImageSafetyAndUpload()
            } else {
                val commentText = binding.editTextComment.text.toString().trim()
                if (commentText.isNotEmpty()) {
                    checkAndSendComment(commentText)
                }
            }
        }

        // Setup image button
        binding.buttonMeme.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to share images")
                return@setOnClickListener
            }

            openImagePicker()
        }

        // Setup back button
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    private fun updateUIForLoginStatus() {
        val isLoggedIn = isUserLoggedIn()

        if (isLoggedIn) {
            binding.editTextComment.hint = "Write a comment..."
        } else {
            binding.editTextComment.hint = "Login to comment..."
        }
    }

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(this, SignIn::class.java)
                startActivityForResult(intent, LOGIN_REQUEST_CODE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCommentsPath(): String {
        // Fix for poll comments - ensure the correct path is used
        return when (messageType) {
            "chat" -> "NoBallZone/chats/$messageId/comments"
            "poll" -> "NoBallZone/polls/$messageId/comments"
            "meme" -> "NoBallZone/memes/$messageId/comments"
            else -> {
                Log.e("CommentActivity", "Unknown message type: $messageType, defaulting to chats")
                "NoBallZone/chats/$messageId/comments" // Default path
            }
        }
    }

    private fun setupCommentsListener() {
        val commentsRef = database.getReference(getCommentsPath())
        Log.d("CommentActivity", "Setting up listener at path: ${getCommentsPath()}")

        commentsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Skip if already in our list (from initial load)
                val commentId = snapshot.key ?: return
                if (commentPositions.containsKey(commentId)) return

                val comment = snapshot.getValue(CommentMessage::class.java) ?: return
                comment.id = commentId

                // Add to the list (at the end for chronological order)
                comments.add(comment)
                val position = comments.size - 1
                commentPositions[commentId] = position

                // Notify adapter about the inserted item
                if (position >= 0 && position < comments.size) {
                    adapter.notifyItemInserted(position)
                }

                // Scroll to bottom
                binding.recyclerViewComments.scrollToPosition(position)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val commentId = snapshot.key ?: return
                val position = commentPositions[commentId] ?: return
                if (position < 0 || position >= comments.size) return

                val updatedComment = snapshot.getValue(CommentMessage::class.java) ?: return
                updatedComment.id = commentId

                val currentComment = comments[position]

                // Check what changed and update accordingly
                if (currentComment.reactions != updatedComment.reactions) {
                    currentComment.reactions = updatedComment.reactions
                    adapter.notifyItemChanged(position, "reaction")
                }

                if (currentComment.hit != updatedComment.hit || currentComment.miss != updatedComment.miss) {
                    currentComment.hit = updatedComment.hit
                    currentComment.miss = updatedComment.miss
                    adapter.notifyItemChanged(position, "hit_miss")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val commentId = snapshot.key ?: return
                val position = commentPositions[commentId] ?: return
                if (position < 0 || position >= comments.size) return

                comments.removeAt(position)
                commentPositions.remove(commentId)

                // Update positions for all comments after this one
                for ((id, pos) in commentPositions) {
                    if (pos > position) {
                        commentPositions[id] = pos - 1
                    }
                }

                // Notify adapter about the removed item
                adapter.notifyItemRemoved(position)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not handling moves for comments
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommentActivity", "Error with comments listener: ${error.message}")
            }
        })
    }

    private fun loadInitialComments() {
        val commentsRef = database.getReference(getCommentsPath())
        Log.d("CommentActivity", "Loading initial comments from path: ${getCommentsPath()}")

        commentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                comments.clear()
                commentPositions.clear()

                Log.d("CommentActivity", "Found ${snapshot.childrenCount} comments")

                for (commentSnapshot in snapshot.children) {
                    val comment = commentSnapshot.getValue(CommentMessage::class.java)
                    comment?.let {
                        it.id = commentSnapshot.key ?: ""
                        comments.add(it)
                    }
                }

                // Sort comments by timestamp
                comments.sortBy { it.timestamp }

                // Update positions map
                comments.forEachIndexed { index, comment ->
                    commentPositions[comment.id] = index
                }

                // Notify adapter about the dataset change
                adapter.notifyDataSetChanged()

                // Scroll to bottom
                if (comments.isNotEmpty()) {
                    binding.recyclerViewComments.scrollToPosition(comments.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommentActivity", "Error loading comments: ${error.message}")
                Toast.makeText(this@CommentActivity, "Failed to load comments", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // New method to check message content before sending
    private fun checkAndSendComment(commentText: String) {
        // Show loading indicator
        binding.buttonSendComment.isEnabled = false
        binding.editTextComment.isEnabled = false

        // Since button is an ImageButton, we use alpha to indicate loading state
        binding.buttonSendComment.alpha = 0.5f

        val progressBar = binding.progressSending
        if (progressBar != null) {
            progressBar.visibility = View.VISIBLE
        }

        moderationService.checkMessageContent(commentText, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(message: String) {
                // Run on UI thread as the callback might be on a background thread
                runOnUiThread {
                    // Message is approved, send it
                    sendComment(message)

                    // Reset UI
                    binding.buttonSendComment.isEnabled = true
                    binding.buttonSendComment.alpha = 1.0f
                    binding.editTextComment.isEnabled = true
                    if (progressBar != null) {
                        progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onMessageRejected(message: String, reason: String) {
                runOnUiThread {
                    // Show feedback to user about rejection
                    Toast.makeText(this@CommentActivity, reason, Toast.LENGTH_LONG).show()

                    // For longer/detailed feedback, show dialog
                    MaterialAlertDialogBuilder(this@CommentActivity)
                        .setTitle("Content Not Allowed")
                        .setMessage("Your comment contains language that doesn't meet our community guidelines. Please revise your message and try again.")
                        .setPositiveButton("OK", null)
                        .show()

                    // Reset UI
                    binding.buttonSendComment.isEnabled = true
                    binding.buttonSendComment.alpha = 1.0f
                    binding.editTextComment.isEnabled = true
                    if (progressBar != null) {
                        progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onError(errorMessage: String) {
                runOnUiThread {
                    Log.e("CommentActivity", "Moderation error: $errorMessage")

                    // On error, we still allow the comment to be posted
                    sendComment(commentText)

                    // Subtle notification to user
                    Toast.makeText(
                        this@CommentActivity,
                        "Continuing with your comment...",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Reset UI
                    binding.buttonSendComment.isEnabled = true
                    binding.buttonSendComment.alpha = 1.0f
                    binding.editTextComment.isEnabled = true
                    if (progressBar != null) {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun openImagePicker() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            // Show image safety check and preview
            checkImageSafetyAndUpload()
        } else if (requestCode == LOGIN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // User has successfully logged in
            updateUIForLoginStatus()
            Toast.makeText(this, "Login successful! You can now comment.", Toast.LENGTH_SHORT).show()
        }
    }

    // Method to check image safety before upload
    private fun checkImageSafetyAndUpload() {
        if (selectedImageUri == null || !isUserLoggedIn()) {
            return
        }

        // Create loading dialog
        val progressView = View.inflate(this, R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Checking content safety...")
            .create()

        progressDialog.show()

        // Check image content safety using Cloud Vision API
        selectedImageUri?.let { uri ->
            // Using coroutines for the async API call
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        safetyChecker.checkImageSafety(uri)
                    }

                    progressDialog.dismiss()

                    if (result.isSafe) {
                        // Image is safe, proceed with upload
                        showImagePreviewDialog()
                    } else if (result.autoBlock) {
                        // Image is NOT safe and should be automatically blocked
                        showContentBlockedDialog(result.issues)
                    } else {
                        // Image has potential issues but is not automatically blocked
                        showContentWarningDialog(result.issues)
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Log.e("CommentActivity", "Error checking image safety: ${e.message}", e)
                    // Show error dialog
                    MaterialAlertDialogBuilder(this@CommentActivity)
                        .setTitle("Error")
                        .setMessage("Failed to analyze image: ${e.message}")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        } ?: run {
            progressDialog.dismiss()
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContentBlockedDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image has been blocked because our system detected prohibited content."
        } else {
            "This image has been blocked because our system detected:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Content Blocked")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    private fun showContentWarningDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image may contain inappropriate content."
        } else {
            "This image may contain inappropriate content:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Content Warning")
            .setMessage(message)
            .setPositiveButton("Upload Anyway") { dialog, _ ->
                dialog.dismiss()
                showImagePreviewDialog()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    // Modified showImagePreviewDialog to integrate caption moderation
    private fun showImagePreviewDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
        val editTextCaption = dialogView.findViewById<EditText>(R.id.editTextCaption)

        // Load image into preview with enhanced Glide configuration
        Glide.with(this)
            .load(selectedImageUri)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.loading)
                    .error(R.drawable.loading)
            )
            .into(imagePreview)

        // Show dialog with preview and send button
        AlertDialog.Builder(this, R.style.CustomAlertForImagePreview)
            .setTitle("Send Image")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val caption = editTextCaption.text.toString().trim()

                // Create loading dialog for caption check
                val progressView = View.inflate(this, R.layout.loading_indicator, null)
                val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
                val progressDialog = MaterialAlertDialogBuilder(this)
                    .setView(progressView)
                    .setCancelable(false)
                    .setTitle("Checking caption...")
                    .create()

                progressDialog.show()

                // Use the caption moderation function
                checkCaptionAndUploadImage(caption, progressDialog)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                selectedImageUri = null
                dialog.dismiss()
            }
            .create()
            .show()
    }

    // Enhanced caption moderation for image uploads
    private fun checkCaptionAndUploadImage(caption: String, progressDialog: AlertDialog) {
        if (caption.isEmpty()) {
            // No caption to check, proceed with upload
            uploadAndSendImage("")
            progressDialog.dismiss()
            return
        }

        // Check caption content
        moderationService.checkMessageContent(caption, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(message: String) {
                runOnUiThread {
                    progressDialog.dismiss()
                    uploadAndSendImage(message)
                }
            }

            override fun onMessageRejected(message: String, reason: String) {
                runOnUiThread {
                    progressDialog.dismiss()

                    // More detailed rejection feedback
                    MaterialAlertDialogBuilder(this@CommentActivity, R.style.CustomAlertDialogTheme)
                        .setTitle("Caption Not Allowed")
                        .setMessage("The caption contains content that doesn't meet our community guidelines. Would you like to upload the image without a caption?")
                        .setPositiveButton("Upload Without Caption") { dialog, _ ->
                            uploadAndSendImage("")
                            dialog.dismiss()
                        }
                        .setNegativeButton("Edit Caption") { dialog, _ ->
                            // Show the dialog again for editing
                            dialog.dismiss()
                            showImagePreviewDialog()
                        }
                        .setNeutralButton("Cancel") { dialog, _ ->
                            selectedImageUri = null
                            dialog.dismiss()
                        }
                        .show()
                }
            }

            override fun onError(errorMessage: String) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Log.e("CommentActivity", "Moderation error: $errorMessage")

                    // On error, just send with original caption
                    uploadAndSendImage(caption)
                }
            }
        })
    }

    private fun uploadAndSendImage(caption: String) {
        if (selectedImageUri == null || !isUserLoggedIn()) {
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // Show loading indicator
        val progressView = View.inflate(this, R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Uploading image...")
            .create()

        progressDialog.show()

        // Create a reference to the image file in Firebase Storage
        val imageRef = storageRef.child("comment_images/${System.currentTimeMillis()}_${currentUser.uid}.jpg")

        // Upload the image
        imageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener { taskSnapshot ->
                // Get the download URL
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Send comment with image
                    sendCommentWithImage(caption, uri.toString())

                    // Clear selected image and reset UI
                    selectedImageUri = null
                    binding.editTextComment.hint = "Write a comment..."
                    binding.editTextComment.text.clear()

                    // Dismiss loading dialog
                    progressDialog.dismiss()
                }
                    .addOnFailureListener { e ->
                        // Handle error getting download URL
                        Toast.makeText(
                            this,
                            "Failed to get download URL: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        progressDialog.dismiss()
                    }
            }
            .addOnFailureListener { e ->
                // Handle error with upload
                Toast.makeText(
                    this,
                    "Failed to upload image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                progressDialog.dismiss()
            }
    }

    private fun sendCommentWithImage(caption: String, imageUrl: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // Get user's info from Firebase
        val userRef = database.getReference("Users/${currentUser.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser.displayName
                    ?: "Anonymous"

                val team = snapshot.child("iplTeam").getValue(String::class.java) ?: "No Team"

                // Create and send the comment with image
                val path = getCommentsPath()
                val commentRef = database.getReference(path).push()
                val commentId = commentRef.key ?: return

                val comment = CommentMessage(
                    id = commentId,
                    senderId = currentUser.uid,
                    senderName = userName,
                    team = team,
                    message = caption,
                    imageUrl = imageUrl,
                    timestamp = System.currentTimeMillis()
                )

                commentRef.setValue(comment)
                    .addOnSuccessListener {
                        updateParentMessageCommentCount()
                    }
                    .addOnFailureListener {
                        Log.e("CommentActivity", "Failed to send comment with image: ${it.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommentActivity", "Failed to get user data: ${error.message}")
            }
        })
    }

    private fun sendComment(commentText: String) {
        if (commentText.isEmpty()) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // Get user's display name and team from Firebase
        val userRef = database.getReference("Users/${currentUser.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser.displayName
                    ?: "Anonymous"

                val team = snapshot.child("iplTeam").getValue(String::class.java) ?: "No Team"

                sendCommentWithUserInfo(commentText, userName, team)
            }

            override fun onCancelled(error: DatabaseError) {
                // Fallback to Firebase Auth display name
                sendCommentWithUserInfo(
                    commentText,
                    currentUser.displayName ?: "Anonymous",
                    "No Team"
                )
            }
        })
    }

    private fun sendCommentWithUserInfo(commentText: String, userName: String, team: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val path = getCommentsPath()
        val commentRef = database.getReference(path).push()
        val commentId = commentRef.key ?: return

        val comment = CommentMessage(
            id = commentId,
            senderId = currentUser.uid,
            senderName = userName,
            team = team,
            message = commentText,
            timestamp = System.currentTimeMillis()
        )

        commentRef.setValue(comment)
            .addOnSuccessListener {
                binding.editTextComment.text.clear()

                // Update the total comment count in the parent message
                updateParentMessageCommentCount()
            }
            .addOnFailureListener {
                Log.e("CommentActivity", "Failed to send comment: ${it.message}")
            }
    }

    private fun updateParentMessageCommentCount() {
        val parentRef = when (messageType) {
            "chat" -> database.getReference("NoBallZone/chats/$messageId")
            "poll" -> database.getReference("NoBallZone/polls/$messageId")
            "meme" -> database.getReference("NoBallZone/memes/$messageId")
            else -> {
                Log.e("CommentActivity", "Unknown message type when updating count: $messageType")
                database.getReference("NoBallZone/chats/$messageId") // Default path
            }
        }

        // Get the current comments count
        val commentsRef = database.getReference(getCommentsPath())
        commentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val commentsCount = snapshot.childrenCount.toInt()

                // Update the parent message with this count using transaction
                // to ensure atomic update and trigger change listeners
                parentRef.child("commentCount").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        mutableData.value = commentsCount
                        return Transaction.success(mutableData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        dataSnapshot: DataSnapshot?
                    ) {
                        if (error != null) {
                            Log.e(
                                "CommentActivity",
                                "Error updating comment count: ${error.message}"
                            )
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CommentActivity", "Error updating comment count: ${error.message}")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateUIForLoginStatus()
    }
}