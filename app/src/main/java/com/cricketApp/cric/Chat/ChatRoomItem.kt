package com.cricketApp.cric.Chat

enum class RoomType {
    GLOBAL,
    TEAM,
    LIVE
}

data class ChatRoomItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: RoomType = RoomType.GLOBAL,
    val teamTag: String = "",
    val bannerImageUrl: String = "",
    val isLive: Boolean = false,
    val activeUsers: Int = 0,
    val createdAt: Long = 0L
)