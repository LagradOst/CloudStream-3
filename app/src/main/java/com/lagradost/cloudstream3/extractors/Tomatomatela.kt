package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper


class Cinestart: Tomatomatela() {
    override val name: String = "Cinestart"
    override val mainUrl: String = "https://cinestart.net"
    override val details = "vr.php?v="
}

open class Tomatomatela : ExtractorApi() {
    override val name = "Tomatomatela"
    override val mainUrl = "https://tomatomatela.com"
    override val requiresReferer = false
    private data class tomato (
        @JsonProperty("status") val status: Int,
        @JsonProperty("file") val file: String
    )
    open val details = "details.php?v="
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = url.replace("$mainUrl/embed.html#","$mainUrl/$details")
        val server = app.get(link, allowRedirects = false).text
        val json = mapper.readValue<tomato>(server)
        if (json.status == 200) return listOf(
            ExtractorLink(
                name,
                name,
                json.file,
                "",
                Qualities.Unknown.value,
                isM3u8 = false
            )
        )
        return null
    }
}