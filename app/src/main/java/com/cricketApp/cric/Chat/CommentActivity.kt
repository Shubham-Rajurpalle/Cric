package com.cricketApp.cric.Chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.databinding.ActivityCommentBinding
import com.google.firebase.auth.FirebaseAuth
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
        adapter = CommentAdapter(comments)
        binding.recyclerViewComments.apply {
            layoutManager = LinearLayoutManager(this@CommentActivity)
            adapter = this@CommentActivity.adapter
        }

        // Load comments
        loadComments()

        // Setup send button
        binding.buttonSendComment.setOnClickListener {
            sendComment()
        }

        // Setup back button
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun loadComments() {
        val path = if (messageType == "chat") {
            "NoBallZone/chats/$messageId/comments"
        } else {
            "NoBallZone/polls/$messageId/comments"
        }

        val commentsRef = database.getReference(path)

        commentsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                comments.clear()

                for (commentSnapshot in snapshot.children) {
                    val comment = commentSnapshot.getValue(CommentMessage::class.java)
                    comment?.let {
                        it.id = commentSnapshot.key ?: ""
                        comments.add(it)
                    }
                }

                // Sort comments by timestamp
                comments.sortBy { it.timestamp }

                adapter.notifyDataSetChanged()

                // Scroll to bottom
                if (comments.isNotEmpty()) {
                    binding.recyclerViewComments.scrollToPosition(comments.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun sendComment() {
        val commentText = binding.editTextComment.text.toString().trim()
        if (commentText.isEmpty()) return

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val path = if (messageType == "chat") {
            "NoBallZone/chats/$messageId/comments"
        } else {
            "NoBallZone/polls/$messageId/comments"
        }

        val commentRef = database.getReference(path).push()
        val commentId = commentRef.key ?: return

        val comment = CommentMessage(
            id = commentId,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Anonymous",
            message = commentText,
            timestamp = System.currentTimeMillis()
        )

        commentRef.setValue(comment)
            .addOnSuccessListener {
                binding.editTextComment.text.clear()
            }
            .addOnFailureListener {
                // Handle error
            }
    }
}