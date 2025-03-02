package com.cricketApp.cric.Chat

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ItemPollMessageBinding
import com.cricketApp.cric.databinding.ItemReceiveChatBinding
import com.cricketApp.cric.databinding.ItemSendChatBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

class ChatAdapter(private val items: List<Any>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SEND_CHAT = 0
        private const val VIEW_TYPE_RECEIVE_CHAT = 1
        private const val VIEW_TYPE_POLL = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ChatMessage -> if ((items[position] as ChatMessage).senderId == "currentUserId") VIEW_TYPE_SEND_CHAT else VIEW_TYPE_RECEIVE_CHAT
            is PollMessage -> VIEW_TYPE_POLL
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SEND_CHAT -> {
                val binding = ItemSendChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ChatSendViewHolder(binding)
            }
            VIEW_TYPE_RECEIVE_CHAT -> {
                val binding = ItemReceiveChatBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ChatReceiveViewHolder(binding)
            }
            VIEW_TYPE_POLL -> {
                val binding = ItemPollMessageBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                PollViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatSendViewHolder -> holder.bind(items[position] as ChatMessage)
            is PollViewHolder -> holder.bind(items[position] as PollMessage)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ChatSendViewHolder(private val binding: ItemSendChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            binding.apply {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message

                // Set reactions
                tvAngryEmoji.text = "🤬 ${chat.reactions["fire"] ?: 0}"
                tvHappyEmoji.text = "😁 ${chat.reactions["laugh"] ?: 0}"
                tvCryingEmoji.text = "😭 ${chat.reactions["cry"] ?: 0}"
                tvSadEmoji.text ="💔 ${chat.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonHit.text = "🔥 ${chat.hit}"
                buttonMiss.text = "❌ ${chat.miss}"

                // Set comment count
                textViewComments.text = "View Comments (${chat.comments.size})"

                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(chat, "fire") }
                tvHappyEmoji.setOnClickListener { addReaction(chat, "laugh") }
                tvCryingEmoji.setOnClickListener { addReaction(chat, "cry") }
                tvSadEmoji.setOnClickListener { addReaction(chat, "troll") }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { addHit(chat) }
                buttonMiss.setOnClickListener { addMiss(chat) }

                // Set comment click listener
                textViewComments.setOnClickListener { showComments(chat) }
            }
        }

        private fun addReaction(chat: ChatMessage, reactionType: String) {
            val chatRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/chats/${chat.id}/reactions/$reactionType")

            // Increment reaction count
            chatRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    TODO("Not yet implemented")
                }
            })
        }

        private fun addHit(chat: ChatMessage) {
            val chatRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/chats/${chat.id}/hit")

            // Increment hit count
            chatRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    // Handle completion
                }
            })
        }

        private fun addMiss(chat: ChatMessage) {
            val chatRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/chats/${chat.id}/miss")

            // Increment miss count
            chatRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    // Handle completion
                }
            })
        }

        private fun showComments(chat: ChatMessage) {
            val context = itemView.context
            val intent = Intent(context, CommentActivity::class.java).apply {
                putExtra("MESSAGE_ID", chat.id)
                putExtra("MESSAGE_TYPE", "chat")
            }
            context.startActivity(intent)
        }
    }

    inner class ChatReceiveViewHolder(private val binding: ItemReceiveChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            binding.apply {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message
                // Set reactions
                tvAngryEmoji.text = "🤬 ${chat.reactions["fire"] ?: 0}"
                tvHappyEmoji.text = "😁 ${chat.reactions["laugh"] ?: 0}"
                tvCryingEmoji.text = "😭 ${chat.reactions["cry"] ?: 0}"
                tvSadEmoji.text ="💔 ${chat.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonHit.text = "🔥 ${chat.hit}"
                buttonMiss.text = "❌ ${chat.miss}"


                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(chat, "fire") }
                tvHappyEmoji.setOnClickListener { addReaction(chat, "laugh") }
                tvCryingEmoji.setOnClickListener { addReaction(chat, "cry") }
                tvSadEmoji.setOnClickListener { addReaction(chat, "troll") }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { addHit(chat) }
                buttonMiss.setOnClickListener { addMiss(chat) }

                // Set comment click listener
                textViewComments.setOnClickListener { showComments(chat) }
            }
        }

        private fun addReaction(chat: ChatMessage, reactionType: String) {
            val chatRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/chats/${chat.id}/reactions/$reactionType")

            // Increment reaction count
            chatRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    TODO("Not yet implemented")
                }

            })
        }

        private fun addHit(chat: ChatMessage) {
            val chatRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/chats/${chat.id}/hit")

            // Increment hit count
            chatRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    // Handle completion
                }
            })
        }

        private fun addMiss(chat: ChatMessage) {
            val chatRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/chats/${chat.id}/miss")

            // Increment miss count
            chatRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    // Handle completion
                }
            })
        }

        private fun showComments(chat: ChatMessage) {
            val context = itemView.context
            val intent = Intent(context, CommentActivity::class.java).apply {
                putExtra("MESSAGE_ID", chat.id)
                putExtra("MESSAGE_TYPE", "chat")
            }
            context.startActivity(intent)
        }
    }

    inner class PollViewHolder(private val binding: ItemPollMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(poll: PollMessage) {
            binding.apply {
                textViewName.text = poll.senderName
                textViewTeam.text = poll.team
                textViewMessage.text = poll.question

                // Set reactions
                tvAngryEmoji.text = "🤬 ${poll.reactions["fire"] ?: 0}"
                tvHappyEmoji.text = "😁 ${poll.reactions["laugh"] ?: 0}"
                tvCryingEmoji.text = "😭 ${poll.reactions["cry"] ?: 0}"
                tvSadEmoji.text ="💔 ${poll.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonHit.text = "🔥 ${poll.hit}"
                buttonMiss.text = "❌ ${poll.miss}"

                // Set up poll options
                linearLayoutOptions.removeAllViews()
                setupPollOptions(poll)

                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(poll, "fire") }
                tvHappyEmoji.setOnClickListener { addReaction(poll, "laugh") }
                tvCryingEmoji.setOnClickListener { addReaction(poll, "cry") }
                tvSadEmoji.setOnClickListener { addReaction(poll, "troll") }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { addHit(poll) }
                buttonMiss.setOnClickListener { addMiss(poll) }

                // Set comment click listener
                textViewComments.setOnClickListener { showComments(poll) }
            }
        }

        private fun setupPollOptions(poll: PollMessage) {
            val context = itemView.context
            val layoutInflater = LayoutInflater.from(context)

            // Calculate total votes
            val totalVotes = poll.options.values.sum().coerceAtLeast(1)

            // Add each option
            poll.options.forEach { (option, votes) ->
                val optionView = layoutInflater.inflate(
                    R.layout.item_poll_option,
                    binding.linearLayoutOptions,
                    false
                )

                val radioButton = optionView.findViewById<RadioButton>(R.id.radioButtonOption)
                val textOption = optionView.findViewById<TextView>(R.id.textViewOption)
                val textPercentage = optionView.findViewById<TextView>(R.id.textViewPercentage)
                val progressBar = optionView.findViewById<ProgressBar>(R.id.progressBarOption)

                // Calculate percentage
                val percentage = (votes * 100 / totalVotes)

                // Set views
                textOption.text = option
                textPercentage.text = "$percentage%"
                progressBar.progress = percentage

                // Set click listener for option selection
                radioButton.setOnClickListener {
                    voteForOption(poll.id, option)
                }

                binding.linearLayoutOptions.addView(optionView)
            }
        }

        private fun voteForOption(pollId: String, option: String) {
            val optionRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/polls/$pollId/options/$option")

            // Increment vote count
            optionRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    // Handle completion
                }
            })
        }

        private fun addReaction(poll: PollMessage, reactionType: String) {
            val pollRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/polls/${poll.id}/reactions/$reactionType")

            // Increment reaction count
            pollRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    // Handle completion
                }
            })
        }

        private fun addHit(poll: PollMessage) {
            val pollRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/polls/${poll.id}/hit")

            // Increment hit count
            pollRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    // Handle completion
                }
            })
        }

        private fun addMiss(poll: PollMessage) {
            val pollRef = FirebaseDatabase.getInstance()
                .getReference("NoBallZone/polls/${poll.id}/miss")

            // Increment miss count
            pollRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = currentValue + 1
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    // Handle completion
                }
            })
        }

        private fun showComments(poll: PollMessage) {
            val context = itemView.context
            val intent = Intent(context, CommentActivity::class.java).apply {
                putExtra("MESSAGE_ID", poll.id)
                putExtra("MESSAGE_TYPE", "poll")
            }
            context.startActivity(intent)
        }
    }
}