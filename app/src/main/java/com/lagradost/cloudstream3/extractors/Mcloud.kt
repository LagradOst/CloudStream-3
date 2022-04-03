package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName

open class Mcloud : ExtractorApi() {
    override var name = "Mcloud"
    override var mainUrl = "https://mcloud.to"
    override val requiresReferer = true
    val headers = mapOf(
        "Host" to "mcloud.to",
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "DNT" to "1",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "iframe",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
        "Referer" to "https://animekisa.in/", //Referer works for wco and animekisa, probably with others too
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",)
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = url.replace(Regex("$mainUrl/e/|$mainUrl/embed"),"$mainUrl/info/")
        val response = app.get(link, headers = headers).text

        if(response.startsWith("<!DOCTYPE html>")) {
            // TODO decrypt html for link
            return emptyList()
        }

        data class Sources (
            @JsonProperty("file") val file: String
        )

        data class Media (
            @JsonProperty("sources") val sources: List<Sources>
        )

        data class JsonMcloud (
            @JsonProperty("success") val success: Boolean,
            @JsonProperty("media") val media: Media,
        )

        val mapped = parseJson<JsonMcloud>(response)
        val sources = mutableListOf<ExtractorLink>()

        if (mapped.success)
            mapped.media.sources.apmap {
                if (it.file.contains("m3u8")) {
                    val link1080 = it.file.replace("list.m3u8","hls/1080/1080.m3u8")
                    val link720 = it.file.replace("list.m3u8","hls/720/720.m3u8")
                    val link480 = it.file.replace("list.m3u8","hls/480/480.m3u8")
                    val link360 = it.file.replace("list.m3u8","hls/360/360.m3u8")
                    val linkauto = it.file
                    listOf(
                        link1080,
                        link720,
                        link480,
                        link360,
                        linkauto).apmap { serverurl ->
                        val testurl = app.get(serverurl, headers = mapOf("Referer" to url)).text
                        if (testurl.contains("EXTM3")) {
                            val quality = if (serverurl.contains("1080")) "1080p"
                            else if (serverurl.contains("720")) "720p"
                            else if (serverurl.contains("480")) "480p"
                            else if (serverurl.contains("360")) "360p"
                            else "Auto"
                            sources.add(
                                ExtractorLink(
                                    "MyCloud",
                                    "MyCloud $quality",
                                    serverurl,
                                    url,
                                    getQualityFromName(quality),
                                    true,
                                )
                            )
                        }
                    }
                }
            }
        return sources
    }
}