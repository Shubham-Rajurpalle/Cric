package com.cricketApp.cric.Chat

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ItemPollMessageBinding
import com.cricketApp.cric.databinding.ItemReceiveChatBinding
import com.cricketApp.cric.databinding.ItemSendChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class ChatAdapter(private val items: List<Any>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SEND_CHAT = 1
        private const val VIEW_TYPE_RECEIVE_CHAT = 2
        private const val VIEW_TYPE_POLL = 3

        // Payload constants
        private const val PAYLOAD_REACTION = "reaction"
        private const val PAYLOAD_HIT_MISS = "hit_miss"
        private const val PAYLOAD_COMMENTS = "comments"
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ChatMessage -> if ((items[position] as ChatMessage).senderId == FirebaseAuth.getInstance().currentUser?.uid) VIEW_TYPE_SEND_CHAT else VIEW_TYPE_RECEIVE_CHAT
            is PollMessage -> VIEW_TYPE_POLL
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEND_CHAT -> {
                val binding = ItemSendChatBinding.inflate(inflater, parent, false)
                ChatSendViewHolder(binding)
            }
            VIEW_TYPE_RECEIVE_CHAT -> {
                val binding = ItemReceiveChatBinding.inflate(inflater, parent, false)
                ChatReceiveViewHolder(binding)
            }
            VIEW_TYPE_POLL -> {
                val binding = ItemPollMessageBinding.inflate(inflater, parent, false)
                PollViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatSendViewHolder -> holder.bind(items[position] as ChatMessage)
            is ChatReceiveViewHolder -> holder.bind(items[position] as ChatMessage)
            is PollViewHolder -> holder.bind(items[position] as PollMessage)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            // No payload, do full bind
            onBindViewHolder(holder, position)
            return
        }

        // Handle partial updates
        for (payload in payloads) {
            if (payload is String) {
                when (payload) {
                    PAYLOAD_REACTION -> {
                        when (holder) {
                            is ChatSendViewHolder -> holder.updateReactions(items[position] as ChatMessage)
                            is ChatReceiveViewHolder -> holder.updateReactions(items[position] as ChatMessage)
                            is PollViewHolder -> holder.updateReactions(items[position] as PollMessage)
                        }
                    }
                    PAYLOAD_HIT_MISS -> {
                        when (holder) {
                            is ChatSendViewHolder -> holder.updateHitMiss(items[position] as ChatMessage)
                            is ChatReceiveViewHolder -> holder.updateHitMiss(items[position] as ChatMessage)
                            is PollViewHolder -> holder.updateHitMiss(items[position] as PollMessage)
                        }
                    }
                    PAYLOAD_COMMENTS -> {
                        when (holder) {
                            is ChatSendViewHolder -> holder.updateComments(items[position] as ChatMessage)
                            is ChatReceiveViewHolder -> holder.updateComments(items[position] as ChatMessage)
                            is PollViewHolder -> holder.updateComments(items[position] as PollMessage)
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ChatSendViewHolder(private val binding: ItemSendChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            with(binding) {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message

                // Load profile picture
                loadProfilePicture(chat.senderId, binding.imageViewProfile)

                // Load team logo
                loadTeamLogo(chat.team, binding.imageViewTeam)

                // Set reaction values
                updateReactions(chat)

                // Set hit/miss counts
                updateHitMiss(chat)

                // Set comment count
                updateComments(chat)

                // Set reaction click listeners without reloading
                tvAngryEmoji.setOnClickListener { addReaction(chat, "fire", adapterPosition) }
                tvHappyEmoji.setOnClickListener { addReaction(chat, "laugh", adapterPosition) }
                tvCryingEmoji.setOnClickListener { addReaction(chat, "cry", adapterPosition) }
                tvSadEmoji.setOnClickListener { addReaction(chat, "troll", adapterPosition) }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { updateHitOrMiss(chat, "hit", adapterPosition) }
                buttonMiss.setOnClickListener { updateHitOrMiss(chat, "miss", adapterPosition) }

                // Set comment click listener
                //textViewComments.setOnClickListener { openCommentsActivity(chat.id, "chat") }
            }
        }

        fun updateReactions(chat: ChatMessage) {
            binding.tvAngryEmoji.text = "🤬 ${chat.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😁 ${chat.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${chat.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "💔 ${chat.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(chat: ChatMessage) {
            binding.buttonHit.text = "🔥 ${chat.hit}"
            binding.buttonMiss.text = "❌ ${chat.miss}"
        }

        fun updateComments(chat: ChatMessage) {
            binding.textViewComments.text = "View Comments (${chat.comments.size})"
        }
    }

    inner class ChatReceiveViewHolder(private val binding: ItemReceiveChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            with(binding) {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message

                // Load profile picture
                loadProfilePicture(chat.senderId, binding.imageViewProfile)

                // Load team logo
                loadTeamLogo(chat.team, binding.imageViewTeam)

                // Set reaction values
                updateReactions(chat)

                // Set hit/miss counts
                updateHitMiss(chat)

                // Set comment count
                updateComments(chat)

                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(chat, "fire", adapterPosition) }
                tvHappyEmoji.setOnClickListener { addReaction(chat, "laugh", adapterPosition) }
                tvCryingEmoji.setOnClickListener { addReaction(chat, "cry", adapterPosition) }
                tvSadEmoji.setOnClickListener { addReaction(chat, "troll", adapterPosition) }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { updateHitOrMiss(chat, "hit", adapterPosition) }
                buttonMiss.setOnClickListener { updateHitOrMiss(chat, "miss", adapterPosition) }

                // Set comment click listener
                //textViewComments.setOnClickListener { openCommentsActivity(chat.id, "chat") }
            }
        }

        fun updateReactions(chat: ChatMessage) {
            binding.tvAngryEmoji.text = "🤬 ${chat.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😁 ${chat.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${chat.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "💔 ${chat.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(chat: ChatMessage) {
            binding.buttonHit.text = "🔥 ${chat.hit}"
            binding.buttonMiss.text = "❌ ${chat.miss}"
        }

        fun updateComments(chat: ChatMessage) {
            binding.textViewComments.text = "View Comments (${chat.comments.size})"
        }
    }

    inner class PollViewHolder(private val binding: ItemPollMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(poll: PollMessage) {
            with(binding) {
                textViewName.text = poll.senderName
                textViewTeam.text = poll.team
                textViewMessage.text = poll.question

                // Load profile picture
                loadProfilePicture(poll.senderId, binding.imageViewProfile)

                // Load team logo
                loadTeamLogo(poll.team, binding.imageViewTeam)

                // Set reaction values
                updateReactions(poll)

                // Set hit/miss counts
                updateHitMiss(poll)

                // Set comment count
                updateComments(poll)

                // Set up poll options
                setupPollOptions(poll)

                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(poll, "fire", adapterPosition) }
                tvHappyEmoji.setOnClickListener { addReaction(poll, "laugh", adapterPosition) }
                tvCryingEmoji.setOnClickListener { addReaction(poll, "cry", adapterPosition) }
                tvSadEmoji.setOnClickListener { addReaction(poll, "troll", adapterPosition) }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { updateHitOrMiss(poll, "hit", adapterPosition) }
                buttonMiss.setOnClickListener { updateHitOrMiss(poll, "miss", adapterPosition) }

                // Set comment click listener
               // textViewComments.setOnClickListener { openCommentsActivity(poll.id, "poll") }
            }
        }

        fun updateReactions(poll: PollMessage) {
            binding.tvAngryEmoji.text = "🤬 ${poll.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😁 ${poll.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${poll.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "💔 ${poll.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(poll: PollMessage) {
            binding.buttonHit.text = "🔥 ${poll.hit}"
            binding.buttonMiss.text = "❌ ${poll.miss}"
        }

        fun updateComments(poll: PollMessage) {
            binding.textViewComments.text = "View Comments (${poll.comments.size})"
        }

        fun setupPollOptions(poll: PollMessage) {
            val context = itemView.context
            val layoutInflater = LayoutInflater.from(context)

            // Clear previous options
            binding.linearLayoutOptions.removeAllViews()

            // Calculate total votes
            val totalVotes = poll.options.values.sum()

            // Add options
            for ((option, votes) in poll.options) {
                val optionView = layoutInflater.inflate(R.layout.item_poll_option, binding.linearLayoutOptions, false)
                val radioButton = optionView.findViewById<RadioButton>(R.id.radioButtonOption)
                val textOption = optionView.findViewById<TextView>(R.id.textViewOption)
                val textPercentage = optionView.findViewById<TextView>(R.id.textViewPercentage)
                val progressBar = optionView.findViewById<ProgressBar>(R.id.progressBarOption)

                // Calculate percentage
                val percentage = if (totalVotes > 0) (votes * 100 / totalVotes) else 0

                // Set views
                radioButton.text = ""  // Clear default text
                textOption.text = option
                textPercentage.text = "$percentage%"

                // Fix progress bar to show percentage instead of loading state
                progressBar.isIndeterminate = false
                progressBar.max = 100
                progressBar.progress = percentage

                // Set click listener for option selection
                radioButton.setOnClickListener {
                    votePollOption(poll, option, adapterPosition)
                }

                binding.linearLayoutOptions.addView(optionView)
            }
        }

        private fun votePollOption(poll: PollMessage, option: String, position: Int) {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return
            val userId = currentUser.uid

            // Check if user already voted
            val pollRef = FirebaseDatabase.getInstance().getReference("NoBallZone/polls/${poll.id}")
            val votersRef = pollRef.child("voters")

            votersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // User already voted
                        val previousVote = snapshot.getValue(String::class.java)
                        if (previousVote != null && previousVote != option) {
                            // Change vote
                            pollRef.child("options").child(previousVote).addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val currentVotes = snapshot.getValue(Int::class.java) ?: 0
                                    if (currentVotes > 0) {
                                        pollRef.child("options").child(previousVote).setValue(currentVotes - 1)

                                        // Increment new option
                                        pollRef.child("options").child(option).addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(snapshot: DataSnapshot) {
                                                val newOptionVotes = snapshot.getValue(Int::class.java) ?: 0
                                                pollRef.child("options").child(option).setValue(newOptionVotes + 1)
                                                votersRef.child(userId).setValue(option)
                                            }

                                            override fun onCancelled(error: DatabaseError) {}
                                        })
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                        }
                    } else {
                        // New vote
                        pollRef.child("options").child(option).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val currentVotes = snapshot.getValue(Int::class.java) ?: 0
                                pollRef.child("options").child(option).setValue(currentVotes + 1)
                                votersRef.child(userId).setValue(option)
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // Utility method to update a reaction (without reloading)
    private fun addReaction(message: Any, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val messageId: String
        val dbPath: String

        when (message) {
            is ChatMessage -> {
                messageId = message.id
                dbPath = "NoBallZone/chats"
            }
            is PollMessage -> {
                messageId = message.id
                dbPath = "NoBallZone/polls"
            }
            else -> return
        }

        val reactionRef = FirebaseDatabase.getInstance()
            .getReference("$dbPath/$messageId/reactions/$reactionType")

        reactionRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null) {
                    // Update was successful, now update the item in our local data
                    when (message) {
                        is ChatMessage -> {
                            val currentValue = message.reactions[reactionType] ?: 0
                            message.reactions[reactionType] = currentValue + 1
                            notifyItemChanged(position, PAYLOAD_REACTION)
                        }
                        is PollMessage -> {
                            val currentValue = message.reactions[reactionType] ?: 0
                            message.reactions[reactionType] = currentValue + 1
                            notifyItemChanged(position, PAYLOAD_REACTION)
                        }
                    }
                }
            }
        })
    }

    // Utility method to update hit or miss counts
    private fun updateHitOrMiss(message: Any, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val messageId: String
        val dbPath: String

        when (message) {
            is ChatMessage -> {
                messageId = message.id
                dbPath = "NoBallZone/chats"
            }
            is PollMessage -> {
                messageId = message.id
                dbPath = "NoBallZone/polls"
            }
            else -> return
        }

        val hitMissRef = FirebaseDatabase.getInstance().getReference("$dbPath/$messageId/$type")

        hitMissRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null) {
                    // Update was successful, update local data
                    when (message) {
                        is ChatMessage -> {
                            if (type == "hit") message.hit += 1 else message.miss += 1
                            notifyItemChanged(position, PAYLOAD_HIT_MISS)
                        }
                        is PollMessage -> {
                            if (type == "hit") message.hit += 1 else message.miss += 1
                            notifyItemChanged(position, PAYLOAD_HIT_MISS)
                        }
                    }
                }
            }
        })
    }

    // Common utility method to open comments
//    private fun openCommentsActivity(messageId: String, type: String) {
//        val context = itemView.context
//        val intent = Intent(context, CommentActivity::class.java).apply {
//            putExtra("MESSAGE_ID", messageId)
//            putExtra("MESSAGE_TYPE", type)
//        }
//        context.startActivity(intent)
//    }

    // Common utility methods
    private fun loadProfilePicture(userId: String, imageView: ImageView) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profileUrl = snapshot.getValue(String::class.java)
                if (profileUrl != null) {
                    Glide.with(imageView.context)
                        .load(profileUrl)
                        .placeholder(R.drawable.profile_icon)
                        .error(R.drawable.profile_icon)
                        .circleCrop()
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile_icon)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                imageView.setImageResource(R.drawable.profile_icon)
            }
        })
    }

    private fun loadTeamLogo(teamName: String, imageView: ImageView) {
        val teamLogoMap = mapOf(
            "CSK" to R.drawable.csk,
            "MI" to R.drawable.mi,
            "RCB" to R.drawable.rcb,
            "KKR" to R.drawable.kkr,
            "DC" to R.drawable.dc,
            "SRH" to R.drawable.sh,
            "PBKS" to R.drawable.pk,
            "RR" to R.drawable.rr,
            "GT" to R.drawable.gt,
            "LSG" to R.drawable.lsg
        )
        val logoResource = teamLogoMap[teamName] ?: R.drawable.icc_logo
        imageView.setImageResource(logoResource)
    }
}