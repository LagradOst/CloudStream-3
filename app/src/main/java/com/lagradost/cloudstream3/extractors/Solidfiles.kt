package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class Solidfiles1: Solidfiles() {
    override val name = "Solidfiles 2"
    override val linkRegex = Regex("(streamUrl\":\"(https|http):\\/\\/.*?\\.mp4)")
}

open class Solidfiles : ExtractorApi() {
    override val name = "Solidfiles"
    override val mainUrl = "https://www.solidfiles.com"
    override val requiresReferer = false
    open val linkRegex = Regex("""(downloadUrl":"(https|http):\/\/.*?\.mp4)""")
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let { link ->
                val extractedlink = link.value.replace("downloadUrl\":\"","").replace("streamUrl\":\"","")
                return listOf(
                    ExtractorLink(
                        name,
                        name,
                        extractedlink,
                        url,
                        Qualities.Unknown.value,
                        isM3u8 = false
                    )
                )
            }
        }
        return null
    }
}