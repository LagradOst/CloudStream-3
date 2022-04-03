package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class Videobin1: Videobin() {
    override val mainUrl: String = "https://www.videobin.co"
}

open class Videobin : ExtractorApi() {
    override val name = "Videobin m3u8"
    override val mainUrl = "https://videobin.co"
    override val requiresReferer = false

    open val linkRegex =
        Regex("""(https:\/\/.*?\.m3u8)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let { link ->
                return listOf(
                    ExtractorLink(
                        name,
                        name,
                        link.value,
                        url,
                        Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            }
        }
        return null
    }
}