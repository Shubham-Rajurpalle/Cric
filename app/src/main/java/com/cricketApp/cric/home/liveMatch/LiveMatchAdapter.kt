package com.cricketApp.cric.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.CardLiveMatchBinding
import com.cricketApp.cric.home.liveMatch.MatchData

class LiveMatchAdapter(
    private val matchList: MutableList<MatchData>,
    private val onMatchClick: (MatchData) -> Unit
) : RecyclerView.Adapter<LiveMatchAdapter.MatchViewHolder>() {

    inner class MatchViewHolder(private val binding: CardLiveMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(match: MatchData) {
            binding.leagueName.text = match.league.name.ifEmpty { "Cricket" }
            loadLogo(match.league.imagePath, binding.leagueLogo)

            val stageOrRound = listOfNotNull(
                match.stage.name.ifEmpty { null },
                match.round.ifEmpty { null }
            ).joinToString(" • ")
            binding.matchNumber.text = stageOrRound.ifEmpty { match.type.ifEmpty { "Match" } }

            binding.team1Name.text = match.localteam.code
            binding.team2Name.text = match.visitorteam.code
            loadLogo(match.localteam.imagePath, binding.team1Logo)
            loadLogo(match.visitorteam.imagePath, binding.team2Logo)

            binding.team1Score.text = formatScore(
                match.localteamScore.runs, match.localteamScore.wickets
            )
            binding.team2Score.text = formatScore(
                match.visitorteamScore.runs, match.visitorteamScore.wickets
            )
            binding.oversText.text = "${match.localteamScore.overs} ov"

            binding.matchStatus.text = when {
                match.live                -> "LIVE"
                match.status.isNotEmpty() -> match.status
                else                      -> "Upcoming"
            }
            binding.matchStatus.setTextColor(
                if (match.live) Color.parseColor("#FF3D3D")
                else Color.parseColor("#AAFFFFFF")
            )

//            if (match.note.isNotEmpty()) {
//                binding.matchNote.visibility = View.VISIBLE
//                binding.matchNote.text = match.note
//            } else {
//                binding.matchNote.visibility = View.GONE
//            }

            binding.root.setOnClickListener { onMatchClick(match) }
        }

        private fun loadLogo(imagePath: String, imageView: ImageView) {
            if (imagePath.isNotEmpty()) {
                Glide.with(imageView.context)
                    .load(imagePath)
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(imageView)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }

        private fun formatScore(runs: Int, wickets: Int) =
            if (runs == 0 && wickets == 0) "Yet to bat" else "$runs/$wickets"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MatchViewHolder(
            CardLiveMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) =
        holder.bind(matchList[position])

    override fun getItemCount() = matchList.size
}