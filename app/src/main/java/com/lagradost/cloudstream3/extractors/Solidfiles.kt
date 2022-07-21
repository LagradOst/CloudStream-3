package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Solidfiles : ExtractorApi() {
    override val name = "Solidfiles"
    override val mainUrl = "https://www.solidfiles.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            this.select("script").map { script ->
                if (script.data().contains("\"streamUrl\":")) {
                    val data = script.data().substringAfter("constant('viewerOptions', {")
                        .substringBefore("});")
                    val source = tryParseJson<ResponseSource>("{$data}")
                    val quality = Regex("\\d{3,4}p").find(source!!.nodeName)?.groupValues?.get(0)
                    sources.add(
                        ExtractorLink(
                            name,
                            name,
                            source.streamUrl,
                            referer = url,
                            quality = getQualityFromName(quality)
                        )
                    )
                }
            }
        }
        return sources
    }


    @Serializable
    private data class ResponseSource(
        @SerialName("streamUrl") val streamUrl: String,
        @SerialName("nodeName") val nodeName: String
    )
}