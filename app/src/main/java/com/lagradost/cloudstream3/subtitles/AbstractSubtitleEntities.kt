package com.lagradost.cloudstream3.subtitles

public class AbstractSubtitleEntities {
    data class SubtitleEntity(
        var id: Int? = null,
        var name: String = "",
        var lang: String = "en",
        var data: String = ""
    )
    data class SubtitleOAuthEntity(
        var user: String = "",
        var pass: String = "",
        var access_token: String = "",
    )
    data class SubtitleSearch(
        var query: String = "",
        var imdb: Long? = null,
        var lang: String = "en"
    )
}