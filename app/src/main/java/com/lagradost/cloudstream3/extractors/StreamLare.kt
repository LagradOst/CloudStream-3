package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class StreamLare: ExtractorApi() {
    override val name: String = "StreamLare"
    override val mainUrl: String = "https://streamlare.com"
    override val requiresReferer = false

    private data class ResponseData(
        @JsonProperty("src") val src: String?,
        @JsonProperty("label") val label: String?,
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val id = url.trim().removeSuffix("/").split("/").last()
        val req = "https://streamlare.com/api/video/get"

        val session = HttpSession()
        val headers: Map<String, String> = mapOf(Pair("Accept", "application/json"))
        val body: Map<String, String> = mapOf(Pair("id", id))
        val data = session.post(
            req,
            headers = headers,
            params = body
        )
        //Log.i(this.name, "Result => (url, id, req) ${url} -> ${id} -> ${req}")
        if (data.statusCode == 200) {
            //Log.i(this.name, "Result => (data) ${data.text}")
            val jsonObject = JSONObject(data.text.trim())
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (jsonObject[key] is JSONObject) {
                    var itemstr = jsonObject[key].toString()
                    itemstr = itemstr.substring(itemstr.indexOf(":") + 1)
                    //Log.i(this.name, "Result => (jsonObject) ${itemstr}")

                    mapper.readValue<ResponseData>(itemstr).let { item ->
                        val linkUrl = item.src ?: ""
                        val linkQual = getQualityFromName(item.label ?: "")
                        //Log.i(this.name, "Result => (item.src) ${item.src}")
                        //Log.i(this.name, "Result => (item.label) ${item.label}")

                        if (linkUrl.isNotBlank()) {
                            extractedLinksList.add(
                                ExtractorLink(
                                    source = name,
                                    name = "$name ${item.label}",
                                    url = linkUrl,
                                    referer = url,
                                    quality = linkQual,
                                    isM3u8 = false
                                )
                            )
                        }
                    }
                }
            }
        }
        return extractedLinksList
    }
}