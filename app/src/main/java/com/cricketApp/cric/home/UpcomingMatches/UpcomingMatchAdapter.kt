package com.cricketApp.cric.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.CardUpcomingMatchesBinding
import com.cricketApp.cric.home.upcomingMatch.MatchData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            try {
                if (!logoUrl.isNullOrEmpty()) {
                    Glide.with(binding.root.context)
                        .load(logoUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.icc_logo)
                        .error(R.drawable.icc_logo)
                        .centerCrop()
                        .into(imageView)
                } else {
                    // If logoUrl is null, fallback to a default drawable
                    Glide.with(binding.root.context)
                        .load(R.drawable.icc_logo)
                        .centerCrop()
                        .into(imageView)
                }
            } catch (e: Exception) {
                // If any exception, still try to show default
                try {
                    Glide.with(binding.root.context)
                        .load(R.drawable.icc_logo)
                        .centerCrop()
                        .into(imageView)
                } catch (innerE: Exception) {
                    // Last resort - just ignore rendering if context is invalid
                }
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

                withContext(Dispatchers.Main) {
                    try {
                        if (binding != null) {
                            binding.seriesName.text = seriesName ?: "N/A"
                            loadSeriesLogo(match.seriesLogo ?: getDefaultSeriesLogo(match.series_id))
                        }
                    } catch (e: Exception) {
                        // View might have been recycled or destroyed
                    }
                }
            }
        }

        private suspend fun fetchSeriesName(seriesId: Int): String? {
            return try {
                // Simulate success or error response
                if (seriesId == 0) throw Exception("Failed to fetch series")
                "Series #$seriesId"
            } catch (e: Exception) {
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
                null
            }
        }

        private fun getDefaultSeriesLogo(seriesId: Int?): String? {
            return if (seriesId != null && seriesId != 0) {
                "https://path.to.your.series.logo/$seriesId.png" // Replace with actual URL
            } else {
                null
            }
        }

        private fun loadSeriesLogo(logoUrl: String?) {
            try {
                if (!logoUrl.isNullOrEmpty()) {
                    Glide.with(binding.root.context)
                        .load(logoUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.icc_logo)
                        .error(R.drawable.icc_logo)
                        .centerCrop()
                        .into(binding.seriesLogo)
                } else {
                    // Fallback to default logo
                    Glide.with(binding.root.context)
                        .load(R.drawable.icc_logo)
                        .centerCrop()
                        .into(binding.seriesLogo)
                }
            } catch (e: Exception) {
                // In case context is invalid, etc.
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
                dateTimeString
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = CardUpcomingMatchesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        if (position < matchList.size) {
            holder.bind(matchList[position])
        }
    }

    fun updateData(newMatches: List<MatchData>) {
        matchList = newMatches
        notifyDataSetChanged()
    }

    override fun getItemCount() = matchList.size
}