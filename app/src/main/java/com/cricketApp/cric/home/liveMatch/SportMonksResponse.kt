package com.cricketApp.cric.home.liveMatch

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SportMonksResponse(
    @Expose @SerializedName("data") val data: List<MatchData>
)

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