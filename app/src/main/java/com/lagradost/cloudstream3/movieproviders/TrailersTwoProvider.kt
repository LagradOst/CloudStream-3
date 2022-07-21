package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TrailersTwoProvider : TmdbProvider() {
    val user = "cloudstream"
    override val apiName = "Trailers.to"
    override var name = "Trailers.to"
    override var mainUrl = "https://trailers.to"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = true

    @Serializable
    data class TrailersEpisode(
        // val tvShowItemID: Long?,
        //val tvShow: String,
        //val tvShowIMDB: String?,
        //val tvShowTMDB: Long?,
        @SerialName("ItemID")
        val itemID: Int,
        //val title: String,
        //@SerialName("IMDb")
        @SerialName("IMDb")
        val imdb: String?,
        //@SerialName("TMDb")
        @SerialName("TMDb")
        val tmdb: Int?,
        //val releaseDate: String,
        //val entryDate: String
    )

    @Serializable
    data class TrailersMovie(
        @SerialName("ItemID")
        val itemID: Int,
        @SerialName("IMDb")
        val imdb: String?,
        @SerialName("TMDb")
        val tmdb: Int?,
        //@SerialName("Title")
        //val title: String?,
    )

    /*companion object {
        private var tmdbToIdMovies: HashMap<Int, Int> = hashMapOf()
        private var imdbToIdMovies: HashMap<String, Int> = hashMapOf()
        private var tmdbToIdTvSeries: HashMap<Int, Int> = hashMapOf()
        private var imdbToIdTvSeries: HashMap<String, Int> = hashMapOf()

        private const val startDate = 1900
        private const val endDate = 9999

        fun getEpisode(tmdb: Int?, imdb: String?): Int? {
            var currentId: Int? = null
            if (tmdb != null) {
                currentId = tmdbToIdTvSeries[tmdb]
            }
            if (imdb != null && currentId == null) {
                currentId = imdbToIdTvSeries[imdb]
            }
            return currentId
        }

        fun getMovie(tmdb: Int?, imdb: String?): Int? {
            var currentId: Int? = null
            if (tmdb != null) {
                currentId = tmdbToIdMovies[tmdb]
            }
            if (imdb != null && currentId == null) {
                currentId = imdbToIdMovies[imdb]
            }
            return currentId
        }

        suspend fun fillData(isMovie: Boolean) {
            if (isMovie) {
                if (tmdbToIdMovies.isNotEmpty() || imdbToIdMovies.isNotEmpty()) {
                    return
                }
                parseJson<List<TrailersMovie>>(
                    app.get(
                        "https://trailers.to/movies?from=$startDate-01-01&to=$endDate",
                        timeout = 30
                    ).text
                ).forEach { movie ->
                    movie.imdb?.let {
                        imdbToIdTvSeries[it] = movie.itemID
                    }
                    movie.tmdb?.let {
                        tmdbToIdTvSeries[it] = movie.itemID
                    }
                }
            } else {
                if (tmdbToIdTvSeries.isNotEmpty() || imdbToIdTvSeries.isNotEmpty()) {
                    return
                }
                parseJson<List<TrailersEpisode>>(
                    app.get(
                        "https://trailers.to/episodes?from=$startDate-01-01&to=$endDate",
                        timeout = 30
                    ).text
                ).forEach { episode ->
                    episode.imdb?.let {
                        imdbToIdTvSeries[it] = episode.itemID
                    }
                    episode.tmdb?.let {
                        tmdbToIdTvSeries[it] = episode.itemID
                    }
                }
            }
        }
    }*/

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        //TvType.AnimeMovie,
        //TvType.Anime,
        //TvType.Cartoon
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = parseJson<TmdbLink>(data)
        val (id, site) = if (mappedData.imdbID != null) listOf(
            mappedData.imdbID,
            "imdb"
        ) else listOf(mappedData.tmdbID.toString(), "tmdb")

        val isMovie = mappedData.episode == null && mappedData.season == null
        val (videoUrl, subtitleUrl) = if (isMovie) {
            val suffix = "$user/$site/$id"
            Pair(
                "https://trailers.to/video/$suffix",
                "https://trailers.to/subtitles/$suffix"
            )
        } else {
            val suffix = "$user/$site/$id/S${mappedData.season ?: 1}E${mappedData.episode ?: 1}"
            Pair(
                "https://trailers.to/video/$suffix",
                "https://trailers.to/subtitles/$suffix"
            )
        }

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                videoUrl,
                "https://trailers.to",
                Qualities.Unknown.value,
                false,
            )
        )

        argamap(
            {
                val subtitles =
                    app.get(subtitleUrl).text
                val subtitlesMapped = parseJson<List<TrailersSubtitleFile>>(subtitles)
                subtitlesMapped.forEach {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            SubtitleHelper.fromTwoLettersToLanguage(it.LanguageCode ?: "en")
                                ?: "English",
                            "https://trailers.to/subtitles/${it.ContentHash ?: return@forEach}/${it.LanguageCode ?: return@forEach}.vtt" // ${it.MetaInfo?.SubFormat ?: "srt"}"
                        )
                    )
                }
            }, {
                //https://trailers.to/en/quick-search?q=iron man
                val name = mappedData.movieName
                if (name != null && isMovie) {
                    app.get("https://trailers.to/en/quick-search?q=${name}").document.select("a.post-minimal")
                        .mapNotNull {
                            it?.attr("href")
                        }.map { Regex("""/movie/(\d+)/""").find(it)?.groupValues?.getOrNull(1) }
                        .firstOrNull()?.let { movieId ->
                            val correctUrl = app.get(videoUrl).url
                            callback.invoke(
                                ExtractorLink(
                                    this.name,
                                    "${this.name} Backup",
                                    correctUrl.replace("/$user/0/", "/$user/$movieId/"),
                                    "https://trailers.to",
                                    Qualities.Unknown.value,
                                    false,
                                )
                            )
                        }
                }
            }
        )

        /*
        // the problem with this code is that it tages ages and the json file is 50mb or so for movies
        fillData(isMovie)
        val movieId = if (isMovie) {
            getMovie(mappedData.tmdbID, mappedData.imdbID)
        } else {
            getEpisode(mappedData.tmdbID, mappedData.imdbID)
        } ?: return@argamap
        val request = app.get(data)
        val endUrl = request.url
        callback.invoke(
            ExtractorLink(
                this.name,
                "${this.name} Backup",
                endUrl.replace("/cloudstream/0/", "/cloudstream/$movieId/"),
                "https://trailers.to",
                Qualities.Unknown.value,
                false,
            )
        )
         */

        return true
    }
}

