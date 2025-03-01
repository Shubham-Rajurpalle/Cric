package com.cricketApp.cric.Chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentAdapter(private val comments: List<CommentMessage>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentMessage) {
            binding.apply {
                textViewCommentName.text = comment.senderName
                textViewCommentMessage.text = comment.message

                // Format timestamp
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                val dateTime = Date(comment.timestamp)
                textViewCommentTime.text = sdf.format(dateTime)

                // Set reactions
                textViewCommentFire.text = "🔥 ${comment.reactions["fire"] ?: 0}"
                textViewCommentLaugh.text = "😂 ${comment.reactions["laugh"] ?: 0}"
                textViewCommentCry.text = "😢 ${comment.reactions["cry"] ?: 0}"
                textViewCommentTroll.text = "🏏 ${comment.reactions["troll"] ?: 0}"

                // Set hit/miss counts
                buttonCommentHit.text = "Hit ${comment.hit}"
                buttonCommentMiss.text = "Miss ${comment.miss}"

                // Set reaction click listeners
                textViewCommentFire.setOnClickListener { addReaction(comment, "fire") }
                textViewCommentLaugh.setOnClickListener { addReaction(comment, "laugh") }
                textViewCommentCry.setOnClickListener { addReaction(comment, "cry") }
                textViewCommentTroll.setOnClickListener { addReaction(comment, "troll") }

                // Set hit/miss click listeners
                buttonCommentHit.setOnClickListener { addHit(comment) }
                buttonCommentMiss.setOnClickListener { addMiss(comment) }
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