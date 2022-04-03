package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList

private fun String.toAscii() = this.map { it.toInt() }.joinToString()


class KrunchyGeoBypasser {
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

    data class KrunchySession (
        @JsonProperty("data") var data :DataInfo? = DataInfo(),
        @JsonProperty("error") var error : Boolean? = null,
        @JsonProperty("code") var code  : String?  = null
    )
    data class DataInfo (
        @JsonProperty("session_id") var sessionId : String? = null,
        @JsonProperty("country_code") var countryCode  : String?  = null,
    )

    private suspend fun getSessionId(): Boolean {
        return try {
            val response = app.get(BYPASS_SERVER, params=mapOf("version" to "1.1")).text
            val json = parseJson<KrunchySession>(response)
            sessionId = json.data?.sessionId
            true
        } catch (e: Exception) {
            sessionId = null
             false
        }
    }

    private suspend fun autoLoadSession(): Boolean {
        if (sessionId != null) return true
        getSessionId()
        return autoLoadSession()
    }

    suspend fun geoBypassRequest(url: String): khttp.responses.Response {
        autoLoadSession()
        return session.get(url, headers=headers, cookies=mapOf("session_id" to sessionId!!))
    }
}

class KrunchyProvider : MainAPI() {
    companion object {
        val crUnblock = KrunchyGeoBypasser()
        val episodeNumRegex = Regex("""Episode (\d+)""")
        val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()
    }

