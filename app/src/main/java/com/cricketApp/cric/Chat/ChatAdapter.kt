package com.cricketApp.cric.Chat

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ItemChatBinding
import com.cricketApp.cric.databinding.ItemPollMessageBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

class ChatAdapter(private val items: List<Any>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CHAT = 0
        private const val VIEW_TYPE_POLL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ChatMessage -> VIEW_TYPE_CHAT
            is PollMessage -> VIEW_TYPE_POLL
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CHAT -> {
                val binding = ItemChatBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ChatViewHolder(binding)
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
            is ChatViewHolder -> holder.bind(items[position] as ChatMessage)
            is PollViewHolder -> holder.bind(items[position] as PollMessage)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            binding.apply {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message

                // Set reactions
                textViewFire.text = "🔥 ${chat.reactions["fire"] ?: 0}"
                textViewLaugh.text = "😂 ${chat.reactions["laugh"] ?: 0}"
                textViewCry.text = "😢 ${chat.reactions["cry"] ?: 0}"
                textViewTroll.text = "🏏 ${chat.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonHit.text = "Hit ${chat.hit}"
                buttonMiss.text = "Miss ${chat.miss}"

                // Set comment count
                textViewComments.text = "View Comments (${chat.comments.size})"

                // Set reaction click listeners
                textViewFire.setOnClickListener { addReaction(chat, "fire") }
                textViewLaugh.setOnClickListener { addReaction(chat, "laugh") }
                textViewCry.setOnClickListener { addReaction(chat, "cry") }
                textViewTroll.setOnClickListener { addReaction(chat, "troll") }

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
                    // Handle completion
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
                textViewPollName.text = poll.senderName
                textViewPollTeam.text = poll.team
                textViewPollQuestion.text = poll.question

                // Set reactions
                textViewPollFire.text = "🔥 ${poll.reactions["fire"] ?: 0}"
                textViewPollLaugh.text = "😂 ${poll.reactions["laugh"] ?: 0}"
                textViewPollCry.text = "😢 ${poll.reactions["cry"] ?: 0}"
                textViewPollTroll.text = "🏏 ${poll.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonPollHit.text = "Hit ${poll.hit}"
                buttonPollMiss.text = "Miss ${poll.miss}"

                // Set comment count
                textViewPollComments.text = "View Comments (${poll.comments.size})"

                // Set up poll options
                linearLayoutOptions.removeAllViews()
                setupPollOptions(poll)

                // Set reaction click listeners
                textViewPollFire.setOnClickListener { addReaction(poll, "fire") }
                textViewPollLaugh.setOnClickListener { addReaction(poll, "laugh") }
                textViewPollCry.setOnClickListener { addReaction(poll, "cry") }
                textViewPollTroll.setOnClickListener { addReaction(poll, "troll") }

                // Set hit/miss click listeners
                buttonPollHit.setOnClickListener { addHit(poll) }
                buttonPollMiss.setOnClickListener { addMiss(poll) }

                // Set comment click listener
                textViewPollComments.setOnClickListener { showComments(poll) }
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