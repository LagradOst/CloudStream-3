package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName


class TheFlixToProvider : MainAPI() {
    override var name = "TheFlix.to"
    override var mainUrl = "https://theflix.to"
    override val instantLinkLoading = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    data class HomeJson (
        @JsonProperty("props") var props         : HomeProps         = HomeProps(),
    )

    data class HomeProps (
        @JsonProperty("pageProps" ) var pageProps : PageProps = PageProps(),
    )

    data class PageProps (
        @JsonProperty("moviesListTrending"     ) var moviesListTrending     : MoviesListTrending    = MoviesListTrending(),
        @JsonProperty("moviesListNewArrivals"  ) var moviesListNewArrivals  : MoviesListNewArrivals = MoviesListNewArrivals(),
        @JsonProperty("tvsListTrending"        ) var tvsListTrending        : TvsListTrending      = TvsListTrending(),
        @JsonProperty("tvsListNewEpisodes"     ) var tvsListNewEpisodes     : TvsListNewEpisodes    = TvsListNewEpisodes(),
    )


    data class MoviesListTrending (
        @JsonProperty("docs"  ) var docs  : ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total" ) var total : Int?            = null,
        @JsonProperty("page"  ) var page  : Int?            = null,
        @JsonProperty("limit" ) var limit : Int?            = null,
        @JsonProperty("pages" ) var pages : Int?            = null,
        @JsonProperty("type"  ) var type  : String?         = null,
    )

    data class MoviesListNewArrivals (
        @JsonProperty("docs"  ) var docs  : ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total" ) var total : Int?            = null,
        @JsonProperty("page"  ) var page  : Int?            = null,
        @JsonProperty("limit" ) var limit : Int?            = null,
        @JsonProperty("pages" ) var pages : Int?            = null,
        @JsonProperty("type"  ) var type  : String?         = null,
    )

    data class TvsListTrending (
        @JsonProperty("docs"  ) var docs  : ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total" ) var total : Int?            = null,
        @JsonProperty("page"  ) var page  : Int?            = null,
        @JsonProperty("limit" ) var limit : Int?            = null,
        @JsonProperty("pages" ) var pages : Int?            = null,
        @JsonProperty("type"  ) var type  : String?         = null,
    )

    data class TvsListNewEpisodes (
        @JsonProperty("docs"  ) var docs  : ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total" ) var total : Int?            = null,
        @JsonProperty("page"  ) var page  : Int?            = null,
        @JsonProperty("limit" ) var limit : Int?            = null,
        @JsonProperty("pages" ) var pages : Int?            = null,
        @JsonProperty("type"  ) var type  : String?         = null,
    )

