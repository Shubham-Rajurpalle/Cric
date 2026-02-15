package com.cricketApp.cric.Leaderboard

data class TeamData(
    var id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    var hits: Int = 0,
    var misses: Int = 0
)