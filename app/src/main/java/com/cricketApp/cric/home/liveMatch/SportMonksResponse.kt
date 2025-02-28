package com.cricketApp.cric.home.liveMatch

import com.google.gson.annotations.SerializedName

data class SportMonksResponse(
    val data: List<MatchData> // Corrected response model name
)

data class MatchData(
    val id: Int,
    val league: League,
    val localteam: Team,
    val visitorteam: Team,
    val runs: List<Score>?,
    @SerializedName("live") val isLive: Boolean
)

data class League(val name: String)
data class Team(val code: String)
data class Score(val score: Int, val wickets: Int, val overs: Double)
