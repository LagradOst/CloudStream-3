package com.lagradost.cloudstream3.providersnsfw

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.HttpSession
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class OpJavCom : MainAPI() {
    override var name = "OpJAV"
    override var mainUrl = "https://opjav.com"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document
        val all = ArrayList<HomePageList>()
        val body = document.getElementsByTag("body")
        val rows = mutableListOf<Pair<String, Element>>()
        val selectorSimple = "div.list-film-simple > div.item"
        val selectorRows = "div.list-film.row > div"

        body?.select("div.content")?.forEach {
            if (it != null) {
                if (it.select(selectorRows).isNullOrEmpty()) {
                    rows.add(Pair(selectorSimple, it))
                } else {
                    rows.add(Pair(selectorRows, it))
                }
            }
        }

        var count = 0
        rows.forEach { row ->
            count++
            val title = "Row $count"
            val isSimple = row.first == selectorSimple
            val entries = row.second.select(row.first)
            val elements = entries?.mapNotNull {
                if (it == null) { return@mapNotNull null }
                var link = ""
                var name = ""
                var image : String? = null
                var year : Int? = null

                if (isSimple) {
                    //Simple load
                    val inner = it.select("div.info") ?: return@mapNotNull null
                    link = fixUrlNull(inner.select("a")?.get(0)?.attr("href")) ?: return@mapNotNull null
                    name = inner.text().trim()
                    val imgsrc = it.select("img")
                    image = imgsrc?.attr("src") ?: imgsrc?.attr("data-src")
                } else {
                    val inner = it.select("div.inner") ?: return@mapNotNull null
                    val poster = inner.select("a.poster") ?: return@mapNotNull null
                    link = fixUrlNull(poster.attr("href")) ?: return@mapNotNull null
                    name = it.text().trim().removePrefix("HD")
                    image = poster.select("img")?.attr("src")
                    year = inner.select("dfn")?.get(1)?.text()?.toIntOrNull()
                }
                MovieSearchResponse(
                    name = name,
                    url = link,
                    apiName = this.name,
                    type = TvType.JAV,
                    posterUrl = image,
                    year = year
                )
            }?.distinctBy { a -> a.url } ?: listOf()
            if (elements.isNotEmpty()) {
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
        val url = "$mainUrl/search/${query}/"
        val document = app.get(url).document
            .select("div.block-body > div.list-film.row > div")
            //.select("div.item.col-lg-3.col-md-3.col-sm-6.col-xs-6")
        //Log.i(this.name, "Result => (document) ${document}")
        return document?.mapNotNull {
            val inner = it.select("div.inner") ?: return@mapNotNull null
            val innerPost = inner.select("a.poster") ?: return@mapNotNull null

            val link = fixUrlNull(innerPost.attr("href")) ?: return@mapNotNull null
            val title = innerPost.attr("title")?.trim() ?: "<No Title found>"
            val imgsrc = innerPost.select("img")
            val image = fixUrlNull(imgsrc?.attr("src") ?: imgsrc?.attr("data-src"))
            val year = inner.select("dfn").last()?.text()?.trim()?.toIntOrNull()

            //Log.i(this.name, "Result => $")
            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.JAV,
                posterUrl = image,
                year = year
            )
        }?.distinctBy { it.url } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.i(this.name, "Result => (url) ${url}")
        val poster = fixUrlNull(doc.select("meta[itemprop=image]")?.get(1)?.attr("content")?.trim())
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content").toString()
        val descript = doc.selectFirst("meta[name=keywords]")?.attr("content")?.trim()
        val year = doc.selectFirst("meta[itemprop=dateCreated]")?.attr("content")?.toIntOrNull()

        val tags = doc.select("dl > dd")?.get(1)?.select("a")?.mapNotNull {
            //Log.i(this.name, "Result => (tag) $it")
            it?.text()?.trim() ?: return@mapNotNull null
        }

        //Fetch server links
        val watchlink = ArrayList<String>()
        val mainLink = doc.select("div.buttons.row a")?.attr("href") ?: ""
        //Log.i(this.name, "Result => (mainLink) $mainLink")

        //Fetch episode links from mainlink
        if (mainLink.isNotBlank()) {
            val epsDoc = app.get(url = mainLink, referer = mainUrl).document
            //Fetch filmId
            /*var filmId = ""
        val epLinkDoc = epsDoc.getElementsByTag("head").select("script").toString()
        //Log.i(this.name, "Result => (epLinkDoc) $epLinkDoc")
        try {
            val epTextTemp = epLinkDoc.substring(epLinkDoc.indexOf("filmID = parseInt"))
            val epText = epTextTemp.substring(1, epTextTemp.indexOf("</script>")).trim()
                .filterNot { a -> a.isWhitespace() }
            if (epText.isNotEmpty()) {
                filmId = try {
                    "(?<=filmID=parseInt\\(')(.*)(?='\\);)".toRegex().find(epText)?.groupValues?.get(0) ?: ""
                } catch (e: Exception) { "" }
                Log.i(this.name, "Result => (filmId) $filmId")
            }
        } catch (e: Exception) { }*/
            //Fetch server links
            epsDoc.select("div.block.servers li")?.mapNotNull {
                val inner = it?.selectFirst("a") ?: return@mapNotNull null
                val linkUrl = inner.attr("href") ?: return@mapNotNull null
                val linkId = inner.attr("id") ?: return@mapNotNull null
                Pair(linkUrl, linkId)
            }?.apmap {
                //First = Url, Second = EpisodeID
                //Log.i(this.name, "Result => (eplink-Id) $it")
                val ajaxHead = mapOf(
                    Pair("Origin", mainUrl),
                    Pair("Referer", it.first)
                )
                //https://opjav.com/movie/War%20of%20the%20Roses-64395/watch-movie.html
                //EpisodeID, 442671
                //filmID, 64395
                val ajaxData = mapOf(
                    Pair("NextEpisode", "1"),
                    Pair("EpisodeID", it.second)
                    //Pair("filmID", filmId)
                )
                val sess = HttpSession()
                val respAjax = sess.post("$mainUrl/ajax", headers = ajaxHead, data = ajaxData).text
                //Log.i(this.name, "Result => (respAjax text) $respAjax")
                Jsoup.parse(respAjax).select("iframe")?.forEach { iframe ->
                    val serverLink = iframe?.attr("src")?.trim()
                    if (!serverLink.isNullOrEmpty()) {
                        watchlink.add(serverLink)
                        Log.i(this.name, "Result => (serverLink) $serverLink")
                    }
                }
            }
        }
        val streamUrl = watchlink.distinct().toJson()
        Log.i(this.name, "Result => (streamUrl) $streamUrl")
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.JAV,
            dataUrl = streamUrl,
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
        if (data == "about:blank") return false
        if (data.isEmpty()) return false

        var count = 0
        mapper.readValue<List<String>>(data).forEach { link ->
            val url = fixUrl(link.trim())
            Log.i(this.name, "Result => (url) $url")
            when {
                url.startsWith("https://opmovie.xyz") -> {
                    val ext = XStreamCdn()
                    ext.domainUrl = "opmovie.xyz"
                    ext.getSafeUrl(url, url)?.forEach {
                        Log.i(this.name, "Result => (xtream) ${it.url}")
                        callback.invoke(it)
                        count++
                    }
                }
                else -> {
                    if (loadExtractor(url, mainUrl, callback)) {
                        count++
                    }
                }
            }
        }
        return count > 0
    }
}