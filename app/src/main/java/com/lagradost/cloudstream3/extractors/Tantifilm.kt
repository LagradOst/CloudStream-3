package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink

open class Tantifilm : ExtractorApi() {
    override var name = "Tantifilm"
    override var mainUrl = "https://cercafilm.net"
    override val requiresReferer = false

    data class TantifilmJsonData (
        @JsonProperty("data") val data : List<TantifilmData>,
    )

    data class TantifilmData (
        @JsonProperty("file") val file : String,
        @JsonProperty("label") val label : String,
        @JsonProperty("type") val type : String
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = "$mainUrl/api/source/${url.substringAfterLast("/")}"
        val response = app.post(link).text.replace("""\""","")
        val jsonvideodata = parseJson<TantifilmJsonData>(response)
        return jsonvideodata.data.map {
            ExtractorLink(
                it.file+".${it.type}",
                this.name,
                it.file+".${it.type}",
                mainUrl,
                it.label.filter{ it.isDigit() }.toInt(),
                false
            )
        }
    }
}