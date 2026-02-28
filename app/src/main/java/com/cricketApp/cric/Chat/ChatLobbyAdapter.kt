package com.cricketApp.cric.Chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.R
import com.google.android.material.chip.Chip

class ChatLobbyAdapter(
    private val rooms: List<ChatRoomItem>,
    private val onRoomClick: (ChatRoomItem) -> Unit
) : RecyclerView.Adapter<ChatLobbyAdapter.RoomViewHolder>() {

    data class TeamConfig(val primaryColor: String, val secondaryColor: String)

    private val teamConfigs = mapOf(
        "CSK"  to TeamConfig("#F5C518", "#8B6914"),
        "MI"   to TeamConfig("#004FC7", "#002060"),
        "RCB"  to TeamConfig("#D50000", "#6B0000"),
        "KKR"  to TeamConfig("#5B2C8D", "#2D1246"),
        "DC"   to TeamConfig("#0039A6", "#001A4E"),
        "GT"   to TeamConfig("#1B4F8A", "#0A2040"),
        "LSG"  to TeamConfig("#00B4D8", "#005F73"),
        "PBKS" to TeamConfig("#C8102E", "#6B0018"),
        "RR"   to TeamConfig("#E91E8C", "#7B0043"),
        "SRH"  to TeamConfig("#F26522", "#7A2D00"),
    )

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val teamColorBg: DiagonalSplitView = itemView.findViewById(R.id.teamColorBg)
        val bannerImage: ImageView = itemView.findViewById(R.id.bannerImage)
        val roomName: TextView = itemView.findViewById(R.id.roomName)
        val roomDescription: TextView = itemView.findViewById(R.id.roomDescription)
        val activeUsersText: TextView = itemView.findViewById(R.id.activeUsersText)
        val chipLive: Chip = itemView.findViewById(R.id.chipLive)
        val chipTeamTag: Chip = itemView.findViewById(R.id.chipTeamTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room_card, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = rooms[position]
        val ctx = holder.itemView.context

        holder.roomName.text = room.name
        holder.roomDescription.text = room.description

        // Active users
        if (room.activeUsers > 0) {
            holder.activeUsersText.visibility = View.VISIBLE
            holder.activeUsersText.text = "${room.activeUsers} online"
        } else {
            holder.activeUsersText.visibility = View.GONE
        }

        // LIVE badge
        holder.chipLive.visibility =
            if (room.isLive && room.type == RoomType.LIVE) View.VISIBLE else View.GONE

        // ── Diagonal triangle color ───────────────────────────────────────────
        when (room.type) {
            RoomType.GLOBAL -> {
                holder.teamColorBg.splitColor = Color.parseColor("#1A3A5C")
                holder.chipTeamTag.visibility = View.GONE
            }
            RoomType.LIVE -> {
                holder.teamColorBg.splitColor = Color.parseColor("#6B0000")
                holder.chipTeamTag.visibility = View.GONE
                holder.bannerImage.scaleType  = ImageView.ScaleType.FIT_CENTER

                if (room.bannerImageUrl.isNotEmpty()) {
                    Glide.with(ctx)
                        .load(room.bannerImageUrl)
                        .placeholder(R.drawable.noballzone)
                        .error(R.drawable.noballzone)
                        .fitCenter()
                        .into(holder.bannerImage)
                } else {
                    holder.bannerImage.setImageResource(R.drawable.noballzone)
                }

                // Dim card slightly if match is delayed/rain
                holder.itemView.alpha = if (room.isLive) 1.0f else 0.75f
            }
            RoomType.TEAM -> {
                val config = teamConfigs[room.teamTag]
                if (config != null) {
                    holder.teamColorBg.splitColor = Color.parseColor(config.primaryColor)
                    holder.chipTeamTag.visibility = View.VISIBLE
                    holder.chipTeamTag.text = room.teamTag
                    holder.chipTeamTag.chipBackgroundColor =
                        android.content.res.ColorStateList.valueOf(
                            Color.parseColor(config.secondaryColor)
                        )
                    holder.chipTeamTag.setTextColor(Color.WHITE)
                } else {
                    holder.teamColorBg.splitColor = Color.parseColor("#1A1A2E")
                    holder.chipTeamTag.visibility = View.GONE
                }
            }
        }

        // ── Logo / banner image — always fully visible, no alpha reduction ────
        holder.bannerImage.alpha = 1.0f

        when {
            room.bannerImageUrl.isNotEmpty() -> {
                holder.bannerImage.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(ctx)
                    .load(room.bannerImageUrl)
                    .fitCenter()
                    .into(holder.bannerImage)
            }
            room.type == RoomType.GLOBAL -> {
                holder.bannerImage.scaleType = ImageView.ScaleType.FIT_CENTER
                val resId = ctx.resources.getIdentifier("team_banner_global", "drawable", ctx.packageName)
                holder.bannerImage.setImageResource(if (resId != 0) resId else R.drawable.cric_logo)
            }
            room.type == RoomType.TEAM -> {
                holder.bannerImage.scaleType = ImageView.ScaleType.FIT_CENTER
                val tag = room.teamTag.lowercase()
                // Try exact name "csk" first, then "team_csk"
                val resId = ctx.resources.getIdentifier(tag, "drawable", ctx.packageName)
                if (resId != 0) {
                    holder.bannerImage.setImageResource(resId)
                } else {
                    val prefixId = ctx.resources.getIdentifier("team_$tag", "drawable", ctx.packageName)
                    holder.bannerImage.setImageResource(
                        if (prefixId != 0) prefixId else R.drawable.noballzone
                    )
                }
            }
        }

        holder.itemView.setOnClickListener { onRoomClick(room) }
    }

    override fun getItemCount() = rooms.size
}