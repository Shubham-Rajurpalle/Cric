package com.cricketApp.cric.Profile

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.cricketApp.cric.Chat.PollOptionView
import com.cricketApp.cric.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UserActivityAdapter(
    private val activities: MutableList<UserActivity>,
    private val onActivityClick: (UserActivity) -> Unit,
    private val enableLongPress: Boolean = false
) : RecyclerView.Adapter<UserActivityAdapter.ActivityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_activity, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activities[position]
        holder.bind(activity, onActivityClick, enableLongPress) { deletedActivity ->
            // Handle deletion by removing from the list and updating the adapter
            val index = activities.indexOf(deletedActivity)
            if (index != -1) {
                activities.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }

    override fun getItemCount() = activities.size

    fun removeActivity(activityId: String) {
        val index = activities.indexOfFirst { it.id == activityId }
        if (index != -1) {
            activities.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameTextView: TextView = itemView.findViewById(R.id.activityUsername)
        private val teamNameTextView: TextView = itemView.findViewById(R.id.teamName)
        private val contentTextView: TextView = itemView.findViewById(R.id.activityContent)
        private val activityImageView: ImageView = itemView.findViewById(R.id.activityImage)
        private val pollOptionsContainer: LinearLayout = itemView.findViewById(R.id.pollOptionsContainer)
        private val userIcon: ImageView = itemView.findViewById(R.id.userIcon)
        private val teamIcon: ImageView = itemView.findViewById(R.id.teamIcon)

        // Emoji reaction TextViews
        private val tvHappyEmoji: TextView = itemView.findViewById(R.id.tvHappyEmoji)
        private val tvAngryEmoji: TextView = itemView.findViewById(R.id.tvAngryEmoji)
        private val tvSadEmoji: TextView = itemView.findViewById(R.id.tvSadEmoji)
        private val tvCryingEmoji: TextView = itemView.findViewById(R.id.tvCryingEmoji)

        // Hit/Miss buttons
        private val buttonHit: TextView = itemView.findViewById(R.id.buttonHit)
        private val buttonMiss: TextView = itemView.findViewById(R.id.buttonMiss)

        fun bind(
            activity: UserActivity,
            onActivityClick: (UserActivity) -> Unit,
            enableLongPress: Boolean = false,
            onActivityDeleted: (UserActivity) -> Unit
        ) {
            // Set basic info
            usernameTextView.text = activity.username
            teamNameTextView.text = activity.team
            contentTextView.text = when (activity.type) {
                UserActivityType.MEME -> "Posted a meme"
                UserActivityType.COMMENT -> "Commented: ${activity.content}"
                else -> activity.content
            }

            // Load profile picture
            loadProfilePicture(activity.userId, userIcon)

            // Load team icon
            loadTeamLogo(activity.team, teamIcon)

            // Handle image if present
            if (activity.imageUrl.isNotEmpty()) {
                activityImageView.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(activity.imageUrl)
                    .apply(RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(activityImageView)
            } else {
                activityImageView.visibility = View.GONE
            }

            // Handle poll options if present
            pollOptionsContainer.visibility = View.GONE
            pollOptionsContainer.removeAllViews()

            if (activity.type == UserActivityType.POLL) {
                val options = activity.additionalData?.get("options") as? Map<*, *>

                if (options != null) {
                    pollOptionsContainer.visibility = View.VISIBLE

                    // Calculate total votes
                    var totalVotes = 0
                    options.values.forEach { value ->
                        if (value is Int) {
                            totalVotes += value
                        } else if (value is Long) {
                            totalVotes += value.toInt()
                        }
                    }

                    // Add poll options
                    options.entries.forEach { entry ->
                        val optionText = entry.key.toString()
                        val votes = when (val value = entry.value) {
                            is Int -> value
                            is Long -> value.toInt()
                            else -> 0
                        }

                        val percentage = if (totalVotes > 0) (votes * 100 / totalVotes) else 0

                        val optionView = PollOptionView(itemView.context).apply {
                            setOptionText(optionText)
                            setVotePercentage(percentage)
                            setVoteCount(votes)
                        }

                        pollOptionsContainer.addView(optionView)
                    }
                }
            }

            // Set reactions counts using the same format as the chat item
            val reactions = activity.reactions ?: mapOf()
            val formatCount = { count: Int ->
                when {
                    count >= 1000 -> String.format("%.1fK", count / 1000f)
                    count > 0 -> count.toString()
                    else -> "0"
                }
            }

            // For happy emoji (😁)
            val happyCount = reactions["happy"] ?: 0
            tvHappyEmoji.text = "😁 ${formatCount(happyCount)}"
            tvHappyEmoji.visibility = if (happyCount > 0) View.VISIBLE else View.VISIBLE

            // For angry emoji (🤬)
            val angryCount = reactions["angry"] ?: 0
            tvAngryEmoji.text = "🤬 ${formatCount(angryCount)}"
            tvAngryEmoji.visibility = if (angryCount > 0) View.VISIBLE else View.VISIBLE

            // For sad emoji (💔)
            val sadCount = reactions["sad"] ?: 0
            tvSadEmoji.text = "💔 ${formatCount(sadCount)}"
            tvSadEmoji.visibility = if (sadCount > 0) View.VISIBLE else View.VISIBLE

            // For crying emoji (😭)
            val cryingCount = reactions["cry"] ?: 0
            tvCryingEmoji.text = "😭 ${formatCount(cryingCount)}"
            tvCryingEmoji.visibility = if (cryingCount > 0) View.VISIBLE else View.VISIBLE

            // Set hit/miss counts
            buttonHit.text = "🔥 ${formatCount(activity.hits)}"
            buttonMiss.text = "❌ ${formatCount(activity.misses)}"

            // Handle item click
            itemView.setOnClickListener {
                onActivityClick(activity)
            }

            // Setup long press if enabled
            if (enableLongPress) {
                itemView.setOnLongClickListener {
                    showDeleteDialog(activity, onActivityDeleted)
                    true
                }
            }
        }

        private fun showDeleteDialog(activity: UserActivity, onDelete: (UserActivity) -> Unit) {
            val context = itemView.context
            AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
                .setTitle("Delete Activity")
                .setMessage("Are you sure you want to delete this activity?")
                .setPositiveButton("Delete") { _, _ ->
                    // Delete the activity from Firebase
                    deleteActivity(activity) { success ->
                        if (success) {
                            onDelete(activity)
                            Toast.makeText(context, "Activity deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to delete activity", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun deleteActivity(activity: UserActivity, callback: (Boolean) -> Unit) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

            // Verify that the current user owns this activity
            if (currentUserId != activity.userId) {
                callback(false)
                return
            }

            val dbRef = when (activity.type) {
                UserActivityType.CHAT -> {
                    FirebaseDatabase.getInstance().getReference("NoBallZone/chats/${activity.id}")
                }
                UserActivityType.MEME -> {
                    FirebaseDatabase.getInstance().getReference("NoBallZone/memes/${activity.id}")
                }
                UserActivityType.POLL -> {
                    FirebaseDatabase.getInstance().getReference("NoBallZone/polls/${activity.id}")
                }
                UserActivityType.COMMENT -> {
                    // For comments, we need the parent ID and type
                    val parentId = activity.additionalData?.get("parentId") as? String
                    val parentType = activity.additionalData?.get("parentType") as? String

                    if (parentId != null && parentType != null) {
                        val path = when (parentType) {
                            "chat" -> "NoBallZone/chats"
                            "meme" -> "NoBallZone/memes"
                            "poll" -> "NoBallZone/polls"
                            else -> null
                        }

                        if (path != null) {
                            FirebaseDatabase.getInstance().getReference("$path/$parentId/comments/${activity.id}")
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } ?: run {
                callback(false)
                return
            }

            dbRef.removeValue()
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        }

        private fun loadProfilePicture(userId: String, imageView: ImageView) {
            // First, check if the ImageView is still attached to a window
            if (imageView.context == null ||
                (imageView.context is Activity && (imageView.context as Activity).isFinishing) ||
                (imageView.context is Activity && (imageView.context as Activity).isDestroyed)) {
                return
            }

            val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val profileUrl = snapshot.getValue(String::class.java)
                        if (profileUrl != null && profileUrl.isNotEmpty()) {
                            Glide.with(imageView.context.applicationContext)
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
                    } catch (e: Exception) {
                        imageView.setImageResource(R.drawable.profile_icon)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    try {
                        imageView.setImageResource(R.drawable.profile_icon)
                    } catch (e: Exception) {
                        // Handle exception
                    }
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
                "SRH" to R.drawable.srh,
                "PBKS" to R.drawable.pbks,
                "RR" to R.drawable.rr,
                "GT" to R.drawable.gt,
                "LSG" to R.drawable.lsg
            )
            val logoResource = teamLogoMap[teamName] ?: R.drawable.icc_logo
            imageView.setImageResource(logoResource)
        }
    }
}