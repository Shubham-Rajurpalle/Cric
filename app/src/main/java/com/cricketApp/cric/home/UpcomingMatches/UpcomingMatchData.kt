package com.cricketApp.cric.home.UpcomingMatches

data class UpcomingMatchData(
    val id: String = "",
    val matchName: String = "",
    val status: String = "",
    val type: String = "",
    val round: String = "",
    val startingAt: String = "",
    val league: UpcomingLeagueData = UpcomingLeagueData(),
    val stage: UpcomingStageData = UpcomingStageData(),
    val localteam: UpcomingTeamData = UpcomingTeamData(),
    val visitorteam: UpcomingTeamData = UpcomingTeamData()
)

data class UpcomingTeamData(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val imagePath: String = ""
)

data class UpcomingLeagueData(
    val id: String = "",
    val name: String = "",
    val imagePath: String = ""
)

data class UpcomingStageData(
    val id: String = "",
    val name: String = ""
)