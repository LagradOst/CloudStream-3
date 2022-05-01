package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class MaxstreamExtractor : ExtractorApi() {
    override var name = "Maxstream"
    override var mainUrl = "https://maxstream.video/"
    override val requiresReferer = false
    private val m3u8Regex = Regex(""".*?(\d*).m3u8""")
    private val m3u8qual = Regex("RESOLUTION=((.|\\n)*?),")
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val response = app.get(url).text
        val jstounpack = Regex("cript\">eval((.|\\n)*?)</script>").find(response)?.groups?.get(1)?.value
        val unpacjed = JsUnpacker(jstounpack).unpack()
        val extractedUrl = unpacjed?.let { Regex("""src:"((.|\n)*?)",type""").find(it) }?.groups?.get(1)?.value.toString()

        with(app.get(extractedUrl)) {
            val links = m3u8Regex.findAll(this.text.substringBefore("#EXT-X-I-FRAME-STREAM")).map { it.value }.toList()
            val qualities = m3u8qual.findAll(this.text.substringBefore("#EXT-X-I-FRAME-STREAM")).map { it.value.substringBefore("x").filter { it.isDigit() }.toInt() }.toList()
            links.zip(qualities).map{ (link,quality) ->
                extractedLinksList.add(
                    ExtractorLink(
                        name,
                        name = name,
                        link,
                        url,
                        quality,
                        isM3u8 = true
                    )
                )
            }
        }
    return extractedLinksList
}
}