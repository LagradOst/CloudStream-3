package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.net.URI
import java.net.URLDecoder
import java.util.ArrayList
import kotlin.math.roundToInt

class OploverzProvider : MainAPI() {
    override var mainUrl = "https://oploverz.asia"
    override var name = "Oploverz"
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
            return when {
                t.contains("TV") -> TvType.Anime
                t.contains("Movie") -> TvType.AnimeMovie
                else -> TvType.OVA
            }
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)

        val homePageList = ArrayList<HomePageList>()

        document.select(".bixbox.bbnofrm").forEach { block ->
            val header = block.selectFirst("h3")!!.text().trim()
            val animes = block.select("article[itemscope=itemscope]").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return when {
            (uri.contains("-episode")) -> {
                val href =
                    "$mainUrl/anime/" + Regex("asia/(.+)-episode.+").find(uri)?.groupValues?.get(1)
                        .toString()
                when {
                    href.contains("kaguya") -> href.replace(Regex("-s\\d+"), "")
                    else -> href
                }
            }
            (uri.contains("-movie")) -> "$mainUrl/anime/" + Regex("asia/(.+)-subtitle.+").find(uri)?.groupValues?.get(
                1
            )
            (uri.contains("-spesial")) -> "$mainUrl/anime/" + Regex("asia/(.+)-\\d.+").find(uri)?.groupValues?.get(
                1
            ).toString().replace("spesial", "special")
            (uri.contains("-ova")) -> "$mainUrl/anime/" + Regex("asia/(.+)-subtitle.+").find(uri)?.groupValues?.get(
                1
            ).toString()
            (uri.contains("-season")) -> "$mainUrl/anime/" + Regex("asia/(.+-\\d+)-\\d.+").find(uri)?.groupValues?.get(
                1
            ).toString()
            else -> "$mainUrl/anime/" + Regex("asia/(.+)-\\d.+").find(uri)?.groupValues?.get(1)
                .toString()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = getProperAnimeLink(this.selectFirst("a.tip")!!.attr("href"))
        val title = this.selectFirst("h2[itemprop=headline]")!!.text().trim()
        val posterUrl = fixUrl(this.selectFirst("img")!!.attr("src"))
        val type = getType(this.selectFirst(".eggtype, .typez")!!.text().trim())
        val epNum =
            this.selectFirst(".eggepisode, span.epx")!!.text().replace(Regex("[^0-9]"), "").trim()
                .toIntOrNull()

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true, subEpisodes = epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val html = app.get(link).text
        val document = Jsoup.parse(html)

        return document.select("article[itemscope=itemscope]").apmap {
            val title = it.selectFirst(".tt")!!.ownText().trim()
            val poster = it.selectFirst("img")!!.attr("src")
            val tvType = getType(it.selectFirst(".typez")?.text().toString())
            val href = fixUrl(it.selectFirst("a.tip")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        val title = document.selectFirst("h1.entry-title")!!.text().trim()
        val poster = document.select(".thumb > img")?.attr("src")
        val tags = document.select(".genxed > a").map { it.text() }

        val year = Regex("\\d, ([0-9]*)").find(
            document.selectFirst(".info-content > .spe > span > time")!!.text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val status = getStatus(
            document.select(".info-content > .spe > span:nth-child(1)")
                .text().trim().replace("Status: ", "")
        )
        val typeCheck =
            when {
                document.select(".info-content > .spe > span:nth-child(5), .info-content > .spe > span")
                    .text().trim().contains("TV") -> "TV"
                document.select(".info-content > .spe > span:nth-child(5), .info-content > .spe > span")
                    .text().trim().contains("TV") -> "Movie"
                else -> "OVA"
            }
        val type = getType(typeCheck)
        val description = document.select(".entry-content > p").text().trim()

        val episodes = document.select(".eplister > ul > li").apmap {
            val name = it.select(".epl-title").text().trim()
            val link = fixUrl(it.select("a").attr("href"))
            Episode(link, name)
        }.reversed()

        val recommendations =
            document.select(".listupd > article[itemscope=itemscope]").mapNotNull { rec ->
                val epTitle = rec.selectFirst(".tt")!!.ownText().trim()
                val epPoster = rec.selectFirst("img")!!.attr("src")
                val epType = getType(rec.selectFirst(".typez")?.text().toString())
                val epHref = fixUrl(rec.selectFirst("a.tip")!!.attr("href"))

                newAnimeSearchResponse(epTitle, epHref, epType) {
                    this.posterUrl = epPoster
                    addDubStatus(dubExist = false, subExist = true)
                }
            }

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val iframeLink = Jsoup.parse(app.get(data).text).selectFirst(".player-embed > iframe")?.attr("src") ?: return false
        val source = Jsoup.parse(app.get(fixUrl(iframeLink)).text).toString().substringAfter("\"streams\":[")
            .substringBefore("]")
        source.split("},").reversed().apmap {
            val url = it.substringAfter("{\"play_url\":\"").substringBefore("\"")
            val quality = when (it.substringAfter("\"format_id\":").substringBefore("}")) {
                "18" -> 360
                "22" -> 720
                else -> Qualities.Unknown.value
            }
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    url,
                    referer = "https://www.youtube.com/",
                    quality = quality
                )
            )
        }

        return true
    }

}