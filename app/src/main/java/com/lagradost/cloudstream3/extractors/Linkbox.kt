package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable

class Linkbox : ExtractorApi() {
    override val name = "Linkbox"
    override val mainUrl = "https://www.linkbox.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val id = url.substringAfter("id=")
        val sources = mutableListOf<ExtractorLink>()

        app.get("$mainUrl/api/open/get_url?itemId=$id", referer = url)
            .parsedSafe<Responses>()?.data?.rList?.map { link ->
            sources.add(
                ExtractorLink(
                    name,
                    name,
                    link.url,
                    url,
                    getQualityFromName(link.resolution)
                )
            )
        }

        return sources
    }

    @Serializable
    data class RList(
        @SerialName("url") val url: String,
        @SerialName("resolution") val resolution: String?,
    )

    @Serializable
    data class Data(
        @SerialName("rList") val rList: List<RList>?,
    )

    @Serializable
    data class Responses(
        @SerialName("data") val data: Data?,
    )

}