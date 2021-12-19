package com.lagradost.cloudstream3.movieproviders

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup

class FilmanProvider : MainAPI() {
    override val mainUrl = "https://filman.cc"
    override val name = "filman.cc"
    override val lang = "pl"
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wyszukiwarka?phrase=$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("#advanced-search > div").get(1).select(".item")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (i in items) {
            val href = i.attr("href")
            val img = i.selectFirst("> img").attr("src").replace("/thumb/", "/big/")
            val name = i.selectFirst(".title").text()
            returnValue.add(MovieSearchResponse(name, href, this.name, TvType.Movie, img, null))
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val title = document.select("span[itemprop=title]").text()
        val data = document.select("#links").outerHtml()
        return MovieLoadResponse(title, url, name, TvType.Movie, data)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if(data.isEmpty()) {
            return false
        }
        val document = Jsoup.parse(data)
        val urls = ArrayList<String>()
        val items = document.select(".link-to-video")
        for (i in items) {
            val decoded = base64Decode(i.select("a").attr("data-iframe"))
            val json = Gson().fromJson(decoded, LinkElement::class.java)
            val link = json.src
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        callback(it)
                    }
                    break
                }
            }
        }
        return false
    }
}

data class LinkElement(
    var src: String
)