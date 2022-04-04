package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.ArrayList


class MonoschinosProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override var mainUrl = "https://monoschinos2.com"
    override var name = "Monoschinos"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/emision", "En emisión"),
            Pair(
                "$mainUrl/animes?categoria=pelicula&genero=false&fecha=false&letra=false",
                "Peliculas"
            ),
            Pair("$mainUrl/animes", "Animes"),
        )

        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select(".col-6").map {
                    val title = it.selectFirst("p.animetitles").text()
                    val poster = it.selectFirst(".animeimghv").attr("data-src")
                    val epRegex = Regex("episodio-(\\d+)")
                    val href = it.selectFirst("a").attr("href").replace("ver/", "anime/")
                        .replace(epRegex, "sub-espanol")
                    val epNum = it.selectFirst(".positioning h5").text().toIntOrNull()
                    AnimeSearchResponse(
                        title,
                        href,
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                        subEpisodes = epNum,
                        dubEpisodes = epNum,
                    )
                })
        )
        urls.apmap { (url, name) ->
            val home = app.get(url).document.select(".col-6").map {
                val title = it.selectFirst(".seristitles").text()
                val poster = it.selectFirst("img.animemainimg").attr("src")
                AnimeSearchResponse(
                    title,
                    fixUrl(it.selectFirst("a").attr("href")),
                    this.name,
                    TvType.Anime,
                    fixUrl(poster),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
            items.add(HomePageList(name, home))
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val search =
            app.get("$mainUrl/buscar?q=$query", timeout = 120).document.select(".col-6").map {
                val title = it.selectFirst(".seristitles").text()
                val href = fixUrl(it.selectFirst("a").attr("href"))
                val image = it.selectFirst("img.animemainimg").attr("src")
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    fixUrl(image),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
        return ArrayList(search)
    }
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".chapterpic img").attr("src")
        val title = doc.selectFirst(".chapterdetails h1").text()
        val type = doc.selectFirst("div.chapterdetls2").text()
        val description = doc.selectFirst("p.textComplete").text().replace("Ver menos", "").trim()
        val genres = doc.select(".breadcrumb-item a").map { it.text() }
        val status = when (doc.selectFirst("button.btn1")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val yearregex = Regex("((\\d+)<\\/li)")
        val yearselector = doc.selectFirst("div.row div.col-lg-12.col-md-9")
        val year1 = try {
            yearregex.findAll(yearselector.toString()).map {
                it.value.replace("</li","")
            }.toList().first().toIntOrNull()
        } catch (e: Exception) {
            null
        }
        val episodes = doc.select("div.heroarea2 div.heromain2 div.allanimes div.row.jpage.row-cols-md-6 div.col-item").map {
            val name = it.selectFirst("p.animetitles").text()
            val link = it.selectFirst("a").attr("href")
            val epThumb = it.selectFirst(".animeimghv").attr("data-src")
            AnimeEpisode(link, name, posterUrl = epThumb)
        }
        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
            year = year1
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val test =
        app.get(data).document.select("div.playother p").apmap {
            val encodedurl = it.select("p").attr("data-player")
            val urlDecoded = base64Decode(encodedurl)
            val url = (urlDecoded).replace("https://monoschinos2.com/reproductor?url=", "")
                .replace("https://repro.monoschinos2.com/aqua/sv?url=","")
            loadExtractor(url, data, callback)
        }
        return true
    }
}