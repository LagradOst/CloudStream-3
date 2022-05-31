package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class Userload : ExtractorApi() {
    override var name = "Userload"
    override var mainUrl = "https://userload.co/"
    override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val response = app.get(url).text
        val jstounpack = Regex("ext/javascript\">eval((.|\\n)*?)</script>").find(response)?.groups?.get(1)?.value
        val unpacjed = JsUnpacker(jstounpack).unpack()
        val morocco = unpacjed?.let { Regex("""cbbdeffb="((.|\n)*?)"""").find(it) }?.groups?.get(1)?.value.toString()
        val mycountry = unpacjed?.let { Regex("""aaedaebdcbfa="((.|\n)*?)"""").find(it) }?.groups?.get(1)?.value.toString()
        val videoLinkPage = app.post("https://userload.co/api/request/", data = mapOf(
            "morocco" to morocco,
            "mycountry" to mycountry
        ))
        val videoLink = videoLinkPage.text
        val namesource = app.get(url).document.head().selectFirst("title")!!.text()
        extractedLinksList.add(
            ExtractorLink(
                name,
                name,
                videoLink,
                mainUrl,
                getQualityFromName(namesource),
            )
        )

        return extractedLinksList
    }
}