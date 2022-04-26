package com.lagradost.cloudstream3.animeproviders

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.fasterxml.jackson.annotation.JsonProperty
import java.text.SimpleDateFormat
import java.util.*
import khttp.structures.cookie.CookieJar
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

//Credits https://raw.githubusercontent.com/ArjixWasTaken/CloudStream-3/master/app/src/main/java/com/ArjixWasTaken/cloudstream3/animeproviders/HahoMoeProvider.kt

class HahoMoe : MainAPI() {
    companion object {
        var token: String? = null
        var cookie: CookieJar? = null

        fun getType(t: String): TvType {
            return TvType.Hentai
            /*
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie else TvType.Anime
             */
        }
    }

    override var mainUrl = "https://haho.moe"
    override var name = "Haho moe"
    override val hasQuickSearch: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val supportedTypes: Set<TvType> get() = setOf(TvType.Hentai)

    private fun loadToken(): Boolean {
        return try {
            val response = khttp.get(mainUrl)
            cookie = response.cookies
            val document = Jsoup.parse(response.text)
            token = document.selectFirst("""meta[name="csrf-token"]""").attr("content")
            token != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        for (section in soup.select("#content > section")) {
            try {
                if (section.attr("id") == "toplist-tabs") {
                    for (top in section.select(".tab-content > [role=\"tabpanel\"]")) {
                        val title = "Top - " + top.attr("id").split("-")[1].capitalize(Locale.UK)
                        val anime =
                            top.select("li > a").map {
                                AnimeSearchResponse(
                                    it.selectFirst(".thumb-title").text(),
                                    fixUrl(it.attr("href")),
                                    this.name,
                                    TvType.Hentai,
                                    it.selectFirst("img").attr("src"),
                                    null,
                                    EnumSet.of(DubStatus.Subbed),
                                    null,
                                    null,
                                    null
                                )
                            }
                        items.add(HomePageList(title, anime))
                    }
                } else {
                    val title = section.selectFirst("h2").text()
                    val anime =
                        section.select("li > a").map {
                            AnimeSearchResponse(
                                (it.selectFirst(".thumb-title").text() ?: null).toString(),
                                fixUrl(it.attr("href")),
                                this.name,
                                TvType.Hentai,
                                it.selectFirst("img").attr("src"),
                                null,
                                EnumSet.of(DubStatus.Subbed),
                                null,
                                null,
                                null
                            )
                        }
                    items.add(HomePageList(title, anime))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun getIsMovie(type: String, id: Boolean = false): Boolean {
        if (!id) return type == "Movie"

        val movies = listOf("rrso24fa", "e4hqvtym", "bl5jdbqn", "u4vtznut", "37t6h2r4", "cq4azcrj")
        val aniId = type.replace("$mainUrl/anime/", "")
        return movies.contains(aniId)
    }

    private fun parseSearchPage(soup: Document): ArrayList<SearchResponse> {
        val items = soup.select("ul.thumb > li > a")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (i in items) {
            val href = fixUrl(i.attr("href"))
            val img = fixUrl(i.selectFirst("img").attr("src"))
            val title = i.attr("title")

            returnValue.add(
                if (getIsMovie(href, true)) {
                    MovieSearchResponse(title, href, this.name, TvType.Hentai, img, null)
                } else {
                    AnimeSearchResponse(
                        title,
                        href,
                        this.name,
                        TvType.Hentai,
                        img,
                        null,
                        EnumSet.of(DubStatus.Subbed),
                        null,
                        null,
                        null
                    )
                }
            )
        }
        return returnValue
    }

    @SuppressLint("SimpleDateFormat")
    private fun dateParser(dateString: String): String? {
        try {
            val format = SimpleDateFormat("dd 'of' MMM',' yyyy")
            val newFormat = SimpleDateFormat("dd-MM-yyyy")
            val data =
                format.parse(
                    dateString
                        .replace("th ", " ")
                        .replace("st ", " ")
                        .replace("nd ", " ")
                        .replace("rd ", " ")
                )
                    ?: return null
            return newFormat.format(data)
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/anime"
        var response =
            app.get(
                url,
                params = mapOf("q" to query),
                cookies = mapOf("loop-view" to "thumb")
            )
        var document = Jsoup.parse(response.text)
        val returnValue = parseSearchPage(document)

        while (!document.select("""a.page-link[rel="next"]""").isEmpty()) {
            val link = document.select("""a.page-link[rel="next"]""")
            if (link != null && !link.isEmpty()) {
                response = app.get(link[0].attr("href"), cookies = mapOf("loop-view" to "thumb"))
                document = Jsoup.parse(response.text)
                returnValue.addAll(parseSearchPage(document))
            } else {
                break
            }
        }

        return returnValue
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = mapOf("loop-view" to "thumb")).document

        val englishTitle =
            document.selectFirst("span.value > span[title=\"English\"]")
                ?.parent()
                ?.text()
                ?.trim()
        val japaneseTitle =
            document.selectFirst("span.value > span[title=\"Japanese\"]")
                ?.parent()
                ?.text()
                ?.trim()
        val canonicalTitle = document.selectFirst("header.entry-header > h1.mb-3").text().trim()

        val episodeNodes = document.select("li[class*=\"episode\"] > a")

        val episodes = episodeNodes?.mapNotNull {
            val dataUrl = it?.attr("href") ?: return@mapNotNull null
            val epi = Episode(
                data = dataUrl,
                name = it.selectFirst(".episode-title")?.text()?.trim(),
                posterUrl = it.selectFirst("img")?.attr("src"),
                description = it.attr("data-content").trim(),
            )
            epi.addDate(it.selectFirst(".episode-date").text().trim())
            epi
        } ?: listOf()
        val status =
            when (document.selectFirst("li.status > .value")?.text()?.trim()) {
                "Ongoing" -> ShowStatus.Ongoing
                "Completed" -> ShowStatus.Completed
                else -> null
            }
        val yearText = document.selectFirst("li.release-date .value").text()
        val pattern = "(\\d{4})".toRegex()
        val (year) = pattern.find(yearText)!!.destructured

        val poster = document.selectFirst("img.cover-image")?.attr("src")
        val type = document.selectFirst("a[href*=\"$mainUrl/type/\"]")?.text()?.trim()

        val synopsis = document.selectFirst(".entry-description > .card-body")?.text()?.trim()
        val genre =
            document.select("li.genre.meta-data > span.value").map {
                it?.text()?.trim().toString()
            }

        val synonyms =
            document.select("li.synonym.meta-data > div.info-box > span.value").map {
                it?.text()?.trim().toString()
            }

        return AnimeLoadResponse(
            englishTitle,
            japaneseTitle,
            canonicalTitle,
            url,
            this.name,
            getType(type ?: ""),
            poster,
            year.toIntOrNull(),
            hashMapOf(DubStatus.Subbed to episodes),
            status,
            synopsis,
            ArrayList(genre),
            ArrayList(synonyms),
            null,
            null,
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val soup = app.get(data).document

        data class Quality(
            @JsonProperty("src") val src: String,
            @JsonProperty("size") val size: Int
        )

        val sources = ArrayList<ExtractorLink>()
        for (source in soup.select("""[aria-labelledby="mirror-dropdown"] > li > a.dropdown-item""")) {
            val release = source.text().replace("/", "").trim()
            val sourceSoup = app.get(
                "$mainUrl/embed?v=${source.attr("href").split("v=")[1].split("&")[0]}",
                headers=mapOf("Referer" to data)
            ).document

            for (quality in sourceSoup.select("video#player > source")) {
                sources.add(
                    ExtractorLink(
                        this.name,
                        "${this.name} $release - " + quality.attr("title"),
                        fixUrl(quality.attr("src")),
                        this.mainUrl,
                        getQualityFromName(quality.attr("title"))
                    )
                )
            }
        }

        for (source in sources) {
            callback.invoke(source)
        }
        return true
    }
}