package com.cricketApp.cric.Meme

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cricketApp.cric.Chat.ActivityImageViewer
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.MessageActionsHandler
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.R
import com.cricketApp.cric.Utils.MilestoneBadgeHelper
import com.cricketApp.cric.Utils.ReactionTracker
import com.cricketApp.cric.databinding.ItemMemeReceiveBinding
import com.cricketApp.cric.databinding.ItemMemeSendBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

// ─────────────────────────────────────────────────────────────────────────────
// DiffUtil — compares by ID only for structural changes (add/remove/move).
// Reaction/hit/miss updates bypass DiffUtil entirely via notifyItemChanged.
// ─────────────────────────────────────────────────────────────────────────────

class MemeDiffCallback : DiffUtil.ItemCallback<MemeMessage>() {
    override fun areItemsTheSame(old: MemeMessage, new: MemeMessage) = old.id == new.id
    // Always return true so DiffUtil never tries to redraw for content changes —
    // those are handled by direct notifyItemChanged calls with payloads below.
    override fun areContentsTheSame(old: MemeMessage, new: MemeMessage) = true
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter
// ─────────────────────────────────────────────────────────────────────────────

private const val PAYLOAD_REACTION = "reaction"
private const val PAYLOAD_HIT_MISS = "hit_miss"
private const val PAYLOAD_COMMENTS = "comments"

private const val VIEW_TYPE_SEND    = 1
private const val VIEW_TYPE_RECEIVE = 2
private const val VIEW_TYPE_LOADING = 3

class MemeAdapter(
    private val onCommentClick: ((MemeMessage) -> Unit)? = null,
    private val onHitMissUpdated: ((String, Int, Int) -> Unit)? = null
) : ListAdapter<MemeMessage, RecyclerView.ViewHolder>(MemeDiffCallback()) {

    // ── Mutable backing list — we own this for in-place updates ──────────────
    // ListAdapter.currentList is read-only, so we keep our own parallel list
    // that we mutate for reactions/hit/miss, then call notifyItemChanged.
    private val localList = mutableListOf<MemeMessage>()

    /** Show/hide the loading footer at the bottom */
    var showLoadingFooter: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(super.getItemCount())
            else       notifyItemRemoved(super.getItemCount())
        }

    // ── Intercept submitList to keep localList in sync ────────────────────────
    override fun submitList(list: List<MemeMessage>?) {
        localList.clear()
        list?.let { localList.addAll(it) }
        super.submitList(list)
    }

