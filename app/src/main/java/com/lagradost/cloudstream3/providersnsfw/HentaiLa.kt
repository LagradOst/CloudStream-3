package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.util.*
import kotlin.collections.ArrayList

class HentaiLa:MainAPI() {
    override var mainUrl = "https://hentaila.com"
    override var name = "HentaiLA"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Hentai)

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/directorio", "Hentais"),
            Pair("$mainUrl/directorio?filter=popular", "Populares"),
            Pair("$mainUrl/directorio?filter=recent", "Recientes"),
            Pair("$mainUrl/hentai-sin-censura", "Sin censura"),
        )
        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select(".grid.episodes .hentai.episode").map {
                    val title = it.selectFirst(".h-title").text()
                    val poster = it.selectFirst("img").attr("src")
                    val epRegex = Regex("(-(\\d+)\$)")
                    val url = it.selectFirst("a").attr("href").replace(epRegex,"")
                        .replace("/ver/","/hentai-")
                    val epNum = it.selectFirst(".num-episode").text().replace("Episodio ","").toIntOrNull()
                    AnimeSearchResponse(
                        title,
                        fixUrl(url),
                        this.name,
                        TvType.Hentai,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                        subEpisodes = epNum,
                        dubEpisodes = epNum,
                    )
                })
        )
        for (i in urls) {
            try {
                val doc = app.get(i.first).document
                val home = doc.select(".section .grid.hentais article").map {
                    val title = it.selectFirst(".h-title").text()
                    val poster = it.selectFirst("img").attr("src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Hentai,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
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

    data class Search (
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("slug") val slug: String
    )


    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val response = app.post("$mainUrl/api/search",
            headers = mapOf(
                "Host" to "hentaila.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to "https://hentaila.com",
                "DNT" to "1",
                "Connection" to "keep-alive",
                "Referer" to "https://hentaila.com/",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",),
            data = mapOf(Pair("value",query))
        ).text
        val searchresult = mutableListOf<AnimeSearchResponse>()
        parseJson<List<Search>>(response).forEach {
            val title = it.title
            val href = "$mainUrl/hentai-${it.slug}"
            val image = "$mainUrl/uploads/portadas/${it.id}.jpg"
            searchresult.add(
                AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Hentai,
                fixUrl(image),
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                )
            )
        }

        return ArrayList(searchresult)
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.h-title").text()
        val description = doc.selectFirst(".h-content > p").text().replace("Sinopsis: ","")
        val poster = doc.selectFirst("div.h-thumb:nth-child(2) > figure:nth-child(1) > img").attr("src")
        val episodes = doc.select(".episodes-list article").map { li ->
            val href = fixUrlNull(li?.selectFirst("a")?.attr("href")) ?: ""
            val epthumb = li.selectFirst("img").attr("src")
            Episode(
                data = href,
                name = li.selectFirst(".h-title").text().replace(title,""),
                posterUrl = fixUrl(epthumb)
            )
        }.reversed()
        val genre = doc.select("nav.genres a")
            .map { it?.text()?.trim().toString() }

        val status = when (doc.selectFirst("div.h-meta")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        return newAnimeLoadResponse(title, url, TvType.Hentai) {
            posterUrl = fixUrl(poster)
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genre
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("var videos = {") || script.data().contains("var anime_id =") || script.data().contains("server")) {
                val videos = script.data().replace("\\/", "/")
                fetchUrls(videos).map {
                    it.replace("https://embedsb.com/e/","https://watchsb.com/e/")
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
                if (videos.contains("Arc")) {
                    val arcregex = Regex("(https:\\/\\/.*\\.us\\.archive\\.org\\/.*\\/items\\/.*\\/video\\.mp4)")
                    arcregex.findAll(videos).map {
                        it.value
                    }.toList().apmap {
                        callback(
                            ExtractorLink(
                                "Arc",
                                "Arc",
                                it,
                                "",
                                Qualities.Unknown.value,
                                false
                            )
                        )
                    }
                }
            }
        }
        return true
    }
}
