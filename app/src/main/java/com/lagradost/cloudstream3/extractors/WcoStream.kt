package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text

class WcoStream : ExtractorApi() {
    override val name: String = "WcoStream"
    override val mainUrl: String = "https://vidstream.pro"
    override val requiresReferer = false
    private val hlsHelper = M3u8Helper()

    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val baseUrl = url.split("/e/")[0]

        val html = get(url, headers = mapOf("Referer" to "https://wcostream.cc/")).text
        val (Id) = "/e/(.*?)?domain".toRegex().find(url)!!.destructured
        val (skey) = """skey\s=\s['"](.*?)['"];""".toRegex().find(html)!!.destructured

        val apiLink = "$baseUrl/info/$Id?domain=wcostream.cc&skey=$skey"
        val referrer = "$baseUrl/e/$Id?domain=wcostream.cc"

        val response = get(apiLink, headers = mapOf("Referer" to referrer)).text

        data class Sources(
            @JsonProperty("file") val file: String,
            @JsonProperty("label") val label: String?
        )

        data class Media(
            @JsonProperty("sources") val sources: List<Sources>
        )

        data class WcoResponse(
            @JsonProperty("success") val success: Boolean,
            @JsonProperty("media") val media: Media
        )

        val mapped = response.let { mapper.readValue<WcoResponse>(it) }
        val sources = mutableListOf<ExtractorLink>()

        if (mapped.success) {
            mapped.media.sources.forEach {
                if (it.file.contains("m3u8")) {
                    hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(it.file, null)).forEach { stream ->
                        sources.add(
                            ExtractorLink(
                                name,
                                name + if (stream.quality != null) " - ${stream.quality}" else "",
                                stream.streamUrl,
                                "",
                                getQualityFromName(stream.quality.toString()),
                                true
                            )
                        )
                    }
                } else {
                    sources.add(
                        ExtractorLink(
                            name,
                            name + if (it.label != null) " - ${it.label}" else "",
                            it.file,
                            "",
                            Qualities.P720.value,
                            false
                        )
                    )
                }
            }
        }
        return sources
    }
}
