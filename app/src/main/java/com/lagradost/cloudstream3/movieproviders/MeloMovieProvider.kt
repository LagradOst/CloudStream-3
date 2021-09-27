package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class MeloMovieProvider : MainAPI() {
    override val name: String
        get() = "MeloMovie"
    override val mainUrl: String
        get() = "https://melomovie.com"
    override val instantLinkLoading: Boolean
        get() = true
    override val hasQuickSearch: Boolean
        get() = true
    override val hasChromecastSupport: Boolean
        get() = false // MKV FILES CANT BE PLAYED ON A CHROMECAST

    data class MeloMovieSearchResult(
        @JsonProperty("id") val id: Int,
        @JsonProperty("imdb_code") val imdbId: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: Int, // 1 = MOVIE, 2 = TV-SERIES
        @JsonProperty("year") val year: Int?, // 1 = MOVIE, 2 = TV-SERIES
        //"mppa" for tags
    )

    data class MeloMovieLink(val name: String, val link: String)

    override fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/movie/search/?name=$query"
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        val response = get(url).text
        val mapped = response.let { mapper.readValue<List<MeloMovieSearchResult>>(it) }
        if (mapped.isEmpty()) return returnValue

        for (i in mapped) {
            val currentUrl = "$mainUrl/movie/${i.id}"
            val currentPoster = "$mainUrl/assets/images/poster/${i.imdbId}.jpg"
            if (i.type == 2) { // TV-SERIES
                returnValue.add(
                    TvSeriesSearchResponse(
                        i.title,
                        currentUrl,
                        this.name,
                        TvType.TvSeries,
                        currentPoster,
                        i.year,
                        null
                    )
                )
            } else if (i.type == 1) { // MOVIE
                returnValue.add(
                    MovieSearchResponse(
                        i.title,
                        currentUrl,
                        this.name,
                        TvType.Movie,
                        currentUrl,
                        i.year
                    )
                )
            }
        }
        return returnValue
    }

    // http not https, the links are not https!
    private fun fixUrl(url: String): String {
        if (url.isEmpty()) return ""

        if (url.startsWith("//")) {
            return "http:$url"
        }
        if (!url.startsWith("http")) {
            return "http://$url"
        }
        return url
    }

    private fun serializeData(element: Element): String {
        val eps = element.select("> tbody > tr")
        val parsed = eps.map {
            try {
                val tds = it.select("> td")
                val name = tds[if (tds.size == 5) 1 else 0].text()
                val url = fixUrl(tds.last().selectFirst("> a").attr("data-lnk").replace(" ", "%20"))
                MeloMovieLink(name, url)
            } catch (e: Exception) {
                MeloMovieLink("", "")
            }
        }.filter { it.link != "" && it.name != "" }
        return mapper.writeValueAsString(parsed)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = mapper.readValue<List<MeloMovieLink>>(data)
        for (link in links) {
            callback.invoke(ExtractorLink(this.name, link.name, link.link, "", getQualityFromName(link.name), false))
        }
        return true
    }

    override fun load(url: String): LoadResponse? {
        val response = get(url).text

        //backdrop = imgurl
        fun findUsingRegex(src: String): String? {
            return src.toRegex().find(response)?.groups?.get(1)?.value ?: return null
        }

        val imdbUrl = findUsingRegex("var imdb = \"(.*?)\"")
        val document = Jsoup.parse(response)
        val poster = document.selectFirst("img.img-fluid").attr("src")
        val type = findUsingRegex("var posttype = ([0-9]*)")?.toInt() ?: return null
        val titleInfo = document.selectFirst("div.movie_detail_title > div > div > h1")
        val title = titleInfo.ownText()
        val year = titleInfo.selectFirst("> a")?.text()?.replace("(", "")?.replace(")", "")?.toIntOrNull()
        val plot = document.selectFirst("div.col-lg-12 > p").text()

        if (type == 1) { // MOVIE
            val serialize = document.selectFirst("table.accordion__list") ?: throw ErrorLoadingException("No links found")
            return MovieLoadResponse(
                title,
                url,
                this.name,
                TvType.Movie,
                serializeData(serialize),
                poster,
                year,
                plot,
                imdbUrlToIdNullable(imdbUrl)
            )
        } else if (type == 2) {
            val episodes = ArrayList<TvSeriesEpisode>()
            val seasons = document.select("div.accordion__card") ?: throw ErrorLoadingException("No episodes found")
            for (s in seasons) {
                val season =
                    s.selectFirst("> div.card-header > button > span").text().replace("Season: ", "").toIntOrNull()
                val localEpisodes = s.select("> div.collapse > div > div > div.accordion__card")
                for (e in localEpisodes) {
                    val episode =
                        e.selectFirst("> div.card-header > button > span").text().replace("Episode: ", "").toIntOrNull()
                    val links = e.selectFirst("> div.collapse > div > table.accordion__list") ?: continue
                    val data = serializeData(links)
                    episodes.add(TvSeriesEpisode(null, season, episode, data))
                }
            }
            episodes.reverse()
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                episodes,
                poster,
                year,
                plot,
                null,
                imdbUrlToIdNullable(imdbUrl)
            )
        }
        return null
    }
}