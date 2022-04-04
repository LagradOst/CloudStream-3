package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app



open class YourUpload : ExtractorApi() {
    override val name = "YourUpload"
    override val mainUrl = "https://www.yourupload.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
       val doc = app.get(url).document
       val link = doc.selectFirst("meta[property=og:video]").attr("content") ?: return null
       val extractedlink = app.get(link, referer = url).url
        return listOf(
            ExtractorLink(
                name,
                name,
                extractedlink,
                url,
                Qualities.Unknown.value,
                extractedlink.contains(".m3u8")
            )
        )
    }
}