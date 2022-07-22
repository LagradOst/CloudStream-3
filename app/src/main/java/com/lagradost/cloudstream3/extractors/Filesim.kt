package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable

class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            this.select("script").map { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val data = getAndUnpack(script.data()).substringAfter("sources:[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        M3u8Helper.generateM3u8(
                            name,
                            it.file,
                            "$mainUrl/",
                        ).forEach { m3uData -> sources.add(m3uData) }
                    }
                }
            }
        }
        return sources
    }

    @Serializable private data class ResponseSource(
        @SerialName("file") val file: String,
        @SerialName("type") val type: String?,
        @SerialName("label") val label: String?
    )

}