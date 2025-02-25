package com.cricketApp.cric

class Cric_shot_video {

    var id: String? = null
    var title: String? = null
    var thumbnailUrl: String? = null
    var viewCount: Long = 0
    var uploadTimestamp: Long = 0

    constructor()
    constructor(
        id: String?,
        title: String?,
        thumbnailUrl: String?,
        viewCount: Long,
        uploadTimestamp: Long
    ) {
        this.id = id
        this.title = title
        this.thumbnailUrl = thumbnailUrl
        this.viewCount = viewCount
        this.uploadTimestamp = uploadTimestamp
    }
}