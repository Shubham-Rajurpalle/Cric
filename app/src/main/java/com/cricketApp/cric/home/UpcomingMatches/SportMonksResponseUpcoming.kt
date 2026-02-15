package com.cricketApp.cric.home.upcomingMatch

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SportMonksResponseUpcoming(
    @Expose @SerializedName("data") val data: List<MatchData>
)

data class MatchData(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("round") val round: String,
    @Expose @SerializedName("starting_at") val starting_at: String,
    @Expose @SerializedName("type") val type: String,
    @Expose @SerializedName("localteam_id") val localteam_id: Int,
    @Expose @SerializedName("visitorteam_id") val visitorteam_id: Int,
    @Expose @SerializedName("localteam") var localteam: Team? = null,
    @Expose @SerializedName("visitorteam") var visitorteam: Team? = null,
    @Expose @SerializedName("series_id") val series_id: Int,
    @Expose @SerializedName("league_id") val league_id: Int,
    @Transient var localteamLogo: String? = null,
    @Transient var visitorteamLogo: String? = null,
    @Transient var seriesLogo: String? = null,
    @Transient var leagueLogo: String? = null
)

data class Team(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String,
    @Expose @SerializedName("code") val code: String,
    @Expose @SerializedName("image_path") val image_path: String? = null
)

data class TeamResponse(
    @Expose @SerializedName("data") val data: TeamDetails
)

data class TeamDetails(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String,
    @Expose @SerializedName("code") val code: String,
    @Expose @SerializedName("image_path") val image_path: String? = null
)

data class SeriesResponse(
    @Expose @SerializedName("data") val data: SeriesDetails
)

data class SeriesDetails(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String? = null,
    @Expose @SerializedName("logo_path") val logo_path: String? = null
)

data class LeagueResponse(
    @Expose @SerializedName("data") val data: LeagueDetails
)

data class LeagueDetails(
    @Expose @SerializedName("id") val id: Int,
    @Expose @SerializedName("name") val name: String,
    @Expose @SerializedName("logo_path") val logo_path: String? = null
)