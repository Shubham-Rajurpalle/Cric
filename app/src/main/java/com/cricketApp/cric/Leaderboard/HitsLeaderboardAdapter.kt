package com.cricketApp.cric.Leaderboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.CardLeaderboardBinding


class HitsLeaderboardAdapter : ListAdapter<TeamData, HitsLeaderboardAdapter.ViewHolder>(TeamDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val team = getItem(position)
        holder.bind(team, position + 4) // Positions start from 4
    }

    class ViewHolder(private val binding: CardLeaderboardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(team: TeamData, rank: Int) {
            binding.tvRank.text = rank.toString()
            binding.tvTeamName.text = team.name
            binding.tvHits.text = "${team.hits} Hits"

            Glide.with(binding.root.context)
                .load(team.logoUrl)
                .into(binding.ivTeamLogo)
        }
    }

    class TeamDiffCallback : DiffUtil.ItemCallback<TeamData>() {
        override fun areItemsTheSame(oldItem: TeamData, newItem: TeamData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TeamData, newItem: TeamData): Boolean {
            return oldItem == newItem
        }
    }
}
