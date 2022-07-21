package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.serialization.SerialName
import java.net.URLDecoder

import kotlinx.serialization.Serializable

class AniflixProvider : MainAPI() {
    override var mainUrl = "https://aniflix.pro"
    override var name = "Aniflix"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    companion object {
        var token: String? = null
    }

    private suspend fun getToken(): String {
        return token ?: run {
            Regex("([^/]*)/_buildManifest\\.js").find(app.get(mainUrl).text)?.groupValues?.getOrNull(
                1
            )
                ?.also {
                    token = it
                }
                ?: throw ErrorLoadingException("No token found")
        }
    }

    private fun Anime.toSearchResponse(): SearchResponse? {
        return newAnimeSearchResponse(
            title?.english ?: title?.romaji ?: return null,
            "$mainUrl/anime/${id ?: return null}"
        ) {
            posterUrl = coverImage?.large ?: coverImage?.medium
        }
    }


    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val elements = listOf(
            Pair("Trending Now", "div:nth-child(3) > div a"),
            Pair("Popular", "div:nth-child(4) > div a"),
            Pair("Top Rated", "div:nth-child(5) > div a"),
        )

        elements.map { (name, element) ->
            val home = soup.select(element).map {
                val href = it.attr("href")
                val title = it.selectFirst("p.mt-2")!!.text()
                val image = it.selectFirst("img.rounded-md[sizes]")!!.attr("src")
                    .replace("/_next/image?url=", "")
                    .replace(Regex("\\&.*\$"), "")
                val realposter = URLDecoder.decode(image, "UTF-8")
                newAnimeSearchResponse(title, fixUrl(href)) {
                    this.posterUrl = realposter
                }
            }
            items.add(HomePageList(name, home))
        }

        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val token = getToken()
        val url = "$mainUrl/_next/data/$token/search.json?keyword=$query"
        val response = app.get(url)
        println("resp: $url ===> ${response.text}")
        val searchResponse =
            response.parsedSafe<Search>()
                ?: throw ErrorLoadingException("No Media")
        return searchResponse.pageProps?.searchResults?.Page?.media?.mapNotNull { media ->
            media.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val token = getToken()

        val id = Regex("$mainUrl/anime/([0-9]*)").find(url)?.groupValues?.getOrNull(1)
            ?: throw ErrorLoadingException("Error parsing link for id")

        val res = app.get("https://aniflix.pro/_next/data/$token/anime/$id.json?id=$id")
            .parsedSafe<AnimeResponsePage>()?.pageProps
            ?: throw ErrorLoadingException("Invalid Json reponse")
        val isMovie = res.anime.format == "MOVIE"
        return newAnimeLoadResponse(
            res.anime.title?.english ?: res.anime.title?.romaji
            ?: throw ErrorLoadingException("Invalid title reponse"),
            url, if (isMovie) TvType.AnimeMovie else TvType.Anime
        ) {
            recommendations = res.recommended.mapNotNull { it.toSearchResponse() }
            tags = res.anime.genres
            posterUrl = res.anime.coverImage?.large ?: res.anime.coverImage?.medium
            plot = res.anime.description
            showStatus = when (res.anime.status) {
                "FINISHED" -> ShowStatus.Completed
                "RELEASING" -> ShowStatus.Ongoing
                else -> null
            }
            addAniListId(id.toIntOrNull())

            // subbed because they are both subbed and dubbed
            if (isMovie)
                addEpisodes(
                    DubStatus.Subbed,
                    listOf(newEpisode("$mainUrl/api/anime/?id=$id&episode=1"))
                )
            else
                addEpisodes(
                    DubStatus.Subbed,
                    res.episodes.episodes?.nodes?.mapIndexed { index, node ->
                        val episodeIndex = node?.number ?: (index + 1)
                        //"$mainUrl/_next/data/$token/watch/$id.json?episode=${node.number ?: return@mapNotNull null}&id=$id"
                        newEpisode("$mainUrl/api/anime?id=$id&episode=${episodeIndex}") {
                            episode = episodeIndex
                            posterUrl = node?.thumbnail?.original?.url
                            name = node?.titles?.canonical
                        }
                    })
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return app.get(data).parsed<AniLoadResponse>().let { res ->
            val dubReferer = res.dub?.Referer ?: ""
            res.dub?.sources?.forEach { source ->
                callback(
                    ExtractorLink(
                        name,
                        "${source.label ?: name} (DUB)",
                        source.file ?: return@forEach,
                        dubReferer,
                        getQualityFromName(source.label),
                        source.type == "hls"
                    )
                )
            }

            val subReferer = res.dub?.Referer ?: ""
            res.sub?.sources?.forEach { source ->
                callback(
                    ExtractorLink(
                        name,
                        "${source.label ?: name} (SUB)",
                        source.file ?: return@forEach,
                        subReferer,
                        getQualityFromName(source.label),
                        source.type == "hls"
                    )
                )
            }

            !res.dub?.sources.isNullOrEmpty() && !res.sub?.sources.isNullOrEmpty()
        }
    }

    @Serializable data class AniLoadResponse(
        @SerialName("sub") val sub: DubSubSource?,
        @SerialName("dub") val dub: DubSubSource?,
        @SerialName("episodes") val episodes: Int?
    )

    @Serializable data class Sources(
        @SerialName("file") val file: String?,
        @SerialName("label") val label: String?,
        @SerialName("type") val type: String?
    )

    @Serializable data class DubSubSource(
        @SerialName("Referer") var Referer: String?,
        @SerialName("sources") var sources: ArrayList<Sources> = arrayListOf()
    )

    @Serializable data class PageProps(
        @SerialName("searchResults") val searchResults: SearchResults?
    )

    @Serializable data class SearchResults(
        @SerialName("Page") val Page: Page?
    )

    @Serializable data class Page(
        @SerialName("media") val media: ArrayList<Anime> = arrayListOf()
    )

    @Serializable data class CoverImage(
        @SerialName("color") val color: String?,
        @SerialName("medium") val medium: String?,
        @SerialName("large") val large: String?,
    )

    @Serializable data class Title(
        @SerialName("english") val english: String?,
        @SerialName("romaji") val romaji: String?,
    )

    @Serializable data class Search(
        @SerialName("pageProps") val pageProps: PageProps?,
        @SerialName("__N_SSP") val _NSSP: Boolean?
    )

    @Serializable data class Anime(
        @SerialName("status") val status: String?,
        @SerialName("id") val id: Int?,
        @SerialName("title") val title: Title?,
        @SerialName("coverImage") val coverImage: CoverImage?,
        @SerialName("format") val format: String?,
        @SerialName("duration") val duration: Int?,
        @SerialName("meanScore") val meanScore: Int?,
        @SerialName("nextAiringEpisode") val nextAiringEpisode: String?,
        @SerialName("bannerImage") val bannerImage: String?,
        @SerialName("description") val description: String?,
        @SerialName("genres") val genres: ArrayList<String>? = null,
        @SerialName("season") val season: String?,
        @SerialName("startDate") val startDate: StartDate?,
    )

    @Serializable data class StartDate(
        @SerialName("year") val year: Int?
    )

    @Serializable data class AnimeResponsePage(
        @SerialName("pageProps") val pageProps: AnimeResponse?,
    )

    @Serializable data class AnimeResponse(
        @SerialName("anime") val anime: Anime,
        @SerialName("recommended") val recommended: ArrayList<Anime>,
        @SerialName("episodes") val episodes: EpisodesParent,
    )

    @Serializable data class EpisodesParent(
        @SerialName("id") val id: String?,
        @SerialName("season") val season: String?,
        @SerialName("startDate") val startDate: String?,
        @SerialName("episodeCount") val episodeCount: Int?,
        @SerialName("episodes") val episodes: Episodes?,
    )

    @Serializable data class Episodes(
        @SerialName("nodes") val nodes: ArrayList<Nodes?> = arrayListOf()
    )

    @Serializable data class Nodes(
        @SerialName("number") val number: Int? = null,
        @SerialName("titles") val titles: Titles?,
        @SerialName("thumbnail") val thumbnail: Thumbnail?,
    )

    @Serializable data class Titles(
        @SerialName("canonical") val canonical: String?,
    )

    @Serializable data class Original(
        @SerialName("url") val url: String?,
    )

    @Serializable data class Thumbnail(
        @SerialName("original") val original: Original?,
    )
}