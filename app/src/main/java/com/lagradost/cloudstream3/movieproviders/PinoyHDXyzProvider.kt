package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PinoyHDXyzProvider : MainAPI() {
    override val name = "Pinoy-HD"
    override val mainUrl = "https://www.pinoy-hd.xyz"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false


    override suspend fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val document = app.get(mainUrl, referer = mainUrl).document
        val mainbody = document.getElementsByTag("body")

        mainbody?.select("div.section-cotent.col-md-12.bordert")?.forEach { row ->
            val title = row?.select("div.title-section.tt")?.text() ?: "<Row>"
            val inner = row?.select("li.img_frame.preview-tumb7")
            if (inner != null) {
                val elements: List<SearchResponse> = inner.map {
                    // Get inner div from article
                    val innerBody = it?.select("a")?.firstOrNull()
                    // Fetch details
                    val name = it?.text() ?: ""
                    val link = innerBody?.attr("href") ?: ""
                    val imgsrc = innerBody?.select("img")?.attr("src")
                    val image = when (!imgsrc.isNullOrEmpty()) {
                        true -> "${mainUrl}${imgsrc}"
                        false -> null
                    }
                    //Log.i(this.name, "Result => (innerBody, image) ${innerBody} / ${image}")
                    // Get Year from Link
                    val rex = Regex("_(\\d+)_")
                    val yearRes = rex.find(link)?.value ?: ""
                    val year = yearRes.replace("_", "").toIntOrNull()
                    //Log.i(this.name, "Result => (yearRes, year) ${yearRes} / ${year}")
                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        TvType.Movie,
                        image,
                        year,
                        null,
                    )
                }.filter { a -> a.url.isNotEmpty() }
                        .filter { b -> b.name.isNotEmpty() }
                        .distinctBy { c -> c.url }
                // Add
                all.add(
                    HomePageList(
                        title, elements
                    )
                )
            }
        }
        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?q=${query.replace(" ", "+")}"
        val document = app.get(url).document.select("div.portfolio-thumb")
        return document?.mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.text() ?: ""
            val year = null
            val image = null // site provides no image on search page

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                year
            )
        }?.distinctBy { c -> c.url } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.info")

        // Video links
        val listOfLinks: MutableList<String> = mutableListOf()

        // Video details
        var title = ""
        var year: Int? = null
        var tags: List<String>? = null
        val poster = fixUrlNull(inner?.select("div.portfolio-tumb.ph-link > img")?.attr("src"))
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        inner?.select("table")?.select("tr")?.forEach {
            val td = it?.select("td") ?: return@forEach
            val caption = td[0].text()?.lowercase()
            //Log.i(this.name, "Result => (caption) $caption")
            when (caption) {
                "name" -> {
                    title = td[1].text()
                }
                "year" -> {
                    var yearRes = td[1].toString()
                    year = if (yearRes.isNotEmpty()) {
                        if (yearRes.contains("var year =")) {
                            yearRes = yearRes.substring(yearRes.indexOf("var year =") + "var year =".length)
                            //Log.i(this.name, "Result => (yearRes) $yearRes")
                            yearRes = yearRes.substring(0, yearRes.indexOf(';'))
                                .trim().removeSurrounding("'")
                        }
                        yearRes.toIntOrNull()
                    } else { null }
                }
                "genre" -> {
                    tags = td[1].select("a")?.mapNotNull { tag ->
                        tag?.text()?.trim() ?: return@mapNotNull null
                    }?.filter { a -> a.isNotEmpty() }
                }
            }
        }

        var descript = body?.select("div.eText")?.text()
        if (!descript.isNullOrEmpty()) {
            try {
                descript = "(undefined_x_Polus+[.\\d+])".toRegex().replace(descript, "")
                descript = "(_x_Polus+[.\\d+])".toRegex().replace(descript, "")
                descript = descript.trim().removeSuffix("undefined").trim()
            } catch (e: java.lang.Exception) {  }
        }
        // Add links hidden in description
        listOfLinks.addAll(fetchUrls(descript))
        listOfLinks.forEach { link ->
            //Log.i(this.name, "Result => (hidden link) $link")
            descript = descript?.replace(link, "")
        }

        // Try looking for episodes, for series
        val episodeList = ArrayList<TvSeriesEpisode>()
        val bodyText = body?.select("div.section-cotent1.col-md-12")?.select("section")
            ?.select("script")?.toString() ?: ""
        //Log.i(this.name, "Result => (bodyText) ${bodyText}")

        "(?<=ses=\\(')(.*)(?='\\).split)".toRegex().find(bodyText)?.groupValues?.get(0).let {
            if (!it.isNullOrEmpty()) {
                var count = 0
                it.split(", ").forEach { ep ->
                    count++
                    val listEpStream = listOf(ep.trim()).toJson()
                    //Log.i(this.name, "Result => (ep $count) $listEpStream")
                    episodeList.add(
                        TvSeriesEpisode(
                            name = null,
                            season = null,
                            episode = count,
                            data = listEpStream,
                            posterUrl = null,
                            date = null
                        )
                    )
                }
            }
        }
        if (episodeList.size > 0) {
            return TvSeriesLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.TvSeries,
                episodes = episodeList,
                posterUrl = poster,
                year = year,
                plot = descript,
                tags = tags
            )
        }

        // Video links for Movie
        body?.select("div.tabcontent > iframe")?.forEach {
            val linkMain = it?.attr("src")
            if (!linkMain.isNullOrEmpty()) {
                listOfLinks.add(linkMain)
                //Log.i(this.name, "Result => (linkMain) $linkMain")
            }
        }
        body?.select("div.tabcontent.hide > iframe")?.forEach {
            val linkMain = it?.attr("src")
            if (!linkMain.isNullOrEmpty()) {
                listOfLinks.add(linkMain)
                //Log.i(this.name, "Result => (linkMain hide) $linkMain")
            }
        }

        val extraLinks = body?.select("div.tabcontent.hide")?.text()
        listOfLinks.addAll(fetchUrls(extraLinks))

        val streamLinks = listOfLinks.distinct().toJson()
        //Log.i(this.name, "Result => (streamLinks) streamLinks")
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.Movie,
            dataUrl = streamLinks,
            posterUrl = poster,
            year = year,
            plot = descript,
            tags = tags
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var count = 0
        mapper.readValue<List<String>>(data).forEach { item ->
            val url = item.trim()
            if (url.isNotEmpty()) {
                if (loadExtractor(url, mainUrl, callback)) {
                    count++
                }
            }
        }
        return count > 0
    }
}