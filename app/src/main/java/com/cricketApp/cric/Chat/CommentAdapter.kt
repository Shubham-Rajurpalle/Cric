package com.cricketApp.cric.Chat

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.R
import com.cricketApp.cric.Utils.MilestoneBadgeHelper
import com.cricketApp.cric.Utils.ReactionTracker
import com.cricketApp.cric.Utils.TeamStatsUtility
import com.cricketApp.cric.databinding.ItemSendCommentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class CommentAdapter(
    private val comments: ArrayList<CommentMessage>,
    private val messageId: String,
    private val messageType: String
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    companion object {
        private const val PAYLOAD_REACTION = "reaction"
        private const val PAYLOAD_HIT_MISS = "hit_miss"
        private const val TAG = "CommentAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemSendCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            // No payload, do full bind
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        // Handle partial updates
        for (payload in payloads) {
            if (payload is String) {
                when (payload) {
                    "reaction" -> holder.updateReactions(comments[position])
                    "hit_miss" -> holder.updateHitMiss(comments[position])
                }
            }
        }
    }

    override fun getItemCount(): Int = comments.size

    /**
     * Remove a comment at the specified position
     */
    fun removeComment(position: Int) {
        if (position < 0 || position >= comments.size) {
        //    Log.e(TAG, "Invalid position for removal: $position, size: ${comments.size}")
            return
        }

        comments.removeAt(position)
        notifyItemRemoved(position)
    //    Log.d(TAG, "Removed comment at position $position")
    }

    /**
     * Check if the user is logged in
     */
    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /**
     * Show login prompt dialog
     */
    private fun showLoginPrompt(view: View, message: String) {
        val context = view.context
        AlertDialog.Builder(context)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(context, SignIn::class.java)
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class CommentViewHolder(private val binding: ItemSendCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentMessage) {
            binding.apply {
                textViewName.text = comment.senderName
                textViewMessage.text = comment.message

                val badgeView = itemView.findViewById<TextView>(R.id.badgeTrending)
                if (badgeView != null) {
                    MilestoneBadgeHelper.updateMilestoneBadge(
                        badgeView = badgeView,
                        badgeText = badgeView,
                        hit = comment.hit,
                        miss = comment.miss,
                        reactions = comment.reactions
                    )
                }

                // Handle image content
                if (comment.imageUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE

                    // Enhanced Glide configuration with error handling
                    Glide.with(itemView.context)
                        .load(comment.imageUrl)
                        .apply(RequestOptions()
                            .timeout(15000)
                            .diskCacheStrategy(DiskCacheStrategy.ALL))
                        .into(imageViewContent)

                    // Make image clickable for full screen view
                    imageViewContent.setOnClickListener {
                        try {
                            val context = itemView.context
                            val intent = Intent(context, ActivityImageViewer::class.java).apply {
                                putExtra("IMAGE_URL", comment.imageUrl)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                        //    Log.e(TAG, "Error opening image viewer", e)
                            Toast.makeText(itemView.context,
                                "Failed to open image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    imageViewContent.visibility = View.GONE
                }

                // Set team name if available, otherwise fetch it
                if (comment.team.isNotEmpty()) {
                    textViewTeam.text = comment.team
                    // Load team logo
                    loadTeamLogo(comment.team, binding.imageViewTeam)
                } else {
                    fetchUserTeam(comment.senderId)
                }

                // Load profile picture
                loadProfilePicture(comment.senderId, binding.imageViewProfile)

                // Set reaction values
                updateReactions(comment)

                // Set hit/miss counts
                updateHitMiss(comment)

                // Set reaction click listeners with login check
                tvAngryEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(comment, "fire", adapterPosition,root.context)
                    } else {
                        showLoginPrompt(itemView, "Login to react to comments")
                    }
                }

                tvHappyEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(comment, "laugh", adapterPosition,root.context)
                    } else {
                        showLoginPrompt(itemView, "Login to react to comments")
                    }
                }

                tvCryingEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(comment, "cry", adapterPosition,root.context)
                    } else {
                        showLoginPrompt(itemView, "Login to react to comments")
                    }
                }

                tvSadEmoji.setOnClickListener {
                    if (isUserLoggedIn()) {
                        addReaction(comment, "troll", adapterPosition,root.context)
                    } else {
                        showLoginPrompt(itemView, "Login to react to comments")
                    }
                }

                // Set hit/miss click listeners with login check
                buttonHit.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(comment, "hit", adapterPosition,root.context)
                    } else {
                        showLoginPrompt(itemView, "Login to rate comments")
                    }
                }

                buttonMiss.setOnClickListener {
                    if (isUserLoggedIn()) {
                        updateHitOrMiss(comment, "miss", adapterPosition,root.context)
                    } else {
                        showLoginPrompt(itemView, "Login to rate comments")
                    }
                }

                // Add long-press listener for comment options (delete/report) with login check
                itemView.setOnLongClickListener {
                    if (isUserLoggedIn()) {
                        MessageActionsHandler.showMessageOptionsBottomSheet(
                            itemView.context,
                            comment,
                            adapterPosition
                        ) { message, position, commentId ->
                            // Handle deletion in the adapter
                            if (position != RecyclerView.NO_POSITION && position < comments.size) {
                                removeComment(position)
                            }
                        }
                    } else {
                        showLoginPrompt(itemView, "Login to access comment options")
                    }
                    true // Consume the long click
                }
            }
        }

        private fun fetchUserTeam(userId: String) {
            val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/iplTeam")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val team = snapshot.getValue(String::class.java) ?: "No Team"
                    binding.textViewTeam.text = team

                    // Also load the team logo
                    loadTeamLogo(team, binding.imageViewTeam)

                    // Update the comment object too to avoid refetching
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION && position < comments.size) {
                        try {
                            val comment = comments[position]
                            // Update the team value
                            comment.team = team
                        } catch (e: Exception) {
                            // If this fails, it's not critical since the UI is already updated
                        //    Log.e(TAG, "Error updating team: ${e.message}")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.textViewTeam.text = "Unknown"
                }
            })
        }

        fun updateReactions(comment: CommentMessage) {
            binding.tvAngryEmoji.text = "🔥 ${comment.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😂 ${comment.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😢 ${comment.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "💔 ${comment.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(comment: CommentMessage) {
            binding.buttonHit.text = "Hit ${comment.hit}"
            binding.buttonMiss.text = "Miss ${comment.miss}"
        }

        private fun loadProfilePicture(userId: String, imageView: ImageView) {
            val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profileUrl = snapshot.getValue(String::class.java)
                    if (profileUrl != null && profileUrl.isNotEmpty()) {
                        Glide.with(imageView.context)
                            .load(profileUrl)
                            .apply(RequestOptions()
                                .placeholder(R.drawable.profile_icon)
                                .error(R.drawable.profile_icon)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .circleCrop())
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

    // Utility method to update a reaction - fixed to prevent double counting
    private fun addReaction(comment: CommentMessage, reactionType: String, position: Int,context: Context) {
        if (position == RecyclerView.NO_POSITION || position >= comments.size) return

        // For comments, we need parent info
        val activity = context as? CommentActivity
        val parentId = activity?.messageId ?: return
        val parentType = activity.messageType ?: return

        // Create parent ID with type prefix
        val formattedParentId = "${parentType}_$parentId"

        // Use ReactionTracker
        ReactionTracker.addEmojiReaction(
            contentType = ReactionTracker.ContentType.COMMENT,
            contentId = comment.id,
            parentId = formattedParentId,
            reactionType = reactionType
        ) { success, newValue ->
            if (success) {
                comment.reactions[reactionType] = newValue
                notifyItemChanged(position, "reaction")
            }
        }
    }

    // Utility method to update hit or miss counts
    private fun updateHitOrMiss(comment: CommentMessage, type: String, position: Int,context: Context) {
        if (position == RecyclerView.NO_POSITION || position >= comments.size) return

        val isHit = type == "hit"

        // For comments, we need parent info
        val activity = context as? CommentActivity
        val parentId = activity?.messageId ?: return
        val parentType = activity.messageType ?: return

        // Create parent ID with type prefix
        val formattedParentId = "${parentType}_$parentId"

        // Use ReactionTracker
        ReactionTracker.updateHitOrMiss(
            contentType = ReactionTracker.ContentType.COMMENT,
            contentId = comment.id,
            parentId = formattedParentId,
            isHit = isHit
        ) { success, newValue ->
            if (success) {
                if (isHit) comment.hit = newValue else comment.miss = newValue
                notifyItemChanged(position, "hit_miss")
            }
        }
    }
}