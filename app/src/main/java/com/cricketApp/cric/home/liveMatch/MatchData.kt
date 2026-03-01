package com.cricketApp.cric.home.liveMatch

data class MatchData(
    val matchId: String = "",
    val matchName: String = "",
    val status: String = "",
    val note: String = "",
    val live: Boolean = false,
    val type: String = "",
    val round: String = "",
    val startingAt: String = "",
    val updatedAt: String = "",
    val league: LeagueData = LeagueData(),
    val stage: StageData = StageData(),
    val localteam: TeamData = TeamData(),
    val visitorteam: TeamData = TeamData(),
    val localteamScore: ScoreData = ScoreData(),
    val visitorteamScore: ScoreData = ScoreData()
)

data class TeamData(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val imagePath: String = ""
)

data class LeagueData(
    val id: String = "",
    val name: String = "",
    val imagePath: String = ""
)

data class StageData(
    val id: String = "",
    val name: String = ""
)

data class ScoreData(
    val runs: Int = 0,
    val wickets: Int = 0,
    val overs: Double = 0.0
)