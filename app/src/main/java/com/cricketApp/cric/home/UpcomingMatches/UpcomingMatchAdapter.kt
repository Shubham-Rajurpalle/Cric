package com.cricketApp.cric.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.CardUpcomingMatchesBinding
import com.cricketApp.cric.home.upcomingMatch.MatchData

class UpcomingMatchAdapter(private var matchList: List<MatchData>) :
    RecyclerView.Adapter<UpcomingMatchAdapter.MatchViewHolder>() {

    inner class MatchViewHolder(private val binding: CardUpcomingMatchesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(match: MatchData) {
            // Set match type or round as the series name
            binding.seriesName.text = match.type ?: match.round ?: "N/A"
            binding.matchNumber.text = "Match ${match.id}"

            // Team names
            binding.team1Name.text = match.localteam?.name ?: "Team 1"
            binding.team2Name.text = match.visitorteam?.name ?: "Team 2"

            // Format the date/time
            val formattedDateTime = formatDateTime(match.starting_at)
            binding.matchTime.text = formattedDateTime

            // Load team logos - handle null values gracefully
            match.localteamLogo?.let { logoUrl ->
                Glide.with(binding.root.context)
                    .load(logoUrl)
                    .centerCrop()
                    .into(binding.team1Logo)
            }

            match.visitorteamLogo?.let { logoUrl ->
                Glide.with(binding.root.context)
                    .load(logoUrl)
                    .centerCrop()
                    .into(binding.team2Logo)
            }
        }

        private fun formatDateTime(dateTimeString: String?): String {
            if (dateTimeString == null) return "TBD"

            // Implement date formatting based on your needs
            // This is just a placeholder - you should use proper date formatting
            return try {
                // Example pattern: Parse "2018-10-12T16:00:00.000000Z" to something like "Oct 12\n16:00"
                val parts = dateTimeString.split("T")
                if (parts.size == 2) {
                    val datePart = parts[0].split("-")
                    val timePart = parts[1].split(":")

                    val months = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val month = months[datePart[1].toInt()]
                    val day = datePart[2].toInt().toString()
                    val hour = timePart[0].toInt()
                    val minute = timePart[1]

                    "$month $day\n$hour:$minute"
                } else {
                    dateTimeString
                }
            } catch (e: Exception) {
                Log.e("DateFormat", "Error formatting date: $e")
                dateTimeString
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = CardUpcomingMatchesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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