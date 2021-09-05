package com.ArjixWasTaken.cloudstream3.animeproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ArjixWasTaken.cloudstream3.*
import com.ArjixWasTaken.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.util.*

private fun String.toAscii() = this.map { it.toInt() }.joinToString()

class CrunchyrollGeoBypasser {
    companion object {
        const val BYPASS_SERVER = "https://cr-unblocker.us.to/start_session"
        val headers = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate",
            "Connection" to "keep-alive",
            "Referer" to "https://google.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36".toAscii()
        )
        var sessionId: String? = null
        val session = HttpSession()
    }

    private fun getSessionId(): Boolean {
        return try {
            sessionId = khttp.get(BYPASS_SERVER, params=mapOf("version" to "1.1")).jsonObject.getJSONObject("data").getString("session_id")
            true
        } catch (e: Exception) {
            sessionId = null
            false
        }
    }

    private fun autoLoadSession(): Boolean {
        if (sessionId != null) return true
        getSessionId()
        return autoLoadSession()
    }

    fun geoBypassRequest(url: String): khttp.responses.Response {
        autoLoadSession()
        return session.get(url, headers=headers, cookies=mapOf("session_id" to sessionId!!))
    }
}


class Crunchyroll : MainAPI() {
    companion object {
        val crUnblock = CrunchyrollGeoBypasser()
        val episodeNumRegex = Regex("""Episode (\d+)""")
        val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()
    }

