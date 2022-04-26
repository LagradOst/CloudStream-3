package com.lagradost.cloudstream3.animeproviders

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

//Credits https://github.com/ArjixWasTaken/CloudStream-3/blob/master/app/src/main/java/com/ArjixWasTaken/cloudstream3/animeproviders/HanimeProvider.kt

class Hanime : MainAPI() {
    companion object {
        @SuppressLint("SimpleDateFormat")
        fun unixToYear(timestamp: Int): Int? {
            val sdf = SimpleDateFormat("yyyy")
            val netDate = Date(timestamp * 1000L)
            val date = sdf.format(netDate)

            return date.toIntOrNull()
        }
        private fun isNumber(num: String) = if (num.toIntOrNull() == null) false else true

        private fun getTitle(title: String): String {
            if (title.contains(" Ep ")) {
                return title.split(" Ep ")[0].trim()
            } else {
                if (isNumber(title.trim().split(" ").last())) {
                    val split = title.trim().split(" ")
                    return split.slice(0..split.size-2).joinToString(" ").trim()
                } else {
                    return title.trim()
                }
            }
        }
    }

    override var mainUrl = "https://hanime.tv"
    override var name = "Hanime"
    override val hasQuickSearch: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasDownloadSupport = true
    override val supportedTypes: Set<TvType> get() = setOf(TvType.Hentai)

    private data class HpHentaiVideos (
        @JsonProperty("id") val id : Int,
        @JsonProperty("name") val name : String,
        @JsonProperty("slug") val slug : String,
        @JsonProperty("released_at_unix") val releasedAt : Int,
        @JsonProperty("poster_url") val posterUrl : String,
        @JsonProperty("cover_url") val coverUrl : String
    )
    private data class HpSections (
        @JsonProperty("title") val title : String,
        @JsonProperty("hentai_video_ids") val hentaiVideoIds : List<Int>
    )
    private data class HpLanding (
        @JsonProperty("sections") val sections : List<HpSections>,
        @JsonProperty("hentai_videos") val hentaiVideos : List<HpHentaiVideos>
    )
    private data class HpData (
        @JsonProperty("landing") val landing : HpLanding
    )
    private data class HpState (
        @JsonProperty("data") val data : HpData
    )
    private data class HpHanimeHomePage (
        @JsonProperty("state") val state : HpState
    )

    private fun getHentaiByIdFromList(id: Int, list: List<HpHentaiVideos>): HpHentaiVideos? {
        for (item in list) {
            if (item.id == id) {
                return item
            }
        }
        return null
    }

