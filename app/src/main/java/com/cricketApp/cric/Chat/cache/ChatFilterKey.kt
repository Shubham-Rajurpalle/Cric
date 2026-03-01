package com.cricketApp.cric.Chat.cache

object ChatFilterKey {
    const val ALL        = "ALL"
    const val TOP_HIT   = "TOP_HIT"
    const val TOP_MISS  = "TOP_MISS"
    const val POLLS_ONLY = "POLLS_ONLY"
    fun team(t: String) = "TEAM_$t"   // e.g. "TEAM_CSK"
}