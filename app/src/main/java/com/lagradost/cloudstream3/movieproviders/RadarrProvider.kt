package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.net.MediaType
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


class RadarrProvider : MainAPI() {
    override var name = "Radarr"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false

    override val providerType = ProviderType.ArrProvider
    override val supportedTypes =
        setOf(TvType.Movie, TvType.Documentary, TvType.AnimeMovie, TvType.AnimeMovie)


    companion object {
        var overrideUrl: String? = null
        var apiKey: String? = null
        var rootFolderPath: String? = null
        const val ERROR_STRING = "No valid radarr account ! Please check your configuration"

        suspend fun requestRadarrDownload(data: String?) { // adds the serie to the collection of the server
            data?: throw ErrorLoadingException(RadarrProvider.ERROR_STRING) // error, no data provided by load function
            val movieObject = JSONObject(data)
            movieObject.put("qualityProfileId", 1) // should add an option
            movieObject.put("monitored", true)
            movieObject.put("rootFolderPath", rootFolderPath)
            val body = movieObject.toString()
                .toRequestBody("application/json;charset=UTF-8".toMediaTypeOrNull())


            val postRequest = mapOf(
                "Accept" to "application/json",
                "X-Api-Key" to apiKey,
            ).let {
                app.post(
                    "$overrideUrl/api/v3/movie?apikey=$apiKey",
                    headers = it as Map<String, String>,
                    requestBody = body
                )
            }
        }
    }


    fun getApiKeyAndPath(): Pair<String, String> { // refresh credentials, url and rootFolderPath
        val url = RadarrProvider.overrideUrl
            ?: throw ErrorLoadingException(RadarrProvider.ERROR_STRING)
        mainUrl = url
        if (mainUrl == "NONE" || mainUrl.isBlank()) {
            throw ErrorLoadingException(RadarrProvider.ERROR_STRING)
        }

        val apiKey = RadarrProvider.apiKey
            ?: throw ErrorLoadingException(RadarrProvider.ERROR_STRING)

        val path = RadarrProvider.rootFolderPath ?: throw ErrorLoadingException(RadarrProvider.ERROR_STRING)
        return Pair(apiKey, path)
    }

    data class lookupJson(
        @JsonProperty("tmdbId") var tmdbId: String,
        @JsonProperty("title") var title: String,
        @JsonProperty("remotePoster") var posterUrl: String?,
        @JsonProperty("year") var year: Int?,
    )

    data class Images(
        @JsonProperty("coverType") var coverType: String,
        @JsonProperty("url") var url: String
    )

    data class loadJson(
        @JsonProperty("title") var title: String,
        @JsonProperty("overview") var plot: String,
        @JsonProperty("year") var year: Int?,
        //@JsonProperty("hasFile") var hasFile: Boolean?,
        @JsonProperty("images") var posterUrl: List<Images?>?,
        //@JsonProperty("genres") var genres: List<String?>?,
    )


    data class mainPage (
        @JsonProperty("title") var title: String,
        @JsonProperty("images") var images: List<image>,
        @JsonProperty("tmdbId") var tmdbId: String,
    )

    data class image (
        @JsonProperty("coverType") var coverType: String?,
        @JsonProperty("remoteUrl") var remoteUrl: String?,
    )

    override suspend fun load(url: String): LoadResponse {
        val (apiKey, path) = getApiKeyAndPath()
        val tmdbId = url.replace(mainUrl, "").replace("/", "")
        val loadResponse =
            app.get("$mainUrl/api/v3/movie/lookup/tmdb?tmdbId=$tmdbId&apikey=$apiKey").text
        println(loadResponse)
        val resultsResponse: loadJson = mapper.readValue(loadResponse)
        val returnedPoster = resultsResponse.posterUrl?.first { it?.coverType == "poster" }?.url
        return newMovieLoadResponse(
            resultsResponse.title,
            tmdbId,
            TvType.Movie,
            loadResponse
        ) {  // here url = loadResponse
            this.year = resultsResponse.year
            this.plot = resultsResponse.plot
            this.posterUrl = returnedPoster
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val (apiKey, path) = getApiKeyAndPath()
        val searchResponse =
            app.get("$mainUrl/api/v3/movie/lookup?term=$query&apikey=$apiKey").text
        val results: List<lookupJson> = mapper.readValue(searchResponse)

        return results.map {
            newMovieSearchResponse(
                it.title,
                it.tmdbId,
                TvType.Movie,
                fix = false
            ) {  // here url = tmdbId
                // this.year = it.year
                this.posterUrl = it.posterUrl
            }
        }
    }

    
    override suspend fun getMainPage(): HomePageResponse {
        val apiKey = getApiKeyAndPath().first
        val collectionResponse = app.get("$mainUrl/api/v3/movie?apikey=$apiKey").text
        val resultsResponse: List<mainPage> = mapper.readValue(collectionResponse)
        val items = java.util.ArrayList<HomePageList>()
        val listOfMovie = ArrayList<MovieSearchResponse>()
        for (movie in resultsResponse) {
            listOfMovie.add(newMovieSearchResponse(
                    movie.title,
                    movie.tmdbId,
                    TvType.Movie,
                ) {
                    this.posterUrl = movie.images.first { it.coverType == "poster" }.remoteUrl
                })
        }
        items.add(HomePageList("Movies", listOfMovie))
        return HomePageResponse(items)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        // These callbacks are functions you should call when you get a link to a subtitle file or media file.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        /*
        if (postRequest.isSuccessful) {
            println("working well")
        } else {
            println("ERROR HERE:")
            println(postRequest.document)
            println(postRequest.okhttpResponse)
            println(postRequest.body)
            println(postRequest.headers)
            println(postRequest.code)
            println(postRequest.isSuccessful)
        }
         */
        return false
    }

}