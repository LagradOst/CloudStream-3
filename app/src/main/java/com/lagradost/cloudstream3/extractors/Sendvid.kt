package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import java.net.URLDecoder

class Sendvid1: Sendvid() {
    override val mainUrl: String = "https://www.sendvid.com"
}

open class Sendvid : ExtractorApi() {
    override val name = "Sendvid"
    override val mainUrl = "https://sendvid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
        val urlString = doc.select("head meta[property=og:video:secure_url]").attr("content")
        val sources = mutableListOf<ExtractorLink>()
        if (urlString.contains("m3u8"))  M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                urlString,
                headers = app.get(url).headers.toMap()
            ), true
        )
            .apmap { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                sources.add(  ExtractorLink(
                    name,
                    "$name $qualityString",
                    stream.streamUrl,
                    url,
                    getQualityFromName(stream.quality.toString()),
                    true
                ))
            }
        return sources
    }
}