    data class Docs (
        @JsonProperty("name"             ) var name             : String           = String(),
        @JsonProperty("originalLanguage" ) var originalLanguage : String?           = null,
        @JsonProperty("popularity"       ) var popularity       : Double?           = null,
        @JsonProperty("runtime"          ) var runtime          : Int?              = null,
        @JsonProperty("status"           ) var status           : String?           = null,
        @JsonProperty("voteAverage"      ) var voteAverage      : Double?           = null,
        @JsonProperty("voteCount"        ) var voteCount        : Int?              = null,
        @JsonProperty("cast"             ) var cast             : String?           = null,
        @JsonProperty("director"         ) var director         : String?           = null,
        @JsonProperty("overview"         ) var overview         : String?           = null,
        @JsonProperty("posterUrl"        ) var posterUrl        : String?           = null,
        @JsonProperty("releaseDate"      ) var releaseDate      : String?           = null,
        @JsonProperty("createdAt"        ) var createdAt        : String?           = null,
        @JsonProperty("updatedAt"        ) var updatedAt        : String?           = null,
        @JsonProperty("conversionDate"   ) var conversionDate   : String?           = null,
        @JsonProperty("id"               ) var id               : Int?              = null,
        @JsonProperty("available"        ) var available        : Boolean?          = null,
    )


    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val doc = app.get(mainUrl, timeout = 120).document
        doc.select("script[type=application/json]").map { script ->
            if (script.data().contains("moviesListTrending")) {
                val text = script.data()
                val json = parseJson<HomeJson>(text)
                val homePageProps = json.props.pageProps
                listOf(
                    Triple(homePageProps.moviesListNewArrivals.docs, homePageProps.moviesListNewArrivals.type, "New Movie arrivals"),
                    Triple(homePageProps.moviesListTrending.docs, homePageProps.moviesListTrending.type, "Trending Movies"),
                    Triple(homePageProps.tvsListTrending.docs, homePageProps.tvsListTrending.type, "Trending TV Series"),
                    Triple(homePageProps.tvsListNewEpisodes.docs, homePageProps.tvsListNewEpisodes.type, "New Episodes")
                ).map { (docs, type, homename) ->
                    val home = docs.map { info ->
                        val title = info.name
                        val poster = info.posterUrl
                        val typeinfo = if (type?.contains("TV") == true) TvType.TvSeries else TvType.Movie
                        val link = if (typeinfo == TvType.Movie) "$mainUrl/movie/${info.id}-${cleanTitle(title)}"
                        else "$mainUrl/tv-show/${info.id}-${cleanTitle(title)}/season-1/episode-1"
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
        }


        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class SearchJson (
        @JsonProperty("props"         ) var props         : SearchProps         = SearchProps(),
    )

    data class SearchProps (
        @JsonProperty("pageProps" ) var pageProps : SearchPageProps = SearchPageProps(),
    )

    data class SearchPageProps (
        @JsonProperty("mainList"            ) var mainList            : SearchMainList             = SearchMainList(),
    )

    data class SearchMainList (
        @JsonProperty("docs"  ) var docs  : ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total" ) var total : Int?            = null,
        @JsonProperty("page"  ) var page  : Int?            = null,
        @JsonProperty("limit" ) var limit : Int?            = null,
        @JsonProperty("pages" ) var pages : Int?            = null,
        @JsonProperty("type"  ) var type  : String?         = null,
    )


    override suspend fun search(query: String): List<SearchResponse> {
        val search = ArrayList<SearchResponse>()
        val urls = listOf(
            "$mainUrl/movies/trending?search=$query",
            "$mainUrl/tv-shows/trending?search=$query"
        )
        urls.apmap { url ->
            val doc = app.get(url).document
            doc.select("script[type=application/json]").map { script ->
                if (script.data().contains("pageProps")) {
                    val text = script.data()
                    val json = parseJson<SearchJson>(text)
                    val searchPageProps = json.props.pageProps
                    val pair = listOf(Pair(searchPageProps.mainList.docs, searchPageProps.mainList.type))
                    pair.map { (docs, type) ->
                        docs.map { info ->
                            val title = info.name
                            val poster = info.posterUrl
                            val typeinfo = if (type?.contains("TV") == true) TvType.TvSeries else TvType.Movie
                            val link = if (typeinfo == TvType.Movie) "$mainUrl/movie/${info.id}-${cleanTitle(title)}"
                            else "$mainUrl/tv-show/${info.id}-${cleanTitle(title)}/season-1/episode-1"
                            val isMovie = link.contains("movie")
                            if (isMovie) {
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
        }
        return search
    }


    data class LoadMain (
        @JsonProperty("props") var props : LoadProps = LoadProps(),
    )
    data class LoadProps (
        @JsonProperty("pageProps" ) var pageProps : LoadPageProps = LoadPageProps(),
    )

    data class LoadPageProps (
        @JsonProperty("selectedTv"          ) var selectedTv          : SelectedTv?          = SelectedTv(),
        @JsonProperty("movie") var movie               : Movie?               = Movie(),
        @JsonProperty("videoUrl") var videoUrl            : String?              = null,
        @JsonProperty("recommendationsList") var recommendationsList : RecommendationsList? = RecommendationsList(),
    )

    data class SelectedTv (
        @JsonProperty("episodeRuntime"   ) var episodeRuntime   : Int?               = null,
        @JsonProperty("name"             ) var name             : String           = String(),
        @JsonProperty("numberOfSeasons"  ) var numberOfSeasons  : Int?               = null,
        @JsonProperty("numberOfEpisodes" ) var numberOfEpisodes : Int?               = null,
        @JsonProperty("originalLanguage" ) var originalLanguage : String?            = null,
        @JsonProperty("popularity"       ) var popularity       : Double?            = null,
        @JsonProperty("status"           ) var status           : String?            = null,
        @JsonProperty("voteAverage"      ) var voteAverage      : Double?            = null,
        @JsonProperty("voteCount"        ) var voteCount        : Int?               = null,
        @JsonProperty("cast"             ) var cast             : String?            = null,
        @JsonProperty("director"         ) var director         : String?            = null,
        @JsonProperty("overview"         ) var overview         : String?            = null,
        @JsonProperty("posterUrl"        ) var posterUrl        : String?            = null,
        @JsonProperty("releaseDate"      ) var releaseDate      : String?            = null,
        @JsonProperty("createdAt"        ) var createdAt        : String?            = null,
        @JsonProperty("updatedAt"        ) var updatedAt        : String?            = null,
        @JsonProperty("id"               ) var id               : Int?               = null,
        @JsonProperty("available"        ) var available        : Boolean?           = null,
        @JsonProperty("genres"           ) var genres           : ArrayList<Genres>  = arrayListOf(),
        @JsonProperty("seasons"          ) var seasons          : ArrayList<Seasons> = arrayListOf()
    )

    data class Genres (
        @JsonProperty("name" ) var name : String,
        @JsonProperty("id"   ) var id   : Int?    = null

    )

    data class Seasons (
        @JsonProperty("name"             ) var name             : String?             = null,
        @JsonProperty("numberOfEpisodes" ) var numberOfEpisodes : Int?                = null,
        @JsonProperty("seasonNumber"     ) var seasonNumber     : Int?                = null,
        @JsonProperty("overview"         ) var overview         : String?             = null,
        @JsonProperty("posterUrl"        ) var posterUrl        : String?             = null,
        @JsonProperty("releaseDate"      ) var releaseDate      : String?             = null,
        @JsonProperty("createdAt"        ) var createdAt        : String?             = null,
        @JsonProperty("updatedAt"        ) var updatedAt        : String?             = null,
        @JsonProperty("id"               ) var id               : Int?                = null,
        @JsonProperty("episodes"         ) var episodes         : ArrayList<Episodes> = arrayListOf()
    )

    data class Episodes (
        @JsonProperty("episodeNumber" ) var episodeNumber : Int?              = null,
        @JsonProperty("name"          ) var name          : String?           = null,
        @JsonProperty("seasonNumber"  ) var seasonNumber  : Int?              = null,
        @JsonProperty("voteAverage"   ) var voteAverage   : Double?           = null,
        @JsonProperty("voteCount"     ) var voteCount     : Int?              = null,
        @JsonProperty("overview"      ) var overview      : String?           = null,
        @JsonProperty("releaseDate"   ) var releaseDate   : String?           = null,
        @JsonProperty("createdAt"     ) var createdAt     : String?           = null,
        @JsonProperty("updatedAt"     ) var updatedAt     : String?           = null,
        @JsonProperty("id"            ) var id            : Int?              = null,
        @JsonProperty("videos"        ) var videos        : ArrayList<Videos> = arrayListOf()
    )

    data class Videos (
        @JsonProperty("language" ) var language : String? = null,
        @JsonProperty("name"     ) var name     : String? = null,
        @JsonProperty("id"       ) var id       : Int?    = null
    )

    data class Movie (
        @JsonProperty("name"             ) var name             : String           = String(),
        @JsonProperty("originalLanguage" ) var originalLanguage : String?           = null,
        @JsonProperty("popularity"       ) var popularity       : Double?           = null,
        @JsonProperty("runtime"          ) var runtime          : Int?              = null,
        @JsonProperty("status"           ) var status           : String?           = null,
        @JsonProperty("voteAverage"      ) var voteAverage      : Double?           = null,
        @JsonProperty("voteCount"        ) var voteCount        : Int?              = null,
        @JsonProperty("cast"             ) var cast             : String?           = null,
        @JsonProperty("director"         ) var director         : String?           = null,
        @JsonProperty("overview"         ) var overview         : String?           = null,
        @JsonProperty("posterUrl"        ) var posterUrl        : String?           = null,
        @JsonProperty("releaseDate"      ) var releaseDate      : String?           = null,
        @JsonProperty("conversionDate"   ) var conversionDate   : String?           = null,
        @JsonProperty("createdAt"        ) var createdAt        : String?           = null,
        @JsonProperty("updatedAt"        ) var updatedAt        : String?           = null,
        @JsonProperty("id"               ) var id               : Int?              = null,
        @JsonProperty("available"        ) var available        : Boolean?          = null,
        @JsonProperty("genres"           ) var genres           : ArrayList<Genres> = arrayListOf(),
        @JsonProperty("videos"           ) var videos           : ArrayList<Videos> = arrayListOf()
    )

    data class RecommendationsList (
        @JsonProperty("docs"  ) var docs  : ArrayList<LoadDocs> = arrayListOf(),
        @JsonProperty("total" ) var total : Int?            = null,
        @JsonProperty("page"  ) var page  : Int?            = null,
        @JsonProperty("limit" ) var limit : Int?            = null,
        @JsonProperty("pages" ) var pages : Int?            = null,
        @JsonProperty("type"  ) var type  : String?         = null,
    )

    data class LoadDocs (
        @JsonProperty("name"             ) var name             : String           = String(),
        @JsonProperty("originalLanguage" ) var originalLanguage : String?           = null,
        @JsonProperty("popularity"       ) var popularity       : Double?           = null,
        @JsonProperty("runtime"          ) var runtime          : Int?              = null,
        @JsonProperty("status"           ) var status           : String?           = null,
        @JsonProperty("voteAverage"      ) var voteAverage      : Double?           = null,
        @JsonProperty("voteCount"        ) var voteCount        : Int?              = null,
        @JsonProperty("cast"             ) var cast             : String?           = null,
        @JsonProperty("director"         ) var director         : String?           = null,
        @JsonProperty("overview"         ) var overview         : String?           = null,
        @JsonProperty("posterUrl"        ) var posterUrl        : String?           = null,
        @JsonProperty("releaseDate"      ) var releaseDate      : String?           = null,
        @JsonProperty("createdAt"        ) var createdAt        : String?           = null,
        @JsonProperty("updatedAt"        ) var updatedAt        : String?           = null,
        @JsonProperty("id"               ) var id               : Int?              = null,
        @JsonProperty("available"        ) var available        : Boolean?          = null,
    )

    private fun cleanTitle(title: String): String =
        (title.replace(" - ","-").replace(" ","-").replace("-&","").replace(Regex("(:|-&)"),"")
            .replace("'","-")).lowercase()
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val scripttext = soup.select("script[type=application/json]").map { it.data() }.first()
        val tvtype = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val json = parseJson<LoadMain>(scripttext)
        val episodes = ArrayList<TvSeriesEpisode>()
        val pageMain = json.props.pageProps

        val available = if (tvtype == TvType.Movie) pageMain.movie?.available
        else pageMain.selectedTv?.available
        val typetext = if (tvtype == TvType.Movie) "This movie is not available on the site."
        else "This tv show has no episodes available on the site."
        if (available == false) throw ErrorLoadingException(typetext)

        val movieId = if (tvtype == TvType.Movie) pageMain.movie?.id else
            pageMain.selectedTv?.id

        val movietitle = if (tvtype == TvType.Movie) pageMain.movie?.name else
            pageMain.selectedTv?.name
        val poster = if (tvtype == TvType.Movie) pageMain.movie?.posterUrl else
            pageMain.selectedTv?.posterUrl


        val description = if (tvtype == TvType.Movie) pageMain.movie?.overview else
            pageMain.selectedTv?.overview

        if (tvtype == TvType.TvSeries) {
            pageMain.selectedTv?.seasons?.map { seasons ->
                val seasonPoster = seasons.posterUrl ?: pageMain.selectedTv!!.posterUrl
                seasons.episodes.forEach { epi ->
                    val episodenu = epi.episodeNumber
                    val seasonum = epi.seasonNumber
                    val title = epi.name
                    val epDesc = epi.overview
                    val test = epi.videos
                    val ratinginfo = (epi.voteAverage)?.times(10)?.toInt()
                    val rating = if (ratinginfo?.equals(0) == true) null else ratinginfo
                    val eps = TvSeriesEpisode(
                        title,
                        seasonum,
                        episodenu,
                        "$mainUrl/tv-show/$movieId-${cleanTitle(movietitle!!)}/season-$seasonum/episode-$episodenu",
                        description = epDesc!!,
                        posterUrl = seasonPoster,
                        rating = rating,
                    )
                    if (test.isEmpty()) {
                        //
                    } else {
                        episodes.add(eps)
                    }
                }
            }
        }
        val rating = if (tvtype == TvType.Movie) (pageMain.movie?.voteAverage)?.toFloat()?.times(1000)?.toInt() else
            (pageMain.selectedTv?.voteAverage)?.toFloat()?.times(1000)?.toInt()

        val tags = if (tvtype == TvType.Movie) pageMain.movie?.genres?.map { it.name }
        else pageMain.selectedTv?.genres?.map { it.name }

        val recommendationsitem =  pageMain.recommendationsList?.docs?.map { loadDocs ->
            val title = loadDocs.name
            val posterrec = loadDocs.posterUrl
            val link = if (tvtype == TvType.Movie) "$mainUrl/movie/${loadDocs.id}-${cleanTitle(title)}"
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

        val castlist = if (tvtype == TvType.Movie) pageMain.movie?.cast?.split(",")?.map { cast ->
            ActorData(
                Actor(
                    name = cast
                )
            )
        } else
            pageMain.selectedTv?.cast?.split(",")?.map { cast ->
                ActorData(
                    Actor(
                        name = cast
                    )
                )
            }

        val year = if (tvtype == TvType.Movie) pageMain.movie?.releaseDate?.substringBefore("-") else
            pageMain.selectedTv?.releaseDate?.substringBefore("-")

        val runtime = if (tvtype == TvType.Movie) (pageMain.movie?.runtime)?.div(60) else
            (pageMain.selectedTv?.episodeRuntime)?.div(60)

        return when (tvtype) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    movietitle!!,
                    url,
                    this.name,
                    tvtype,
                    episodes,
                    poster,
                    year?.toIntOrNull(),
                    description,
                    null,
                    null,
                    rating,
                    tags,
                    recommendations = recommendationsitem,
                    actors = castlist,
                    duration = runtime

                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    movietitle!!,
                    url,
                    this.name,
                    tvtype,
                    url,
                    poster,
                    year?.toIntOrNull(),
                    description,
                    null,
                    rating,
                    tags,
                    recommendations = recommendationsitem,
                    actors = castlist,
                    duration = runtime
                )
            }
            else -> null
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val script = doc.select("script[type=application/json]").map { it.data() }.first()
        val json = parseJson<LoadMain>(script)
        val extractedLink = json.props.pageProps.videoUrl
        val qualityReg = Regex("(\\d+p)")
        if (extractedLink != null) {
            val quality = qualityReg.find(extractedLink)?.value ?: ""
            callback(
                ExtractorLink(
                    name,
                    "$name $quality",
                    extractedLink,
                    "",
                    getQualityFromName(quality),
                    false
                ))
        }
        return true
    }
}
