package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class PelisplusHDProvider:MainAPI() {
    override var mainUrl = "https://ww1.pelisplushd.nu"
    override var name = "PelisplusHD"
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
        val document = app.get(mainUrl).document
        val map = mapOf(
            "Películas" to "#default-tab-1",
            "Series" to "#default-tab-2",
            "Anime" to "#default-tab-3",
            "Doramas" to "#default-tab-4",
        )
        map.forEach {
            items.add(HomePageList(
                it.key,
                document.select(it.value).select("a.Posters-link").map { element ->
                    element.toSearchResult()
                }
            ))
        }
        return HomePageResponse(items)
    }
    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select(".listing-content p").text()
        val href = this.select("a").attr("href")
        val posterUrl = fixUrl(this.select(".Posters-img").attr("src"))
        val isMovie = href.contains("/pelicula/")
        return if (isMovie) {
            MovieSearchResponse(
                title,
                href,
                name,
                TvType.Movie,
                posterUrl,
                null
            )
        } else {
            TvSeriesSearchResponse(
                title,
                href,
                name,
                TvType.Movie,
                posterUrl,
                null,
                null
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=${query}"
        val document = app.get(url).document

        return document.select("a.Posters-link").map {
            val title = it.selectFirst(".listing-content p").text()
            val href = it.selectFirst("a").attr("href")
            val image = fixUrl(it.selectFirst(".Posters-img").attr("src"))
            val isMovie = href.contains("/pelicula/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document

        val title = soup.selectFirst(".m-b-5").text()
        val description = soup.selectFirst("div.text-large")?.text()?.trim()
        val poster: String? = soup.selectFirst(".img-fluid").attr("src")
        val episodes = soup.select("div.tab-pane .btn").map { li ->
            val href = li.selectFirst("a").attr("href")
            val name = li.selectFirst(".btn-primary.btn-block").text().replace(Regex("(T(\\d+).*E(\\d+):)"),"").trim()
            val seasoninfo = href.substringAfter("temporada/").replace("/capitulo/","-")
            val seasonid =
                seasoninfo.let { str ->
                    str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
                }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            Episode(
                href,
                name,
                season,
                episode,
            )
        }

        val year = soup.selectFirst(".p-r-15 .text-semibold").text().toIntOrNull()
        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val tags = soup.select(".p-h-15.text-center a span.font-size-18.text-info.text-semibold")
            .map { it?.text()?.trim().toString().replace(", ","") }

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    fixUrl(poster!!),
                    year,
                    description,
                    null,
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
                    fixUrl(poster!!),
                    year,
                    description,
                    null,
                    tags,
                )
            }
            else -> null
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div.player > script").map { script ->
            fetchUrls(script.data()
                .replace("https://api.mycdn.moe/furl.php?id=","https://www.fembed.com/v/")
                .replace("https://api.mycdn.moe/sblink.php?id=","https://streamsb.net/e/"))
                .apmap { link ->
                    if (link.contains("https://api.mycdn.moe/video/")) {
                        val doc = app.get(link).document
                        doc.select("div.ODDIV li").apmap {
                            val linkencoded = it.attr("data-r")
                            val linkdecoded = base64Decode(linkencoded).replace("https://owodeuwu.xyz","https://embedsito.com")
                                .replace(Regex(".poster.*"),"")
                            loadExtractor(linkdecoded, link, callback)
                        }
                    }
                loadExtractor(link, data, callback)
            }
        }
        return true
    }
}
