package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.getStatus
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

class AsiaFlixProvider : MainAPI() {
    override var mainUrl = "https://asiaflix.app"
    override var name = "AsiaFlix"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val supportedTypes = setOf(TvType.AsianDrama)

    private val apiUrl = "https://api.asiaflix.app/api/v2"

    @Serializable
    data class DashBoardObject(
        @SerialName("sectionName") val sectionName: String,
        @SerialName("type") val type: String?,
        @SerialName("data") val data: List<Data>?
    )

    @Serializable
    data class Episodes(
        @SerialName("_id") val _id: String,
        @SerialName("epUrl") val epUrl: String?,
        @SerialName("number") val number: Int?,
        @SerialName("type") val type: String?,
        @SerialName("extracted") val extracted: String?,
        @SerialName("videoUrl") val videoUrl: String?
    )


    @Serializable
    data class Data(
        @SerialName("_id") val _id: String,
        @SerialName("name") val name: String,
        @SerialName("altNames") val altNames: String?,
        @SerialName("image") val image: String?,
        @SerialName("tvStatus") val tvStatus: String?,
        @SerialName("genre") val genre: String?,
        @SerialName("releaseYear") val releaseYear: Int?,
        @SerialName("createdAt") val createdAt: Long?,
        @SerialName("episodes") val episodes: List<Episodes>?,
        @SerialName("views") val views: Int?
    )

    @Serializable
    data class DramaPage(
        @SerialName("_id") val _id: String,
        @SerialName("name") val name: String,
        @SerialName("altNames") val altNames: String?,
        @SerialName("synopsis") val synopsis: String?,
        @SerialName("image") val image: String?,
        @SerialName("language") val language: String?,
        @SerialName("dramaUrl") val dramaUrl: String?,
        @SerialName("published") val published: Boolean?,
        @SerialName("tvStatus") val tvStatus: String?,
        @SerialName("firstAirDate") val firstAirDate: String?,
        @SerialName("genre") val genre: String?,
        @SerialName("releaseYear") val releaseYear: Int?,
        @SerialName("createdAt") val createdAt: Long?,
        @SerialName("modifiedAt") val modifiedAt: Long?,
        @SerialName("episodes") val episodes: List<Episodes>,
        @SerialName("__v") val __v: Int?,
        @SerialName("cdnImage") val cdnImage: String?,
        @SerialName("views") val views: Int?
    )

    private fun Data.toSearchResponse(): TvSeriesSearchResponse {
        return TvSeriesSearchResponse(
            name,
            _id,
            this@AsiaFlixProvider.name,
            TvType.AsianDrama,
            image,
            releaseYear,
            episodes?.size,
        )
    }

    private fun Episodes.toEpisode(): Episode? {
        if (videoUrl != null && videoUrl.contains("watch/null") || number == null) return null
        return videoUrl?.let {
            Episode(
                it,
                null,
                number,
            )
        }
    }

    private fun DramaPage.toLoadResponse(): TvSeriesLoadResponse {
        return TvSeriesLoadResponse(
            name,
            "$mainUrl$dramaUrl/$_id".replace("drama-detail", "show-details"),
            this@AsiaFlixProvider.name,
            TvType.AsianDrama,
            episodes.mapNotNull { it.toEpisode() }.sortedBy { it.episode },
            image,
            releaseYear,
            synopsis,
            getStatus(tvStatus ?: ""),
            null,
            genre?.split(",")?.map { it.trim() }
        )
    }

    override suspend fun getMainPage(): HomePageResponse {
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        val response = app.get("$apiUrl/dashboard", headers = headers).text

        // Hack, because it can either be object or a list
        val cleanedResponse = Regex(""""data":(\{.*?),\{"sectionName"""").replace(response) {
            """"data":null},{"sectionName""""
        }

        val dashBoard = parseJson<List<DashBoardObject>?>(cleanedResponse)

        val listItems = dashBoard?.mapNotNull {
            it.data?.map { data ->
                data.toSearchResponse()
            }?.let { searchResponse ->
                HomePageList(it.sectionName, searchResponse)
            }
        }
        return HomePageResponse(listItems ?: listOf())
    }

    @Serializable
    data class Link(
        @SerialName("url") val url: String?,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        app.get(
            "$apiUrl/utility/get-stream-links?url=$data",
            headers = headers
        ).parsed<Link>().url?.let {
//            val fixedUrl = "https://api.asiaflix.app/api/v2/utility/cors-proxy/playlist/${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}"
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    it,
                    "https://asianload1.com/",
                    /** <------ This provider should be added instead */
                    getQualityFromName(it),
                    URI(it).path.endsWith(".m3u8")
                )
            )
        }
        return true
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        val url = "$apiUrl/drama/search?q=$query"
        val response = app.get(url, headers = headers).text
        return parseJson<List<Data>?>(response)?.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        val requestUrl = "$apiUrl/drama?id=${url.split("/").lastOrNull()}"
        val dramaPage = app.get(requestUrl, headers = headers).parsed<DramaPage>()
        return dramaPage.toLoadResponse()
    }
}