    override val mainUrl: String
        get() = "http://www.crunchyroll.com"
    override val name: String
        get() = "Crunchyroll"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = true

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.AnimeMovie,
            TvType.Anime,
            TvType.ONA
        )

    override fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/videos/anime/popular/ajax_page?pg=1", "Popular 1"),
            Pair("$mainUrl/videos/anime/popular/ajax_page?pg=2", "Popular 2"),
            Pair("$mainUrl/videos/anime/popular/ajax_page?pg=3", "Popular 3"),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val response = crUnblock.geoBypassRequest(i.first)
                val soup = Jsoup.parse(response.text)

                val episodes = soup.select("li").map {

                    AnimeSearchResponse(
                        it.selectFirst("a").attr("title"),
                        "$mainUrl/${it.selectFirst("a").attr("href")}",
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

                items.add(HomePageList(i.second, episodes))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun getCloseMatches(sequence: String, items: Collection<String>): ArrayList<String> {
        val closeMatches = ArrayList<String>()
        val a = sequence.trim().toLowerCase()

        for (item in items) {
            val b = item.trim().toLowerCase()
            if (b.contains(a)) {
                closeMatches.add(item)
            } else if (a.contains(b)) {
                closeMatches.add(item)
            }
        }
        return closeMatches
    }

    private data class CrunchyAnimeData (
        @JsonProperty("name") val name : String,
        @JsonProperty("img") var img : String,
        @JsonProperty("link") var link : String
    )
    private data class CrunchyJson (
        @JsonProperty("data") val data : List<CrunchyAnimeData>,
    )


    override fun search(query: String): ArrayList<SearchResponse> {
        val json = crUnblock.geoBypassRequest("http://www.crunchyroll.com/ajax/?req=RpcApiSearch_GetSearchCandidates").text.split("*/")[0].replace("\\/", "/")
        val data = mapper.readValue<CrunchyJson>(json.split("\n").mapNotNull { if (!it.startsWith("/")) it else null }.joinToString("\n")).data

        val results = getCloseMatches(query, data.map { it.name })
        if (results.isEmpty()) return ArrayList()
        val searchResutls = ArrayList<SearchResponse>()

        var count = 0
        for (anime in data) {
            if (count == results.size) {
                break
            }
            if (anime.name == results[count]) {
                anime.link = "$mainUrl${anime.link}"
                anime.img = anime.img.replace("small", "full")
                searchResutls.add(AnimeSearchResponse(
                    anime.name,
                    anime.link,
                    this.name,
                    TvType.Anime,
                    anime.img,
                    null,
                    null,
                    EnumSet.of(DubStatus.Subbed),
                    null,
                    null
                ))
                ++count
            }
        }

        return searchResutls
    }

    override fun load(url: String): LoadResponse {
        val soup = Jsoup.parse(crUnblock.geoBypassRequest(url).text)
        val title = soup.selectFirst("#showview-content-header .ellipsis")?.text()?.trim()
        val poster = soup.selectFirst(".poster")?.attr("src")

        val p = soup.selectFirst(".description")

        val description = if (p.selectFirst(".more") != null && !p.selectFirst(".more")?.text()?.trim().isNullOrEmpty()) {
            p.selectFirst(".more").text().trim()
        } else {
            p.selectFirst("span").text().trim()
        }

        val genres = soup.select(".large-margin-bottom > ul:nth-child(2) li:nth-child(2) a").map { it.text() }
        val year = genres.filter { it.toIntOrNull() != null }.map { it.toInt() }.sortedBy { it }.getOrNull(0)

        val episodes = soup.select(".season").mapNotNull {
            val seasonName = it.selectFirst("a.season-dropdown")?.text()?.trim()
            if (seasonName == null) {
                it
            } else if (seasonName.contains("Dub") || seasonName.contains("Russian") || seasonName.contains("(HD)")) {
                null
            } else {
                it
            }
        }.map {
            it.select(".episode").map { episode ->
                val epTitle = episode.selectFirst(".short-desc")?.text()
                var epNum = episodeNumRegex.find(episode.selectFirst("span.ellipsis")?.text().toString())?.destructured?.component1()
                if (epNum == null) epNum = ""
                else epNum = "Episode $epNum - "
                AnimeEpisode(
                    mainUrl + episode.attr("href"),
                    "$epTitle",
                    it.selectFirst("img")?.attr("data-thumbnailurl")?.replace("wide", "full"),
                    null,
                    null,
                    epNum + it.selectFirst("a.season-dropdown")?.text()?.trim() ?: "",
                )
            }
        }.flatten().reversed()

        return AnimeLoadResponse(
            title,
            null,
            title.toString(),
            url,
            this.name,
            TvType.Anime,
            poster,
            year,
            null,
            episodes,
            null,
            description,
            genres
        )
    }

    data class Subtitles (
        @JsonProperty("language") val language : String,
        @JsonProperty("url") val url : String,
        @JsonProperty("title") val  title : String?,
        @JsonProperty("format") val format : String?
    )

    data class Streams (
        @JsonProperty("format") val format : String?,
        @JsonProperty("audio_lang") val audioLang : String?,
        @JsonProperty("hardsub_lang") val hardsubLang : String?,
        @JsonProperty("url") val url : String,
        @JsonProperty("resolution") val resolution : String?,
        @JsonProperty("title") var title : String?
    )

    data class CrunchyrollVideo (
        @JsonProperty("streams") val streams : List<Streams>,
        @JsonProperty("subtitles") val subtitles : List<Subtitles>,
    )

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val contentRegex = Regex("""vilos\.config\.media = (\{.+\})""")
        val response = crUnblock.geoBypassRequest(data)

        val hlsHelper = M3u8Helper()

        val dat = contentRegex.find(response.text)?.destructured?.component1()

        if (!dat.isNullOrEmpty()) {
            val json = mapper.readValue<CrunchyrollVideo>(dat)
            val streams = ArrayList<Streams>()

            for (stream in json.streams) {
                if (
                    listOf(
                        "adaptive_hls", "adaptive_dash",
                        "multitrack_adaptive_hls_v2",
                        "vo_adaptive_dash", "vo_adaptive_hls"
                    ).contains(stream.format)
                ) {
                    if (stream.audioLang == "jaJP" && (listOf(null, "enUS").contains(stream.hardsubLang)) && listOf("m3u", "m3u8").contains(hlsHelper.absoluteExtensionDetermination(stream.url))) {
                        stream.title = if (stream.hardsubLang == "enUS") "Hardsub" else "Raw"
                        streams.add(stream)
                    }
                }
            }

            streams.forEach { stream ->
                hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(stream.url, null)).forEach {
                    callback(
                        ExtractorLink(
                            "Crunchyroll",
                            "Crunchy - ${stream.title} - ${it.quality}",
                            it.streamUrl,
                            "",
                            getQualityFromName(it.quality.toString()),
                            true
                        )
                    )
                }
            }

            json.subtitles.forEach {
                subtitleCallback(
                    SubtitleFile(it.language, it.url)
                )
            }

            return true
        }
        return false
    }
}
