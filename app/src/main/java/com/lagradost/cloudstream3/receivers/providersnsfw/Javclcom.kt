package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app

class Javclcom : MainAPI() {
    override var name = "JAVcl.com"
    override var mainUrl = "https://javcl.com"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = false
    override val hasQuickSearch: Boolean get() = false

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query/"
        val document = app.get(url).document.getElementsByTag("body")
            .select("div.col-xl-3.col-sm-4.col-6.mb-4")
        //Log.i(this.name, "Result => $document")
        return document?.mapNotNull {
            if (it == null) { return@mapNotNull null }

            val link = fixUrlNull(it.selectFirst("a.video-link")?.attr("href")) ?: return@mapNotNull null
            val content = it.selectFirst("img.video-thumb") ?: return@mapNotNull null
            //Log.i(this.name, "Result => $content")

            val title = content.attr("alt") ?: "<No Title Found>"
            val image = content.attr("src")
            val year = null
            //Log.i(this.name, "Result => Title: ${title}, Image: ${image}")

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.JAV,
                image,
                year
            )
        } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.i(this.name, "Url => ${url}")

        // Video details
        //Log.i(this.name, "Result => ${body}")
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: "<No Title>"
        val descript = doc.selectFirst("meta[property=og:description]")?.attr("content")
        //Log.i(this.name, "Result => ${descript}")
        val re = Regex("\\d{4}")
        val year = when (!poster.isNullOrEmpty()) {
            true -> re.find(poster)?.groupValues?.firstOrNull()?.toIntOrNull()
            false -> null
        }

        // Video links
        // WIP: POST https://javcl.me/api/source/${id}
        val streamUrl = ""
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.JAV,
            dataUrl = streamUrl,
            posterUrl = poster,
            year = year,
            plot = descript,
            comingSoon = true
        )
    }
}