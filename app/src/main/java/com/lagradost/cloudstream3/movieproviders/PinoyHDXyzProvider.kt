package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.VoeExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

class PinoyHDXyzProvider : MainAPI() {
    override val name = "Pinoy-HD"
    override val mainUrl = "https://www.pinoy-hd.xyz"
    override val lang = "tl"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val hasQuickSearch = false


    override fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        try {
            val html = app.get(mainUrl, timeout = 15).text
            val document = Jsoup.parse(html)
            val mainbody = document.getElementsByTag("body")

            mainbody?.select("div.section-cotent.col-md-12.bordert")?.forEach { row ->
                val title = row?.select("div.title-section.tt")?.text() ?: "<Row>"
                val inner = row?.select("li.img_frame.preview-tumb7")
                if (inner != null) {
                    val elements: List<SearchResponse> = inner.map { it ->
                        // Get inner div from article
                        val innerBody = it?.select("a")?.firstOrNull()
                        // Fetch details
                        val name = it?.text() ?: "<No Title>"
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
            Log.i(this.name, "Result => (Exception) ${e}")
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
                if (!urls.isNullOrEmpty()) {
                    for (url in urls) {
                        if (!url.isNullOrEmpty()) {
                            //Log.i(this.name, "Result => (url) ${url}")
                            if (url.contains("dood.watch")) {
                                // WIP: Not working for current domain. Still, adding it.
                                val extractor = DoodLaExtractor()
                                val src = extractor.getUrl(url)
                                if (src != null) {
                                    sources.addAll(src)
                                }
                            }
                            if (url.startsWith("https://voe.sx")) {
                                val extractor = VoeExtractor()
                                val src = extractor.getUrl(url)
                                if (!src.isNullOrEmpty()) {
                                    sources.addAll(src)
                                }
                            }
                            if (url.startsWith("https://mixdrop.co/")) {
                                val extractor = MixDrop()
                                val src = extractor.getUrl(url)
                                if (src != null) {
                                    sources.addAll(src)
                                }
                            }
                            // end if
                        }
                    }
                }
            } else {
                // parse single link
                if (data.contains("fembed.com")) {
                    val extractor = FEmbed()
                    extractor.domainUrl = "diasfem.com"
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