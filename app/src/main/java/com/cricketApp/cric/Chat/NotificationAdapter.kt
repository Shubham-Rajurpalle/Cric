package com.cricketApp.cric

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.Notifications.NotificationItem
import com.cricketApp.cric.databinding.ItemNotificationBinding

class NotificationsAdapter(
    private val notifications: List<NotificationItem>,
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: NotificationItem) {
            binding.textNotificationTitle.text = notification.getNotificationText()
            binding.textTimestamp.text = notification.getFormattedTimestamp()

            // Set content type icon
            binding.iconNotificationType.setImageResource(notification.getTypeIcon())

            // Set reaction type icon
            binding.iconReactionType.setImageResource(notification.getReactionIcon())

            // Set team information if available
            if (notification.team.isNotEmpty()) {
                binding.textTeam.visibility = View.VISIBLE
                binding.textTeam.text = notification.team
                loadTeamLogo(notification.team, binding.imageTeamLogo)
                binding.imageTeamLogo.visibility = View.VISIBLE
            } else {
                binding.textTeam.visibility = View.GONE
                binding.imageTeamLogo.visibility = View.GONE
            }

            // Set message preview if available
            if (notification.message.isNotEmpty() && notification.message != "Meme") {
                binding.textMessagePreview.visibility = View.VISIBLE

                val messagePreview = if (notification.message.length > 50) {
                    "${notification.message.substring(0, 47)}..."
                } else {
                    notification.message
                }

                binding.textMessagePreview.text = "\"$messagePreview\""
            } else {
                binding.textMessagePreview.visibility = View.GONE
            }

            // Set read/unread status
            if (notification.read) {
                binding.root.setBackgroundResource(R.drawable.notification_read_background)
                binding.textNotificationTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.unreadIndicator.visibility = View.GONE
            } else {
                binding.root.setBackgroundResource(R.drawable.notification_unread_background)
                binding.textNotificationTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.unreadIndicator.visibility = View.VISIBLE
            }

            // Set click listener
            binding.root.setOnClickListener {
                onItemClick(notification)
            }
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