package com.cricketApp.cric

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.R
import com.cricketApp.cric.home.liveMatch.MatchData

class LiveScoreStripAdapter(
    private val matchList: MutableList<MatchData>,
    private val onMatchClick: (MatchData) -> Unit
) : RecyclerView.Adapter<LiveScoreStripAdapter.StripViewHolder>() {

    inner class StripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val liveStrip: View          = itemView.findViewById(R.id.liveIndicatorStrip)
        private val leagueLogo: ImageView     = itemView.findViewById(R.id.stripLeagueLogo)
        private val leagueName: TextView      = itemView.findViewById(R.id.stripLeagueName)
        private val status: TextView          = itemView.findViewById(R.id.stripStatus)
        private val team1Logo: ImageView      = itemView.findViewById(R.id.stripTeam1Logo)
        private val team1Code: TextView       = itemView.findViewById(R.id.stripTeam1Code)
        private val team1Score: TextView      = itemView.findViewById(R.id.stripTeam1Score)
        private val team2Logo: ImageView      = itemView.findViewById(R.id.stripTeam2Logo)
        private val team2Code: TextView       = itemView.findViewById(R.id.stripTeam2Code)
        private val team2Score: TextView      = itemView.findViewById(R.id.stripTeam2Score)
        private val overs: TextView           = itemView.findViewById(R.id.stripOvers)
        private val card: CardView            = itemView as CardView

        fun bind(match: MatchData) {
            // League
            leagueName.text = match.league.name.ifEmpty { "Cricket" }
            loadImage(match.league.imagePath, leagueLogo)

            // Status
            if (match.live) {
                status.text      = "LIVE"
                status.setTextColor(Color.parseColor("#FF3D3D"))
                liveStrip.setBackgroundColor(Color.parseColor("#FF3D3D"))
                startPulseAnimation(liveStrip)
            } else {
                status.text = match.status.ifEmpty { "–" }
                status.setTextColor(Color.parseColor("#AAFFFFFF"))
                liveStrip.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                liveStrip.clearAnimation()
            }

            // Teams
            team1Code.text = match.localteam.code.ifEmpty { match.localteam.name.take(3).uppercase() }
            team2Code.text = match.visitorteam.code.ifEmpty { match.visitorteam.name.take(3).uppercase() }
            loadImage(match.localteam.imagePath, team1Logo)
            loadImage(match.visitorteam.imagePath, team2Logo)

            // Scores
            team1Score.text = formatScore(match.localteamScore.runs, match.localteamScore.wickets)
            team2Score.text = formatScore(match.visitorteamScore.runs, match.visitorteamScore.wickets)

            // Style active batting team score
            val team1Active = match.localteamScore.runs > 0 || match.localteamScore.wickets > 0
            team1Score.setTextColor(
                if (team1Active) Color.parseColor("#FFD700") else Color.parseColor("#88FFFFFF")
            )
            team2Score.setTextColor(
                if (!team1Active && (match.visitorteamScore.runs > 0 || match.visitorteamScore.wickets > 0))
                    Color.parseColor("#FFD700") else Color.parseColor("#88FFFFFF")
            )

            // Overs / Note
            val oversStr = if (match.localteamScore.overs > 0) "${match.localteamScore.overs} ov" else ""
            val noteStr  = match.note.take(30)
            overs.text = when {
                oversStr.isNotEmpty() && noteStr.isNotEmpty() -> "$oversStr • $noteStr"
                oversStr.isNotEmpty() -> oversStr
                noteStr.isNotEmpty()  -> noteStr
                else                  -> match.startingAt.take(16).replace("T", " ")
            }

            card.setOnClickListener { onMatchClick(match) }
        }

        private fun formatScore(runs: Int, wickets: Int): String =
            if (runs == 0 && wickets == 0) "Yet to bat" else "$runs/$wickets"

        private fun loadImage(url: String, view: ImageView) {
            if (url.isNotEmpty()) {
                Glide.with(view.context)
                    .load(url)
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(view)
            } else {
                view.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }

        /** Pulse alpha animation for the LIVE red top strip */
        private fun startPulseAnimation(view: View) {
            view.clearAnimation()
            ObjectAnimator.ofFloat(view, "alpha", 1f, 0.2f, 1f).apply {
                duration      = 1200
                repeatCount   = ValueAnimator.INFINITE
                repeatMode    = ValueAnimator.RESTART
                start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StripViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_live_score_strip, parent, false)
        return StripViewHolder(view)
    }

    override fun onBindViewHolder(holder: StripViewHolder, position: Int) =
        holder.bind(matchList[position])

    override fun getItemCount() = matchList.size

    fun updateMatches(newList: List<MatchData>) {
        matchList.clear()
        matchList.addAll(newList)
        notifyDataSetChanged()
    }
}