    override var mainUrl = "http://www.crunchyroll.com"
    override var name: String = "Crunchyroll"
    override val lang = "en"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = true

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.AnimeMovie,
            TvType.Anime,
            TvType.OVA
        )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/videos/anime/popular/ajax_page?pg=1", "Popular 1"),
            Pair("$mainUrl/videos/anime/popular/ajax_page?pg=2", "Popular 2"),
            Pair("$mainUrl/videos/anime/popular/ajax_page?pg=3", "Popular 3"),
            Pair("$mainUrl/videos/anime/simulcasts/ajax_page", "Simulcasts"),
        )
        val doc = Jsoup.parse(crUnblock.geoBypassRequest(mainUrl).text)
        val items = ArrayList<HomePageList>()
        val featured = doc.select(".js-featured-show-list > li").map { anime ->
            AnimeSearchResponse(
                anime.selectFirst("img").attr("alt"),
                fixUrl(anime.selectFirst("a").attr("href")),
                this.name,
                TvType.Anime,
                anime.selectFirst("img").attr("src").replace("small", "full"),
                null,
                EnumSet.of(DubStatus.Subbed),
                null,
                null
            )
        }
        val recent = doc.select("div.welcome-countdown-day:contains(Now Showing) li")?.mapNotNull {
            val link = fixUrl(it.selectFirst("a").attr("href"))
            val name = it.selectFirst("span.welcome-countdown-name").text()
            val img = it.selectFirst("img").attr("src").replace("medium","full")
            val dubstat = if (name.contains("Dub)",true)) EnumSet.of(DubStatus.Dubbed) else
                EnumSet.of(DubStatus.Subbed)
            val details = it.selectFirst("span.welcome-countdown-details").text()
            val epnum = episodeNumRegex.find(details)?.value?.replace("Episode ","") ?: ""
            AnimeSearchResponse(
                "★ $name ★",
                link.replace(Regex("(\\/episode.*)"),""),
                this.name,
                TvType.Anime,
                fixUrl(img),
                null,
                dubstat,
                subEpisodes = epnum.toIntOrNull(),
                dubEpisodes = epnum.toIntOrNull()
            )
        }
        if (recent!!.isNotEmpty()) {
            items.add(HomePageList("Now Showing", recent))
        }
        items.add(HomePageList("Featured", featured))
        urls.apmap { (url, name) ->
            val response = crUnblock.geoBypassRequest(url)
            val soup = Jsoup.parse(response.text)

            val episodes = soup.select("li").map {

                AnimeSearchResponse(
                    it.selectFirst("a").attr("title"),
                    fixUrl(it.selectFirst("a").attr("href")),
                    this.name,
                    TvType.Anime,
                    it.selectFirst("img").attr("src"),
                    null,
                    EnumSet.of(DubStatus.Subbed),
                    null,
                    null
                )
            }
            items.add(HomePageList(name, episodes))
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun getCloseMatches(sequence: String, items: Collection<String>): ArrayList<String> {
        val closeMatches = ArrayList<String>()
        val a = sequence.trim().lowercase()

        for (item in items) {
            val b = item.trim().lowercase()
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


    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val json = crUnblock.geoBypassRequest("http://www.crunchyroll.com/ajax/?req=RpcApiSearch_GetSearchCandidates").text.split("*/")[0].replace("\\/", "/")
        val data = parseJson<CrunchyJson>(json.split("\n").mapNotNull { if (!it.startsWith("/")) it else null }.joinToString("\n")).data

        val results = getCloseMatches(query, data.map { it.name })
        if (results.isEmpty()) return ArrayList()
        val searchResutls = ArrayList<SearchResponse>()

        var count = 0
        for (anime in data) {
            if (count == results.size) {
                break
            }
            if (anime.name == results[count]) {
                val dubstat = if (anime.name.contains("Dub)",true)) EnumSet.of(DubStatus.Dubbed) else
                    EnumSet.of(DubStatus.Subbed)
                anime.link = fixUrl(anime.link)
                anime.img = anime.img.replace("small", "full")
                searchResutls.add(AnimeSearchResponse(
                    anime.name,
                    anime.link,
                    this.name,
                    TvType.Anime,
                    anime.img,
                    null,
                    dubstat,
                    null,
                    null
                ))
                ++count
            }
        }

        return searchResutls
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = Jsoup.parse(crUnblock.geoBypassRequest(url).text)
        val title = soup.selectFirst("#showview-content-header .ellipsis")?.text()?.trim()
        val posterU = soup.selectFirst(".poster")?.attr("src")

        val p = soup.selectFirst(".description")
        val description = if (p.selectFirst(".more") != null && !p.selectFirst(".more")?.text()?.trim().isNullOrEmpty()) {
            p.selectFirst(".more").text().trim()
        } else {
            p.selectFirst("span").text().trim()
        }
        val rating = soup.selectFirst(".average-rating div meta").attr("content").toFloatOrNull()
            ?.times(1000)?.toInt()?.times(2)

        val genres = soup.select(".large-margin-bottom > ul:nth-child(2) li:nth-child(2) a").map { it.text() }
        val year = genres.filter { it.toIntOrNull() != null }.map { it.toInt() }.sortedBy { it }.getOrNull(0)
        val subEpisodes = ArrayList<AnimeEpisode>()
        val dubEpisodes = ArrayList<AnimeEpisode>()
        val premiumSubEpisodes = ArrayList<AnimeEpisode>()
        val premiumDubEpisodes = ArrayList<AnimeEpisode>()
        soup.select(".season").forEach {
            val seasonName = it.selectFirst("a.season-dropdown")?.text()?.trim()
            it.select(".episode").forEach { ep ->
                val epTitle = ep.selectFirst(".short-desc")?.text()

                val epNum = episodeNumRegex.find(ep.selectFirst("span.ellipsis")?.text().toString())?.destructured?.component1()
                var poster = ep.selectFirst("img.landscape")?.attr("data-thumbnailurl")
                val poster2 = ep.selectFirst("img")?.attr("src")
                if (poster.isNullOrBlank()) { poster = poster2}

                var epDesc = (if (epNum == null) "" else "Episode $epNum") + (if (!seasonName.isNullOrEmpty()) " - $seasonName" else "")
                val isPremium = poster?.contains("widestar", ignoreCase = true) == true
                if (isPremium) {
                    epDesc = "★ $epDesc ★"
                }

                val epi = AnimeEpisode(
                    fixUrl(ep.attr("href")),
                    "$epTitle",
                    poster?.replace("widestar","full")?.replace("wide","full"),
                    null,
                    null,
                    epDesc,
                    null
                )
                if (isPremium && seasonName != null && (seasonName.contains("Dub") || seasonName.contains("Russian") || seasonName.contains("Spanish"))) {
                    premiumDubEpisodes.add(epi)
                }
                 else if (isPremium) {
                     premiumSubEpisodes.add(epi)
                 }
                 else if (seasonName != null && (seasonName.contains("Dub"))) {
                     dubEpisodes.add(epi)
                 }
                 else {
                    subEpisodes.add(epi)
                }
            }
        }


        val recommendations =
            soup.select(".other-series > ul li")?.mapNotNull { element ->
                val recTitle = element.select("span.ellipsis[dir=auto]").text() ?: return@mapNotNull null
                val image = element.select("img")?.attr("src")
                val recUrl = fixUrl(element.select("a").attr("href"))
                AnimeSearchResponse(
                    recTitle,
                    fixUrl(recUrl),
                    this.name,
                    TvType.Anime,
                    fixUrl(image!!),
                    dubStatus =
                    if (recTitle.contains("(DUB)") || recTitle.contains("Dub")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }


        return newAnimeLoadResponse(title.toString(), url, TvType.Anime) {
            this.posterUrl = posterU
            this.engName = title
           if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes.reversed())
           if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes.reversed())
           if (premiumDubEpisodes.isNotEmpty()) addEpisodes(DubStatus.PremiumDub, premiumDubEpisodes.reversed())
           if (premiumSubEpisodes.isNotEmpty()) addEpisodes(DubStatus.PremiumSub, premiumSubEpisodes.reversed())
            this.plot = description
            this.tags = genres
            this.year = year
            this.recommendations = recommendations
            this.rating = rating
        }
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

    data class KrunchyVideo (
        @JsonProperty("streams") val streams : List<Streams>,
        @JsonProperty("subtitles") val subtitles : List<Subtitles>,
    )

    override suspend fun loadLinks(
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
            val json = parseJson<KrunchyVideo>(dat)
            val streams = ArrayList<Streams>()
            for (stream in json.streams) {
                if (
                    listOf(
                        "adaptive_hls", "adaptive_dash",
                        "multitrack_adaptive_hls_v2",
                        "vo_adaptive_dash", "vo_adaptive_hls",
                        "trailer_hls",
                    ).contains(stream.format)
                ) {
                    if (stream.format!!.contains("adaptive") && listOf("jaJP", "esLA", "esES", "enUS")
                            .contains(stream.audioLang) && (listOf("esLA", "esES", "enUS", null).contains(stream.hardsubLang))
                        && listOf("m3u", "m3u8").contains(hlsHelper.absoluteExtensionDetermination(stream.url)))
                    {
                        stream.title = if (stream.hardsubLang == "enUS" && stream.audioLang == "jaJP") "Hardsub (English)"
                        else if (stream.hardsubLang == "esLA" && stream.audioLang == "jaJP") "Hardsub (Latino)"
                        else if (stream.hardsubLang == "esES" && stream.audioLang == "jaJP") "Hardsub (Español España)"
                        else if (stream.audioLang == "esLA") "Latino"
                        else if (stream.audioLang == "esES") "Español España"
                        else if (stream.audioLang == "enUS") "English (US)"
                        else "RAW"
                        streams.add(stream)
                    }
                    //Premium eps
                    if (stream.format == "trailer_hls" && listOf("jaJP", "esLA", "esES", "enUS").contains(stream.audioLang) &&
                        (listOf("esLA", "esES", "enUS", null).contains(stream.hardsubLang))) {
                        stream.title =
                            if (stream.hardsubLang == "enUS" && stream.audioLang == "jaJP") "Hardsub (English)"
                            else if (stream.hardsubLang == "esLA" && stream.audioLang == "jaJP") "Hardsub (Latino)"
                            else if (stream.hardsubLang == "esES" && stream.audioLang == "jaJP") "Hardsub (Español España)"
                            else if (stream.audioLang == "esLA") "Latino"
                            else if (stream.audioLang == "esES") "Español España"
                            else if (stream.audioLang == "enUS") "English (US)"
                            else "RAW"
                        streams.add(stream)
                    }
                }
            }
            streams.apmap { stream ->
              if (stream.url.contains("m3u8") && stream.format!!.contains("adaptive") ) {
                  hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(stream.url, null), false).apmap {
                    callback(
                        ExtractorLink(
                            "Crunchyroll",
                            "Crunchy - ${stream.title} - ${it.quality}p",
                            it.streamUrl,
                            "",
                            getQualityFromName(it.quality.toString()),
                            true
                        )
                    )
                }
              } else if (stream.format == "trailer_hls") {
                  val premiumstream = stream.url
                      .replace("\\/", "/")
                      .replace(Regex("\\/clipFrom.*?index.m3u8"), "").replace("'_,'", "'_'")
                      .replace(stream.url.split("/")[2], "fy.v.vrv.co")
                  callback(
                  ExtractorLink(
                      "Crunchyroll",
                      "Crunchy - ${stream.title} ★",
                      premiumstream,
                      "",
                      Qualities.Unknown.value,
                      false
                  )
              )
              } else null
            }
            json.subtitles.apmap {
                val langclean = it.language.replace("esLA","Spanish")
                    .replace("enUS","English")
                    .replace("esES","Spanish (Spain)")
                subtitleCallback(
                    SubtitleFile(langclean, it.url)
                )
            }

            return true
        }
        return false
    }
}
