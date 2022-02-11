package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.setDuration
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.network.getRequestCreator
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import kotlin.system.measureTimeMillis

class SflixProvider(providerUrl: String, providerName: String) : MainAPI() {
    override val mainUrl = providerUrl
    override val name = providerName

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.None

    override suspend fun getMainPage(): HomePageResponse {
        val html = app.get("$mainUrl/home").text
        val document = Jsoup.parse(html)

        val all = ArrayList<HomePageList>()

        val map = mapOf(
            "Trending Movies" to "div#trending-movies",
            "Trending TV Shows" to "div#trending-tv",
        )
        map.forEach {
            all.add(HomePageList(
                it.key,
                document.select(it.value).select("div.film-poster").map { element ->
                    element.toSearchResult()
                }
            ))
        }

        document.select("section.block_area.block_area_home.section-id-02").forEach {
            val title = it.select("h2.cat-heading").text().trim()
            val elements = it.select("div.film-poster").map { element ->
                element.toSearchResult()
            }
            all.add(HomePageList(title, elements))
        }

        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "-")}"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("div.flw-item").map {
            val title = it.select("h2.film-name").text()
            val href = fixUrl(it.select("a").attr("href"))
            val year = it.select("span.fdi-item").text().toIntOrNull()
            val image = it.select("img").attr("data-src")
            val isMovie = href.contains("/movie/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    year,
                    null
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val details = document.select("div.detail_page-watch")
        val img = details?.select("img.film-poster-img")
        val posterUrl = img?.attr("src")
        val title = img?.attr("title") ?: throw ErrorLoadingException("No Title")

        /*
        val year = Regex("""[Rr]eleased:\s*(\d{4})""").find(
            document.select("div.elements").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val duration = Regex("""[Dd]uration:\s*(\d*)""").find(
            document.select("div.elements").text()
        )?.groupValues?.get(1)?.trim()?.plus(" min")*/
        var duration = document.selectFirst(".fs-item > .duration")?.text()?.trim()
        var year: Int? = null
        var tags: List<String>? = null
        var cast: List<String>? = null
        document.select("div.elements > .row > div > .row-line")?.forEach { element ->
            val type = element?.select(".type")?.text() ?: return@forEach
            when {
                type.contains("Released") -> {
                    year = Regex("\\d+").find(
                        element.ownText() ?: return@forEach
                    )?.groupValues?.firstOrNull()?.toIntOrNull()
                }
                type.contains("Genre") -> {
                    tags = element.select("a")?.mapNotNull { it.text() }
                }
                type.contains("Cast") -> {
                    cast = element.select("a")?.mapNotNull { it.text() }
                }
                type.contains("Duration") -> {
                    duration = duration ?: element.ownText()?.trim()
                }
            }
        }
        val plot = details.select("div.description")?.text()?.replace("Overview:", "")?.trim()

        val isMovie = url.contains("/movie/")

        // https://sflix.to/movie/free-never-say-never-again-hd-18317 -> 18317
        val idRegex = Regex(""".*-(\d+)""")
        val dataId = details.attr("data-id")
        val id = if (dataId.isNullOrEmpty())
            idRegex.find(url)?.groupValues?.get(1)
                ?: throw RuntimeException("Unable to get id from '$url'")
        else dataId

        val recommendations =
            document.select("div.film_list-wrap > div.flw-item")?.mapNotNull { element ->
                val titleHeader =
                    element.select("div.film-detail > .film-name > a") ?: return@mapNotNull null
                val recUrl = fixUrlNull(titleHeader.attr("href")) ?: return@mapNotNull null
                val recTitle = titleHeader.text() ?: return@mapNotNull null
                val poster = element.select("div.film-poster > img")?.attr("data-src")
                MovieSearchResponse(
                    recTitle,
                    recUrl,
                    this.name,
                    if (recUrl.contains("/movie/")) TvType.Movie else TvType.TvSeries,
                    poster,
                    year = null
                )
            }

        if (isMovie) {
            // Movies
            val episodesUrl = "$mainUrl/ajax/movie/episodes/$id"
            val episodes = app.get(episodesUrl).text

            // Supported streams, they're identical
            val sourceIds = Jsoup.parse(episodes).select("a").mapNotNull { element ->
                var sourceId = element.attr("data-id")
                if (sourceId.isNullOrEmpty())
                    sourceId = element.attr("data-linkid")

                if (element.select("span")?.text()?.trim()?.isValidServer() == true) {
                    if (sourceId.isNullOrEmpty()) {
                        fixUrlNull(element.attr("href"))
                    } else {
                        "$url.$sourceId".replace("/movie/", "/watch-movie/")
                    }
                } else {
                    null
                }
            }

            return newMovieLoadResponse(title, url, TvType.Movie, sourceIds.toJson()) {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                setDuration(duration)
                addActors(cast)
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            val seasonsDocument = app.get("$mainUrl/ajax/v2/tv/seasons/$id").document
            val episodes = arrayListOf<TvSeriesEpisode>()
            var seasonItems = seasonsDocument.select("div.dropdown-menu.dropdown-menu-model > a")
            if (seasonItems.isNullOrEmpty())
                seasonItems = seasonsDocument.select("div.dropdown-menu > a.dropdown-item")
            seasonItems?.forEachIndexed { season, element ->
                val seasonId = element.attr("data-id")
                if (seasonId.isNullOrBlank()) return@forEachIndexed

                var episode = 0
                val seasonEpisodes = app.get("$mainUrl/ajax/v2/season/episodes/$seasonId").document
                var seasonEpisodesItems =
                    seasonEpisodes.select("div.flw-item.film_single-item.episode-item.eps-item")
                if (seasonEpisodesItems.isNullOrEmpty()) {
                    seasonEpisodesItems =
                        seasonEpisodes.select("ul > li > a")
                }
                seasonEpisodesItems.forEach {
                    val episodeImg = it?.select("img")
                    val episodeTitle = episodeImg?.attr("title") ?: it.ownText()
                    val episodePosterUrl = episodeImg?.attr("src")
                    val episodeData = it.attr("data-id") ?: return@forEach

                    episode++

                    val episodeNum =
                        (it.select("div.episode-number")?.text()
                            ?: episodeTitle).let { str ->
                            Regex("""\d+""").find(str)?.groupValues?.firstOrNull()
                                ?.toIntOrNull()
                        } ?: episode

                    episodes.add(
                        TvSeriesEpisode(
                            episodeTitle?.removePrefix("Episode $episodeNum: "),
                            season + 1,
                            episodeNum,
                            Pair(url, episodeData).toJson(),
                            fixUrlNull(episodePosterUrl)
                        )
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                setDuration(duration)
                addActors(cast)
                this.tags = tags
                this.recommendations = recommendations
            }
        }
    }

    data class Tracks(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("kind") val kind: String?
    )

    data class Sources(
        @JsonProperty("file") val file: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

    data class SourceObject(
        @JsonProperty("sources") val sources: List<Sources?>?,
        @JsonProperty("sources_1") val sources1: List<Sources?>?,
        @JsonProperty("sources_2") val sources2: List<Sources?>?,
        @JsonProperty("sourcesBackup") val sourcesBackup: List<Sources?>?,
        @JsonProperty("tracks") val tracks: List<Tracks?>?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = (tryParseJson<Pair<String, String>>(data)?.let { (prefix, server) ->
            val episodesUrl = "$mainUrl/ajax/v2/episode/servers/$server"

            // Supported streams, they're identical
            app.get(episodesUrl).document.select("a").mapNotNull { element ->
                val id = element?.attr("data-id") ?: return@mapNotNull null
                if (element.select("span")?.text()?.trim()?.isValidServer() == true) {
                    "$prefix.$id".replace("/tv/", "/watch-tv/")
                } else {
                    null
                }
            }
        } ?: tryParseJson<List<String>>(data))?.distinct()

        urls?.apmap { url ->
            suspendSafeApiCall {
                val resolved = WebViewResolver(
                    Regex("""/getSources"""),
                    // This is unreliable, generating my own link instead
//                  additionalUrls = listOf(Regex("""^.*transport=polling(?!.*sid=).*$"""))
                ).resolveUsingWebView(getRequestCreator(url))
//              val extractorData = resolved.second.getOrNull(0)?.url?.toString()

                // Some smarter ws11 or w10 selection might be required in the future.
                val extractorData =
                    "https://ws10.rabbitstream.net/socket.io/?EIO=4&transport=polling"

                val sources = resolved.first?.let { app.baseClient.newCall(it).execute().text }
                    ?: return@suspendSafeApiCall

                val mapped = parseJson<SourceObject>(sources)

                mapped.tracks?.forEach {
                    it?.toSubtitleFile()?.let { subtitleFile ->
                        subtitleCallback.invoke(subtitleFile)
                    }
                }

                listOf(
                    mapped.sources to "",
                    mapped.sources1 to "source 2",
                    mapped.sources2 to "source 3",
                    mapped.sourcesBackup to "source backup"
                ).forEach { (sources, sourceName) ->
                    sources?.forEach {
                        it?.toExtractorLink(this, sourceName, extractorData)?.forEach(callback)
                    }
                }
            }
        }

        return !urls.isNullOrEmpty()
    }

    data class PollingData(
        @JsonProperty("sid") val sid: String? = null,
        @JsonProperty("upgrades") val upgrades: ArrayList<String> = arrayListOf(),
        @JsonProperty("pingInterval") val pingInterval: Int? = null,
        @JsonProperty("pingTimeout") val pingTimeout: Int? = null
    )

    /*
    # python code to figure out the time offset based on code if necessary
    chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
    code = "Nxa_-bM"
    total = 0
    for i, char in enumerate(code[::-1]):
        index = chars.index(char)
        value = index * 64**i
        total += value
    print(f"total {total}")
    */
    private fun generateTimeStamp(): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
        var code = ""
        var time = unixTimeMS
        while (time > 0) {
            code += chars[(time % (chars.length)).toInt()]
            time /= chars.length
        }
        return code.reversed()
    }

    override suspend fun extractorVerifierJob(extractorData: String?) {
        if (extractorData == null) return

        val jsonText =
            app.get("$extractorData&t=${generateTimeStamp()}").text.replaceBefore("{", "")
        val data = parseJson<PollingData>(jsonText)
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "https://rabbitstream.net/"
        )

        // 40 is hardcoded, dunno how it's generated, but it seems to work everywhere.
        // This request is obligatory
        app.post(
            "$extractorData&t=${generateTimeStamp()}&sid=${data.sid}",
            data = 40, headers = headers
        )//.also { println("First post ${it.text}") }
        // This makes the second get request work, and re-connect work.
        val reconnectSid =
            parseJson<PollingData>(
                app.get(
                    "$extractorData&t=${generateTimeStamp()}&sid=${data.sid}",
                    headers = headers
                )
                    //.also { println("First get ${it.text}") }
                    .text.replaceBefore("{", "")
            ).sid
        // This response is used in the post requests. Same contents in all it seems.
        val authInt =
            app.get(
                "$extractorData&t=${generateTimeStamp()}&sid=${data.sid}",
                timeout = 60,
                headers = headers
            ).text
                //.also { println("Second get ${it}") }
                // Dunno if it's actually generated like this, just guessing.
                .toIntOrNull()?.plus(1) ?: 3

        // Prevents them from fucking us over with doing a while(true){} loop
        val interval = maxOf(data.pingInterval?.toLong()?.plus(2000) ?: return, 10000L)
        var reconnect = false
        // New SID can be negotiated as above, but not implemented yet as it seems rare.
        while (true) {
            val authData = if (reconnect) """
                42["_reconnect", "$reconnectSid"]
            """.trimIndent() else authInt

            val url = "${extractorData}&t=${generateTimeStamp()}&sid=${data.sid}"
            app.post(url, data = authData, headers = headers)
            //.also { println("Sflix post job ${it.text}") }
            Log.d(this.name, "Running Sflix job $url")

            val time = measureTimeMillis {
                // This acts as a timeout
                val getResponse = app.get(
                    "${extractorData}&t=${generateTimeStamp()}&sid=${data.sid}",
                    timeout = 60,
                    headers = headers
                ).text //.also { println("Sflix get job $it") }
                if (getResponse.contains("sid")) {
                    reconnect = true
//                println("Reconnecting")
                }
            }
            // Always waits even if the get response is instant, to prevent a while true loop.
            if (time < interval - 4000)
                delay(interval)
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val img = this.select("img")
        val title = img.attr("title")
        val posterUrl = img.attr("data-src")
        val href = fixUrl(this.select("a").attr("href"))
        val isMovie = href.contains("/movie/")
        return if (isMovie) {
            MovieSearchResponse(
                title,
                href,
                this@SflixProvider.name,
                TvType.Movie,
                posterUrl,
                null
            )
        } else {
            TvSeriesSearchResponse(
                title,
                href,
                this@SflixProvider.name,
                TvType.Movie,
                posterUrl,
                null,
                null
            )
        }
    }

    companion object {
        fun String?.isValidServer(): Boolean {
            if (this.isNullOrEmpty()) return false
            if (this.equals("UpCloud", ignoreCase = true) || this.equals(
                    "Vidcloud",
                    ignoreCase = true
                ) || this.equals("RapidStream", ignoreCase = true)
            ) return true
            return false
        }

        // For re-use in Zoro
        fun Sources.toExtractorLink(
            caller: MainAPI,
            name: String,
            extractorData: String? = null
        ): List<ExtractorLink>? {
            return this.file?.let { file ->
                //println("FILE::: $file")
                val isM3u8 = URI(this.file).path.endsWith(".m3u8") || this.type.equals(
                    "hls",
                    ignoreCase = true
                )
                if (isM3u8) {
                    M3u8Helper().m3u8Generation(M3u8Helper.M3u8Stream(this.file, null), true)
                        .map { stream ->
                            //println("stream: ${stream.quality} at ${stream.streamUrl}")
                            val qualityString = if ((stream.quality ?: 0) == 0) label
                                ?: "" else "${stream.quality}p"
                            ExtractorLink(
                                caller.name,
                                "${caller.name} $qualityString $name",
                                stream.streamUrl,
                                caller.mainUrl,
                                getQualityFromName(stream.quality.toString()),
                                true,
                                extractorData = extractorData
                            )
                        }
                } else {
                    listOf(ExtractorLink(
                        caller.name,
                        this.label?.let { "${caller.name} - $it" } ?: caller.name,
                        file,
                        caller.mainUrl,
                        getQualityFromName(this.type ?: ""),
                        false,
                        extractorData = extractorData
                    ))
                }
            }
        }

        fun Tracks.toSubtitleFile(): SubtitleFile? {
            return this.file?.let {
                SubtitleFile(
                    this.label ?: "Unknown",
                    it
                )
            }
        }
    }
}

