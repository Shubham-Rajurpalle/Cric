package com.cricketApp.cric.Profile

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.cricketApp.cric.Chat.PollOptionView
import com.cricketApp.cric.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UserActivityAdapter(
    private val activities: List<UserActivity>,
    private val onActivityClick: (UserActivity) -> Unit
) : RecyclerView.Adapter<UserActivityAdapter.ActivityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_activity, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activities[position]
        holder.bind(activity, onActivityClick)
    }

    override fun getItemCount() = activities.size

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

        fun bind(activity: UserActivity, onActivityClick: (UserActivity) -> Unit) {
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
                        // Check again if the view is still valid before loading the image
                        if (imageView.context == null ||
                            (imageView.context is Activity && (imageView.context as Activity).isFinishing) ||
                            (imageView.context is Activity && (imageView.context as Activity).isDestroyed)) {
                            return
                        }

                        val profileUrl = snapshot.getValue(String::class.java)
                        if (profileUrl != null && profileUrl.isNotEmpty()) {
                            // Use applicationContext to prevent memory leaks
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
                        // Catch any Glide or context-related exceptions
                    //    Log.e("UserActivityAdapter", "Error loading profile picture", e)

                        // Set default profile picture
                        try {
                            imageView.setImageResource(R.drawable.profile_icon)
                        } catch (e2: Exception) {
                            // Even setting the image resource might fail if the view is no longer valid
                        //    Log.e("UserActivityAdapter", "Could not set default profile picture", e2)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    try {
                        // Check if view is still valid
                        if (imageView.isAttachedToWindow) {
                            imageView.setImageResource(R.drawable.profile_icon)
                        }
                    } catch (e: Exception) {
                    //    Log.e("UserActivityAdapter", "Error in onCancelled", e)
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
}