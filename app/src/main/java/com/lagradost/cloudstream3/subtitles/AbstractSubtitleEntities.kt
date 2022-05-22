package com.lagradost.cloudstream3.subtitles

class AbstractSubtitleEntities {
    data class SubtitleEntity(
        var name: String = "", //Title of movie/series. This is the one to be displayed when choosing.
        var lang: String = "en",
        var data: String = "", //Id or link, depends on provider how to process
        var type: String = "" //Movie, TV series, etc..
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