    override suspend fun getMainPage(): HomePageResponse {


        val data = app.get("https://hanime.tv/").text
        val jsonText = Regex("""window\.__NUXT__=(.*?);</script>""").find(data)!!.destructured.component1()
        val json = mapper.readValue<HpHanimeHomePage>(jsonText)
        val titles = ArrayList<String>()
        val items = ArrayList<HomePageList>()

        try {
            json.state.data.landing.sections.forEach { section ->
                items.add(HomePageList(section.title, (section.hentaiVideoIds.map {
                    val hentai = getHentaiByIdFromList(it, json.state.data.landing.hentaiVideos)!!
                    val title = getTitle(hentai.name)
                    if (!titles.contains(title)) {
                        titles.add(title)
                        AnimeSearchResponse(
                            title,
                            "https://hanime.tv/videos/hentai/${hentai.slug}?id=${hentai.id}&title=${title}",
                            this.name,
                            TvType.Hentai,
                            hentai.coverUrl,
                            null,
                            EnumSet.of(DubStatus.Subbed),
                            "",
                            null,
                            null
                        )
                    } else {
                        null
                    }
                }).filterNotNull()))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class HanimeSearchResult (
        @JsonProperty("id") val id : Int,
        @JsonProperty("name") val name : String,
        @JsonProperty("slug") val slug : String,
        @JsonProperty("titles") val titles : List<String>?,
        @JsonProperty("cover_url") val coverUrl : String,
        @JsonProperty("tags") val tags : List<String>,
        @JsonProperty("released_at") val releasedAt : Int
    )

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val link = "https://search.htv-services.com/"
        val data = mapOf("search_text" to query, "tags" to listOf<String>(), "tags_mode" to "AND", "brands" to listOf<String>(), "blacklist" to listOf<String>(), "order_by" to "created_at_unix", "ordering" to "desc", "page" to 0)
        val response = khttp.post(link, json=data).jsonObject.getString("hits").let { mapper.readValue<List<HanimeSearchResult>>(it) }
        val titles = ArrayList<String>()
        val searchResults = ArrayList<SearchResponse>()

        response.reversed().forEach {
            val title = getTitle(it.name)
            if (!titles.contains(title)) {
                titles.add(title)
                searchResults.add(
                    AnimeSearchResponse(
                        title,
                        "https://hanime.tv/videos/hentai/${it.slug}?id=${it.id}&title=${title}",
                        this.name,
                        TvType.Hentai,
                        it.coverUrl,
                        unixToYear(it.releasedAt),
                        EnumSet.of(DubStatus.Subbed),
                        it.titles?.get(0),
                        null,
                        null,
                        null
                    )
                )
            }
        }
        return searchResults
    }

    private data class HentaiTags (
        @JsonProperty("text") val text : String
    )

    private data class HentaiVideo (
        @JsonProperty("name") val name : String,
        @JsonProperty("description") val description : String,
        @JsonProperty("cover_url") val coverUrl : String,
        @JsonProperty("released_at_unix") val releasedAtUnix : Int,
        @JsonProperty("hentai_tags") val hentaiTags : List<HentaiTags>
    )

    private data class HentaiFranchiseHentaiVideos (
        @JsonProperty("id") val id : Int,
        @JsonProperty("name") val name : String,
        @JsonProperty("poster_url") val posterUrl : String,
        @JsonProperty("released_at_unix") val releasedAtUnix : Int
    )

    private data class Streams (
        @JsonProperty("height") val height : String,
        @JsonProperty("filesize_mbs") val filesizeMbs : Int,
        @JsonProperty("url") val url : String,
    )

    private data class Servers (
        @JsonProperty("name") val name : String,
        @JsonProperty("streams") val streams : List<Streams>
    )

    private data class VideosManifest (
        @JsonProperty("servers") val servers : List<Servers>
    )

    private data class HanimeEpisodeData (
        @JsonProperty("hentai_video") val hentaiVideo : HentaiVideo,
        @JsonProperty("hentai_tags") val hentaiTags : List<HentaiTags>,
        @JsonProperty("hentai_franchise_hentai_videos") val hentaiFranchiseHentaiVideos : List<HentaiFranchiseHentaiVideos>,
        @JsonProperty("videos_manifest") val videosManifest: VideosManifest,
    )

    override suspend fun load(url: String): LoadResponse {
        val params: List<Pair<String, String>> = url.split("?")[1].split("&").map {
            val split = it.split("=")
            Pair(split[0], split[1])
        }
        val id = params[0].second
        val title = params[1].second

        val uri = "$mainUrl/api/v8/video?id=${id}&"
        val response = app.get(uri)

        val data = mapper.readValue<HanimeEpisodeData>(response.text)

        val tags = data.hentaiTags.map { it.text }

        val episodes = data.hentaiFranchiseHentaiVideos.map {
            Episode(
                data = "$mainUrl/api/v8/video?id=${it.id}&",
                name = it.name,
                posterUrl = it.posterUrl
            )
        }

        return AnimeLoadResponse(
            title,
            null,
            title,
            url,
            this.name,
            TvType.Hentai,
            data.hentaiVideo.coverUrl,
            unixToYear(data.hentaiVideo.releasedAtUnix),
            hashMapOf(DubStatus.Subbed to episodes),
            null,
            data.hentaiVideo.description.replace(Regex("</?p>"), ""),
            tags,
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data).text
        val response = mapper.readValue<HanimeEpisodeData>(res)

        val streams = ArrayList<ExtractorLink>()

        response.videosManifest.servers.map { server ->
            server.streams.forEach {
                if (it.url.isNotEmpty()) {
                    streams.add(ExtractorLink(
                        "Hanime",
                        "Hanime - ${server.name} - ${it.filesizeMbs}mb",
                        it.url,
                        "",
                        getQualityFromName(it.height),
                        true
                    ))
                }
            }
        }

        streams.forEach {
            callback(it)
        }
        return true
    }
}
