package com.cricketApp.cric.Chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.databinding.ItemSendCommentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

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

        fun updateReactions(comment: CommentMessage) {
            binding.tvAngryEmoji.text = "🔥 ${comment.reactions["fire"] ?: 0}"
            binding.tvHappyEmoji.text = "😂 ${comment.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😢 ${comment.reactions["cry"] ?: 0}"
            binding.tvSadEmoji.text = "🏏 ${comment.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(comment: CommentMessage) {
            binding.buttonHit.text = "Hit ${comment.hit}"
            binding.buttonMiss.text = "Miss ${comment.miss}"
        }
    }

    // Utility method to update a reaction (without reloading)
    private fun addReaction(comment: CommentMessage, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

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

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                if (committed && error == null) {
                    // Update was successful, now update the item in our local data
                    val currentValue = comment.reactions[reactionType] ?: 0
                    comment.reactions[reactionType] = currentValue + 1
                    notifyItemChanged(position, PAYLOAD_REACTION)
                }
            }
        })
    }

    // Utility method to update hit or miss counts
    private fun updateHitOrMiss(comment: CommentMessage, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        val path = if (messageType == "chat") {
            "NoBallZone/chats/$messageId/comments/${comment.id}/$type"
        } else {
            "NoBallZone/polls/$messageId/comments/${comment.id}/$type"
        }

        val hitMissRef = FirebaseDatabase.getInstance().getReference(path)

        hitMissRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                if (committed && error == null) {
                    // Update was successful, update local data
                    if (type == "hit") {
                        comment.hit += 1
                    } else {
                        comment.miss += 1
                    }
                    notifyItemChanged(position, PAYLOAD_HIT_MISS)
                }
            }
        })
    }
}