package com.cricketApp.cric.home.upcomingMatch

data class SportMonksResponseUpcoming(
    val data: List<MatchData>
)

data class MatchData(
    val id: Int,
    val round: String,
    val starting_at: String,
    val type: String,
    val localteam_id: Int,
    val visitorteam_id: Int,
    var localteam: Team? = null,
    var visitorteam: Team? = null,
    var localteamLogo: String? = null,
    var visitorteamLogo: String? = null,
    var seriesLogo: String? = null,  // This will hold the series logo URL
    val series_id: Int  // This links to the series
)

data class Team(
    val id: Int,
    val name: String,
    val code: String,
    val image_path: String? = null
)

data class TeamResponse(
    val data: TeamDetails
)

data class TeamDetails(
    val id: Int,
    val name: String,
    val code: String,
    val image_path: String? = null
)

data class SeriesResponse(
    val data: SeriesDetails
)

data class SeriesDetails(
    val id: Int,
    val name: String,
    val logo_path: String? = null
)


data class LeagueResponse(
    val data: LeagueDetails
)

data class LeagueDetails(
    val id: Int,
    val name: String,
    val logo_path: String? = null
)
