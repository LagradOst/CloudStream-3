package com.lagradost.cloudstream3.animeproviders

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class TenshiProvider : MainAPI() {
    companion object {
        //var token: String? = null
        //var cookie: Map<String, String> = mapOf()

        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.ONA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override val mainUrl: String
        get() = "https://tenshi.moe"
    override val name: String
        get() = "Tenshi.moe"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = true


    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Anime, TvType.AnimeMovie, TvType.ONA)

    /*private fun loadToken(): Boolean {
        return try {
            val response = get(mainUrl)
            cookie = response.cookies
            val document = Jsoup.parse(response.text)
            token = document.selectFirst("""meta[name="csrf-token"]""").attr("content")
            token != null
        } catch (e: Exception) {
            false
        }
    }*/

    override fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = Jsoup.parse(get(mainUrl).text)
        for (section in soup.select("#content > section")) {
            try {
                if (section.attr("id") == "toplist-tabs") {
                    for (top in section.select(".tab-content > [role=\"tabpanel\"]")) {
                        val title = "Top - " + top.attr("id").split("-")[1].capitalize(Locale.UK)
                        val anime = top.select("li > a").map {
                            AnimeSearchResponse(
                                it.selectFirst(".thumb-title").text(),
                                fixUrl(it.attr("href")),
                                this.name,
                                TvType.Anime,
                                it.selectFirst("img").attr("src"),
                                null,
                                null,
                                EnumSet.of(DubStatus.Subbed),
                                null,
                                null
                            )
                        }
                        items.add(HomePageList(title, anime))
                    }
                } else {
                    val title = section.selectFirst("h2").text()
                    val anime = section.select("li > a").map {
                        AnimeSearchResponse(
                            it.selectFirst(".thumb-title")?.text() ?: "",
                            fixUrl(it.attr("href")),
                            this.name,
                            TvType.Anime,
                            it.selectFirst("img").attr("src"),
                            null,
                            null,
                            EnumSet.of(DubStatus.Subbed),
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
                    MovieSearchResponse(
                        title, href, this.name, TvType.Movie, img, null
                    )
                } else {
                    AnimeSearchResponse(
                        title,
                        href,
                        this.name,
                        TvType.Anime,
                        img,
                        null,
                        null,
                        EnumSet.of(DubStatus.Subbed),
                        null,
                        null
                    )
                }
            )
        }
        return returnValue
    }

    @SuppressLint("SimpleDateFormat")
    private fun dateParser(dateString: String?): String? {
        if(dateString == null) return null
        try {
            val format = SimpleDateFormat("dd 'of' MMM',' yyyy")
            val newFormat = SimpleDateFormat("dd-MM-yyyy")
            val data = format.parse(
                dateString.replace("th ", " ").replace("st ", " ").replace("nd ", " ").replace("rd ", " ")
            ) ?: return null
            return newFormat.format(data)
        } catch (e: Exception) {
            return null
        }
    }

//    data class TenshiSearchResponse(
//        @JsonProperty("url") var url : String,
//        @JsonProperty("title") var title : String,
//        @JsonProperty("cover") var cover : String,
//        @JsonProperty("genre") var genre : String,
//        @JsonProperty("year") var year : Int,
//        @JsonProperty("type") var type : String,
//        @JsonProperty("eps") var eps : String,
//        @JsonProperty("cen") var cen : String
//    )

//    override fun quickSearch(query: String): ArrayList<SearchResponse>? {
//        if (!autoLoadToken()) return quickSearch(query)
//        val url = "$mainUrl/anime/search"
//        val response = khttp.post(
//            url,
//            data=mapOf("q" to query),
//            headers=mapOf("x-csrf-token" to token, "x-requested-with" to "XMLHttpRequest"),
//            cookies = cookie
//
//        )
//
//        val items = mapper.readValue<List<TenshiSearchResponse>>(response.text)
//
//        if (items.isEmpty()) return ArrayList()
//
//        val returnValue = ArrayList<SearchResponse>()
//        for (i in items) {
//            val href = fixUrl(i.url)
//            val title = i.title
//            val img = fixUrl(i.cover)
//            val year = i.year
//
//            returnValue.add(
//                if (getIsMovie(i.type)) {
//                    MovieSearchResponse(
//                        title, href, getSlug(href), this.name, TvType.Movie, img, year
//                    )
//                } else {
//                    AnimeSearchResponse(
//                        title, href, getSlug(href), this.name,
//                        TvType.Anime, img,  year, null,
//                        EnumSet.of(DubStatus.Subbed),
//                        null, null
//                    )
//                }
//            )
//        }
//        return returnValue
//    }

    override fun search(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/anime"
        var response = get(url, params = mapOf("q" to query), cookies = mapOf("loop-view" to "thumb")).text
        var document = Jsoup.parse(response)

        val returnValue = parseSearchPage(document)

        while (!document.select("""a.page-link[rel="next"]""").isEmpty()) {
            val link = document.select("""a.page-link[rel="next"]""")
            if (link != null && !link.isEmpty()) {
                response = get(link[0].attr("href"), cookies = mapOf("loop-view" to "thumb")).text
                document = Jsoup.parse(response)
                returnValue.addAll(parseSearchPage(document))
            } else {
                break
            }
        }

        return returnValue
    }

    override fun load(url: String): LoadResponse {
        var response = get(url, cookies = mapOf("loop-view" to "thumb")).text
        var document = Jsoup.parse(response)

        val englishTitle = document.selectFirst("span.value > span[title=\"English\"]")?.parent()?.text()?.trim()
        val japaneseTitle = document.selectFirst("span.value > span[title=\"Japanese\"]")?.parent()?.text()?.trim()
        val canonicalTitle = document.selectFirst("header.entry-header > h1.mb-3").text().trim()

        val episodeNodes = document.select("li[class*=\"episode\"] > a").toMutableList()
        val totalEpisodePages = if (document.select(".pagination").size > 0)
                document.select(".pagination .page-item a.page-link:not([rel])").last().text().toIntOrNull()
            else 1

        if (totalEpisodePages != null && totalEpisodePages > 1) {
            for (pageNum in 2..totalEpisodePages) {
                response = get("$url?page=$pageNum", cookies = mapOf("loop-view" to "thumb")).text
                document = Jsoup.parse(response)
                episodeNodes.addAll(document.select("li[class*=\"episode\"] > a"))
            }
        }

        val episodes = ArrayList(episodeNodes.map {
            AnimeEpisode(
                it.attr("href"),
                it.selectFirst(".episode-title")?.text()?.trim(),
                it.selectFirst("img")?.attr("src"),
                dateParser(it?.selectFirst(".episode-date")?.text()?.trim()),
                null,
                it.attr("data-content").trim(),
            )
        })
        val status = when (document.selectFirst("li.status > .value")?.text()?.trim()) {
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
        val genre = document.select("li.genre.meta-data > span.value").map { it?.text()?.trim().toString() }

        val synonyms =
            document.select("li.synonym.meta-data > div.info-box > span.value").map { it?.text()?.trim().toString() }

        return AnimeLoadResponse(
            englishTitle,
            japaneseTitle,
            canonicalTitle,
            url,
            this.name,
            getType(type ?: ""),
            poster,
            year.toIntOrNull(),
            null,
            episodes,
            status,
            synopsis,
            ArrayList(genre),
            ArrayList(synonyms),
            null,
            null,
        )
    }


    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = get(data).text
        val soup = Jsoup.parse(response)

        data class Quality(
            @JsonProperty("src") val src: String,
            @JsonProperty("size") val size: Int
        )

        val sources = ArrayList<ExtractorLink>()
        for (source in soup.select("""[aria-labelledby="mirror-dropdown"] > li > a.dropdown-item""")) {
            val release = source.text().replace("/", "").trim()
            val sourceHTML = get(
                "https://tenshi.moe/embed?v=${source.attr("href").split("v=")[1].split("&")[0]}",
                headers = mapOf("Referer" to data)
            ).text

            val match = Regex("""sources: (\[(?:.|\s)+?type: ['"]video/.*?['"](?:.|\s)+?])""").find(sourceHTML)
            if (match != null) {
                val qualities = mapper.readValue<List<Quality>>(
                    match.destructured.component1()
                        .replace("'", "\"")
                        .replace(Regex("""(\w+): """), "\"\$1\": ")
                        .replace(Regex("""\s+"""), "")
                        .replace(",}", "}")
                        .replace(",]", "]")
                )
                sources.addAll(qualities.map {
                    ExtractorLink(
                        this.name,
                        "${this.name} $release - " + it.size + "p",
                        fixUrl(it.src),
                        this.mainUrl,
                        getQualityFromName("${it.size}")
                    )
                })
            }
        }

        for (source in sources) {
            callback.invoke(source)
        }
        return true
    }
}
