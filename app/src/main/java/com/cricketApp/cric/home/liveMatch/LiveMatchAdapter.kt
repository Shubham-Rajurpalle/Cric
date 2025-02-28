package com.cricketApp.cric.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.databinding.CardLiveMatchBinding
import com.cricketApp.cric.home.liveMatch.MatchData

class LiveMatchAdapter(private var matchList: List<MatchData>) :
    RecyclerView.Adapter<LiveMatchAdapter.MatchViewHolder>() {

    inner class MatchViewHolder(private val binding: CardLiveMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(match: MatchData) {
            binding.leagueName.text = match.league?.name ?: "Unknown"
            binding.team1Name.text = match.localteam?.code ?: "N/A"
            binding.team2Name.text = match.visitorteam?.code ?: "N/A"

            // Handle scores safely
            val runs = match.runs ?: emptyList()  // Ensure non-null list

            if (runs.isNotEmpty()) {
                binding.score1.text = "${runs[0].score}/${runs[0].wickets}"
                binding.score2.text = if (runs.size > 1) {
                    "${runs[1].score}/${runs[1].wickets}"
                } else {
                    "N/A"
                }
                binding.oversInfo.text = "Overs: ${runs[0].overs}"
            } else {
                binding.score1.text = "N/A"
                binding.score2.text = "N/A"
                binding.oversInfo.text = "Overs: N/A"
            }

            // Match status
            binding.matchStatus.text = if (match.isLive) "Live" else "Completed"
            binding.matchStatus.setTextColor(
                if (match.isLive) android.graphics.Color.GREEN else android.graphics.Color.RED
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = CardLiveMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(matchList[position])
    }
    fun updateData(newMatches: List<MatchData>) {
        matchList = newMatches
        notifyDataSetChanged()
    }
    override fun getItemCount() = matchList.size
}
