package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.serialization.SerialName


import kotlinx.serialization.Serializable

class TheFlixToProvider : MainAPI() {
    companion object {
        var latestCookies: Map<String, String> = emptyMap()
    }

    override var name = "TheFlix.to"
    override var mainUrl = "https://theflix.to"
    override val instantLinkLoading = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    @Serializable
    data class HomeJson(
        @SerialName("props") val props: HomeProps = HomeProps(),
    )

    @Serializable
    data class HomeProps(
        @SerialName("pageProps") val pageProps: PageProps = PageProps(),
    )

    @Serializable
    data class PageProps(
        @SerialName("moviesListTrending") val moviesListTrending: MoviesListTrending = MoviesListTrending(),
        @SerialName("moviesListNewArrivals") val moviesListNewArrivals: MoviesListNewArrivals = MoviesListNewArrivals(),
        @SerialName("tvsListTrending") val tvsListTrending: TvsListTrending = TvsListTrending(),
        @SerialName("tvsListNewEpisodes") val tvsListNewEpisodes: TvsListNewEpisodes = TvsListNewEpisodes(),
    )


    @Serializable
    data class MoviesListTrending(
        @SerialName("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @SerialName("total") val total: Int? = null,
        @SerialName("page") val page: Int? = null,
        @SerialName("limit") val limit: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("type") val type: String? = null,
    )

    @Serializable
    data class MoviesListNewArrivals(
        @SerialName("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @SerialName("total") val total: Int? = null,
        @SerialName("page") val page: Int? = null,
        @SerialName("limit") val limit: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("type") val type: String? = null,
    )

    @Serializable
    data class TvsListTrending(
        @SerialName("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @SerialName("total") val total: Int? = null,
        @SerialName("page") val page: Int? = null,
        @SerialName("limit") val limit: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("type") val type: String? = null,
    )

    @Serializable
    data class TvsListNewEpisodes(
        @SerialName("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @SerialName("total") val total: Int? = null,
        @SerialName("page") val page: Int? = null,
        @SerialName("limit") val limit: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("type") val type: String? = null,
    )

    @Serializable
    data class Docs(
        @SerialName("name") val name: String = String(),
        @SerialName("originalLanguage") val originalLanguage: String? = null,
        @SerialName("popularity") val popularity: Double? = null,
        @SerialName("runtime") val runtime: Int? = null,
        @SerialName("status") val status: String? = null,
        @SerialName("voteAverage") val voteAverage: Double? = null,
        @SerialName("voteCount") val voteCount: Int? = null,
        @SerialName("cast") val cast: String? = null,
        @SerialName("director") val director: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("posterUrl") val posterUrl: String? = null,
        @SerialName("releaseDate") val releaseDate: String? = null,
        @SerialName("createdAt") val createdAt: String? = null,
        @SerialName("updatedAt") val updatedAt: String? = null,
        @SerialName("conversionDate") val conversionDate: String? = null,
        @SerialName("id") val id: Int? = null,
        @SerialName("available") val available: Boolean? = null,
        @SerialName("videos") val videos: ArrayList<String>? = arrayListOf(),
    )


    private suspend fun getCookies(): Map<String, String> {
        //  val cookieResponse = app.post(
        //      "https://theflix.to:5679/authorization/session/continue?contentUsageType=Viewing",
        //    headers = mapOf(
        //          "Host" to "theflix.to:5679",
        //          "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
        //          "Accept" to "application/json, text/plain,"
        //          "Accept-Language" to "en-US,en;q=0.5",
        //          "Content-Type" to "application/json;charset=utf-8",
        //          "Content-Length" to "35",
        //          "Origin" to "https://theflix.to",
        //          "DNT" to "1",
        //          "Connection" to "keep-alive",
        //          "Referer" to "https://theflix.to/",
        //          "Sec-Fetch-Dest" to "empty",
        //          "Sec-Fetch-Mode" to "cors",
        //          "Sec-Fetch-Site" to "same-site",)).okhttpResponse.headers.values("Set-Cookie")

        val cookies = app.post(
            "$mainUrl:5679/authorization/session/continue?contentUsageType=Viewing",
            headers = mapOf(
                "Host" to "theflix.to:5679",
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Content-Type" to "application/json;charset=utf-8",
                "Content-Length" to "35",
                "Origin" to mainUrl,
                "DNT" to "1",
                "Connection" to "keep-alive",
                "Referer" to mainUrl,
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-site",
            )
        ).cookies
        /* val cookieRegex = Regex("(theflix\\..*?id\\=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
       val findcookie = cookieRegex.findAll(cookieResponse.toString()).map { it.value }.toList()
       val cookiesstring = findcookie.toString().replace(", ","; ").replace("[","").replace("]","")
       val cookiesmap = mapOf("Cookie" to cookiesstring) */
        latestCookies = cookies
        return latestCookies
    }


    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val doc = app.get(mainUrl).document
        val scriptText = doc.selectFirst("script[type=application/json]")!!.data()
        if (scriptText.contains("moviesListTrending")) {
            val json = parseJson<HomeJson>(scriptText)
            val homePageProps = json.props.pageProps
            listOf(
                Triple(
                    homePageProps.moviesListNewArrivals.docs,
                    homePageProps.moviesListNewArrivals.type,
                    "New Movie arrivals"
                ),
                Triple(
                    homePageProps.moviesListTrending.docs,
                    homePageProps.moviesListTrending.type,
                    "Trending Movies"
                ),
                Triple(
                    homePageProps.tvsListTrending.docs,
                    homePageProps.tvsListTrending.type,
                    "Trending TV Series"
                ),
                Triple(
                    homePageProps.tvsListNewEpisodes.docs,
                    homePageProps.tvsListNewEpisodes.type,
                    "New Episodes"
                )
            ).map { (docs, type, homename) ->
                val home = docs.map { info ->
                    val title = info.name
                    val poster = info.posterUrl
                    val typeinfo =
                        if (type?.contains("TV") == true) TvType.TvSeries else TvType.Movie
                    val link =
                        if (typeinfo == TvType.Movie) "$mainUrl/movie/${info.id}-${cleanTitle(title)}"
                        else "$mainUrl/tv-show/${info.id}-${
                            cleanTitle(title).replace(
                                "?",
                                ""
                            )
                        }/season-1/episode-1"
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        typeinfo,
                        poster,
                        null,
                        null,
                    )
                }
                items.add(HomePageList(homename, home))
            }

        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    @Serializable
    data class SearchJson(
        @SerialName("props") val props: SearchProps = SearchProps(),
    )

    @Serializable
    data class SearchProps(
        @SerialName("pageProps") val pageProps: SearchPageProps = SearchPageProps(),
    )

    @Serializable
    data class SearchPageProps(
        @SerialName("mainList") val mainList: SearchMainList = SearchMainList(),
    )

    @Serializable
    data class SearchMainList(
        @SerialName("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @SerialName("total") val total: Int? = null,
        @SerialName("page") val page: Int? = null,
        @SerialName("limit") val limit: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("type") val type: String? = null,
    )


    override suspend fun search(query: String): List<SearchResponse> {
        val search = ArrayList<SearchResponse>()
        val urls = listOf(
            "$mainUrl/movies/trending?search=$query",
            "$mainUrl/tv-shows/trending?search=$query"
        )
        urls.apmap { url ->
            val doc = app.get(url).document
            val scriptText = doc.selectFirst("script[type=application/json]")!!.data()
            if (scriptText.contains("pageProps")) {
                val json = parseJson<SearchJson>(scriptText)
                val searchPageProps = json.props.pageProps.mainList
                val pair = listOf(Pair(searchPageProps.docs, searchPageProps.type))
                pair.map { (docs, type) ->
                    docs.map { info ->
                        val title = info.name
                        val poster = info.posterUrl
                        val typeinfo =
                            if (type?.contains("TV") == true) TvType.TvSeries else TvType.Movie
                        val link = if (typeinfo == TvType.Movie) "$mainUrl/movie/${info.id}-${
                            cleanTitle(title)
                        }"
                        else "$mainUrl/tv-show/${info.id}-${cleanTitle(title)}/season-1/episode-1"
                        if (typeinfo == TvType.Movie) {
                            search.add(
                                MovieSearchResponse(
                                    title,
                                    link,
                                    this.name,
                                    TvType.Movie,
                                    poster,
                                    null
                                )
                            )
                        } else {
                            search.add(
                                TvSeriesSearchResponse(
                                    title,
                                    link,
                                    this.name,
                                    TvType.TvSeries,
                                    poster,
                                    null,
                                    null
                                )
                            )
                        }
                    }
                }
            }
        }
        return search
    }

    @Serializable
    data class LoadMain(
        @SerialName("props") val props: LoadProps? = LoadProps(),
        @SerialName("page") val page: String? = null,
        @SerialName("buildId") val buildId: String? = null,
        @SerialName("runtimeConfig") val runtimeConfig: RuntimeConfig? = RuntimeConfig(),
        @SerialName("isFallback") val isFallback: Boolean? = null,
        @SerialName("gssp") val gssp: Boolean? = null,
        @SerialName("customServer") val customServer: Boolean? = null,
        @SerialName("appGip") val appGip: Boolean? = null
    )

    @Serializable
    data class LoadProps(
        @SerialName("pageProps") val pageProps: LoadPageProps? = LoadPageProps(),
        @SerialName("__N_SSP") val _NSSP: Boolean? = null
    )

    @Serializable
    data class LoadPageProps(
        @SerialName("selectedTv") val selectedTv: TheFlixMetadata? = TheFlixMetadata(),
        @SerialName("movie") val movie: TheFlixMetadata? = TheFlixMetadata(),
        @SerialName("recommendationsList") val recommendationsList: RecommendationsList? = RecommendationsList(),
        @SerialName("basePageSegments") val basePageSegments: ArrayList<String>? = arrayListOf()
    )

    @Serializable
    data class TheFlixMetadata(
        @SerialName("episodeRuntime") val episodeRuntime: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("numberOfSeasons") val numberOfSeasons: Int? = null,
        @SerialName("numberOfEpisodes") val numberOfEpisodes: Int? = null,
        @SerialName("originalLanguage") val originalLanguage: String? = null,
        @SerialName("popularity") val popularity: Double? = null,
        @SerialName("status") val status: String? = null,
        @SerialName("voteAverage") val voteAverage: Double? = null,
        @SerialName("voteCount") val voteCount: Int? = null,
        @SerialName("cast") val cast: String? = null,
        @SerialName("director") val director: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("posterUrl") val posterUrl: String? = null,
        @SerialName("releaseDate") val releaseDate: String? = null,
        @SerialName("createdAt") val createdAt: String? = null,
        @SerialName("updatedAt") val updatedAt: String? = null,
        @SerialName("id") val id: Int? = null,
        @SerialName("available") val available: Boolean? = null,
        @SerialName("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @SerialName("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @SerialName("videos") val videos: ArrayList<String>? = arrayListOf(),
        @SerialName("runtime") val runtime: Int? = null,
    )

    @Serializable
    data class Seasons(
        @SerialName("name") val name: String? = null,
        @SerialName("numberOfEpisodes") val numberOfEpisodes: Int? = null,
        @SerialName("seasonNumber") val seasonNumber: Int? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("posterUrl") val posterUrl: String? = null,
        @SerialName("releaseDate") val releaseDate: String? = null,
        @SerialName("createdAt") val createdAt: String? = null,
        @SerialName("updatedAt") val updatedAt: String? = null,
        @SerialName("id") val id: Int? = null,
        @SerialName("episodes") val episodes: ArrayList<Episodes>? = arrayListOf()
    )

    @Serializable
    data class Episodes(
        @SerialName("episodeNumber") val episodeNumber: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("seasonNumber") val seasonNumber: Int? = null,
        @SerialName("voteAverage") val voteAverage: Double? = null,
        @SerialName("voteCount") val voteCount: Int? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("releaseDate") val releaseDate: String? = null,
        @SerialName("createdAt") val createdAt: String? = null,
        @SerialName("updatedAt") val updatedAt: String? = null,
        @SerialName("id") val id: Int? = null,
        @SerialName("videos") val videos: ArrayList<String>? = arrayListOf()
    )


    @Serializable
    data class Genres(
        @SerialName("name") val name: String? = null,
        @SerialName("id") val id: Int? = null
    )

    @Serializable
    data class RuntimeConfig(
        @SerialName("AddThisService") val AddThisService: RuntimeConfigData? = RuntimeConfigData(),
        @SerialName("Application") val Application: RuntimeConfigData? = RuntimeConfigData(),
        @SerialName("GtmService") val GtmService: RuntimeConfigData? = RuntimeConfigData(),
        @SerialName("Services") val Services: RuntimeConfigData? = RuntimeConfigData(),
    )

    @Serializable
    data class RuntimeConfigData(
        @SerialName("PublicId") val PublicId: String? = null,
        @SerialName("ContentUsageType") val ContentUsageType: String? = null,
        @SerialName("IsDevelopmentMode") val IsDevelopmentMode: Boolean? = null,
        @SerialName("IsDevelopmentOrProductionMode") val IsDevelopmentOrProductionMode: Boolean? = null,
        @SerialName("IsProductionMode") val IsProductionMode: Boolean? = null,
        @SerialName("IsStagingMode") val IsStagingMode: Boolean? = null,
        @SerialName("IsTestMode") val IsTestMode: Boolean? = null,
        @SerialName("Mode") val Mode: String? = null,
        @SerialName("Name") val Name: String? = null,
        @SerialName("Url") val Url: String? = null,
        @SerialName("UseFilterInfoInUrl") val UseFilterInfoInUrl: Boolean? = null,
        @SerialName("TrackingId") val TrackingId: String? = null,
        @SerialName("Server") val Server: Server? = Server(),
        @SerialName("TmdbServer") val TmdbServer: TmdbServer? = TmdbServer(),
    )

    @Serializable
    data class TmdbServer(
        @SerialName("Url") val Url: String? = null
    )


    @Serializable
    data class Server(
        @SerialName("Url") val Url: String? = null
    )

    @Serializable
    data class RecommendationsList(
        @SerialName("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @SerialName("total") val total: Int? = null,
        @SerialName("page") val page: Int? = null,
        @SerialName("limit") val limit: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("type") val type: String? = null,
    )

    private fun cleanTitle(title: String): String {
        val dotTitle = title.substringBefore("/season")
        if (dotTitle.contains(Regex("\\..\\."))) { //For titles containing more than two dots (S.W.A.T.)
            return (dotTitle.removeSuffix(".")
                .replace(" - ", "-")
                .replace(".", "-").replace(" ", "-")
                .replace("-&", "")
                .replace(Regex("(:|-&)"), "")
                .replace("'", "-")).lowercase()
        }
        return (title
            .replace(" - ", "-")
            .replace(" ", "-")
            .replace("-&", "")
            .replace("/", "-")
            .replace(Regex("(:|-&|\\.)"), "")
            .replace("'", "-")).lowercase()
    }

    private suspend fun getLoadMan(url: String): LoadMain {
        getCookies()
        val og = app.get(url, headers = latestCookies)
        val soup = og.document
        val script = soup.selectFirst("script[type=application/json]")!!.data()
        return parseJson(script)
    }

    override suspend fun load(url: String): LoadResponse? {
        val tvtype = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val json = getLoadMan(url)
        val episodes = ArrayList<Episode>()
        val isMovie = tvtype == TvType.Movie
        val pageMain = json.props?.pageProps

        val metadata: TheFlixMetadata? = if (isMovie) pageMain?.movie else pageMain?.selectedTv

        val available = metadata?.available

        val comingsoon = !available!!

        val movieId = metadata.id

        val movietitle = metadata.name

        val poster = metadata.posterUrl

        val description = metadata.overview

        if (!isMovie) {
            metadata.seasons?.map { seasons ->
                val seasonPoster = seasons.posterUrl ?: metadata.posterUrl
                seasons.episodes?.forEach { epi ->
                    val episodenu = epi.episodeNumber
                    val seasonum = epi.seasonNumber
                    val title = epi.name
                    val epDesc = epi.overview
                    val test = epi.videos
                    val ratinginfo = (epi.voteAverage)?.times(10)?.toInt()
                    val rating = if (ratinginfo?.equals(0) == true) null else ratinginfo
                    val eps = Episode(
                        "$mainUrl/tv-show/$movieId-${cleanTitle(movietitle!!)}/season-$seasonum/episode-$episodenu",
                        title,
                        seasonum,
                        episodenu,
                        description = epDesc!!,
                        posterUrl = seasonPoster,
                        rating = rating,
                    )
                    if (test!!.isNotEmpty()) {
                        episodes.add(eps)
                    } else {
                        //Nothing, will prevent seasons/episodes with no videos to be added
                    }
                }
            }
        }
        val rating = metadata.voteAverage?.toFloat()?.times(1000)?.toInt()

        val tags = metadata.genres?.mapNotNull { it.name }

        val recommendationsitem = pageMain?.recommendationsList?.docs?.map { loadDocs ->
            val title = loadDocs.name
            val posterrec = loadDocs.posterUrl
            val link = if (isMovie) "$mainUrl/movie/${loadDocs.id}-${cleanTitle(title)}"
            else "$mainUrl/tv-show/${loadDocs.id}-${cleanTitle(title)}/season-1/episode-1"
            MovieSearchResponse(
                title,
                link,
                this.name,
                tvtype,
                posterrec,
                year = null
            )
        }

        val year = metadata.releaseDate?.substringBefore("-")

        val runtime = metadata.runtime?.div(60) ?: metadata.episodeRuntime?.div(60)
        val cast = metadata.cast?.split(",")

        return when (tvtype) {
            TvType.TvSeries -> {
                return newTvSeriesLoadResponse(movietitle!!, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year?.toIntOrNull()
                    this.plot = description
                    this.duration = runtime
                    addActors(cast)
                    this.tags = tags
                    this.recommendations = recommendationsitem
                    this.comingSoon = comingsoon
                    this.rating = rating
                }
            }
            TvType.Movie -> {
                newMovieLoadResponse(movietitle!!, url, TvType.Movie, url) {
                    this.year = year?.toIntOrNull()
                    this.posterUrl = poster
                    this.plot = description
                    this.duration = runtime
                    addActors(cast)
                    this.tags = tags
                    this.recommendations = recommendationsitem
                    this.comingSoon = comingsoon
                    this.rating = rating
                }
            }
            else -> null
        }
    }


    @Serializable
    data class VideoData(
        @SerialName("url") val url: String? = null,
        @SerialName("id") val id: String? = null,
        @SerialName("type") val type: String? = null,
    )


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = getLoadMan(data)
        val authhost = json.runtimeConfig?.Services?.Server?.Url
        val isMovie = data.contains("/movie/")
        val qualityReg = Regex("(\\d+p)")
        if (isMovie) {
            json.props?.pageProps?.movie?.videos?.apmap { id ->
                val jsonmovie = app.get(
                    "$authhost/movies/videos/$id/request-access?contentUsageType=Viewing",
                    headers = latestCookies
                ).parsedSafe<VideoData>() ?: return@apmap false
                val extractedlink = jsonmovie.url
                if (!extractedlink.isNullOrEmpty()) {
                    val quality = qualityReg.find(extractedlink)?.value ?: ""
                    callback(
                        ExtractorLink(
                            name,
                            name,
                            extractedlink,
                            "",
                            getQualityFromName(quality),
                            false
                        )
                    )
                } else null
            }
        } else {
            val dataRegex = Regex("(season-(\\d+)\\/episode-(\\d+))")
            val cleandatainfo =
                dataRegex.find(data)?.value?.replace(Regex("(season-|episode-)"), "")
                    ?.replace("/", "x")
            val tesatt = cleandatainfo.let { str ->
                str?.split("x")?.mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val epID = tesatt?.getOrNull(1)
            val seasonid = tesatt?.getOrNull(0)
            json.props?.pageProps?.selectedTv?.seasons?.map {
                it.episodes?.map {
                    val epsInfo = Triple(it.seasonNumber, it.episodeNumber, it.videos)
                    if (epsInfo.first == seasonid && epsInfo.second == epID) {
                        epsInfo.third?.apmap { id ->
                            val jsonserie = app.get(
                                "$authhost/tv/videos/$id/request-access?contentUsageType=Viewing",
                                headers = latestCookies
                            ).parsedSafe<VideoData>() ?: return@apmap false
                            val extractedlink = jsonserie.url
                            if (!extractedlink.isNullOrEmpty()) {
                                val quality = qualityReg.find(extractedlink)?.value ?: ""
                                callback(
                                    ExtractorLink(
                                        name,
                                        name,
                                        extractedlink,
                                        "",
                                        getQualityFromName(quality),
                                        false
                                    )
                                )
                            } else null
                        }
                    }
                }
            }
        }
        return true
    }
}
