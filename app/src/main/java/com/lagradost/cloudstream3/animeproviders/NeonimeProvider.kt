package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URL
import java.util.*

class NeonimeProvider : MainAPI() {
    override var mainUrl = "https://neonime.watch"
    override var name = "Neonime"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val lang = "id"
    override val hasDownloadSupport = true
    override val usesWebView = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Ended"  -> ShowStatus.Completed
                "OnGoing" -> ShowStatus.Ongoing
                "In Production" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)

        val homePageList = ArrayList<HomePageList>()

        document.select("div.item_1.items").forEach { block ->
            val header = block.previousElementSibling()?.select("h1")!!.text()
            val animes = block.select("div.item").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private suspend fun getProperAnimeLink(uri: String): String {
        return when {
            uri.contains("/episode") -> {
//                val href = "$mainUrl/tvshows/" + Regex("episode/(.*)").find(uri)?.groupValues?.get(1).toString().replace(Regex("-[0-9]x[0-9]"), "")
//                when {
//                    href.contains("boruto") -> href.replace(Regex("[0-9]+"), "")
//                    href.contains("-special") -> href.replace(Regex("-[a-z]\\d.+"), "")
//                    href.contains("one-piece") -> href.replace(Regex("-\\d.+[a-z]\\d.+-"), "-")
//                    else -> href
//                }
                app.get(uri).document.select(".epinav > a:nth-child(3)").attr("href")
            }
            else -> uri
        }
    }

    private suspend fun Element.toSearchResult(): SearchResponse? {
        val href = getProperAnimeLink(fixUrl(this.select("a").attr("href")))
        val title = this.select("span.tt.title-episode,h2.title-episode-movie").text()
        val posterUrl = fixUrl(this.select("img").attr("data-src"))
        val epNum = this.select(".fixyear > h2.text-center").text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true, subEpisodes = epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val html = app.get(link).text
        val document = Jsoup.parse(html)

        return document.select("div.item.episode-home").apmap {
            val title = it.selectFirst("div.judul-anime > span")!!.text()
            val poster = it.selectFirst("img")!!.attr("data-src")
            val episodes = it.selectFirst("div.fixyear > h2.text-center")!!
                .text()?.replace(Regex("[^0-9]"), "")?.trim()?.toIntOrNull()
            val tvType = getType(it.selectFirst("span.calidad2.episode")?.text().toString())
            val href = getProperAnimeLink(fixUrl(it.selectFirst("a")!!.attr("href")))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true, subEpisodes = episodes)
            }
        }
    }

    data class NeonimeSyncData(
        @JsonProperty("mal_id") val malId: String?,
        @JsonProperty("anilist_id") val aniListId: String?,
    )

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url, interceptor = DdosGuardKiller(true)).text
        val document = Jsoup.parse(html)

//        var title: String = ""
//        var poster: String? = null
//        var tags: List<String>? = null
//        var year: Int? = null
//        var japaneseTitle: String? = null
//        var checkStatus: String? = null
//        var status: ShowStatus? = null
//        var description: String? = null
//        var rating: Int? = null
//        var episodes: List<Episode>? = null

            if (url.contains("movie") || url.contains("live-action")) {
                val mTitle = document.selectFirst(".sbox > .data > h1[itemprop = name]")?.text().toString().trim()
                val mPoster =
                    document.selectFirst(".sbox > .imagen > .fix > img[itemprop = image]")?.attr("data-src")
                val mTags = document.select("p.meta_dd > a").map { it.text() }
                val mYear = document.selectFirst("a[href*=release-year]")!!.text().toIntOrNull()
                val mDescription = document.select("div[itemprop = description]").text().trim()
                val mRating = document.select("span[itemprop = ratingValue]").text().toIntOrNull()

                return MovieLoadResponse(
                    name = mTitle,
                    url = url,
                    this.name,
                    type = TvType.Movie,
                    dataUrl = url,
                    posterUrl = mPoster,
                    year = mYear,
                    plot = mDescription,
                    rating = mRating,
                    tags = mTags
                )
            }
            else {
                val title = document.select("h1[itemprop = name]").text().trim()
                val poster = document.selectFirst(".imagen > img")?.attr("data-src")
                val tags = document.select("#info a[href*=\"-genre/\"]").map { it.text() }
                val year = document.select("#info a[href*=\"-year/\"]").text().toIntOrNull()
                val status = getStatus(document.select("div.metadatac > span").last()!!.text().trim())
                val description = document.select("div[itemprop = description] > p").text().trim()

                val episodes = document.select("ul.episodios > li").apmap {
                    val name = it.selectFirst(".episodiotitle > a")!!.ownText().trim()
                    val link = fixUrl(it.selectFirst(".episodiotitle > a")!!.attr("href"))
                    Episode(link, name)
                }.reversed()

                return newAnimeLoadResponse(title, url, TvType.Anime) {
                    engName = title
                    posterUrl = poster
                    this.year = year
                    addEpisodes(DubStatus.Subbed, episodes)
                    showStatus = status
                    plot = description
                    this.tags = tags
                }
            }
    }

    data class Server(
        @JsonProperty("data") val data: List<Datum>,
    )

    data class Datum(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type") val type: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val source = if(data.contains("movie") || data.contains("live-action")) {
            app.get(data).document.select("#player2-1 > div[id*=div]").joinToString {
                fixUrl(it.select("iframe").attr("data-src"))
            }
        } else {
            app.get(data).document.select(".player2 > .embed2 > div[id*=player]").joinToString {
                fixUrl(it.select("iframe").attr("data-src"))
            }
        }

        val fid = source.substringAfter("https://www.fembed.com/v/").substringBefore(",")
        val url = "https://suzihaza.com/api/source/$fid"
        val referer = "https://suzihaza.com/v/$fid"

        mapper.readValue<Server>(
            app.post(
                url = url,
                headers = mapOf("Referer" to referer),
                data = mapOf("r" to "", "d" to "suzihaza.com")
            ).text
        ).data.apmap {
            callback(
                ExtractorLink(
                    name,
                    name,
                    it.file,
                    referer = "",
                    quality = it.label.replace("p", "").toInt()
                )
            )
        }

        return true
    }

}