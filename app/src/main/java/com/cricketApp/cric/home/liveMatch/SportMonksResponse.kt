package com.cricketApp.cric.home.liveMatch

import com.google.gson.annotations.SerializedName

data class SportMonksResponse(
    val data: List<MatchData>
)

data class MatchData(
    val id: Int,
    val league: League,
    val localteam: Team,
    val visitorteam: Team,
    val runs: List<Score>?,
    @SerializedName("live") val isLive: Boolean,
    val stage: Stage? = null,
    @SerializedName("round") val matchNumber: String? = null
)

data class League(
    val id: Int,
    val name: String,
    val image_path: String? = null
)

data class TeamResponse(
    val data: Team
)

data class Team(
    val id: Int,
    val code: String,
    val name: String? = null,
    val image_path: String? = null
)

data class StageResponse(
    val data: Stage
)

data class Stage(
    val id: Int,
    val name: String
)

data class ScoreResponse(
    val data: Score
)

data class Score(
    val team_id: Int,
    val score: Int,
    val wickets: Int,
    val overs: Double
)