    override fun submitList(list: List<MemeMessage>?, commitCallback: Runnable?) {
        localList.clear()
        list?.let { localList.addAll(it) }
        super.submitList(list, commitCallback)
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun getItemCount(): Int = super.getItemCount() + if (showLoadingFooter) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (showLoadingFooter && position == super.getItemCount()) return VIEW_TYPE_LOADING
        return if (getItem(position).senderId == FirebaseAuth.getInstance().currentUser?.uid)
            VIEW_TYPE_SEND else VIEW_TYPE_RECEIVE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEND    -> MemeSendViewHolder(ItemMemeSendBinding.inflate(inflater, parent, false))
            VIEW_TYPE_RECEIVE -> MemeReceiveViewHolder(ItemMemeReceiveBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LOADING -> LoadingViewHolder(inflater.inflate(R.layout.item_loading_footer, parent, false))
            else -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is LoadingViewHolder) return
        val meme = getItem(position)
        when (holder) {
            is MemeSendViewHolder    -> holder.bind(meme)
            is MemeReceiveViewHolder -> holder.bind(meme)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty() || holder is LoadingViewHolder) {
            onBindViewHolder(holder, position); return
        }
        // Use localList for payload updates — currentList may be stale for same-object mutations
        val meme = if (position < localList.size) localList[position] else getItem(position)
        for (p in payloads) when (p as? String) {
            PAYLOAD_REACTION -> when (holder) {
                is MemeSendViewHolder    -> holder.updateReactions(meme)
                is MemeReceiveViewHolder -> holder.updateReactions(meme)
            }
            PAYLOAD_HIT_MISS -> when (holder) {
                is MemeSendViewHolder    -> holder.updateHitMiss(meme)
                is MemeReceiveViewHolder -> holder.updateHitMiss(meme)
            }
            PAYLOAD_COMMENTS -> when (holder) {
                is MemeSendViewHolder    -> holder.updateComments(meme)
                is MemeReceiveViewHolder -> holder.updateComments(meme)
            }
        }
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    fun findPositionById(memeId: String): Int =
        currentList.indexOfFirst { it.id == memeId }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun isUserLoggedIn() = FirebaseAuth.getInstance().currentUser != null

    private fun showLoginPrompt(context: Context, message: String) {
        AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ -> context.startActivity(Intent(context, SignIn::class.java)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFullScreenImage(context: Context, imageUrl: String) {
        context.startActivity(Intent(context, ActivityImageViewer::class.java).apply {
            putExtra("IMAGE_URL", imageUrl)
        })
    }

    private fun wireShareButton(view: View, context: Context, meme: MemeMessage) {
        view.setOnClickListener { MemeShareHelper.shareMeme(context, meme) }
    }

    private fun wireLongPress(itemView: View, meme: MemeMessage, getPosition: () -> Int) {
        itemView.setOnLongClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt(itemView.context, "Login to access meme options")
                return@setOnLongClickListener true
            }
            MessageActionsHandler.showMessageOptionsBottomSheet(
                itemView.context, meme, getPosition(),
                onShareClick = { MemeShareHelper.shareMeme(itemView.context, meme) }
            ) { _, _, messageId ->
                messageId?.let {
                    val pos = findPositionById(it)
                    if (pos != -1) {
                        val updated = currentList.toMutableList().also { l -> l.removeAt(pos) }
                        submitList(updated)
                    }
                }
            }
            true
        }
    }

    private fun loadProfilePicture(userId: String, imageView: ImageView) {
        FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = snapshot.getValue(String::class.java)
                    if (url != null) {
                        Glide.with(imageView.context).load(url)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .placeholder(R.drawable.profile_icon)
                            .error(R.drawable.profile_icon)
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
            "CSK"  to R.drawable.csk,  "MI"   to R.drawable.mi,
            "RCB"  to R.drawable.rcb,  "KKR"  to R.drawable.kkr,
            "DC"   to R.drawable.dc,   "SRH"  to R.drawable.srh,
            "PBKS" to R.drawable.pbks, "RR"   to R.drawable.rr,
            "GT"   to R.drawable.gt,   "LSG"  to R.drawable.lsg
        )
        imageView.setImageResource(map[teamName] ?: R.drawable.icc_logo)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Firebase reaction helpers
    // KEY FIX: mutate localList item, then call notifyItemChanged with payload.
    // Do NOT rely on submitList/DiffUtil for these micro-updates.
    // ─────────────────────────────────────────────────────────────────────────

    private fun addReaction(meme: MemeMessage, reactionType: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        ReactionTracker.addEmojiReaction(
            contentType  = ReactionTracker.ContentType.MEME,
            contentId    = meme.id,
            reactionType = reactionType
        ) { success, newValue ->
            if (success) {
                // Mutate the item in localList so payload bind reads fresh value
                if (position < localList.size) {
                    localList[position].reactions[reactionType] = newValue
                }
                notifyItemChanged(position, PAYLOAD_REACTION)
            }
        }
    }

    private fun updateHitOrMiss(meme: MemeMessage, type: String, position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val isHit = type == "hit"
        ReactionTracker.updateHitOrMiss(
            contentType = ReactionTracker.ContentType.MEME,
            contentId   = meme.id,
            isHit       = isHit
        ) { success, newValue ->
            if (success) {
                // Mutate localList item so payload bind reads fresh value
                if (position < localList.size) {
                    if (isHit) localList[position].hit = newValue
                    else       localList[position].miss = newValue
                }
                notifyItemChanged(position, PAYLOAD_HIT_MISS)
                onHitMissUpdated?.invoke(meme.id,
                    if (position < localList.size) localList[position].hit else meme.hit,
                    if (position < localList.size) localList[position].miss else meme.miss
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOADING footer ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view)

    // ─────────────────────────────────────────────────────────────────────────
    // SEND ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    inner class MemeSendViewHolder(private val binding: ItemMemeSendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meme: MemeMessage) = with(binding) {
            textViewName.text = meme.senderName
            textViewTeam.text = meme.team

            itemView.findViewById<TextView>(R.id.badgeTrending)?.let {
                MilestoneBadgeHelper.updateMilestoneBadge(it, it, meme.hit, meme.miss, meme.reactions)
            }

            if (meme.memeUrl.isNotEmpty()) {
                imageViewContent.visibility = View.VISIBLE
                Glide.with(itemView.context).load(meme.memeUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(imageViewContent)
                imageViewContent.setOnClickListener { showFullScreenImage(itemView.context, meme.memeUrl) }
            } else {
                imageViewContent.visibility = View.GONE
            }

            loadProfilePicture(meme.senderId, imageViewProfile)
            loadTeamLogo(meme.team, imageViewTeam)
            updateReactions(meme)
            updateHitMiss(meme)
            updateComments(meme)

            tvAngryEmoji.setOnClickListener  { reactOrPrompt(meme, "fire")  }
            tvHappyEmoji.setOnClickListener  { reactOrPrompt(meme, "laugh") }
            tvCryingEmoji.setOnClickListener { reactOrPrompt(meme, "cry")   }
            tvSadEmoji.setOnClickListener    { reactOrPrompt(meme, "troll") }

            buttonHit.setOnClickListener  { hitMissOrPrompt(meme, "hit")  }
            buttonMiss.setOnClickListener { hitMissOrPrompt(meme, "miss") }

            textViewComments.setOnClickListener { openCommentsOrPrompt(meme) }
            wireShareButton(tvShareMeme, itemView.context, meme)
            wireLongPress(itemView, meme) { adapterPosition }
        }

        fun updateReactions(meme: MemeMessage) = with(binding) {
            tvAngryEmoji.text  = "🤬 ${meme.reactions["fire"]  ?: 0}"
            tvHappyEmoji.text  = "😁 ${meme.reactions["laugh"] ?: 0}"
            tvCryingEmoji.text = "😭 ${meme.reactions["cry"]   ?: 0}"
            tvSadEmoji.text    = "💔 ${meme.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(meme: MemeMessage) = with(binding) {
            buttonHit.text  = "🔥 ${meme.hit}"
            buttonMiss.text = "❌ ${meme.miss}"
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
            if (onCommentClick != null) onCommentClick.invoke(meme)
            else try {
                ctx.startActivity(Intent(ctx, CommentActivity::class.java).apply {
                    putExtra("MESSAGE_ID", meme.id); putExtra("MESSAGE_TYPE", "meme")
                })
            } catch (e: Exception) {
                Toast.makeText(ctx, "Unable to open comments", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RECEIVE ViewHolder — mirrors SEND exactly
    // ─────────────────────────────────────────────────────────────────────────

    inner class MemeReceiveViewHolder(private val binding: ItemMemeReceiveBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meme: MemeMessage) = with(binding) {
            textViewName.text = meme.senderName
            textViewTeam.text = meme.team

            itemView.findViewById<TextView>(R.id.badgeTrending)?.let {
                MilestoneBadgeHelper.updateMilestoneBadge(it, it, meme.hit, meme.miss, meme.reactions)
            }

            if (meme.memeUrl.isNotEmpty()) {
                imageViewContent.visibility = View.VISIBLE
                Glide.with(itemView.context).load(meme.memeUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(imageViewContent)
                imageViewContent.setOnClickListener { showFullScreenImage(itemView.context, meme.memeUrl) }
            } else {
                imageViewContent.visibility = View.GONE
            }

            loadProfilePicture(meme.senderId, imageViewProfile)
            loadTeamLogo(meme.team, imageViewTeam)
            updateReactions(meme)
            updateHitMiss(meme)
            updateComments(meme)

            tvAngryEmoji.setOnClickListener  { reactOrPrompt(meme, "fire")  }
            tvHappyEmoji.setOnClickListener  { reactOrPrompt(meme, "laugh") }
            tvCryingEmoji.setOnClickListener { reactOrPrompt(meme, "cry")   }
            tvSadEmoji.setOnClickListener    { reactOrPrompt(meme, "troll") }

            buttonHit.setOnClickListener  { hitMissOrPrompt(meme, "hit")  }
            buttonMiss.setOnClickListener { hitMissOrPrompt(meme, "miss") }

            textViewComments.setOnClickListener { openCommentsOrPrompt(meme) }
            wireShareButton(tvShareMeme, itemView.context, meme)
            wireLongPress(itemView, meme) { adapterPosition }
        }

        fun updateReactions(meme: MemeMessage) = with(binding) {
            tvAngryEmoji.text  = "🤬 ${meme.reactions["fire"]  ?: 0}"
            tvHappyEmoji.text  = "😁 ${meme.reactions["laugh"] ?: 0}"
            tvCryingEmoji.text = "😭 ${meme.reactions["cry"]   ?: 0}"
            tvSadEmoji.text    = "💔 ${meme.reactions["troll"] ?: 0}"
        }

        fun updateHitMiss(meme: MemeMessage) = with(binding) {
            buttonHit.text  = "🔥 ${meme.hit}"
            buttonMiss.text = "❌ ${meme.miss}"
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
            if (onCommentClick != null) onCommentClick.invoke(meme)
            else try {
                ctx.startActivity(Intent(ctx, CommentActivity::class.java).apply {
                    putExtra("MESSAGE_ID", meme.id); putExtra("MESSAGE_TYPE", "meme")
                })
            } catch (e: Exception) {
                Toast.makeText(ctx, "Unable to open comments", Toast.LENGTH_SHORT).show()
            }
        }
    }
}