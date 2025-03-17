package com.cricketApp.cric.Meme

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.ActivityImageViewer
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.MessageActionsHandler
import com.cricketApp.cric.R
import com.cricketApp.cric.Utils.TeamStatsUtility
import com.cricketApp.cric.databinding.ItemMemeReceiveBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import android.util.Log
import android.widget.Toast
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.databinding.ItemMemeSendBinding

class MemeAdapter(
    private val items: MutableList<MemeMessage>,
    private val onCommentClickListener: ((MemeMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    companion object {
        private const val VIEW_TYPE_SEND_MEME = 1
        private const val VIEW_TYPE_RECEIVE_MEME = 2

        // Payload constants
        private const val PAYLOAD_REACTION = "reaction"
        private const val PAYLOAD_HIT_MISS = "hit_miss"
        private const val PAYLOAD_COMMENTS = "comments"
        private const val TAG = "MemeAdapter"
    }

    // Map to keep track of meme positions
    private val memePositions = mutableMapOf<String, Int>()

    override fun getItemViewType(position: Int): Int {
        return if (items[position].senderId == FirebaseAuth.getInstance().currentUser?.uid)
            VIEW_TYPE_SEND_MEME
        else
            VIEW_TYPE_RECEIVE_MEME
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEND_MEME -> {
                val binding = ItemMemeSendBinding.inflate(inflater, parent, false)
                MemeSendViewHolder(binding)
            }
            VIEW_TYPE_RECEIVE_MEME -> {
                val binding = ItemMemeReceiveBinding.inflate(inflater, parent, false)
                MemeReceiveViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MemeSendViewHolder -> holder.bind(items[position])
            is MemeReceiveViewHolder -> holder.bind(items[position])
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        // Handle partial updates
        for (payload in payloads) {
            if (payload is String) {
                when (payload) {
                    PAYLOAD_REACTION -> {
                        when (holder) {
                            is MemeSendViewHolder -> holder.updateReactions(items[position])
                            is MemeReceiveViewHolder -> holder.updateReactions(items[position])
                        }
                    }
                    PAYLOAD_HIT_MISS -> {
                        when (holder) {
                            is MemeSendViewHolder -> holder.updateHitMiss(items[position])
                            is MemeReceiveViewHolder -> holder.updateHitMiss(items[position])
                        }
                    }
                    PAYLOAD_COMMENTS -> {
                        when (holder) {
                            is MemeSendViewHolder -> holder.updateComments(items[position])
                            is MemeReceiveViewHolder -> holder.updateComments(items[position])
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * Find a meme's position by its ID
     */
    fun findPositionById(memeId: String): Int {
        for (i in 0 until items.size) {
            if (items[i].id == memeId) {
                return i
            }
        }
        return -1  // Not found
    }

    /**
     * Remove a meme at the specified position
     */
    fun removeMeme(position: Int, expectedId: String? = null) {
        if (position < 0 || position >= items.size) {
            Log.e(TAG, "Invalid position for removal: $position, size: ${items.size}")
            return
        }

        // Get the meme ID before removing it
        val actualId = items[position].id

        // Log before removal
        Log.d(TAG, "About to remove meme at position $position, ID: $actualId")

        // If an expected ID was provided and doesn't match, find the correct position
        if (expectedId != null && expectedId != actualId) {
            Log.e(TAG, "ID mismatch during removal! Expected: $expectedId, Actual: $actualId")

            // Try to find the correct position for the expected ID
            val correctPosition = findPositionById(expectedId)
            if (correctPosition != -1) {
                Log.d(TAG, "Found expected meme at position $correctPosition, removing from there")
                removeMeme(correctPosition)
                return
            } else {
                Log.w(TAG, "Expected meme $expectedId not found in list")
                return
            }
        }

        // Remove the item
        items.removeAt(position)

        // Update position mappings
        updatePositionsMap()

        // Notify about the specific removal
        notifyItemRemoved(position)

        Log.d(TAG, "Removed meme at position $position, ID: $actualId")
        Log.d(TAG, "New item count: ${items.size}")
    }

    /**
     * Update the positions map (called after list changes)
     */
    private fun updatePositionsMap() {
        memePositions.clear()
        items.forEachIndexed { index, meme ->
            memePositions[meme.id] = index
        }
    }

    private fun showFullScreenImage(context: Context, imageUrl: String) {
        val intent = Intent(context, ActivityImageViewer::class.java).apply {
            putExtra("IMAGE_URL", imageUrl)
        }
        context.startActivity(intent)
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

    inner class MemeSendViewHolder(private val binding: ItemMemeSendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meme: MemeMessage) {
            with(binding) {
                textViewName.text = meme.senderName
                textViewTeam.text = meme.team

                // Handle meme image
                if (meme.memeUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(meme.memeUrl)
                        .into(imageViewContent)

                    // Make image clickable for full screen view
                    imageViewContent.setOnClickListener {
                        showFullScreenImage(itemView.context, meme.memeUrl)
                    }
                } else {
                    imageViewContent.visibility = View.GONE
                }

                // Load profile picture
                loadProfilePicture(meme.senderId, binding.imageViewProfile)

                // Load team logo
                loadTeamLogo(meme.team, binding.imageViewTeam)

                // Set reaction values
                updateReactions(meme)

                // Set hit/miss counts
                updateHitMiss(meme)

                // Set comment count
                updateComments(meme)

                // Set reaction click listeners without reloading
                tvAngryEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "fire", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                tvHappyEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "laugh", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                tvCryingEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "cry", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                tvSadEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "troll", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                // Set hit/miss click listeners with login checks
                buttonHit.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(meme, "hit", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate memes")
                    }
                }

                buttonMiss.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(meme, "miss", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate memes")
                    }
                }

                textViewComments.setOnClickListener {
                    val context = itemView.context

                    // First check if the user is logged in
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(context, "Login to view and add comments")
                        return@setOnClickListener
                    }

                    // Either use the provided callback or open the comment activity directly
                    if (onCommentClickListener != null) {
                        onCommentClickListener.invoke(meme)
                    } else {
                        try {
                            Log.d(TAG, "Opening comments for meme ID: ${meme.id}")
                            val intent = Intent(context, CommentActivity::class.java).apply {
                                putExtra("MESSAGE_ID", meme.id)
                                putExtra("MESSAGE_TYPE", "meme")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening comments: ${e.message}", e)
                            Toast.makeText(context, "Unable to open comments", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Add long-press listener for message options
                itemView.setOnLongClickListener {
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(itemView.context, "Login to access meme options")
                        return@setOnLongClickListener true
                    }

                    MessageActionsHandler.showMessageOptionsBottomSheet(
                        itemView.context,
                        meme,
                        adapterPosition
                    ) { message, position, messageId ->
                        // Use the modified method that checks the ID
                        if (position != RecyclerView.NO_POSITION) {
                            removeMeme(position, messageId)
                        }
                    }
                    true // Consume the long click
                }
            }
        }

        fun updateReactions(meme: MemeMessage) {
            binding.tvAngryEmoji.text = "🤬 ${meme.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😁 ${meme.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${meme.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "💔 ${meme.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(meme: MemeMessage) {
            binding.buttonHit.text = "🔥 ${meme.hit}"
            binding.buttonMiss.text = "❌ ${meme.miss}"
        }

        fun updateComments(meme: MemeMessage) {
            val count = if (meme.commentCount > 0) meme.commentCount else meme.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }
    }

    inner class MemeReceiveViewHolder(private val binding: ItemMemeReceiveBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meme: MemeMessage) {
            with(binding) {
                textViewName.text = meme.senderName
                textViewTeam.text = meme.team

                // Handle meme image
                if (meme.memeUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(meme.memeUrl)
                        .into(imageViewContent)

                    // Make image clickable for full screen view
                    imageViewContent.setOnClickListener {
                        showFullScreenImage(itemView.context, meme.memeUrl)
                    }
                } else {
                    imageViewContent.visibility = View.GONE
                }

                // Load profile picture
                loadProfilePicture(meme.senderId, binding.imageViewProfile)

                // Load team logo
                loadTeamLogo(meme.team, binding.imageViewTeam)

                // Set reaction values
                updateReactions(meme)

                // Set hit/miss counts
                updateHitMiss(meme)

                // Set comment count
                updateComments(meme)

                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "fire", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                tvHappyEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "laugh", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                tvCryingEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "cry", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                tvSadEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(meme, "troll", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to react to memes")
                    }
                }

                // Set hit/miss click listeners with login checks
                buttonHit.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(meme, "hit", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate memes")
                    }
                }

                buttonMiss.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(meme, "miss", adapterPosition)
                    } else {
                        showLoginPrompt(itemView.context, "Login to rate memes")
                    }
                }

                textViewComments.setOnClickListener {
                    val context = itemView.context

                    // First check if the user is logged in
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(context, "Login to view and add comments")
                        return@setOnClickListener
                    }

                    // Either use the provided callback or open the comment activity directly
                    if (onCommentClickListener != null) {
                        onCommentClickListener.invoke(meme)
                    } else {
                        try {
                            Log.d(TAG, "Opening comments for meme ID: ${meme.id}")
                            val intent = Intent(context, CommentActivity::class.java).apply {
                                putExtra("MESSAGE_ID", meme.id)
                                putExtra("MESSAGE_TYPE", "meme")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening comments: ${e.message}", e)
                            Toast.makeText(context, "Unable to open comments", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Add long-press listener for message options
                itemView.setOnLongClickListener {
                    if (!isUserLoggedIn()) {
                        showLoginPrompt(itemView.context, "Login to access meme options")
                        return@setOnLongClickListener true
                    }

                    MessageActionsHandler.showMessageOptionsBottomSheet(
                        itemView.context,
                        meme,
                        adapterPosition
                    ) { message, position, messageId ->
                        // Use the modified method that checks the ID
                        if (position != RecyclerView.NO_POSITION) {
                            removeMeme(position, messageId)
                        }
                    }
                    true // Consume the long click
                }
            }
        }

        fun updateReactions(meme: MemeMessage) {
            binding.tvAngryEmoji.text = "🤬 ${meme.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😁 ${meme.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${meme.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "💔 ${meme.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(meme: MemeMessage) {
            binding.buttonHit.text = "🔥 ${meme.hit}"
            binding.buttonMiss.text = "❌ ${meme.miss}"
        }

        fun updateComments(meme: MemeMessage) {
            val count = if (meme.commentCount > 0) meme.commentCount else meme.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }
    }

    private fun addReaction(meme: MemeMessage, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val reactionRef = FirebaseDatabase.getInstance()
            .getReference("NoBallZone/memes/${meme.id}/reactions/$reactionType")

        reactionRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    val newValue = currentData.getValue(Int::class.java) ?: 0
                    meme.reactions[reactionType] = newValue
                    notifyItemChanged(position, PAYLOAD_REACTION)
                }
            }
        })
    }

    private fun updateHitOrMiss(meme: MemeMessage, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val isHit = type == "hit"

        // Use the TeamStatsUtility to update both the meme stats and team stats
        TeamStatsUtility.updateContentAndTeamStats(
            contentType = "memes",
            contentId = meme.id,
            team = meme.team,
            isHit = isHit
        ) { success, newValue ->
            if (success) {
                // Update local model with the received value from Firebase
                if (isHit) meme.hit = newValue else meme.miss = newValue

                // Notify adapter of the change
                notifyItemChanged(position, PAYLOAD_HIT_MISS)
            }
        }
    }

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