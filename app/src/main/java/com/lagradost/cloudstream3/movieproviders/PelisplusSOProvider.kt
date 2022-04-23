package com.lagradost.cloudstream3.movieproviders


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.*

class PelisplusSOProvider:MainAPI() {
    override var mainUrl = "https://pelisplus.so"
    override var name = "Pelisplus.so"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series", "Series actualizadas",),
            Pair("$mainUrl/", "Peliculas actualizadas"),
        )
        argamap({
            items.add(HomePageList("Estrenos", app.get(mainUrl).document.select("div#owl-demo-premiere-movies .pull-left").map{
                val title = it.selectFirst("p").text()
                TvSeriesSearchResponse(
                    title,
                    fixUrl(it.selectFirst("a").attr("href")),
                    this.name,
                    TvType.Movie,
                    it.selectFirst("img").attr("src"),
                    it.selectFirst("span.year").toString().toIntOrNull(),
                    null,
                )
            }))

            urls.apmap { (url, name) ->
                val soup = app.get(url).document
                val home = soup.select(".main-peliculas div.item-pelicula").map {
                    val title = it.selectFirst(".item-detail p").text()
                    val titleRegex = Regex("(\\d+)x(\\d+)")
                    TvSeriesSearchResponse(
                        title.replace(titleRegex,""),
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Movie,
                        it.selectFirst("img").attr("src"),
                        it.selectFirst("span.year").toString().toIntOrNull(),
                        null,
                    )
                }

                items.add(HomePageList(name, home))
            }
        })

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search.html?keyword=${query}"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "X-Requested-With" to "XMLHttpRequest",
            "DNT" to "1",
            "Connection" to "keep-alive",
            "Referer" to url,
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
        )
        val html = app.get(
            url,
            headers = headers
        ).text
        val document = Jsoup.parse(html)

        return document.select(".item-pelicula.pull-left").map {
            val title = it.selectFirst("div.item-detail p").text()
            val href = fixUrl(it.selectFirst("a").attr("href"))
            val year = it.selectFirst("span.year").text().toIntOrNull()
            val image = it.selectFirst("figure img").attr("src")
            val isMovie = href.contains("/pelicula/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    year,
                    null
                )
            }
        }
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document

        val title = soup.selectFirst(".info-content h1").text()

        val description = soup.selectFirst("span.sinopsis")?.text()?.trim()
        val poster: String? = soup.selectFirst(".poster img").attr("src")
        val episodes = soup.select(".item-season-episodes a").map { li ->
            val epTitle = li.selectFirst("a").text()
            val href = fixUrl(li.selectFirst("a").attr("href"))
            val seasonid = href.replace(Regex("($mainUrl\\/.*\\/temporada-|capitulo-)"),"").replace("/","-").let { str ->
                str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            Episode(
                href,
                epTitle,
                season = season,
                episode = episode,

            )
        }.reversed()

        val year = Regex("(\\d*)").find(soup.select(".info-half").text())

        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val tags = soup.select(".content-type-a a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        val duration = Regex("""(\d*)""").find(
            soup.select("p.info-half:nth-child(4)").text())

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year.toString().toIntOrNull(),
                    description,
                    ShowStatus.Ongoing,
                    null,
                    tags,
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    year.toString().toIntOrNull(),
                    description,
                    null,
                    tags,
                    duration.toString().toIntOrNull(),

                    )
            }
            else -> null
        }
    }

    private suspend fun getPelisStream(
        link: String,
        callback: (ExtractorLink) -> Unit) : Boolean {
        val soup = app.get(link).text
        val m3u8regex = Regex("((https:|http:)\\/\\/.*m3u8.*expiry=(\\d+))")
        val m3u8 = m3u8regex.find(soup)?.value ?: return false
        M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                m3u8,
                headers = mapOf("Referer" to mainUrl)
            ), true
        )
            .map { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                callback(
                    ExtractorLink(
                        name,
                        "$name $qualityString",
                        stream.streamUrl,
                        mainUrl,
                        getQualityFromName(stream.quality.toString()),
                        true
                    )
                )
            }
        return true
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        argamap({
            document.select(".server-item-1 li.tab-video").apmap {
           val url = fixUrl(it.attr("data-video"))
           if (url.contains("pelisplus.icu")) {
               val doc = app.get(url).document
               getPelisStream(url, callback)
               doc.select("ul.list-server-items li").map {
                   val secondurl = fixUrl(it.attr("data-video"))
                   for (extractor in extractorApis) {
                       if (secondurl.startsWith(extractor.mainUrl)) {
                           extractor.getSafeUrl(secondurl, data)?.apmap {
                               it.name += " Latino"
                               callback(it)
                           }
                       }
                   }
               }
           }
            for (extractor in extractorApis) {
                if (url.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(url, data)?.apmap {
                        it.name += " Latino"
                        callback(it)
                    }
                }
            }
        }
        document.select(".server-item-0 li.tab-video").apmap {
            val url = fixUrl(it.attr("data-video"))
            if (url.contains("pelisplus.icu")) {
                val doc = app.get(url).document
                getPelisStream(url, callback)
                doc.select("ul.list-server-items li").map {
                    val secondurl = fixUrl(it.attr("data-video"))
                    for (extractor in extractorApis) {
                        if (secondurl.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(secondurl, data)?.apmap {
                                it.name += " Subtitulado"
                                callback(it)
                            }
                        }
                    }
                }
            }
            for (extractor in extractorApis) {
                if (url.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(url, data)?.apmap {
                        it.name += " Subtitulado"
                        callback(it)
                    }
                }
            }
        }
        document.select(".server-item-2 li.tab-video").apmap {
            val url = fixUrl(it.attr("data-video"))
            if (url.contains("pelisplus.icu")) {
                val doc = app.get(url).document
                getPelisStream(url, callback)
                doc.select("ul.list-server-items li").map {
                    val secondurl = fixUrl(it.attr("data-video"))
                    for (extractor in extractorApis) {
                        if (secondurl.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(secondurl, data)?.apmap {
                                it.name += " Castellano"
                                callback(it)
                            }
                        }
                    }
                }
            }
            for (extractor in extractorApis) {
                if (url.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(url, data)?.apmap {
                        it.name += " Castellano"
                        callback(it)
                    }
                }
            }
        } })
        return true
    }

}