// Auto generated
@Serializable
data class TrailersSubtitleFile(
    @SerialName("SubtitleID") val SubtitleID: Int?,
    @SerialName("ItemID") val ItemID: Int?,
    @SerialName("ContentText") val ContentText: String?,
    @SerialName("ContentHash") val ContentHash: String?,
    @SerialName("LanguageCode") val LanguageCode: String?,
    @SerialName("MetaInfo") val MetaInfo: MetaInfo?,
    @SerialName("EntryDate") val EntryDate: String?,
    @SerialName("ItemSubtitleAdaptations") val ItemSubtitleAdaptations: List<ItemSubtitleAdaptations>?,
    @SerialName("ReleaseNames") val ReleaseNames: List<String>?,
    @SerialName("SubFileNames") val SubFileNames: List<String>?,
    @SerialName("Framerates") val Framerates: List<Int>?,
    @SerialName("IsRelevant") val IsRelevant: Boolean?
)

@Serializable
data class QueryParameters(
    @SerialName("imdbid") val imdbid: String?
)

@Serializable
data class MetaInfo(
    @SerialName("MatchedBy") val MatchedBy: String?,
    @SerialName("IDSubMovieFile") val IDSubMovieFile: String?,
    @SerialName("MovieHash") val MovieHash: String?,
    @SerialName("MovieByteSize") val MovieByteSize: String?,
    @SerialName("MovieTimeMS") val MovieTimeMS: String?,
    @SerialName("IDSubtitleFile") val IDSubtitleFile: String?,
    @SerialName("SubFileName") val SubFileName: String?,
    @SerialName("SubActualCD") val SubActualCD: String?,
    @SerialName("SubSize") val SubSize: String?,
    @SerialName("SubHash") val SubHash: String?,
    @SerialName("SubLastTS") val SubLastTS: String?,
    @SerialName("SubTSGroup") val SubTSGroup: String?,
    @SerialName("InfoReleaseGroup") val InfoReleaseGroup: String?,
    @SerialName("InfoFormat") val InfoFormat: String?,
    @SerialName("InfoOther") val InfoOther: String?,
    @SerialName("IDSubtitle") val IDSubtitle: String?,
    @SerialName("UserID") val UserID: String?,
    @SerialName("SubLanguageID") val SubLanguageID: String?,
    @SerialName("SubFormat") val SubFormat: String?,
    @SerialName("SubSumCD") val SubSumCD: String?,
    @SerialName("SubAuthorComment") val SubAuthorComment: String?,
    @SerialName("SubAddDate") val SubAddDate: String?,
    @SerialName("SubBad") val SubBad: String?,
    @SerialName("SubRating") val SubRating: String?,
    @SerialName("SubSumVotes") val SubSumVotes: String?,
    @SerialName("SubDownloadsCnt") val SubDownloadsCnt: String?,
    @SerialName("MovieReleaseName") val MovieReleaseName: String?,
    @SerialName("MovieFPS") val MovieFPS: String?,
    @SerialName("IDMovie") val IDMovie: String?,
    @SerialName("IDMovieImdb") val IDMovieImdb: String?,
    @SerialName("MovieName") val MovieName: String?,
    @SerialName("MovieNameEng") val MovieNameEng: String?,
    @SerialName("MovieYear") val MovieYear: String?,
    @SerialName("MovieImdbRating") val MovieImdbRating: String?,
    @SerialName("SubFeatured") val SubFeatured: String?,
    @SerialName("UserNickName") val UserNickName: String?,
    @SerialName("SubTranslator") val SubTranslator: String?,
    @SerialName("ISO639") val ISO639: String?,
    @SerialName("LanguageName") val LanguageName: String?,
    @SerialName("SubComments") val SubComments: String?,
    @SerialName("SubHearingImpaired") val SubHearingImpaired: String?,
    @SerialName("UserRank") val UserRank: String?,
    @SerialName("SeriesSeason") val SeriesSeason: String?,
    @SerialName("SeriesEpisode") val SeriesEpisode: String?,
    @SerialName("MovieKind") val MovieKind: String?,
    @SerialName("SubHD") val SubHD: String?,
    @SerialName("SeriesIMDBParent") val SeriesIMDBParent: String?,
    @SerialName("SubEncoding") val SubEncoding: String?,
    @SerialName("SubAutoTranslation") val SubAutoTranslation: String?,
    @SerialName("SubForeignPartsOnly") val SubForeignPartsOnly: String?,
    @SerialName("SubFromTrusted") val SubFromTrusted: String?,
    @SerialName("QueryCached") val QueryCached: Int?,
    @SerialName("SubTSGroupHash") val SubTSGroupHash: String?,
    @SerialName("SubDownloadLink") val SubDownloadLink: String?,
    @SerialName("ZipDownloadLink") val ZipDownloadLink: String?,
    @SerialName("SubtitlesLink") val SubtitlesLink: String?,
    @SerialName("QueryNumber") val QueryNumber: String?,
    @SerialName("QueryParameters") val QueryParameters: QueryParameters?,
    @SerialName("Score") val Score: Double?
)

@Serializable
data class ItemSubtitleAdaptations(
    @SerialName("ContentHash") val ContentHash: String?,
    @SerialName("OffsetMs") val OffsetMs: Int?,
    @SerialName("Framerate") val Framerate: Int?,
    @SerialName("Views") val Views: Int?,
    @SerialName("EntryDate") val EntryDate: String?,
    @SerialName("Subtitle") val Subtitle: String?
)