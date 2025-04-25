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
import com.cricketApp.cric.R
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
            try {
                // League/Series info
                val leagueName = match.league?.name ?: "Unknown League"
                binding.leagueName.text = leagueName

                match.league?.image_path?.let { logoUrl ->
                    try {
                        Glide.with(binding.root.context)
                            .load(logoUrl)
                            .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_report_image))
                            .centerCrop()
                            .into(binding.leagueLogo)
                    } catch (e: Exception) {
                        binding.leagueLogo.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                } ?: run {
                    binding.leagueLogo.setImageResource(android.R.drawable.ic_menu_report_image)
                }

                // Match number
                binding.matchNumber.text = "Match ${match.matchNumber ?: match.stage?.name ?: "?"}"

                val team1Name = match.localteam?.name ?: match.localteam?.code ?: "Team 1"
                val team2Name = match.visitorteam?.name ?: match.visitorteam?.code ?: "Team 2"
                binding.team1Name.text = team1Name
                binding.team2Name.text = team2Name

                // Team logos - find views by direct ID or resource identifier
                val team1LogoView = findViewSafely<ImageView>("team1_logo")
                val team2LogoView = findViewSafely<ImageView>("team2_logo")

                // Score TextViews
                val team1ScoreView = findViewSafely<TextView>("team1_score")
                val team2ScoreView = findViewSafely<TextView>("team2_score")

                // Overs info
                val oversView = findViewSafely<TextView>("overs_text")

                // Match status
                val statusView = findViewSafely<TextView>("match_status")

                // Load team logos with safe error handling
                team1LogoView?.let { imageView ->
                    match.localteam?.image_path?.let { logoUrl ->
                        try {
                            Glide.with(binding.root.context)
                                .load(logoUrl)
                                .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_report_image))
                                .centerCrop()
                                .into(imageView)
                        } catch (e: Exception) {
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    } ?: run {
                        imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                }

                team2LogoView?.let { imageView ->
                    match.visitorteam?.image_path?.let { logoUrl ->
                        try {
                            Glide.with(binding.root.context)
                                .load(logoUrl)
                                .apply(RequestOptions().placeholder(android.R.drawable.ic_menu_report_image))
                                .centerCrop()
                                .into(imageView)
                        } catch (e: Exception) {
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
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
            } catch (e: Exception) {
                // Fail gracefully - at least show team names
                try {
                    binding.team1Name.text = match.localteam?.code ?: "Team 1"
                    binding.team2Name.text = match.visitorteam?.code ?: "Team 2"
                } catch (ignored: Exception) {
                    // Last resort fallback
                }
            }
        }

        // Generic method to find views safely by either direct ID or resource identifier
        private inline fun <reified T : View> findViewSafely(idName: String): T? {
            return try {
                // First try to find by direct resource ID
                val field = R.id::class.java.getDeclaredField(idName)
                field.isAccessible = true
                val id = field.getInt(null)
                binding.root.findViewById(id)
            } catch (e1: Exception) {
                try {
                    // Fallback to resource identifier lookup
                    val resId = binding.root.context.resources.getIdentifier(
                        idName, "id", binding.root.context.packageName
                    )
                    if (resId != 0) binding.root.findViewById(resId) else null
                } catch (e2: Exception) {
                    null
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = CardLiveMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        try {
            holder.bind(matchList[position])
        } catch (e: Exception) {
            // Silent fail to prevent crashes in production
        }
    }

    fun updateData(newMatches: List<MatchData>) {
        matchList = newMatches
        notifyDataSetChanged()
        checkEmptyState()
    }

    override fun getItemCount() = matchList.size
}