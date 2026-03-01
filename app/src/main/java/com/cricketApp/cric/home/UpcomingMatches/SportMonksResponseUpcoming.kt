package com.cricketApp.cric.home.upcomingMatch

import com.cricketApp.cric.home.liveMatch.MatchData
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SportMonksResponseUpcoming(
    @Expose @SerializedName("data") val data: List<MatchData>
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