package com.cricketApp.cric.Chat

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.cricketApp.cric.R
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
    private val comments: List<CommentMessage>,
    private val messageId: String,
    private val messageType: String
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    companion object {
        private const val PAYLOAD_REACTION = "reaction"
        private const val PAYLOAD_HIT_MISS = "hit_miss"
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
                    PAYLOAD_REACTION -> holder.updateReactions(comments[position])
                    PAYLOAD_HIT_MISS -> holder.updateHitMiss(comments[position])
                }
            }
        }
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(private val binding: ItemSendCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentMessage) {
            binding.apply {
                textViewName.text = comment.senderName
                textViewMessage.text = comment.message

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
                            Log.e("CommentAdapter", "Error opening image viewer", e)
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

                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(comment, "fire", adapterPosition) }
                tvHappyEmoji.setOnClickListener { addReaction(comment, "laugh", adapterPosition) }
                tvCryingEmoji.setOnClickListener { addReaction(comment, "cry", adapterPosition) }
                tvSadEmoji.setOnClickListener { addReaction(comment, "troll", adapterPosition) }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { updateHitOrMiss(comment, "hit", adapterPosition) }
                buttonMiss.setOnClickListener { updateHitOrMiss(comment, "miss", adapterPosition) }
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
                            // Use mutable properties instead of reflection
                            val field = comment.javaClass.getDeclaredField("team")
                            field.isAccessible = true
                            field.set(comment, team)
                        } catch (e: Exception) {
                            // If this fails, it's not critical since the UI is already updated
                            Log.e("CommentAdapter", "Error updating team: ${e.message}")
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
    private fun addReaction(comment: CommentMessage, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= comments.size) return

        val path = if (messageType == "chat") {
            "NoBallZone/chats/$messageId/comments/${comment.id}/reactions/$reactionType"
        } else {
            "NoBallZone/polls/$messageId/comments/${comment.id}/reactions/$reactionType"
        }

        val reactionRef = FirebaseDatabase.getInstance().getReference(path)

        reactionRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    // Only update the local model with the value from Firebase
                    // This prevents any possibility of double counting
                    val newValue = currentData.getValue(Int::class.java) ?: 0
                    comment.reactions[reactionType] = newValue

                    if (position < comments.size) {
                        notifyItemChanged(position, PAYLOAD_REACTION)
                    }
                }
            }
        })
    }

    // Utility method to update hit or miss counts - fixed to prevent double counting
    private fun updateHitOrMiss(comment: CommentMessage, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= comments.size) return

        val isHit = type == "hit"

        // Determine the parent content type based on messageType
        val contentType = when (messageType) {
            "chat" -> "chats"
            "poll" -> "polls"
            "meme" -> "memes"
            else -> return
        }

        // Update comment stats and team stats
        TeamStatsUtility.updateContentAndTeamStats(
            contentType = contentType,
            contentId = "", // Not used for comments
            team = comment.team,
            isHit = isHit,
            commentId = comment.id,
            parentId = messageId
        ) { success, newValue ->
            if (success && position < comments.size) {
                // Update local model with received value
                if (isHit) {
                    comment.hit = newValue
                } else {
                    comment.miss = newValue
                }

                // Notify adapter of the change
                notifyItemChanged(position, PAYLOAD_HIT_MISS)
            }
        }
    }
}