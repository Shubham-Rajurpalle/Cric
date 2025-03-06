package com.cricketApp.cric.Meme

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
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ItemMemeReceiveBinding
import com.cricketApp.cric.databinding.ItemMemeSendBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class MemeAdapter(
    private val items: List<MemeMessage>,
    private val onCommentClickListener: ((MemeMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    companion object {
        private const val VIEW_TYPE_SEND_MEME = 1
        private const val VIEW_TYPE_RECEIVE_MEME = 2

        // Payload constants
        private const val PAYLOAD_REACTION = "reaction"
        private const val PAYLOAD_HIT_MISS = "hit_miss"
        private const val PAYLOAD_COMMENTS = "comments"
    }

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
                val binding =ItemMemeSendBinding.inflate(inflater, parent, false)
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

    private fun showFullScreenImage(context: Context, imageUrl: String) {
        val intent = Intent(context, ActivityImageViewer::class.java).apply {
            putExtra("IMAGE_URL", imageUrl)
        }
        context.startActivity(intent)
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
                tvAngryEmoji.setOnClickListener { addReaction(meme, "fire", adapterPosition) }
                tvHappyEmoji.setOnClickListener { addReaction(meme, "laugh", adapterPosition) }
                tvCryingEmoji.setOnClickListener { addReaction(meme, "cry", adapterPosition) }
                tvSadEmoji.setOnClickListener { addReaction(meme, "troll", adapterPosition) }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { updateHitOrMiss(meme, "hit", adapterPosition) }
                buttonMiss.setOnClickListener { updateHitOrMiss(meme, "miss", adapterPosition) }

                textViewComments.setOnClickListener {
                    val context = itemView.context
                    onCommentClickListener?.invoke(meme) ?: run {
                        val intent = Intent(context, CommentActivity::class.java).apply {
                            putExtra("MESSAGE_ID", meme.id)
                            putExtra("MESSAGE_TYPE", "meme")
                        }
                        context.startActivity(intent)
                    }
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
                tvAngryEmoji.setOnClickListener { addReaction(meme, "fire", adapterPosition) }
                tvHappyEmoji.setOnClickListener { addReaction(meme, "laugh", adapterPosition) }
                tvCryingEmoji.setOnClickListener { addReaction(meme, "cry", adapterPosition) }
                tvSadEmoji.setOnClickListener { addReaction(meme, "troll", adapterPosition) }

                // Set hit/miss click listeners
                buttonHit.setOnClickListener { updateHitOrMiss(meme, "hit", adapterPosition) }
                buttonMiss.setOnClickListener { updateHitOrMiss(meme, "miss", adapterPosition) }

                textViewComments.setOnClickListener {
                    val context = itemView.context
                    onCommentClickListener?.invoke(meme) ?: run {
                        val intent = Intent(context, CommentActivity::class.java).apply {
                            putExtra("MESSAGE_ID", meme.id)
                            putExtra("MESSAGE_TYPE", "meme")
                        }
                        context.startActivity(intent)
                    }
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

        val hitMissRef = FirebaseDatabase.getInstance()
            .getReference("NoBallZone/memes/${meme.id}/$type")

        hitMissRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentValue = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentValue + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed && error == null && currentData != null) {
                    val newValue = currentData.getValue(Int::class.java) ?: 0
                    if (type == "hit") meme.hit = newValue else meme.miss = newValue
                    notifyItemChanged(position, PAYLOAD_HIT_MISS)
                }
            }
        })
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