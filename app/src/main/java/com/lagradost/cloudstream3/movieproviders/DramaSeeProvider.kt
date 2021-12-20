package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

class DramaSeeProvider : MainAPI() {
    override val mainUrl = "https://dramasee.net"
    override val name = "DramaSee"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        try {
            val headers = mapOf("X-Requested-By" to "dramasee.net")
            val html = app.get(mainUrl, headers = headers).text
            val document = Jsoup.parse(html)
            val mainbody = document.getElementsByTag("body")

            mainbody?.select("section")?.forEach { row ->
                val main = row?.select("main")

                val title = main?.select("div.title > div > h2")?.text() ?: "<Row>"
                val inner = main?.select("li.series-item")
                if (inner != null) {
                    val elements: List<SearchResponse> = inner.map {
                        // Get inner div from article
                        val innerBody = it?.select("a")?.firstOrNull()
                        // Fetch details
                        val href = innerBody?.attr("href")
                        val link = if (!href.isNullOrEmpty()) { fixUrl(href) } else { "" }
                        var image = innerBody?.select("img")?.attr("src") ?: ""
                        if (image.isNotEmpty()) {
                            if (!image.startsWith("https")) {
                                image = fixUrl(image)
                            }
                        }
                        val name = it?.select("a.series-name")?.firstOrNull()?.text() ?: ""
                        //Log.i(this.name, "Result => (innerBody, image) ${innerBody} / ${image}")

                        MovieSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.TvSeries,
                            image,
                            year = null,
                            id = null,
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

        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Result => (Exception) $e")
        }
        return HomePageResponse(all)
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?q=${query.replace(" ", "+")}"
        val html = app.get(url).text
        val document = Jsoup.parse(html).select("div.portfolio-thumb")
        if (document != null) {
            return document.map {

                val link = it?.select("a")?.firstOrNull()?.attr("href") ?: ""
                val title = it?.text() ?: ""
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
            }.filter { a -> a.url.isNotEmpty() }
                .filter { b -> b.name.isNotEmpty() }
                .distinctBy { c -> c.url }
        }
        return listOf()
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val doc = Jsoup.parse(response)
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.series-info")

        // Video details
        var poster = inner?.select("div.img > img")?.attr("src") ?: ""
        if (poster.isNotEmpty()) {
            if (!poster.startsWith("http")) {
                poster = fixUrl(poster)
            }
        }
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        val title = inner?.select("h1.series-name")?.text() ?: ""
        val year = if (title.length > 5) { title.substring(title.length - 5)
            .trim().trimEnd(')').toIntOrNull() } else { null }
        //Log.i(this.name, "Result => (year) ${title.substring(title.length - 5)}")
        val descript = body?.select("div.series-body")?.firstOrNull()
            ?.select("div.js-content")?.text()

        // Episodes Links
        val episodeList = ArrayList<TvSeriesEpisode>()
        val eps = body?.select("ul.episodes > li.episode-item")
        if (!eps.isNullOrEmpty()) {
            for (ep in eps) {
                if (ep != null) {
                    val innerA = ep.select("a")
                    val count = innerA.select("span.episode")?.text()?.toIntOrNull() ?: 0
                    var epLink = innerA.attr("href") ?: ""
                    //Log.i(this.name, "Result => (epLink) ${epLink}")
                    if (epLink.isNotEmpty()) {
                        if (!epLink.startsWith("http")) {
                            epLink = fixUrl(epLink)
                        }
                        // Fetch video links
                        val epVidLinkEl = Jsoup.parse(app.get(epLink, referer = mainUrl).text)
                        val ajaxUrl = epVidLinkEl?.select("div#js-player")?.attr("embed")
                        //Log.i(this.name, "Result => (ajaxUrl) ${ajaxUrl}")
                        if (!ajaxUrl.isNullOrEmpty()) {
                            val innerPage = Jsoup.parse(app.get(fixUrl(ajaxUrl), referer = epLink).text)
                            val epTitle = " Episode ${count}"
                            val listOfLinks = mutableListOf<String>()
                            val serverAvail = innerPage?.select("div.player.active > main > div")
                            if (!serverAvail.isNullOrEmpty()) {
                                for (em in serverAvail) {
                                    val href = em.attr("src")
                                    if (!href.isNullOrEmpty()) {
                                        listOfLinks.add(href)
                                    }
                                }
                            }
                            //Log.i(this.name, "Result => (listOfLinks) ${listOfLinks}")
                            episodeList.add(
                                TvSeriesEpisode(
                                    name = name + epTitle,
                                    season = null,
                                    episode = count,
                                    data = listOfLinks.toString(),
                                    posterUrl = poster,
                                    date = null
                                )
                            )
                        }
                    }
                }
            }
        }
        val streamlinks = episodeList[0].data //TODO: Fetch streaming vid links from ep link
        if (episodeList.size == 1) {
            return MovieLoadResponse(title, url, this.name, TvType.Movie, streamlinks, poster, year, descript, null, null)
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

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data == "[]") return false
        if (data.isEmpty()) return false
        val sources = mutableListOf<ExtractorLink>()
        try {
            val urls = data.trim('[').trim(']').split(',')
            if (!urls.isNullOrEmpty()) {
                for (item in urls) {
                    if (item.isNotEmpty()) {
                        var url = item.trim()
                        if (url.startsWith("//")) {
                            url = "https:$url"
                        }
                        Log.i(this.name, "Result => (url) ${url}")
                        if (url.startsWith("https://mixdrop")) {
                            val extractor = MixDrop()
                            val src = extractor.getUrl(url)
                            if (!src.isNullOrEmpty()) {
                                sources.addAll(src)
                            }
                            break
                        }
                        if (url.startsWith("https://asianload")) {
                            val extractor = AsianLoad()
                            val src = extractor.getUrl(url)
                            if (!src.isNullOrEmpty()) {
                                sources.addAll(src)
                            }
                            break
                        }
                        if (url.startsWith("https://dood.la")) {
                            val extractor = DoodLaExtractor()
                            val src = extractor.getUrl(url)
                            if (!src.isNullOrEmpty()) {
                                sources.addAll(src)
                            }
                            break
                        }
                        if (url.startsWith("https://streamtape")) {
                            val extractor = StreamTape()
                            val src = extractor.getUrl(url)
                            if (!src.isNullOrEmpty()) {
                                sources.addAll(src)
                            }
                            break
                        }
                        if (url.startsWith("https://sbplay")) {
                            val extractor = StreamSB()
                            val src = extractor.getUrl(url)
                            if (!src.isNullOrEmpty()) {
                                sources.addAll(src)
                            }
                            break
                        }
                        if (url.startsWith("https://embedsito.com")) {
                            val extractor = XStreamCdn()
                            extractor.domainUrl = "embedsito.com"
                            val src = extractor.getUrl(url)
                            if (!src.isNullOrEmpty()) {
                                sources.addAll(src)
                            }
                            break
                        }
                        // end if
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
            Log.i(this.name, "Result => (e) $e")
        }
        return false
    }
}