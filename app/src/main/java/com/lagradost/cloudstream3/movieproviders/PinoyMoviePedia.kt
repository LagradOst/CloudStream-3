package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mapper
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

    private data class JsonVoeLinks(
        @JsonProperty("hls") val url: String?,
        @JsonProperty("video_height") val label: Int?
    )

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
                Pair("Family", "genre_family")
                //Pair("Adult +18", "genre_pinay-sexy-movies")
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
                            if (url.contains("voe.sx/")) {
                                val doc = Jsoup.parse(app.get(url).text)?.toString() ?: ""
                                if (doc.isNotEmpty()) {
                                    val start = "const sources ="
                                    var src = doc.substring(doc.indexOf(start))
                                    src = src.substring(start.length, src.indexOf(";"))
                                        .replace("0,", "0")
                                        .trim()
                                    //Log.i(this.name, "Result => (src) ${src}")
                                    mapper.readValue<JsonVoeLinks?>(src)?.let { voelink ->
                                        //Log.i(this.name, "Result => (voelink) ${voelink}")
                                        val linkUrl = voelink.url
                                        val linkLabel = voelink.label?.toString() ?: ""
                                        if (!linkUrl.isNullOrEmpty()) {
                                            sources.add(
                                                ExtractorLink(
                                                    name = "Voe m3u8 ${linkLabel}",
                                                    source = "Voe",
                                                    url = linkUrl,
                                                    quality = getQualityFromName(linkLabel),
                                                    referer = url,
                                                    isM3u8 = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            if (url.startsWith("https://upstream.to")) {
                                // WIP: m3u8 link fetched but not playing
                                //Log.i(this.name, "Result => (no extractor) ${url}")
                                val doc = Jsoup.parse(app.get(url, referer = "https://upstream.to").text)?.toString() ?: ""
                                if (doc.isNotEmpty()) {
                                    var reg = Regex("(?<=master)(.*)(?=hls)")
                                    val result = reg.find(doc)?.groupValues?.map {
                                        it.trim('|')
                                    }?.toList()
                                    reg = Regex("(?<=\\|file\\|)(.*)(?=\\|remove\\|)")
                                    val domainList = reg.find(doc)?.groupValues?.get(1)?.split("|")
                                    var domain = when (!domainList.isNullOrEmpty()) {
                                        true -> {
                                            if (domainList.isNotEmpty()) {
                                                var domName = ""
                                                for (part in domainList) {
                                                    domName = "${part}.${domName}"
                                                }
                                                domName.trimEnd('.')
                                            } else { "" }
                                        }
                                        false -> ""
                                    }
                                    //Log.i(this.name, "Result => (domain) ${domain}")
                                    if (domain.isEmpty()) {
                                        domain = "s96.upstreamcdn.co"
                                        //Log.i(this.name, "Result => (default domain) ${domain}")
                                    }
                                    result?.forEach {
                                        val linkUrl = "https://${domain}/hls/${it}/master.m3u8"
                                        sources.add(
                                            ExtractorLink(
                                                name = "Upstream m3u8",
                                                source = "Voe",
                                                url = linkUrl,
                                                quality = Qualities.Unknown.value,
                                                referer = "https://upstream.to",
                                                isM3u8 = true
                                            )
                                        )
                                    }
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