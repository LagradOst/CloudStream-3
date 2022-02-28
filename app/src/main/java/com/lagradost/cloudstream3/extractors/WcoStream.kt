package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.*

class Vidstreamz : WcoStream() {
    override val mainUrl: String = "https://vidstreamz.online"
}
class Vizcloud : WcoStream() {
    override val mainUrl: String = "https://vizcloud2.ru"
}


open class WcoStream : ExtractorApi() {
    override val name = "VidStream" //Cause works for animekisa and wco
    override val mainUrl = "https://vidstream.pro"
    override val requiresReferer = false
    private val hlsHelper = M3u8Helper()

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val baseUrl = url.split("/e/")[0]

        val html = app.get(url, headers = mapOf("Referer" to "https://wcostream.cc/")).text
        val (Id) = (Regex("/e/(.*?)?domain").find(url)?.destructured ?: Regex("""/e/(.*)""").find(url)?.destructured) ?: return emptyList()
        val (skey) = Regex("""skey\s=\s['"](.*?)['"];""").find(html)?.destructured ?: return emptyList()

        val apiLink = "$baseUrl/info/$Id?domain=wcostream.cc&skey=$skey"
        val referrer = "$baseUrl/e/$Id?domain=wcostream.cc"

        val response = app.get(apiLink, headers = mapOf("Referer" to referrer)).text

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
                if (mainUrl == "https://vizcloud2.ru") {
                    if (it.file.contains("vizcloud2.ru"))
                        hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(it.file.replace("#.mp4",""), null), true)
                            .forEach { stream ->
                                val qualityString =
                                    if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                                sources.add(
                                    ExtractorLink(
                                        name,
                                        "$name $qualityString",
                                        stream.streamUrl,
                                        "",
                                        getQualityFromName(stream.quality.toString()),
                                        true
                                    )
                                )
                            }
                }
                if (mainUrl == "https://vidstream.pro" || mainUrl == "https://vidstreamz.online") {
                if (it.file.contains("m3u8")) {
                    hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(it.file, null), true)
                        .forEach { stream ->
                            val qualityString =
                                if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                            sources.add(
                                ExtractorLink(
                                    name,
                                    "$name $qualityString",
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
        }
        return sources
    }
}
