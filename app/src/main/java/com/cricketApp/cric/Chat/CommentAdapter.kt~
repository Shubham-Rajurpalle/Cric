package com.cricketApp.cric.Chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.databinding.ItemSendChatBinding
import com.cricketApp.cric.databinding.ItemSendCommentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentAdapter(private val comments: List<CommentMessage>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemSendCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(private val binding: ItemSendCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentMessage) {
            binding.apply {

                textViewName.text = comment.senderName
                textViewMessage.text = comment.message

                // Set reactions
                tvAngryEmoji.text = "🔥 ${comment.reactions["fire"] ?: 0}"
                tvHappyEmoji.text = "😂 ${comment.reactions["laugh"] ?: 0}"
                tvCryingEmoji.text = "😢 ${comment.reactions["cry"] ?: 0}"
                tvSadEmoji.text = "🏏 ${comment.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonHit.text = "Hit ${comment.hit}"
                buttonMiss.text = "Miss ${comment.miss}"


                // Set reaction click listeners
                tvAngryEmoji.setOnClickListener { addReaction(comment, "fire") }
                tvHappyEmoji.setOnClickListener { addReaction(comment, "laugh") }
                tvCryingEmoji.setOnClickListener { addReaction(comment, "cry") }
                tvSadEmoji.setOnClickListener { addReaction(comment, "troll") }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { addHit(comment) }
                buttonMiss.setOnClickListener { addMiss(comment) }

            }
        }

        private fun addReaction(comment: CommentMessage, reactionType: String) {
            // Implementation similar to chat/poll reaction
        }

        private fun addHit(comment: CommentMessage) {
            // Implementation similar to chat/poll hit
        }

        private fun addMiss(comment: CommentMessage) {
            // Implementation similar to chat/poll miss
        }
    }
}