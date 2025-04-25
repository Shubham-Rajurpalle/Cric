package com.cricketApp.cric.home.liveMatch

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SportMonksResponse(
    @Expose @SerializedName("data") val data: List<MatchData>
)

data class MatchData(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("league") val league: League? = null,
    @Expose @SerializedName("localteam") val localteam: Team? = null,
    @Expose @SerializedName("visitorteam") val visitorteam: Team? = null,
    @Expose @SerializedName("runs") val runs: List<Score>? = null,
    @Expose @SerializedName("live") val live: Boolean = false,
    @Expose @SerializedName("stage") val stage: Stage? = null,
    @Expose @SerializedName("round") val round: String? = null,
    @Expose @SerializedName("status") val status: String? = null,
    @Expose @SerializedName("note") val note: String? = null,
    @Expose @SerializedName("starting_at") val starting_at: String? = null,
    @Expose @SerializedName("type") val type: String? = null,
    @Expose @SerializedName("localteam_id") val localteam_id: Int? = null,
    @Expose @SerializedName("visitorteam_id") val visitorteam_id: Int? = null,
    @Expose @SerializedName("series_id") val series_id: Int? = null,
    @Expose @SerializedName("league_id") val league_id: Int? = null,
    @Transient var localteamLogo: String? = null,
    @Transient var visitorteamLogo: String? = null,
    @Transient var seriesLogo: String? = null,
    @Transient var leagueLogo: String? = null
){
    val isLive: Boolean
        get() = live || status?.contains("Innings", ignoreCase = true) == true

    val matchNumber: String?
        get() = round
}

data class LeagueResponse(
    @Expose @SerializedName("data") val data: League
)

data class League(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String,
    @Expose @SerializedName("image_path") val image_path: String? = null
)

data class TeamResponse(
    @Expose @SerializedName("data") val data: Team
)

data class Team(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("code") val code: String,
    @Expose @SerializedName("name") val name: String? = null,
    @Expose @SerializedName("image_path") val image_path: String? = null
)

data class Stage(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String? = null
)

data class Score(
    @Expose @SerializedName("team_id") val team_id: Int,
    @Expose @SerializedName("score") val score: Int = 0,
    @Expose @SerializedName("wickets") val wickets: Int = 0,
    @Expose @SerializedName("overs") val overs: Double = 0.0
)

data class SeriesResponse(
    @Expose @SerializedName("data") val data: SeriesDetails
)

data class SeriesDetails(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String? = null,
    @Expose @SerializedName("logo_path") val logo_path: String? = null
)