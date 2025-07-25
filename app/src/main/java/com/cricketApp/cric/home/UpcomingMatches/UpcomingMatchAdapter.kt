package com.cricketApp.cric.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.CardUpcomingMatchesBinding
import com.cricketApp.cric.home.upcomingMatch.MatchData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpcomingMatchAdapter(private var matchList: List<MatchData>) :
    RecyclerView.Adapter<UpcomingMatchAdapter.MatchViewHolder>() {

    inner class MatchViewHolder(private val binding: CardUpcomingMatchesBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(match: MatchData) {

            // Set initial loading state for series name
            binding.seriesName.text = "Loading..."
            fetchSeriesOrLeagueName(match)

            // Set match ID
            binding.matchNumber.text = "Match ${match.id}"

            // Set team names
            binding.team1Name.text = match.localteam?.name ?: "Team 1"
            binding.team2Name.text = match.visitorteam?.name ?: "Team 2"

            // Format and set the match date/time
            val formattedDateTime = formatDateTime(match.starting_at)
            binding.matchTime.text = formattedDateTime

            // Load team logos
            loadTeamLogo(match.localteamLogo, binding.team1Logo)
            loadTeamLogo(match.visitorteamLogo, binding.team2Logo)
        }

        private fun loadTeamLogo(logoUrl: String?, imageView: ImageView) {
            if (logoUrl != null) {
                Glide.with(binding.root.context)
                    .load(logoUrl)
                    .centerCrop()
                    .into(imageView)
            } else {
                // If logoUrl is null, fallback to a default drawable (icc_logo)
                Glide.with(binding.root.context)
                    .load(R.drawable.icc_logo) // Replace with your drawable
                    .centerCrop()
                    .into(imageView)
            }
        }

        private fun fetchSeriesOrLeagueName(match: MatchData) {
            // Fetch series or league name asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                val seriesName = when {
                    match.series_id != 0 -> fetchSeriesName(match.series_id)
                    match.league_id != 0 -> fetchLeagueName(match.league_id)
                    else -> null
                }

                CoroutineScope(Dispatchers.Main).launch {
                    binding.seriesName.text = seriesName ?: "N/A"
                    loadSeriesLogo(match.series_id)
                }
            }
        }

        private suspend fun fetchSeriesName(seriesId: Int): String? {
            return try {
                // Simulate success or error response
                if (seriesId == 0) throw Exception("Failed to fetch series")
                "Series #$seriesId"
            } catch (e: Exception) {
                Log.e("API_ERROR", "Failed to fetch series $seriesId: ${e.message}")
                null
            }
        }

        private suspend fun fetchLeagueName(leagueId: Int): String? {
            // Simulate network call to fetch league name (replace with your repository call)
            return try {
                // Simulate success or error response
                if (leagueId == 0) throw Exception("Failed to fetch league")
                "League #$leagueId"
            } catch (e: Exception) {
                Log.e("API_ERROR", "Failed to fetch league $leagueId: ${e.message}")
                null
            }
        }

        private fun loadSeriesLogo(seriesId: Int?) {
            if (seriesId != null && seriesId != 0) {
                val seriesLogoUrl = "https://path.to.your.series.logo/$seriesId.png" // Replace with actual URL
                Glide.with(binding.root.context)
                    .load(seriesLogoUrl)
                    .centerCrop()
                    .into(binding.seriesLogo)
            } else {
                // If seriesId is null or 0, fallback to the default series logo (icc_logo)
                Glide.with(binding.root.context)
                    .load(R.drawable.icc_logo) // Replace with your default series logo
                    .centerCrop()
                    .into(binding.seriesLogo)
            }
        }

        private fun formatDateTime(dateTimeString: String?): String {
            if (dateTimeString == null) return "TBD"

            return try {
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
