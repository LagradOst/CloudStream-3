package com.lagradost.cloudstream3.animeproviders

import android.util.Range
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import java.util.*
import kotlin.collections.ArrayList

class HenaojaraProvider: MainAPI() {
    override var mainUrl: String = "https://henaojara.com"
    override var name: String = "Henaojara"
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
            Pair("$mainUrl/category/categorias/", "Animes",),
            Pair("$mainUrl/category/pelicula/", "Películas",),
            Pair("$mainUrl/category/estrenos/", "Estrenos",),
        )
        val items = ArrayList<HomePageList>()

        for (i in urls) {
            try {
                val doc = app.get(i.first).document
                val home = doc.select("li.TPostMv article").map {
                    val title = it.selectFirst("h3.title").text()
                    val poster = it.selectFirst("img").attr("src")
                    AnimeSearchResponse(
                        title,
                        it.selectFirst("a").attr("href"),
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                            DubStatus.Subbed),
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
    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val url = "${mainUrl}/?s=${query}"
        val doc = app.get(url).document
        val episodes = doc.select("li.TPostMv article").map {
            val title = it.selectFirst("h3.title").text()
            val href =it.selectFirst("a").attr("href")
            val image = it.selectFirst("img").attr("src")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                image,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed),
            )
        }
        return ArrayList(episodes)
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = soup.selectFirst("h1.Title").text()
        val description = try {
            soup.selectFirst("div.text-large").text()
        } catch (e: Exception) {
            soup.selectFirst("div.Description p").text()
        }
        val status = when (soup.selectFirst("span.Qlty")?.text()) {
            "ESTRENO" -> ShowStatus.Ongoing
            "FINALIZADO" -> ShowStatus.Completed
            else -> null
        }
        val poster: String? = soup.selectFirst("img.attachment-thumbnail.size-thumbnail").attr("src")
        val genres = soup.select("li.AAIco-adjust a").map { it.text() }
        val episodes = soup.select("div.TpRwCont div.TPTblCn.AA-cont table tbody tr").map { li ->
            val href = li.select("a").attr("href")
            val epThumb = li.select("a.MvTbImg img").attr("src")
            val name = li.select("a").text()
            AnimeEpisode(
                href,
                name,
                epThumb
            )
        }
        return newAnimeLoadResponse(title, url, if (episodes.isEmpty()) TvType.Movie else TvType.Anime) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //For normal links
        val movieID = app.get(data).document.select(".TPlayerTb iframe").attr("src").replace("https://henaojara.com/?trembed=0&trid=","")
            .replace("&trtype=2","")
        app.get(data).document.select("li.Button.STPb").forEach {
            val serverID = it.attr("data-tplayernv").replace("Opt","")
            val url = "https://henaojara.com/?trembed=$serverID&trid=$movieID&trtype=2"
            val docu = app.get(url).document
            val urlserver = docu.select("html body div.Video iframe").toString()
            val serversRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*))")
            val links = serversRegex.findAll(urlserver).map {
                it.value.replace("https://pelisplushd.net/fembed.php?url=","https://www.fembed.com/v/")
                    .replace("https://pelistop.co/","https://watchsb.com/")
            }.toList()
            for (link in links) {
                for (extractor in extractorApis) {
                    if (link.startsWith(extractor.mainUrl)) {
                        extractor.getSafeUrl(link, data)?.forEach {
                            callback(it)
                        }
                    }
                }
            }
        }
        return true
    }
}