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
import com.cricketApp.cric.databinding.ItemMemeReceiveBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.widget.TextView
import android.widget.Toast
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Utils.MilestoneBadgeHelper
import com.cricketApp.cric.Utils.ReactionTracker
import com.cricketApp.cric.databinding.ItemMemeSendBinding

class MemeAdapter(
    private val items: MutableList<MemeMessage>,
    private val onCommentClickListener: ((MemeMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SEND_MEME    = 1
        private const val VIEW_TYPE_RECEIVE_MEME = 2
        private const val PAYLOAD_REACTION       = "reaction"
        private const val PAYLOAD_HIT_MISS       = "hit_miss"
        private const val PAYLOAD_COMMENTS       = "comments"
        private const val TAG                    = "MemeAdapter"
    }

    private val memePositions = mutableMapOf<String, Int>()

    // ─────────────────────────────────────────────────────────────────────────
    // Boilerplate
    // ─────────────────────────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int =
        if (items[position].senderId == FirebaseAuth.getInstance().currentUser?.uid)
            VIEW_TYPE_SEND_MEME else VIEW_TYPE_RECEIVE_MEME

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEND_MEME    -> MemeSendViewHolder(ItemMemeSendBinding.inflate(inflater, parent, false))
            VIEW_TYPE_RECEIVE_MEME -> MemeReceiveViewHolder(ItemMemeReceiveBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MemeSendViewHolder    -> holder.bind(items[position])
            is MemeReceiveViewHolder -> holder.bind(items[position])
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) { onBindViewHolder(holder, position); return }
        for (payload in payloads) {
            if (payload is String) when (payload) {
                PAYLOAD_REACTION -> when (holder) {
                    is MemeSendViewHolder    -> holder.updateReactions(items[position])
                    is MemeReceiveViewHolder -> holder.updateReactions(items[position])
                }
                PAYLOAD_HIT_MISS -> when (holder) {
                    is MemeSendViewHolder    -> holder.updateHitMiss(items[position])
                    is MemeReceiveViewHolder -> holder.updateHitMiss(items[position])
                }
                PAYLOAD_COMMENTS -> when (holder) {
                    is MemeSendViewHolder    -> holder.updateComments(items[position])
                    is MemeReceiveViewHolder -> holder.updateComments(items[position])
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // ─────────────────────────────────────────────────────────────────────────
    // Public helpers
    // ─────────────────────────────────────────────────────────────────────────

    fun findPositionById(memeId: String): Int = items.indexOfFirst { it.id == memeId }

    fun removeMeme(position: Int, expectedId: String? = null) {
        if (position < 0 || position >= items.size) return
        val actualId = items[position].id
        if (expectedId != null && expectedId != actualId) {
            val correct = findPositionById(expectedId)
            if (correct != -1) removeMeme(correct)
            return
        }
        items.removeAt(position)
        updatePositionsMap()
        notifyItemRemoved(position)
    }

    private fun updatePositionsMap() {
        memePositions.clear()
        items.forEachIndexed { i, m -> memePositions[m.id] = i }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun showFullScreenImage(context: Context, imageUrl: String) {
        context.startActivity(Intent(context, ActivityImageViewer::class.java).apply {
            putExtra("IMAGE_URL", imageUrl)
        })
    }

    private fun isUserLoggedIn() = FirebaseAuth.getInstance().currentUser != null

    private fun showLoginPrompt(context: Context, message: String) {
        AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ -> context.startActivity(Intent(context, SignIn::class.java)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Shared logic wired into both ViewHolders */
    private fun wireShareButton(view: View, context: Context, meme: MemeMessage) {
        view.setOnClickListener {
            MemeShareHelper.shareMeme(context, meme)
        }
    }

    /** Long-press bottom sheet wired into both ViewHolders — includes Share option */
    private fun wireLongPress(itemView: View, meme: MemeMessage, getPosition: () -> Int) {
        itemView.setOnLongClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt(itemView.context, "Login to access meme options")
                return@setOnLongClickListener true
            }
            // Show bottom sheet with Delete + Share options
            MessageActionsHandler.showMessageOptionsBottomSheet(
                itemView.context,
                meme,
                getPosition(),
                onShareClick = {
                    // ★ Share from long-press sheet
                    MemeShareHelper.shareMeme(itemView.context, meme)
                }
            ) { _, position, messageId ->
                if (position != RecyclerView.NO_POSITION) removeMeme(position, messageId)
            }
            true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEND ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    inner class MemeSendViewHolder(private val binding: ItemMemeSendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meme: MemeMessage) {
            with(binding) {
                textViewName.text = meme.senderName
                textViewTeam.text = meme.team

                itemView.findViewById<TextView>(R.id.badgeTrending)?.let {
                    MilestoneBadgeHelper.updateMilestoneBadge(it, it, meme.hit, meme.miss, meme.reactions)
                }

                if (meme.memeUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE
                    Glide.with(itemView.context).load(meme.memeUrl).into(imageViewContent)
                    imageViewContent.setOnClickListener { showFullScreenImage(itemView.context, meme.memeUrl) }
                } else {
                    imageViewContent.visibility = View.GONE
                }

                loadProfilePicture(meme.senderId, imageViewProfile)
                loadTeamLogo(meme.team, imageViewTeam)
                updateReactions(meme)
                updateHitMiss(meme)
                updateComments(meme)

                tvAngryEmoji.setOnClickListener { reactOrPrompt(meme, "fire")  }
                tvHappyEmoji.setOnClickListener { reactOrPrompt(meme, "laugh") }
                tvCryingEmoji.setOnClickListener { reactOrPrompt(meme, "cry")  }
                tvSadEmoji.setOnClickListener   { reactOrPrompt(meme, "troll") }

                buttonHit.setOnClickListener  { hitMissOrPrompt(meme, "hit")  }
                buttonMiss.setOnClickListener { hitMissOrPrompt(meme, "miss") }

                textViewComments.setOnClickListener { openCommentsOrPrompt(meme) }

                // ★ Share button on card
                wireShareButton(tvShareMeme, itemView.context, meme)

                // ★ Long-press (includes Share + Delete in bottom sheet)
                wireLongPress(itemView, meme) { adapterPosition }
            }
        }

        fun updateReactions(meme: MemeMessage) {
            binding.tvAngryEmoji.text  = "🤬 ${meme.reactions["fire"]  ?: 0}"
            binding.tvHappyEmoji.text  = "😁 ${meme.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${meme.reactions["cry"]   ?: 0}"
            binding.tvSadEmoji.text    = "💔 ${meme.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(meme: MemeMessage) {
            binding.buttonHit.text  = "🔥 ${meme.hit}"
            binding.buttonMiss.text = "❌ ${meme.miss}"
        }

        fun updateComments(meme: MemeMessage) {
            val count = if (meme.commentCount > 0) meme.commentCount else meme.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }

        private fun reactOrPrompt(meme: MemeMessage, type: String) {
            if (isUserLoggedIn()) addReaction(meme, type, adapterPosition)
            else showLoginPrompt(itemView.context, "Login to react to memes")
        }

        private fun hitMissOrPrompt(meme: MemeMessage, type: String) {
            if (isUserLoggedIn()) updateHitOrMiss(meme, type, adapterPosition)
            else showLoginPrompt(itemView.context, "Login to rate memes")
        }

        private fun openCommentsOrPrompt(meme: MemeMessage) {
            val ctx = itemView.context
            if (!isUserLoggedIn()) { showLoginPrompt(ctx, "Login to view and add comments"); return }
            if (onCommentClickListener != null) {
                onCommentClickListener.invoke(meme)
            } else {
                try {
                    ctx.startActivity(Intent(ctx, CommentActivity::class.java).apply {
                        putExtra("MESSAGE_ID", meme.id)
                        putExtra("MESSAGE_TYPE", "meme")
                    })
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Unable to open comments", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RECEIVE ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    inner class MemeReceiveViewHolder(private val binding: ItemMemeReceiveBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meme: MemeMessage) {
            with(binding) {
                textViewName.text = meme.senderName
                textViewTeam.text = meme.team

                itemView.findViewById<TextView>(R.id.badgeTrending)?.let {
                    MilestoneBadgeHelper.updateMilestoneBadge(it, it, meme.hit, meme.miss, meme.reactions)
                }

                if (meme.memeUrl.isNotEmpty()) {
                    imageViewContent.visibility = View.VISIBLE
                    Glide.with(itemView.context).load(meme.memeUrl).into(imageViewContent)
                    imageViewContent.setOnClickListener { showFullScreenImage(itemView.context, meme.memeUrl) }
                } else {
                    imageViewContent.visibility = View.GONE
                }

                loadProfilePicture(meme.senderId, imageViewProfile)
                loadTeamLogo(meme.team, imageViewTeam)
                updateReactions(meme)
                updateHitMiss(meme)
                updateComments(meme)

                tvAngryEmoji.setOnClickListener { reactOrPrompt(meme, "fire")  }
                tvHappyEmoji.setOnClickListener { reactOrPrompt(meme, "laugh") }
                tvCryingEmoji.setOnClickListener { reactOrPrompt(meme, "cry")  }
                tvSadEmoji.setOnClickListener   { reactOrPrompt(meme, "troll") }

                buttonHit.setOnClickListener  { hitMissOrPrompt(meme, "hit")  }
                buttonMiss.setOnClickListener { hitMissOrPrompt(meme, "miss") }

                textViewComments.setOnClickListener { openCommentsOrPrompt(meme) }

                // ★ Share button on card
                wireShareButton(tvShareMeme, itemView.context, meme)

                // ★ Long-press (includes Share + Delete in bottom sheet)
                wireLongPress(itemView, meme) { adapterPosition }
            }
        }

        fun updateReactions(meme: MemeMessage) {
            binding.tvAngryEmoji.text  = "🤬 ${meme.reactions["fire"]  ?: 0}"
            binding.tvHappyEmoji.text  = "😁 ${meme.reactions["laugh"] ?: 0}"
            binding.tvCryingEmoji.text = "😭 ${meme.reactions["cry"]   ?: 0}"
            binding.tvSadEmoji.text    = "💔 ${meme.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(meme: MemeMessage) {
            binding.buttonHit.text  = "🔥 ${meme.hit}"
            binding.buttonMiss.text = "❌ ${meme.miss}"
        }

        fun updateComments(meme: MemeMessage) {
            val count = if (meme.commentCount > 0) meme.commentCount else meme.comments.size
            binding.textViewComments.text = "View Comments ($count)"
        }

        private fun reactOrPrompt(meme: MemeMessage, type: String) {
            if (isUserLoggedIn()) addReaction(meme, type, adapterPosition)
            else showLoginPrompt(itemView.context, "Login to react to memes")
        }

        private fun hitMissOrPrompt(meme: MemeMessage, type: String) {
            if (isUserLoggedIn()) updateHitOrMiss(meme, type, adapterPosition)
            else showLoginPrompt(itemView.context, "Login to rate memes")
        }

        private fun openCommentsOrPrompt(meme: MemeMessage) {
            val ctx = itemView.context
            if (!isUserLoggedIn()) { showLoginPrompt(ctx, "Login to view and add comments"); return }
            if (onCommentClickListener != null) {
                onCommentClickListener.invoke(meme)
            } else {
                try {
                    ctx.startActivity(Intent(ctx, CommentActivity::class.java).apply {
                        putExtra("MESSAGE_ID", meme.id)
                        putExtra("MESSAGE_TYPE", "meme")
                    })
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Unable to open comments", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Firebase helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun addReaction(meme: MemeMessage, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        ReactionTracker.addEmojiReaction(
            contentType = ReactionTracker.ContentType.MEME,
            contentId = meme.id,
            reactionType = reactionType
        ) { success, newValue ->
            if (success) {
                meme.reactions[reactionType] = newValue
                notifyItemChanged(position, PAYLOAD_REACTION)
            }
        }
    }

    private fun updateHitOrMiss(meme: MemeMessage, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val isHit = type == "hit"
        ReactionTracker.updateHitOrMiss(
            contentType = ReactionTracker.ContentType.MEME,
            contentId = meme.id,
            isHit = isHit
        ) { success, newValue ->
            if (success) {
                if (isHit) meme.hit = newValue else meme.miss = newValue
                notifyItemChanged(position, PAYLOAD_HIT_MISS)
            }
        }
    }

    private fun loadProfilePicture(userId: String, imageView: ImageView) {
        FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = snapshot.getValue(String::class.java)
                    if (url != null) {
                        Glide.with(imageView.context).load(url)
                            .placeholder(R.drawable.profile_icon).error(R.drawable.profile_icon)
                            .circleCrop().into(imageView)
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
        val map = mapOf(
            "CSK" to R.drawable.csk, "MI"   to R.drawable.mi,  "RCB"  to R.drawable.rcb,
            "KKR" to R.drawable.kkr, "DC"   to R.drawable.dc,  "SRH"  to R.drawable.srh,
            "PBKS" to R.drawable.pbks, "RR" to R.drawable.rr,  "GT"   to R.drawable.gt,
            "LSG" to R.drawable.lsg
        )
        imageView.setImageResource(map[teamName] ?: R.drawable.icc_logo)
    }
}