package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.AnimeflvnetProvider
import com.lagradost.cloudstream3.animeproviders.MonoschinosProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


class SonarrProvider : MainAPI() {
    override var name = "Sonarr"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = false
    //override val providerType = ProviderType.ArrProvider
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon)
    override val providerType = ProviderType.ArrProvider


    companion object {
        var overrideUrl: String? = null
        var apiKey: String? = null
        var rootFolderPath: String? = null
        const val ERROR_STRING = "Invalid sonarr credentials ! Please check your configuration"


        suspend fun requestSonarrDownload(data: String?, monitorStatus: String) { // adds the serie to the collection of the server
            data?: throw ErrorLoadingException(SonarrProvider.ERROR_STRING)
            val clearedData = data.removePrefix("[").removeSuffix("]") // removes array delimiter
            val movieObject = JSONObject(clearedData)

            //val movieObject: List<loadJson> = mapper.readValue(data)
            movieObject.put("qualityProfileId", 1)
            movieObject.put("monitored", true)
            movieObject.put("rootFolderPath", rootFolderPath)
            movieObject.put("LanguageProfileId", 1)
            movieObject.put("seasonFolder", true)

            val options = JSONObject()
            options.put("monitor", monitorStatus)
            /* monitor can be:
                all
                future
                missing
                existing
                pilot
                firstSeason
                latestSeason
                none
             */
            options.put("searchForCutoffUnmetEpisodes", false)
            options.put("searchForMissingEpisodes", true)
            options.put("ignoreEpisodesWithFiles", true)
            movieObject.put("addOptions", options)

            /*

            "addOptions":
            {
              "ignoreEpisodesWithFiles": true,
              "ignoreEpisodesWithoutFiles": true,
              "searchForMissingEpisodes": false,
              "searchForCutoffUnmetEpisodes":	false
            }

             */

            val body = movieObject.toString().toRequestBody("application/json;charset=UTF-8".toMediaTypeOrNull())


            val postRequest = mapOf(
                "Accept" to "application/json",
                "X-Api-Key" to apiKey,
            ).let {
                app.post(
                    "$overrideUrl/api/v3/series?apikey=$apiKey",
                    headers= it as Map<String, String>,
                    requestBody=body)
            }

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

        }
    }


    fun getApiKeyAndPath(): Pair<String, String> { // refresh credentials, url and rootFolderPath
        val url = SonarrProvider.overrideUrl
            ?: throw ErrorLoadingException(SonarrProvider.ERROR_STRING)
        mainUrl = url

        if (mainUrl == "NONE" || mainUrl.isBlank()) {
            throw ErrorLoadingException(SonarrProvider.ERROR_STRING)
        }

        val apiKey = SonarrProvider.apiKey
            ?: throw ErrorLoadingException(SonarrProvider.ERROR_STRING)



        val path = SonarrProvider.rootFolderPath ?: throw ErrorLoadingException(SonarrProvider.ERROR_STRING)
        return Pair(apiKey, path)
    }

    data class lookupJson (
        @JsonProperty("tvdbId") var tvdbId:String, //not tmdb but tvdb lol
        @JsonProperty("title") var title: String,
        @JsonProperty("remotePoster") var posterUrl: String?,
        @JsonProperty("year") var year: Int?,
    )

    data class Images (
        @JsonProperty("coverType") var coverType : String?,
        @JsonProperty("remoteUrl") var url : String?,
    )



    data class Seasons (
        @JsonProperty("seasonNumber" ) var seasonNumber : Int?     = null,
        @JsonProperty("monitored"    ) var monitored    : Boolean? = null
    )

    data class loadJson (
        @JsonProperty("title") var title: String,
        @JsonProperty("overview") var plot: String,
        @JsonProperty("year") var year: Int?,
        @JsonProperty("hasFile") var hasFile: Boolean?,
        @JsonProperty("images") var posterUrl: List<Images?>?,
        @JsonProperty("seasons") var season: List<Seasons?>?,
        //@JsonProperty("genres") var genres: List<String?>?,
    )


    data class mainPage (
        @JsonProperty("title") var title: String,
        @JsonProperty("images") var images: List<image>,
        @JsonProperty("network") var network: String,
        @JsonProperty("tvdbId") var tvdbId: String,

    )


    data class image (
        @JsonProperty("coverType") var coverType: String?,
        @JsonProperty("remoteUrl") var remoteUrl: String?,
    )


    override suspend fun load(url: String): LoadResponse {
        val apiKey = getApiKeyAndPath().first
        val tvdbId = url.replace(mainUrl, "").replace("/", "")
        val loadResponse = app.get("$mainUrl/api/v3/series/lookup?term=tvdb:$tvdbId&apikey=$apiKey").text
        val resultsResponse: List<loadJson> = mapper.readValue(loadResponse)
        val contentResponse = resultsResponse[0]
        val returnedPoster = contentResponse.posterUrl?.first { it?.coverType == "poster" }?.url
        val seasonsList = ArrayList<Episode>()

        contentResponse.season?.map { seasons ->
            val seasonNumber: Int? = seasons?.seasonNumber
            //val monitored = seasons?.monitored
            if (seasonNumber != 0) {
                seasonsList.add(
                    Episode(
                        //seasonNumber.toString(),
                        //loadResponse.replace("\"seasonNumber\": $seasonNumber,\n        \"monitored\": false", "\"seasonNumber\": $seasonNumber,\n        \"monitored\": true"),
                        loadResponse,
                        "Season: $seasonNumber",
                    )
                )
            }
        }
        seasonsList.add(
            Episode(
                loadResponse,
                "Special episodes",
            )
        )
        return newSonarrTvSeriesLoadResponse(contentResponse.title, contentResponse.title, TvType.TvSeries, seasonsList) {  // here url = loadResponse
            this.year = contentResponse.year
            this.plot = contentResponse.plot
            this.posterUrl = returnedPoster
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val apiKey = getApiKeyAndPath().first


        val searchResponse = app.get("$mainUrl/api/v3/series/lookup?term=$query&apikey=$apiKey").text
        val results: List<lookupJson> = mapper.readValue(searchResponse)

        return results.map {
            newTvSeriesSearchResponse(it.title, it.tvdbId, TvType.TvSeries, fix=false) {  // here url = tmdbId
                // this.year = it.year
                this.posterUrl = it.posterUrl
            }
        }
    }


    override suspend fun getMainPage(): HomePageResponse {
        val apiKey = getApiKeyAndPath().first
        val collectionResponse = app.get("$mainUrl/api/v3/series?apikey=$apiKey").text
        val resultsResponse: List<mainPage> = mapper.readValue(collectionResponse)
        val items = java.util.ArrayList<HomePageList>()
        val listOfNetworks = ArrayList<String>() // can be Netflix or Disney or smth, will be the category displayed
        for (serie in resultsResponse) {
            if (serie.network !in listOfNetworks){
                listOfNetworks.add(serie.network)
            }
        }
        for (network in listOfNetworks) {
            val listOfMatchingSerie = ArrayList<TvSeriesSearchResponse>()
            for (serie in resultsResponse) {
                if (serie.network == network) {
                    listOfMatchingSerie.add(newTvSeriesSearchResponse(
                        serie.title,
                        serie.tvdbId,
                        TvType.TvSeries,
                    ) {
                        this.posterUrl = serie.images.first { it.coverType == "poster" }.remoteUrl
                    })
                }
            }
            items.add(HomePageList(network,listOfMatchingSerie))
        }
        return HomePageResponse(items)
    }

}