package com.cricketApp.cric.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.CardUpcomingMatchesBinding
import com.cricketApp.cric.home.UpcomingMatches.UpcomingMatchData

class UpcomingMatchAdapter(
    private val matchList: MutableList<UpcomingMatchData>
) : RecyclerView.Adapter<UpcomingMatchAdapter.MatchViewHolder>() {

    init {
        sortMatchesByDate()
    }

    private fun sortMatchesByDate() {
        matchList.sortBy { match ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    java.time.LocalDateTime.parse(match.startingAt)
                } else {
                    TODO("VERSION.SDK_INT < O")
                }
            } catch (e: Exception) {
                null
            }
        }
    }


    inner class MatchViewHolder(private val binding: CardUpcomingMatchesBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(match: UpcomingMatchData) {
            // League/series name
            binding.seriesName.text = match.league.name.ifEmpty { match.stage.name.ifEmpty { "Cricket" } }

            // Match round/type
            binding.matchNumber.text = match.round.ifEmpty { match.type.ifEmpty { "Match" } }

            // Team names
            binding.team1Name.text = match.localteam.name
            binding.team2Name.text = match.visitorteam.name

            // Date/time
            binding.matchTime.text = formatDateTime(match.startingAt)

            // Logos
            loadLogo(match.localteam.imagePath, binding.team1Logo)
            loadLogo(match.visitorteam.imagePath, binding.team2Logo)
            loadLogo(match.league.imagePath, binding.seriesLogo)
        }

        private fun loadLogo(url: String, imageView: ImageView) {
            Glide.with(imageView.context)
                .load(url.ifEmpty { null })
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.icc_logo)
                .error(R.drawable.icc_logo)
                .centerCrop()
                .into(imageView)
        }

        private fun formatDateTime(dateTimeString: String): String {
            if (dateTimeString.isEmpty()) return "TBD"
            return try {
                val parts = dateTimeString.split("T")
                if (parts.size == 2) {
                    val datePart = parts[0].split("-")
                    val timePart = parts[1].split(":")
                    val months = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May",
                        "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val month = months[datePart[1].toInt()]
                    val day   = datePart[2].toInt().toString()
                    val hour  = timePart[0].toInt()
                    val min   = timePart[1]
                    "$month $day\n$hour:$min"
                } else dateTimeString
            } catch (e: Exception) { dateTimeString }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MatchViewHolder(CardUpcomingMatchesBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ))

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) =
        holder.bind(matchList[position])

    override fun getItemCount() = matchList.size
}