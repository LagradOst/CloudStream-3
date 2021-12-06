package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup

class PinoyMoviePedia : MainAPI() {
    override val name: String
        get() = "Pinoy Moviepedia"

    override val mainUrl: String
        get() = "https://pinoymoviepedia.ru"

    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Movie, TvType.TvSeries)

    override val hasDownloadSupport: Boolean
        get() = false

    override val hasMainPage: Boolean
        get() = true

    override val hasQuickSearch: Boolean
        get() = false

    class Response(json: String) : JSONObject(json) {
        val id: String? = this.optString("id")
        val poster: String? = this.optString("poster")
        val list = this.optJSONArray("list")
            ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } } // returns an array of JSONObject
            ?.map { Links(it.toString()) } // transforms each JSONObject of the array into 'Links'
    }
    class Links(json: String) : JSONObject(json) {
        val url: String? = this.optString("url")
        val server: String? = this.optString("server")
        //val active: Int? = this.optInt("active")
    }

    override fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        try {
            val html = app.get(mainUrl, timeout = 15).text
            val document = Jsoup.parse(html)
            val mainbody = document.getElementsByTag("body")
            // All rows will be hardcoded bc of the nature of the site
            val rows: List<Pair<String, String>> = listOf(
                Pair("Latest Movies", "featured-titles"),
                Pair("Movies", "dt-movies"),
                Pair("Digitally Restored", "genre_digitally-restored"),
                Pair("Action", "genre_action"),
                Pair("Romance", "genre_romance"),
                Pair("Comedy", "genre_comedy"),
                Pair("Family", "genre_family"),
                Pair("Adult +18", "genre_pinay-sexy-movies")
            )
            for (item in rows) {
                val title = item.first
                val inner = mainbody?.select("div#${item.second} > article")
                if (inner != null) {
                    val elements: List<SearchResponse> = inner.map {
                        // Get inner div from article
                        val urlTitle = it?.select("div.data")
                        // Fetch details
                        val link = urlTitle?.select("a")?.attr("href") ?: ""
                        val name = urlTitle?.text() ?: "<No Title>"
                        val image = it?.select("div.poster > img")?.attr("src")
                        // Get Year from Title
                        val rex = Regex("\\((\\d+)")
                        val yearRes = rex.find(name)?.value ?: ""
                        val year = yearRes.replace("(", "").toIntOrNull()

                        val tvType = TvType.Movie
                        MovieSearchResponse(
                            name,
                            link,
                            this.name,
                            tvType,
                            image,
                            year,
                            null,
                        )
                    }
                    // Add
                    all.add(
                        HomePageList(
                            title, elements
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Exception => ${e}")
        }
        return HomePageResponse(all)
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val html = app.get(url).text
        val document = Jsoup.parse(html).select("div.search-page")?.firstOrNull()
            ?.select("div.result-item")
        if (document != null) {
            return document.map {
                val inner = it.select("article")
                val details = inner.select("div.details")
                val href = details?.select("div.title > a")?.attr("href") ?: ""

                val title = details?.select("div.title")?.text() ?: "<Untitled>"
                val link: String = when (href != "") {
                    true -> fixUrl(href)
                    false -> ""
                }
                val year = details?.select("div.meta > span.year")?.text()?.toIntOrNull()
                val image = inner.select("div.image > div > a > img")?.attr("src")

                MovieSearchResponse(
                    title,
                    link,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            }
        }
        return listOf<SearchResponse>()
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val doc = Jsoup.parse(response)
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.sheader")
        // Identify if movie or series
        val isTvSeries = doc?.select("title")?.text()?.lowercase()?.contains("full episode -") ?: false

        // Video details
        val poster = doc.select("meta[property=og:image]").firstOrNull()?.attr("content")
        val title = inner?.select("div.data > h1")?.firstOrNull()?.text() ?: "<Untitled>"
        val descript = body?.select("div#info")?.text()
        val rex = Regex("\\((\\d+)")
        val yearRes = rex.find(title)?.value ?: ""
        //Log.i(this.name, "Result => (yearRes) ${yearRes}")
        val year = yearRes.replace("(", "").toIntOrNull()

        // Video links
        val linksContainer = body?.select("div#playcontainer")
        val streamlinks = linksContainer?.toString() ?: ""
        //Log.i(this.name, "Result => (streamlinks) ${streamlinks}")

        // Parse episodes if series
        if (isTvSeries) {
            val episodeList = ArrayList<TvSeriesEpisode>()
            val epList = body?.select("div#playeroptions > ul > li")
            //Log.i(this.name, "Result => (epList) ${epList}")
            val epLinks = linksContainer?.select("div > div > div.source-box")
            //Log.i(this.name, "Result => (epLinks) ${epLinks}")
            if (epList != null) {
                for (ep in epList) {
                    val epTitle = ep.select("span.title")?.text() ?: ""
                    if (epTitle.isNotEmpty()) {
                        val epNum = epTitle.lowercase().replace("episode", "").trim().toIntOrNull()
                        //Log.i(this.name, "Result => (epNum) ${epNum}")
                        val href = when (epNum != null && epLinks != null) {
                            true -> epLinks.select("div#source-player-${epNum}")
                                ?.select("iframe")?.attr("src") ?: ""
                            false -> ""
                        }
                        //Log.i(this.name, "Result => (epLinks href) ${href}")
                        episodeList.add(
                            TvSeriesEpisode(
                                name,
                                null,
                                epNum,
                                href,
                                poster,
                                null
                            )
                        )
                    }
                }
                return TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    TvType.TvSeries,
                    episodeList,
                    poster,
                    year,
                    descript,
                    null,
                    null,
                    null
                )
            }
        }
        return MovieLoadResponse(title, url, this.name, TvType.Movie, streamlinks, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data == "") return false
        val sources = mutableListOf<ExtractorLink>()
        try {
            if (data.contains("playcontainer")) {
                // parse movie servers
                //Log.i(this.name, "Result => (data) ${data}")
                val urls = Jsoup.parse(data).select("div")?.map { item ->
                    item.select("iframe")?.attr("src")
                }
                if (urls != null) {
                    for (url in urls) {
                        if (url != null) {
                            if (url.isNotEmpty()) {
                                //Log.i(this.name, "Result => (url) ${url}")
                                if (url.contains("dood.watch")) {
                                    val extractor = DoodLaExtractor()
                                    val src = extractor.getUrl(url)
                                    if (src != null) {
                                        //Log.i(this.name, "Result => (url dood) ${src}")
                                        sources.addAll(src)
                                    }
                                }
                                if (url.contains("voe.sx/")) {
                                    val doc = Jsoup.parse(app.get(url).text)?.toString() ?: ""
                                    if (doc.isNotEmpty()) {
                                        var src = doc.substring(doc.indexOf("const sources = {"))
                                        src = src.substring(0, src.indexOf(";"))
                                        src = src.substring(src.indexOf("https"))
                                        src = src.substring(0, src.indexOf("\""))
                                        sources.add(
                                            ExtractorLink(
                                                name = "Voe m3u8",
                                                source = "Voe",
                                                url = src,
                                                quality = Qualities.Unknown.value,
                                                referer = "",
                                                isM3u8 = true
                                            )
                                        )
                                    }
                                }
                                if (url.startsWith("https://upstream.to")) {
                                    // WIP
                                    Log.i(this.name, "Result => (no extractor) ${url}")
                                }
                                if (url.startsWith("https://mixdrop.co/")) {
                                    val extractor = MixDrop()
                                    val src = extractor.getUrl(url)
                                    if (src != null) {
                                        //Log.i(this.name, "Result => (url MixDrop) ${src}")
                                        sources.addAll(src)
                                    }
                                }
                                // end if
                            }
                        }
                    }
                }
            } else {
                // parse single link
                if (data.contains("fembed.com")) {
                    val extractor = FEmbed()
                    val src = extractor.getUrl(data)
                    if (src.isNotEmpty()) {
                        sources.addAll(src)
                    }
                }
            }
            // Invoke sources
            if (sources.isNotEmpty()) {
                for (source in sources) {
                    callback.invoke(source)
                    //Log.i(this.name, "Result => (source) ${source.url}")
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Result => (e) ${e}")
        }
        return false
    }
}