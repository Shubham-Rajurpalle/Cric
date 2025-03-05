package com.cricketApp.cric.Chat

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.databinding.ActivityCommentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CommentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentBinding
    private lateinit var adapter: CommentAdapter
    private lateinit var comments: ArrayList<CommentMessage>
    private lateinit var database: FirebaseDatabase
    private var messageId: String = ""
    private var messageType: String = ""

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
            sendComment()
        }

        // Setup back button
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun getCommentsPath(): String {
        return if (messageType == "chat") {
            "NoBallZone/chats/$messageId/comments"
        } else {
            "NoBallZone/polls/$messageId/comments"
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

                adapter.notifyItemInserted(position)

                // Scroll to bottom
                binding.recyclerViewComments.scrollToPosition(position)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val commentId = snapshot.key ?: return
                val position = commentPositions[commentId] ?: return

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

                comments.removeAt(position)
                commentPositions.remove(commentId)

                // Update positions for all comments after this one
                for ((id, pos) in commentPositions) {
                    if (pos > position) {
                        commentPositions[id] = pos - 1
                    }
                }

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

    private fun sendComment() {
        val commentText = binding.editTextComment.text.toString().trim()
        if (commentText.isEmpty()) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // Get user's display name from Firebase
        val userRef = database.getReference("Users/${currentUser.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser.displayName
                    ?: "Anonymous"

                sendCommentWithUserName(commentText, userName)
            }

            override fun onCancelled(error: DatabaseError) {
                // Fallback to Firebase Auth display name
                sendCommentWithUserName(commentText, currentUser.displayName ?: "Anonymous")
            }
        })
    }

    private fun sendCommentWithUserName(commentText: String, userName: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val path = getCommentsPath()
        val commentRef = database.getReference(path).push()
        val commentId = commentRef.key ?: return

        val comment = CommentMessage(
            id = commentId,
            senderId = currentUser.uid,
            senderName = userName,
            message = commentText,
            timestamp = System.currentTimeMillis()
        )

        commentRef.setValue(comment)
            .addOnSuccessListener {
                binding.editTextComment.text.clear()
            }
            .addOnFailureListener {
                Log.e("CommentActivity", "Failed to send comment: ${it.message}")
            }
    }
}