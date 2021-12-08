package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.Session
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mapper

class FEmbed: ExtractorApi() {
    override val name: String = "FEmbed"
    override val mainUrl: String = "https://www.fembed.com"
    override val requiresReferer = false
    var domainUrl: String = "femax20.com" // Alt domain: gcloud.live

    private data class JsonResponseData(
        @JsonProperty("data") val data: List<JsonLinks>?,
    )

    private data class JsonLinks(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?
    )

    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        try {
            val id = url.trimEnd('/').split("/").last()
            val reqLink = "https://${domainUrl}/api/source/${id}"
            val session = Session()
            val headers: Map<String, String> = mapOf(Pair("Accept", "application/json"))
            val data = session.post(reqLink, headers = headers, referer = url)
            //Log.i(this.name, "Result => status: ${data.code} / req: ${reqLink}")
            if (data.code == 200) {
                mapper.readValue<JsonResponseData?>(data.text)?.let {
                    it.data?.forEach { link ->
                        //Log.i(this.name, "Result => link: ${link}")
                        val linkUrl = link.file
                        val linkLabel = link.label ?: ""
                        if (!linkUrl.isNullOrEmpty()) {
                            extractedLinksList.add(
                                ExtractorLink(
                                    source = name,
                                    name = "${name} ${linkLabel}",
                                    url = linkUrl,
                                    referer = this.domainUrl,
                                    quality = getQualityFromName(linkLabel),
                                    isM3u8 = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Result => (Exception) ${e}")
        }
        return extractedLinksList
    }
}