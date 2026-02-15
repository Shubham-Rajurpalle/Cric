package com.cricketApp.cric.Chat

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.R
import com.cricketApp.cric.Utils.MilestoneBadgeHelper
import com.cricketApp.cric.Utils.ReactionTracker
import com.cricketApp.cric.Utils.TeamStatsUtility
import com.cricketApp.cric.databinding.ItemPollMessageBinding
import com.cricketApp.cric.databinding.ItemReceiveChatBinding
import com.cricketApp.cric.databinding.ItemSendChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.BuildConfig
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class ChatAdapter(private val items: MutableList<Any>) :
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

    // Map to keep track of message positions for efficient updates
    private val messagePositions = mutableMapOf<String, Int>()

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

    /**
     * Find a message's position by its ID
     */
    fun findPositionById(messageId: String): Int {
        for (i in 0 until items.size) {
            val itemId = when (val item = items[i]) {
                is ChatMessage -> item.id
                is PollMessage -> item.id
                else -> null
            }

            if (itemId == messageId) {
                return i
            }
        }
        return -1  // Not found
    }

    /**
     * Remove a message at the specified position
     * Optional expectedId parameter to verify we're removing the correct message
     */
    fun removeMessage(position: Int, expectedId: String? = null) {
        if (position < 0 || position >= items.size) {
             //   Log.e("ChatAdapter", "Invalid position for removal: $position, size: ${items.size}")

            return
        }

        // Get the message ID before removing it
        val actualId = when (val message = items[position]) {
            is ChatMessage -> message.id
            is PollMessage -> message.id
            else -> null
        } ?: return

        // Log before removal
            //Log.d("ChatAdapter", "About to remove message at position $position, ID: $actualId")


        // If an expected ID was provided and doesn't match, find the correct position
        if (expectedId != null && expectedId != actualId) {

              //  Log.e("ChatAdapter", "ID mismatch during removal! Expected: $expectedId, Actual: $actualId")

            // Try to find the correct position for the expected ID
            val correctPosition = findPositionById(expectedId)
            if (correctPosition != -1) {
               // Log.d("ChatAdapter", "Found expected message at position $correctPosition, removing from there")
                removeMessage(correctPosition)
                return
            } else {
               // Log.w("ChatAdapter", "Expected message $expectedId not found in list")
                return
            }
        }

        // Remove the item
        items.removeAt(position)

        // Update position mappings
        updatePositionsMap()

        // Only notify about the specific removal - no range changes
        notifyItemRemoved(position)

       // Log.d("ChatAdapter", "Removed message at position $position, ID: $actualId")
       // Log.d("ChatAdapter", "New item count: ${items.size}")
    }

    // Helper method for showing full screen images
    private fun showFullScreenImage(context: Context, imageUrl: String) {
        val intent = Intent(context, ActivityImageViewer::class.java).apply {
            putExtra("IMAGE_URL", imageUrl)
        }
        context.startActivity(intent)
    }

    inner class ChatSendViewHolder(private val binding: ItemSendChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            with(binding) {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message

                //For notification
                val badgeView = itemView.findViewById<TextView>(R.id.badgeTrending)
                if (badgeView != null) {
                    MilestoneBadgeHelper.updateMilestoneBadge(
                        badgeView = badgeView,
                        badgeText = badgeView,
                        hit = chat.hit,
                        miss = chat.miss,
                        reactions = chat.reactions
                    )
                }

                // Handle image content
                if (chat.imageUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE
                    // Load image with Glide
                    Glide.with(itemView.context)
                        .load(chat.imageUrl)
                        .into(imageViewContent)

                    // Make image clickable for full screen view
                    imageViewContent.setOnClickListener {
                        showFullScreenImage(itemView.context, chat.imageUrl)
                    }
                } else {
                    imageViewContent.visibility = View.GONE
                }

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

                tvAngryEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "fire", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvHappyEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "laugh", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvCryingEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "cry", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvSadEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "troll", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

// Update hit/miss buttons:
                buttonHit.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(chat, "hit", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate messages")
                    }
                }

                buttonMiss.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(chat, "miss", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate messages")
                    }
                }

                textViewComments.setOnClickListener {
                    val context = itemView.context

                    // Check if user is logged in first
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(context, "Login to view and add comments")
                        return@setOnClickListener
                    }

                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("MESSAGE_ID", chat.id)
                        putExtra("MESSAGE_TYPE", "chat") // Make sure type is set correctly
                    }
                    context.startActivity(intent)
                }


                // Add long-press listener for message options
                itemView.setOnLongClickListener {
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(itemView.context, "Login to access message options")
                        return@setOnLongClickListener true
                    }

                    MessageActionsHandler.showMessageOptionsBottomSheet(
                        itemView.context,
                        chat,
                        adapterPosition
                    ) { message, position, messageId ->
                        // Use the modified method that checks the ID
                        if (position != RecyclerView.NO_POSITION) {
                            removeMessage(position, messageId)
                        }
                    }
                    true // Consume the long click
                }
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
            // First check if commentCount is greater than 0, use that if available
            val count = if (chat.commentCount > 0) chat.commentCount else chat.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }
    }

    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /**
     * Show login prompt
     */
    private fun showLoginPrompt(context: Context, message: String) {
        AlertDialog.Builder(context,R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(context, SignIn::class.java)
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class ChatReceiveViewHolder(private val binding: ItemReceiveChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatMessage) {
            with(binding) {
                textViewName.text = chat.senderName
                textViewTeam.text = chat.team
                textViewMessage.text = chat.message

                // Update badge visibility based on milestone
                val badgeView = itemView.findViewById<TextView>(R.id.badgeTrending)
                if (badgeView != null) {
                    MilestoneBadgeHelper.updateMilestoneBadge(
                        badgeView = badgeView,
                        badgeText = badgeView,
                        hit = chat.hit,
                        miss = chat.miss,
                        reactions = chat.reactions
                    )
                }

                // Handle image content
                if (chat.imageUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE
                    // Load image with Glide
                    Glide.with(itemView.context)
                        .load(chat.imageUrl)
                        .into(imageViewContent)

                    // Make image clickable for full screen view
                    imageViewContent.setOnClickListener {
                        showFullScreenImage(itemView.context, chat.imageUrl)
                    }
                } else {
                    imageViewContent.visibility = View.GONE
                }

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

                tvAngryEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "fire", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvHappyEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "laugh", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvCryingEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "cry", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvSadEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(chat, "troll", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

// Update hit/miss buttons:
                buttonHit.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(chat, "hit", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate messages")
                    }
                }

                buttonMiss.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(chat, "miss", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate messages")
                    }
                }

                textViewComments.setOnClickListener {
                    val context = itemView.context

                    // Check if user is logged in first
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(context, "Login to view and add comments")
                        return@setOnClickListener
                    }

                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("MESSAGE_ID", chat.id)
                        putExtra("MESSAGE_TYPE", "chat") // Make sure type is set correctly
                    }
                    context.startActivity(intent)
                }


                // Add long-press listener for message options
                itemView.setOnLongClickListener {
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(itemView.context, "Login to access message options")
                        return@setOnLongClickListener true
                    }

                    MessageActionsHandler.showMessageOptionsBottomSheet(
                        itemView.context,
                        chat,
                        adapterPosition
                    ) { message, position, messageId ->
                        // Use the modified method that checks the ID
                        if (position != RecyclerView.NO_POSITION) {
                            removeMessage(position, messageId)
                        }
                    }
                    true // Consume the long click
                }
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
            val count = if (chat.commentCount > 0) chat.commentCount else chat.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }
    }

    inner class PollViewHolder(private val binding: ItemPollMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(poll: PollMessage) {
            with(binding) {
                textViewName.text = poll.senderName
                textViewTeam.text = poll.team
                textViewMessage.text = poll.question
                pollcount.text=poll.voters?.size.toString()+" Voters"

                // Update badge visibility based on milestone
                val badgeView = itemView.findViewById<TextView>(R.id.badgeTrending)
                if (badgeView != null) {
                    MilestoneBadgeHelper.updateMilestoneBadge(
                        badgeView = badgeView,
                        badgeText = badgeView,
                        hit = poll.hit,
                        miss = poll.miss,
                        reactions = poll.reactions
                    )
                }

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

                tvAngryEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(poll, "fire", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvHappyEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(poll, "laugh", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvCryingEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(poll, "cry", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                tvSadEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(poll, "troll", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to messages")
                    }
                }

                // Update hit/miss buttons:
                buttonHit.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(poll, "hit", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate messages")
                    }
                }

                buttonMiss.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(poll, "miss", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate messages")
                    }
                }

                textViewComments.setOnClickListener {
                    val context = itemView.context

                    // Check if user is logged in first
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(context, "Login to view and add comments")
                        return@setOnClickListener
                    }

                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("MESSAGE_ID", poll.id)
                        putExtra("MESSAGE_TYPE", "poll") // Make sure type is set correctly
                    }
                    context.startActivity(intent)
                }

                // Add long-press listener for message options
                itemView.setOnLongClickListener {
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(itemView.context, "Login to access message options")
                        return@setOnLongClickListener true
                    }

                    MessageActionsHandler.showMessageOptionsBottomSheet(
                        itemView.context,
                        poll,
                        adapterPosition
                    ) { message, position, messageId ->
                        // Use the modified method that checks the ID
                        if (position != RecyclerView.NO_POSITION) {
                            removeMessage(position, messageId)
                        }
                    }
                    true // Consume the long click
                }
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

        fun updateComments(chat: PollMessage) {
            val count = if (chat.commentCount > 0) chat.commentCount else chat.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }

        fun setupPollOptions(poll: PollMessage) {
            val context = itemView.context
            val layoutInflater = LayoutInflater.from(context)
            val currentUser = FirebaseAuth.getInstance().currentUser

            // Clear previous options
            binding.linearLayoutOptions.removeAllViews()

            // Calculate total votes
            val totalVotes = poll.options.values.sum()

            // Check if current user has voted
            val currentUserVote = if (currentUser != null && poll.voters != null) {
                poll.voters!![currentUser.uid]
            } else null

            // Keep track of all radio buttons to enforce mutual exclusivity
            val allRadioButtons = mutableListOf<RadioButton>()

            // Add options
            for ((option, votes) in poll.options) {
                val optionView = layoutInflater.inflate(R.layout.item_poll_option, binding.linearLayoutOptions, false)
                val radioButton = optionView.findViewById<RadioButton>(R.id.radioButtonOption)
                val textOption = optionView.findViewById<TextView>(R.id.textViewOption)
                val textPercentage = optionView.findViewById<TextView>(R.id.textViewPercentage)
                val progressBar = optionView.findViewById<ProgressBar>(R.id.progressBarOption)

                // Add this radio button to our list
                allRadioButtons.add(radioButton)

                // Calculate percentage
                val percentage = if (totalVotes > 0) (votes * 100 / totalVotes) else 0

                // Set views
                radioButton.text = ""  // Clear default text
                textOption.text = option
                textPercentage.text = "$percentage%"

                // Check if this is the user's selection
                val isUserChoice = option == currentUserVote
                radioButton.isChecked = isUserChoice

                // Style the selected option differently
                if (isUserChoice) {
                    textOption.setTextColor(ContextCompat.getColor(context, R.color.grey))
                    textOption.setTypeface(null, Typeface.BOLD)
                } else {
                    textOption.setTextColor(ContextCompat.getColor(context, R.color.white))
                    textOption.setTypeface(null, Typeface.NORMAL)
                }

                // Set up progress bar
                progressBar.isIndeterminate = false
                progressBar.max = 100
                progressBar.progress = percentage

                // Set direct click listener for the radio button
                radioButton.setOnClickListener {
                    if (currentUser != null && option != currentUserVote) {
                        // Update UI immediately
                        for (rb in allRadioButtons) {
                            rb.isChecked = false
                        }
                        radioButton.isChecked = true

                        // Update Firebase and refresh UI
                        votePoll(itemView,poll, option, adapterPosition)
                    }
                }

                // Set click listener for the whole row
                optionView.setOnClickListener {
                    if (currentUser != null) {
                        if (option != currentUserVote) {
                            // Update UI immediately
                            for (rb in allRadioButtons) {
                                rb.isChecked = false
                            }
                            radioButton.isChecked = true

                            // Update Firebase and refresh UI
                            votePoll(itemView,poll, option, adapterPosition)
                        }
                    } else {
                        Toast.makeText(context, "Please log in to vote", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.linearLayoutOptions.addView(optionView)
            }
        }
    }

    // Add this function at the same level as addReaction and updateHitOrMiss
    private fun votePoll(itemView:View,poll: PollMessage, selectedOption: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        // Check if user is logged in first
        if (!isUserLoggedIn()) {
            showLoginPrompt(itemView.context, "Login to vote in polls")
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        // Get poll reference
        val pollRef = FirebaseDatabase.getInstance().getReference("NoBallZone/polls/${poll.id}")

        // Get previous vote if any
        val previousVote = poll.voters?.get(userId)

        // Update options map
        if (previousVote != null && previousVote != selectedOption) {
            // Handle previous vote
            val prevRef = pollRef.child("options").child(previousVote)
            prevRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(Int::class.java) ?: 0
                    if (currentValue > 0) {
                        mutableData.value = currentValue - 1
                    }
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    // Just log completion
                    if (error != null) {
                       // Log.e("PollVoting", "Error decrementing previous vote: ${error.message}")
                    }
                }
            })
        }

        // Increment selected option
        val selectedRef = pollRef.child("options").child(selectedOption)
        selectedRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentValue = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = currentValue + 1
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null) {
                    // Once option is updated, update voters map
                    pollRef.child("voters").child(userId).setValue(selectedOption)
                        .addOnSuccessListener {
                            // On success, trigger UI refresh by notifying the adapter
                            // This will do a full bind for all fields
                            if (position < items.size) {
                                notifyItemChanged(position)
                            }
                        }
                } else if (error != null) {
                   // Log.e("PollVoting", "Error updating vote: ${error.message}")
                }
            }
        })
    }

    // Utility method to update a reaction (fixed to prevent double counting)
    private fun addReaction(message: Any, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val messageId: String
        val contentType: ReactionTracker.ContentType

        when (message) {
            is ChatMessage -> {
                messageId = message.id
                contentType = ReactionTracker.ContentType.CHAT
            }
            is PollMessage -> {
                messageId = message.id
                contentType = ReactionTracker.ContentType.POLL
            }
            else -> return
        }

        // Use ReactionTracker to handle the reaction
        ReactionTracker.addEmojiReaction(
            contentType = contentType,
            contentId = messageId,
            reactionType = reactionType
        ) { success, newValue ->
            if (success) {
                when (message) {
                    is ChatMessage -> {
                        message.reactions[reactionType] = newValue
                        notifyItemChanged(position, PAYLOAD_REACTION)
                    }
                    is PollMessage -> {
                        message.reactions[reactionType] = newValue
                        notifyItemChanged(position, PAYLOAD_REACTION)
                    }
                }
            }
        }
    }

    // Utility method to update hit or miss counts (fixed to prevent double counting)
    private fun updateHitOrMiss(message: Any, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val isHit = type == "hit"
        val messageId: String
        val contentType: ReactionTracker.ContentType

        when (message) {
            is ChatMessage -> {
                messageId = message.id
                contentType = ReactionTracker.ContentType.CHAT
            }
            is PollMessage -> {
                messageId = message.id
                contentType = ReactionTracker.ContentType.POLL
            }
            else -> return
        }

        // Use ReactionTracker to handle the hit/miss
        ReactionTracker.updateHitOrMiss(
            contentType = contentType,
            contentId = messageId,
            isHit = isHit
        ) { success, newValue ->
            if (success) {
                when (message) {
                    is ChatMessage -> {
                        if (isHit) message.hit = newValue else message.miss = newValue
                        notifyItemChanged(position, PAYLOAD_HIT_MISS)
                    }
                    is PollMessage -> {
                        if (isHit) message.hit = newValue else message.miss = newValue
                        notifyItemChanged(position, PAYLOAD_HIT_MISS)
                    }
                }
            }
        }
    }

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


    // Update the positions map (called after any list changes)
    fun updatePositionsMap() {
        messagePositions.clear()
        items.forEachIndexed { index, message ->
            when (message) {
                is ChatMessage -> messagePositions[message.id] = index
                is PollMessage -> messagePositions[message.id] = index
            }
        }
    }
}