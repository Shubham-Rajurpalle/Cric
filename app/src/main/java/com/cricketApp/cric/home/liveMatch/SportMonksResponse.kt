// Response models
package com.cricketApp.cric.home.liveMatch

data class SportMonksResponse(
    val data: List<MatchData>
)

data class MatchData(
    val id: Int,
    val league: League? = null,
    val localteam: Team? = null,
    val visitorteam: Team? = null,
    val runs: List<Score>? = null,
    val live: Boolean = false,
    val stage: Stage? = null,
    val round: String? = null,
    val status: String? = null,
    val note: String? = null
) {
    // Provide a more accessible property for isLive
    val isLive: Boolean
        get() = live || status?.contains("Innings", ignoreCase = true) == true

    // Provide a more accessible property for matchNumber
    val matchNumber: String?
        get() = round
}

data class LeagueResponse(
    val data: League
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

data class Stage(
    val id: Int,
    val name: String? = null
)

data class Score(
    val team_id: Int,
    val score: Int = 0,
    val wickets: Int = 0,
    val overs: Double = 0.0
)