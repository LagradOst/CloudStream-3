package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.ArrayList

class AnimeIndoProvider : MainAPI() {
    override var mainUrl = "https://anime-indo.one"
    override var name = "AnimeIndo"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

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
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.widget_senction").forEach { block ->
            val header = block.selectFirst("div.widget-title > h3")!!.text().trim()
            val items = block.select("div.post-show > article").mapNotNull {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains("-episode")) && !(title.contains("-movie")) -> Regex("(.+)-episode").find(
                    title
                )?.groupValues?.get(1).toString()
                (title.contains("-movie")) -> Regex("(.+)-movie").find(title)?.groupValues?.get(
                    1
                ).toString()
                else -> title
            }
            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("div.title")!!.text().trim()
        val href = getProperAnimeLink(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.select("img[itemprop=image]").attr("src").toString()
        val type = getType(this.select("div.type").text().trim())
        val epNum =
            this.selectFirst("span.episode")?.ownText()?.replace(Regex("[^0-9]"), "")?.trim()
                ?.toIntOrNull()
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true, subEpisodes = epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select(".site-main.relat > article").map {
            val title = it.selectFirst("div.title > h2")!!.ownText().trim()
            val href = it.selectFirst("a")!!.attr("href")
            val posterUrl = it.selectFirst("img")!!.attr("src").toString()
            val type = getType(it.select("div.type").text().trim())
            newAnimeSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text().toString().trim()
        val poster = document.selectFirst("div.thumb > img[itemprop=image]")?.attr("src")
        val tags = document.select("div.genxed > a").map { it.text() }
        val type = getType(
            document.selectFirst("div.info-content > div.spe > span:nth-child(6)")?.ownText()
                .toString()
        )
        val year = Regex("\\d, ([0-9]*)").find(
            document.select("div.info-content > div.spe > span:nth-child(9) > time").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(
            document.selectFirst("div.info-content > div.spe > span:nth-child(1)")!!.ownText()
                .trim()
        )
        val description = document.select("div[itemprop=description] > p").text()
        val trailer = document.selectFirst("div.player-embed iframe")?.attr("src")
        val episodes = document.select("div.lstepsiode.listeps ul li").mapNotNull {
            val name = it.selectFirst("span.lchx > a")!!.text().trim()
            val episode = it.selectFirst("span.lchx > a")!!.text().trim().replace("Episode", "").trim().toIntOrNull()
            val link = fixUrl(it.selectFirst("span.lchx > a")!!.attr("href"))
            Episode(link, name = name, episode = episode)
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val sources = document.select("div.itemleft > .mirror > option").mapNotNull {
            fixUrl(Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src"))
        }.map {
            if (it.startsWith("https://uservideo.xyz")) {
                app.get(it, referer = "$mainUrl/").document.select("iframe").attr("src")
            } else {
                it
            }
        }

        sources.apmap {
            loadExtractor(it, data, callback)
        }


        return true
    }


}