package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList


class AnimekisaProvider : MainAPI() {

    override val mainUrl = "https://animekisa.in"
    override val name = "Animekisa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    data class Response (
        @JsonProperty("html") val html: String
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/ajax/list/views?type=all", "All animes"),
            Pair("$mainUrl/ajax/list/views?type=day", "Trending now"),
            Pair("$mainUrl/ajax/list/views?type=week", "Trending by week"),
            Pair("$mainUrl/ajax/list/views?type=month", "Trending by month"),

            )

        val items = ArrayList<HomePageList>()

        for (i in urls) {
            try {
                val home = Jsoup.parse(
                    parseJson<Response>(
                        app.get(
                            i.first
                        ).text
                    ).html
                ).select("div.flw-item").map {
                    val title = it.selectFirst("h3.title a").text()
                    val url = it.selectFirst("a").attr("href")
                    val poster = it.selectFirst("img.lazyload").attr("data-src")
                    AnimeSearchResponse(
                        title,
                        url,
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("(DUB)") || title.contains("(Dub)")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/search/?keyword=$query").document.select("div.flw-item").map {
            val title = it.selectFirst("h3 a").text()
            val url = it.selectFirst("a.film-poster-ahref").attr("href")
                .replace("watch/","anime/").replace(Regex("(-episode-(\\d+)\\/\$|-episode-(\\d+)\$|-episode-full)"),"")
            val poster = it.selectFirst(".film-poster img").attr("data-src")
            AnimeSearchResponse(
                title,
                url,
                this.name,
                TvType.Anime,
                poster,
                null,
                if (title.contains("(DUB)") || title.contains("(Dub)")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed),
            )
        }.toList()
    }
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".mb-2 img").attr("src")
        val title = doc.selectFirst("h1.heading-name a").text()
        val description = doc.selectFirst("div.description p").text().trim()
        val genres = doc.select("div.row-line a").map { it.text() }
        val test = if (doc.selectFirst("div.dp-i-c-right").toString().contains("Airing")) ShowStatus.Ongoing else ShowStatus.Completed
        val episodes = doc.select("div.tab-content ul li.nav-item").map {
            val link = it.selectFirst("a").attr("href")
            AnimeEpisode(link)
        }
        val type = if (doc.selectFirst(".dp-i-stats").toString().contains("Movies")) TvType.AnimeMovie else TvType.Anime
        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = test
            plot = description
            tags = genres
        }
    }

    data class Sources (
        @JsonProperty("file") val file: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("#servers-list ul.nav li a").apmap {
            val server = it.attr("data-embed")
            val servername = it.select("span").text()
            val response = app.get(server, interceptor = WebViewResolver(
                Regex(".m3u8")
            ),
                referer = data
            )
            if (response.text.contains("#EXTM3U"))
                M3u8Helper().m3u8Generation(
                    M3u8Helper.M3u8Stream(
                        response.url,
                        headers = app.get(data).headers.toMap(),
                    ), true
                )
                    .map { stream ->
                        val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                       listOf( callback( ExtractorLink(
                            servername,
                            "$servername $qualityString",
                            stream.streamUrl,
                            data,
                            getQualityFromName(stream.quality.toString()),
                            true
                        )))
                    }
            }
        //When the extracted vidstream link above won't work
        app.get(data).document.select("div#servers-list ul.nav li a:contains(VidStream)").apmap {
            val server = it.attr("data-embed")
            val getserver = app.get(server, referer = data).text
            val key = getserver.substringAfter("window.skey = '").substringBefore("'")
            val linkwkey = (server.replace("/e/","/info/")+"&skey=$key")
            val normallink = app.get(linkwkey, referer = data).text
            val extractedlink = normallink.substringAfter("},{\"file\":\"").substringBefore("\"}]")
                .replace("\\/", "/")
               if (extractedlink.isNotBlank()) M3u8Helper().m3u8Generation(
                    M3u8Helper.M3u8Stream(
                        extractedlink,
                        headers = app.get(extractedlink).headers.toMap(),
                    ), true
                )
                    .map { stream ->
                        val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                        callback( ExtractorLink(
                            "Vidstream 2",
                            "Vidstream 2 $qualityString",
                            stream.streamUrl,
                            data,
                            getQualityFromName(stream.quality.toString()),
                            true
                        ))

         }
        }
        return true
    }
}