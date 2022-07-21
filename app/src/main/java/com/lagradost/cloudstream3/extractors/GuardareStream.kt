package com.lagradost.cloudstream3.extractors
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable

open class GuardareStream : ExtractorApi() {
    override var name = "Guardare"
    override var mainUrl = "https://guardare.stream"
    override val requiresReferer = false

    data class GuardareJsonData (
        @SerialName("data") val data : List<GuardareData>,
    )

    data class GuardareData (
        @SerialName("file") val file : String,
        @SerialName("label") val label : String,
        @SerialName("type") val type : String
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.post(url.replace("/v/","/api/source/"), data = mapOf("d" to mainUrl)).text
        val jsonvideodata = AppUtils.parseJson<GuardareJsonData>(response)
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