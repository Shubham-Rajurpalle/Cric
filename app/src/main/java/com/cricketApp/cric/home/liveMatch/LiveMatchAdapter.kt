package com.cricketApp.cric.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.cricketApp.cric.databinding.CardLiveMatchBinding
import com.cricketApp.cric.home.liveMatch.MatchData

class LiveMatchAdapter(private var matchList: List<MatchData>) :
    RecyclerView.Adapter<LiveMatchAdapter.MatchViewHolder>() {

    private var emptyStateLayout: LinearLayout? = null
    private var recyclerView: RecyclerView? = null

    fun setEmptyStateViews(recyclerView: RecyclerView, emptyStateLayout: LinearLayout) {
        this.recyclerView = recyclerView
        this.emptyStateLayout = emptyStateLayout
        checkEmptyState()
    }

    private fun checkEmptyState() {
        if (matchList.isEmpty()) {
            recyclerView?.visibility = View.GONE
            emptyStateLayout?.visibility = View.VISIBLE
        } else {
            recyclerView?.visibility = View.VISIBLE
            emptyStateLayout?.visibility = View.GONE
        }
    }

    inner class MatchViewHolder(private val binding: CardLiveMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(match: MatchData) {
            // Find all necessary views using direct IDs instead of dynamic resource lookups
            // League/Series info
            val leagueName = match.league?.name ?: "Unknown League"
            val leagueLogoView = binding.root.findViewById<ImageView>(
                binding.root.context.resources.getIdentifier("league_logo", "id", binding.root.context.packageName)
            )
            val leagueNameView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("league_name", "id", binding.root.context.packageName)
            )

            // Match number
            val matchNumberView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("match_number", "id", binding.root.context.packageName)
            )

            // Team names
            val team1NameView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("team1_name", "id", binding.root.context.packageName)
            )
            val team2NameView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("team2_name", "id", binding.root.context.packageName)
            )

            // Team logos
            val team1LogoView = binding.root.findViewById<ImageView>(
                binding.root.context.resources.getIdentifier("team1_logo", "id", binding.root.context.packageName)
            )
            val team2LogoView = binding.root.findViewById<ImageView>(
                binding.root.context.resources.getIdentifier("team2_logo", "id", binding.root.context.packageName)
            )

            // Score TextViews
            val team1ScoreView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("team1_score", "id", binding.root.context.packageName)
            )
            val team2ScoreView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("team2_score", "id", binding.root.context.packageName)
            )

            // Overs info
            val oversView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("overs_text", "id", binding.root.context.packageName)
            )

            // Match status
            val statusView = binding.root.findViewById<TextView>(
                binding.root.context.resources.getIdentifier("match_status", "id", binding.root.context.packageName)
            )

            // Set data to views, handling null cases

            // League info
            leagueNameView?.text = leagueName
            leagueLogoView?.let { imageView ->
                match.league?.image_path?.let { logoUrl ->
                    Glide.with(binding.root.context)
                        .load(logoUrl)
                        .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_report_image))
                        .centerCrop()
                        .into(imageView)
                } ?: run {
                    // Set a placeholder if logo URL is null
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            }

            // Match number/round
            matchNumberView?.text = "Match ${match.matchNumber ?: match.stage?.name ?: "?"}"

            // Team names - use code as fallback
            val team1Name = match.localteam?.name ?: match.localteam?.code ?: "Team 1"
            val team2Name = match.visitorteam?.name ?: match.visitorteam?.code ?: "Team 2"

            team1NameView?.text = team1Name
            team2NameView?.text = team2Name

            // Team logos with error handling and placeholders
            team1LogoView?.let { imageView ->
                match.localteam?.image_path?.let { logoUrl ->
                    Glide.with(binding.root.context)
                        .load(logoUrl)
                        .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_report_image))
                        .centerCrop()
                        .into(imageView)
                } ?: run {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            }

            team2LogoView?.let { imageView ->
                match.visitorteam?.image_path?.let { logoUrl ->
                    Glide.with(binding.root.context)
                        .load(logoUrl)
                        .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_report_image))
                        .centerCrop()
                        .into(imageView)
                } ?: run {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            }

            // Handle scores with proper null checking
            val runs = match.runs ?: emptyList()

            // Find team IDs
            val team1Id = match.localteam?.id
            val team2Id = match.visitorteam?.id

            var team1Score = "0/0"
            var team2Score = "0/0"
            var currentOvers = "0.0"

            // Find scores for each team based on team_id
            if (runs.isNotEmpty()) {
                runs.forEach { score ->
                    when (score.team_id) {
                        team1Id -> {
                            team1Score = "${score.score}/${score.wickets}"
                            currentOvers = score.overs.toString()
                        }
                        team2Id -> {
                            team2Score = "${score.score}/${score.wickets}"
                        }
                    }
                }
            }

            team1ScoreView?.text = team1Score
            team2ScoreView?.text = team2Score
            oversView?.text = "$currentOvers Overs"

            // Match status
            statusView?.let {
                it.text = if (match.isLive) "LIVE" else "Completed"
                it.setTextColor(
                    if (match.isLive) android.graphics.Color.GREEN else android.graphics.Color.RED
                )
            }
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
        checkEmptyState()
    }

    override fun getItemCount() = matchList.size
}