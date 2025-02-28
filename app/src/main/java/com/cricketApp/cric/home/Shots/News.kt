package com.cricketApp.cric.home.Shots

data class News(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    var views: Int = 0,
    val timestamp: Long = 0,
    val newsContent: String = ""
)
