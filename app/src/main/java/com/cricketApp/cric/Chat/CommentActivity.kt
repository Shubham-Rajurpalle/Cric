package com.cricketApp.cric.Chat

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
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

class CommentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentBinding
    private lateinit var adapter: CommentAdapter
    private lateinit var comments: ArrayList<CommentMessage>
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: StorageReference

    // Make these properties public so MessageActionsHandler can access them
    var messageId: String = ""
    var messageType: String = ""

    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    // Map to keep track of comment positions for efficient updates
    private val commentPositions = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get message details from intent
        messageId = intent.getStringExtra("MESSAGE_ID") ?: ""
        messageType = intent.getStringExtra("MESSAGE_TYPE") ?: ""

        if (messageId.isEmpty() || messageType.isEmpty()) {
            finish()
            return
        }

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

        // Setup send button
        binding.buttonSendComment.setOnClickListener {
            if (selectedImageUri != null) {
                uploadAndSendImage()
            } else {
                sendComment()
            }
        }

        // Setup image button
        binding.buttonMeme.setOnClickListener {
            openImagePicker()
        }

        // Setup back button
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun getCommentsPath(): String {
        return when (messageType) {
            "chat" -> "NoBallZone/chats/$messageId/comments"
            "poll" -> "NoBallZone/polls/$messageId/comments"
            "meme" -> "NoBallZone/memes/$messageId/comments"
            else -> throw IllegalArgumentException("Unknown message type")
        }
    }

    private fun setupCommentsListener() {
        val commentsRef = database.getReference(getCommentsPath())

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

        commentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                comments.clear()
                commentPositions.clear()

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

            // Show preview of selected image
            uploadAndSendImage()
        }
    }

    private fun uploadAndSendImage() {
        if (selectedImageUri == null || FirebaseAuth.getInstance().currentUser == null) {
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // Show the image preview dialog
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
        AlertDialog.Builder(this)
            .setTitle("Send Image")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val caption = editTextCaption.text.toString().trim()

                // Show loading indicator
                val progressView = View.inflate(this, R.layout.loading_indicator, null)
                val progressIndicator =
                    progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)

                val progressDialog = MaterialAlertDialogBuilder(this)
                    .setView(progressView)
                    .setCancelable(false)
                    .setTitle("Uploading image...")
                    .create()

                progressDialog.show()

                // Create a reference to the image file in Firebase Storage
                val imageRef =
                    storageRef.child("comment_images/${System.currentTimeMillis()}_${currentUser.uid}.jpg")

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
            .setNegativeButton("Cancel") { dialog, _ ->
                selectedImageUri = null
                dialog.dismiss()
            }
            .create()
            .show()
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

    private fun sendComment() {
        val commentText = binding.editTextComment.text.toString().trim()
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
            else -> throw IllegalArgumentException("Unknown message type")
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
}