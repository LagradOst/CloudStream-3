package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import java.util.ArrayList

class NontonAnimeIDProvider : MainAPI() {
    override var mainUrl = "https://75.119.159.228"
    override var name = "NontonAnimeID"
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
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("section#postbaru").forEach { block ->
            val header = block.selectFirst("h2")!!.text().trim()
            val animes = block.select("article.animeseries").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        document.select("aside#sidebar_right > div.side:nth-child(2)").forEach { block ->
            val header = block.selectFirst("h3")!!.ownText().trim()
            val animes = block.select("li.fullwdth").mapNotNull {
                it.toSearchResultPopular()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            val name = Regex("$mainUrl/(.*)-episode.*").find(uri)?.groupValues?.get(1).toString()
            if (name.contains("movie")) {
                return "$mainUrl/anime/" + name.replace("-movie", "")
            } else {
                "$mainUrl/anime/$name"
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")!!.attr("href")))
        val title = this.selectFirst("h3.title")!!.text()
        val posterUrl = fixUrl(this.select("img").attr("data-src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true)
        }

    }

    private fun Element.toSearchResultPopular(): SearchResponse {
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")!!.attr("href")))
        val title = this.select("h4").text().trim()
        val posterUrl = fixUrl(this.select("img").attr("data-src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select(".result > ul > li").mapNotNull {
            val title = it.selectFirst("h2")!!.text().trim()
            val poster = it.selectFirst("img")!!.attr("src")
            val tvType = getType(
                it.selectFirst(".boxinfores > span.typeseries")!!.text().toString()
            )
            val href = fixUrl(it.selectFirst("a")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title.cs")!!.text().trim()
        val poster = document.selectFirst(".poster > img")?.attr("data-src")
        val tags = document.select(".tagline > a").map { it.text() }

        val year = Regex("\\d, ([0-9]*)").find(
            document.select(".bottomtitle > span:nth-child(5)").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(
            document.select("span.statusseries").text().trim()
        )
        val type = getType(document.select("span.typeseries").text().trim())
        val rating = document.select("span.nilaiseries").text().trim().toIntOrNull()
        val description = document.select(".entry-content.seriesdesc > p").text().trim()
        val trailer = document.select("a.ytp-impression-link").attr("href")

        val episodes =
            document.select("ul.misha_posts_wrap2 > li").map {
                val name = it.select("a").text().trim()
                val link = it.select("a").attr("href")
                Episode(link, name)
            }.reversed()

        val recommendations = document.select(".result > li").mapNotNull {
            val epHref = it.selectFirst("a")!!.attr("href")
            val epTitle = it.selectFirst("h3")!!.text()
            val epPoster = it.select(".top > img").attr("data-src")

            newAnimeSearchResponse(epTitle, epHref, TvType.Anime) {
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
            this.rating = rating
            plot = description
            this.trailers = listOf(trailer)
            this.tags = tags
            this.recommendations = recommendations
        }

    }

    private suspend fun invokeKotakSource(
        source: String,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(source).document

        doc.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val data = getAndUnpack(script.data())
                val server = data.substringAfter("sources:[").substringBefore("]")
                tryParseJson<List<KotakSource>>("[$server]")?.map {
                    sourceCallback.invoke(
                        ExtractorLink(
                            name,
                            "AnimeId",
                            it.file,
                            referer = "https://kotakanimeid.com/",
                            quality = getQualityFromName(it.label)
                        )
                    )
                }
            }
        }
    }

    data class KotakSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("label") val label: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val sources = ArrayList<String>()

        document.select(".container1 > ul > li").apmap {
            val dataPost = it.attr("data-post")
            val dataNume = it.attr("data-nume")
            val dataType = it.attr("data-type")

            val iframe = app.post(
                url = "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "player_ajax",
                    "post" to dataPost,
                    "nume" to dataNume,
                    "type" to dataType
                )
            ).document.select("iframe").attr("src")

            sources.add(fixUrl(iframe))
        }

        sources.map {
            it.replace("https://ok.ru", "http://ok.ru")
        }.apmap {
                when {
                    it.contains("blogger.com") -> invokeBloggerSource(it, this.name, callback)
                    it.contains("kotakanimeid.com") -> invokeKotakSource(it, callback)
                    else -> loadExtractor(it, data, callback)
                }
        }

        return true
    }
}

// re-use as extractorApis
suspend fun invokeBloggerSource(
    url: String,
    name: String,
    sourceCallback: (ExtractorLink) -> Unit
) {
    val doc = app.get(url).document

    val server =
        doc.selectFirst("script")?.data()!!.substringAfter("\"streams\":[").substringBefore("]")
    tryParseJson<List<BloggerSource>>("[$server]")?.map {
        sourceCallback.invoke(
            ExtractorLink(
                name,
                "Blogger",
                it.play_url,
                referer = "https://www.youtube.com/",
                quality = when (it.format_id) {
                    18 -> 360
                    22 -> 720
                    else -> Qualities.Unknown.value
                }
            )
        )
    }
}

data class BloggerSource(
    @JsonProperty("play_url") val play_url: String,
    @JsonProperty("format_id") val format_id: Int
)