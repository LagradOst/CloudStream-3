package com.lagradost.cloudstream3.subtitles

public class AbstractSubtitleEntities {
    data class SubtitleEntity(
        val id: Int? = null,
        val name: String = "",
        val lang: String = "en",
        val data: String = ""
    )
    data class SubtitleOAuthEntity(
        val user: String = "",
        val pass: String = "",
        val access_token: String = ""
    )
}