package com.cricketApp.cric.Chat

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatFragment : Fragment() {

    private lateinit var binding: FragmentChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var messages: ArrayList<Any> // Can hold both ChatMessage and PollMessage
    private lateinit var database: FirebaseDatabase
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Setup RecyclerView
        messages = ArrayList()
        adapter = ChatAdapter(messages)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ChatFragment.adapter
        }

        // Setup filters
        setupFilters()

        // Load messages from Firebase
        loadMessages()

        // Setup send button
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }

        // Setup poll button
        binding.buttonPoll.setOnClickListener {
            showCreatePollDialog()
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { loadMessages() }
        binding.chipTopHits.setOnClickListener { loadTopHitMessages() }
        binding.chipPolls.setOnClickListener { loadPollsOnly() }
        binding.chipCSK.setOnClickListener { loadTeamMessages("CSK") }
    }

    private fun loadMessages() {
        val chatsRef = database.getReference("NoBallZone/chats")
        val pollsRef = database.getReference("NoBallZone/polls")

        messages.clear()

        // Load chat messages
        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (chatSnapshot in snapshot.children) {
                    val chat = chatSnapshot.getValue(ChatMessage::class.java)
                    chat?.let {
                        it.id = chatSnapshot.key ?: ""
                        messages.add(it)
                    }
                }

                // Sort messages by timestamp
                messages.sortByDescending {
                    when (it) {
                        is ChatMessage -> it.timestamp
                        is PollMessage -> it.timestamp
                        else -> 0L
                    }
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })

        // Load poll messages
        pollsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (pollSnapshot in snapshot.children) {
                    val poll = pollSnapshot.getValue(PollMessage::class.java)
                    poll?.let {
                        it.id = pollSnapshot.key ?: ""
                        messages.add(it)
                    }
                }

                // Sort messages by timestamp
                messages.sortByDescending {
                    when (it) {
                        is ChatMessage -> it.timestamp
                        is PollMessage -> it.timestamp
                        else -> 0L
                    }
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun loadTopHitMessages() {
        // Load messages with high hit count
        // Implementation similar to loadMessages() but with filtering
    }

    private fun loadPollsOnly() {
        // Load only poll messages
        // Implementation similar to loadMessages() but with filtering
    }

    private fun loadTeamMessages(team: String) {
        // Load messages for specific team
        // Implementation similar to loadMessages() but with filtering
    }

    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isEmpty()) return

        val chatRef = database.getReference("NoBallZone/chats").push()
        val chatId = chatRef.key ?: return

        val chat = ChatMessage(
            id = chatId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "Anonymous",
            team = "CSK", // Replace with user's selected team
            message = messageText,
            timestamp = System.currentTimeMillis()
        )

        chatRef.setValue(chat)
            .addOnSuccessListener {
                binding.editTextMessage.text.clear()
            }
            .addOnFailureListener {
                // Handle error
            }
    }

    private fun showCreatePollDialog() {
        val dialogView = layoutInflater.inflate(R.layout.poll_create_dialog, null)
        val editTextQuestion = dialogView.findViewById<EditText>(R.id.editTextPollQuestion)
        val editTextOption1 = dialogView.findViewById<EditText>(R.id.editTextOption1)
        val editTextOption2 = dialogView.findViewById<EditText>(R.id.editTextOption2)

        AlertDialog.Builder(requireContext())
            .setTitle("Create Poll")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val question = editTextQuestion.text.toString().trim()
                val option1 = editTextOption1.text.toString().trim()
                val option2 = editTextOption2.text.toString().trim()

                if (question.isNotEmpty() && option1.isNotEmpty() && option2.isNotEmpty()) {
                    createPoll(question, option1, option2)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPoll(question: String, option1: String, option2: String) {
        val pollRef = database.getReference("NoBallZone/polls").push()
        val pollId = pollRef.key ?: return

        val options = mutableMapOf<String, Int>()
        options[option1] = 0
        options[option2] = 0

        val poll = PollMessage(
            id = pollId,
            senderId = currentUser?.uid ?: "",
            senderName = currentUser?.displayName ?: "Anonymous",
            team = "CSK", // Replace with user's selected team
            question = question,
            options = options,
            timestamp = System.currentTimeMillis()
        )

        pollRef.setValue(poll)
            .addOnSuccessListener {
                // Poll created successfully
            }
            .addOnFailureListener {
                // Handle error
            }
    }
}