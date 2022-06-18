package com.lagradost.cloudstream3.movieproviders

import androidx.core.text.parseAsHtml
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating


class Filmpertutti : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://www.filmpertutti.buzz"
    override var name = "Filmpertutti"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/category/serie-tv/", "Serie Tv"),
            Pair("$mainUrl/category/film/azione/", "Azione"),
            Pair("$mainUrl/category/film/avventura/", "Avventura"),
        )
        for ((url, name) in urls) {
            try {
                val soup = app.get(url).document
                val home = soup.select("ul.posts > li").map {
                    val title = it.selectFirst("div.title")!!.text().substringBeforeLast("(").substringBeforeLast("[")
                    val link = it.selectFirst("a")!!.attr("href")
                    val image = it.selectFirst("a")!!.attr("data-thumbnail")
                    val qualitydata = it.selectFirst("div.hd")
                    val quality = if (qualitydata!= null) {
                        getQualityFromString(qualitydata.text())
                    }
                    else {
                        null
                    }
                    newTvSeriesSearchResponse(
                        title,
                        link) {
                        this.posterUrl = image
                        this.quality = quality
                    }
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "+")
        val url = "$mainUrl/?s=$queryformatted"
        val doc = app.get(url).document
        return doc.select("ul.posts > li").map {
            val title = it.selectFirst("div.title")!!.text().substringBeforeLast("(").substringBeforeLast("[")
            val link = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("a")!!.attr("data-thumbnail")
            val qualitydata = it.selectFirst("div.hd")
            val quality = if (qualitydata!= null) {
                getQualityFromString(qualitydata.text())
            }
            else {
                null
            }
            MovieSearchResponse(
                title,
                link,
                this.name,
                quality = quality,
                posterUrl = image
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val type =
            if (document.selectFirst("a.taxonomy.category")!!.attr("href").contains("serie-tv")
                    .not()
            ) TvType.Movie else TvType.TvSeries
        val title = document.selectFirst("#content > h1")!!.text().substringBeforeLast("(")
            .substringBeforeLast("[")
        val descipt = document.selectFirst("i.fa.fa-file-text-o.fa-fw")!!.parent()!!.nextSibling()!!
            .toString().parseAsHtml().toString()
        val rating = document.selectFirst("div.rating > div.value")?.text()
        val year =
            document.selectFirst("i.fa.fa-calendar.fa-fw")!!.parent()!!.nextSibling()!!.childNode(0)
                .toString().substringAfterLast(" ")
                .filter { it.isDigit() }.toInt()
        // ?: does not wor
        val poster = document.selectFirst("div.meta > div > img")!!.attr("data-src")

        //val recommend?

        val trailerurl =
            "https://www.youtube.com/watch?v=" + document.selectFirst("div.youtube-player")!!
                .attr("data-id")

        if (type == TvType.TvSeries) {

            val episodeList = ArrayList<Episode>()
            document.select("div.accordion-item").map { element ->
                val season =
                    element.selectFirst("#season > ul > li.s_title > span")!!.text().toInt()
                element.select("div.episode-wrap").map { episode ->
                    val href =
                        episode.select("#links > div > div > table > tbody:nth-child(2) > tr")
                            .map { it.selectFirst("a")!!.attr("href") }.toString()
                    val epNum = episode.selectFirst("li.season-no")!!.text().substringAfter("x")
                        .filter { it.isDigit() }.toIntOrNull()
                    val epTitle = episode.selectFirst("li.other_link > a")!!.text()
                    val posterUrl = episode.selectFirst("figure > img")!!.attr("data-src")
                    episodeList.add(
                        Episode(
                            href,
                            epTitle,
                            season,
                            epNum,
                            posterUrl,
                        )
                    )
                }
            }
            return newTvSeriesLoadResponse(
                title,
                url, type, episodeList
            ) {

                this.posterUrl = poster
                this.year = year
                this.plot = descipt
                addRating(rating)
            }
        } else {
            val urls = document.select("div.embed-player").map { it.attr("data-id") }.toString()
            return newMovieLoadResponse(
                title,
                url,
                type,
                urls
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year
                this.plot = descipt
                addRating(rating)
                addTrailer(trailerurl)

            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.drop(1).dropLast(1).split(",").forEach { id ->
            val doc2 = app.get(id).document
            val id2 = app.get(doc2.selectFirst("iframe")!!.attr("src")).url
            loadExtractor(id2, data, callback)
        }
        return true
    }
}