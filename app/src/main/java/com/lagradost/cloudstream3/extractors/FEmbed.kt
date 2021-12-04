package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class FEmbed: ExtractorApi() {
    override val name: String = "FEmbed"
    override val mainUrl: String = "https://www.fembed.com"
    override val requiresReferer = false

    class Response(json: String) : JSONObject(json) {
        val data = this.optJSONArray("data")
            ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } } // returns an array of JSONObject
            ?.map { FEmbed.Links(it.toString()) } // transforms each JSONObject of the array into 'Links'
    }
    class Links(json: String) : JSONObject(json) {
        val file: String? = this.optString("file")
        val label: String? = this.optString("label")
        val type: String? = this.optString("type")
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val id = url.split("/").last()
        val req = "https://femax20.com/api/source/${id}"
        val session = HttpSession()
        val data = session.post(req)
        if (data.statusCode == 200) {
            //Log.i(this.name, "Result => (data) ${data.text}")
            val response = FEmbed.Response(data.text)
            if (response.data != null) {
                //Log.i(this.name, "Result => (response.data) ${response.data}")
                for (link in response.data) {
                    val linkUrl = link.file?.replace("\\\\", "") ?: ""
                    val linkQual = getQualityFromName(link.label ?: "")
                    extractedLinksList.add(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = linkUrl,
                            referer = this.mainUrl,
                            quality = linkQual,
                            isM3u8 = false
                        )
                    )
                }
            }
        }
        return extractedLinksList
    }
}