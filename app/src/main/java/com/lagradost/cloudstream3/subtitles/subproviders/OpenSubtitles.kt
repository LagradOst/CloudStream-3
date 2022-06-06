package com.lagradost.cloudstream3.subtitles.subproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.subtitles.AbstractSubProvider
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class OpenSubtitles: AbstractSubProvider() {
    override val name = "Opensubtitles"

    val host = "https://api.opensubtitles.com/api/v1"
    val apiKey = ""
    val TAG = "ApiError"

    data class OAuthToken (
        @JsonProperty("token") var token: String? = null,
        @JsonProperty("status") var status: Int? = null
    )
    data class Results(
        @JsonProperty("data") var data: List<ResultData>? = listOf()
    )
    data class ResultData(
        @JsonProperty("id") var id: String? = null,
        @JsonProperty("type") var type: String? = null,
        @JsonProperty("attributes") var attributes: ResultAttributes? = ResultAttributes()
    )
    data class ResultAttributes(
        @JsonProperty("subtitle_id") var subtitleId: String? = null,
        @JsonProperty("language") var language: String? = null,
        @JsonProperty("release") var release: String? = null,
        @JsonProperty("url") var url: String? = null,
        @JsonProperty("files") var files: List<ResultFiles>? = listOf(),
        @JsonProperty("feature_details") var featDetails: ResultFeatureDetails? = ResultFeatureDetails()
    )
    data class ResultFiles(
        @JsonProperty("file_id") var fileId: Int? = null,
        @JsonProperty("file_name") var fileName: String? = null
    )
    data class ResultDownloadLink(
        @JsonProperty("link") var link: String? = null,
        @JsonProperty("file_name") var fileName: String? = null,
        @JsonProperty("requests") var requests: Int? = null,
        @JsonProperty("remaining") var remaining: Int? = null,
        @JsonProperty("message") var message: String? = null,
        @JsonProperty("reset_time") var resetTime: String? = null,
        @JsonProperty("reset_time_utc") var resetTimeUtc: String? = null
    )
    data class ResultFeatureDetails(
        @JsonProperty("year") var year: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("movie_name") var movieName: String? = null,
        @JsonProperty("imdb_id") var imdbId: Int? = null,
        @JsonProperty("tmdb_id") var tmdbId: Int? = null,
        @JsonProperty("season_number") var seasonNumber: Int? = null,
        @JsonProperty("episode_number") var episodeNumber: Int? = null,
        @JsonProperty("parent_imdb_id") var parentImdbId: Int? = null,
        @JsonProperty("parent_title") var parentTitle: String? = null,
        @JsonProperty("parent_tmdb_id") var parentTmdbId: Int? = null,
        @JsonProperty("parent_feature_id") var parentFeatureId: Int? = null
    )

    init {
        Log.i(TAG, "Initialize ${this.name}.")
    }

    /*
        Authorize app to connect to API, using username/password.
        Required to run at startup.
        Returns OAuth entity with valid access token.
     */
    override suspend fun authorize() {
        Log.i(TAG, "OAuth => ${ouath.toJson()}")
        try {
            val data = app.post(
                url = "$host/login",
                headers = mapOf(
                    Pair("Api-Key", apiKey),
                    Pair("Content-Type", "application/json")
                ),
                data = mapOf(
                    Pair("username", ouath.user),
                    Pair("password", ouath.pass)
                )
            )
            if (data.isSuccessful) {
                Log.i(TAG, "Result => ${data.text}")
                tryParseJson<OAuthToken>(data.text)?.let {
                    ouath.access_token = it.token ?: ouath.access_token
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    /*
        Fetch subtitles using token authenticated on previous method (see authorize).
        Returns list of Subtitles which user can select to download (see load).
     */
    override suspend fun search(query: SubtitleSearch): List<SubtitleEntity> {
        val results = mutableListOf<SubtitleEntity>()
        val imdb_id = query.imdb ?: 0
        val queryText = query.query.replace(" ", "+")
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0
        val epQuery = if (epNum > 0) "&episode_number=$epNum" else ""
        val seasonQuery = if (seasonNum > 0) "&season_number=$seasonNum" else ""
        val yearQuery = if (yearNum > 0) "&year=$yearNum" else ""

        val search_query_url = when (imdb_id > 0) {
            //Use imdb_id to search if its valid
            true -> "$host/subtitles?imdb_id=$imdb_id&languages=${query.lang}$yearQuery$epQuery$seasonQuery"
            false -> "$host/subtitles?query=$queryText&languages=${query.lang}$yearQuery$epQuery$seasonQuery"
        }
        try {
            val req = app.get(
                url = search_query_url,
                headers = mapOf(
                    Pair("Api-Key", apiKey),
                    Pair("Content-Type", "application/json")
                )
            )
            Log.i(TAG, "Search Req => ${req.text}")
            if (req.isSuccessful) {
                tryParseJson<Results>(req.text)?.let {
                    it.data?.forEach { item ->
                        val attr = item.attributes ?: return@forEach
                        val featureDetails = attr.featDetails
                        //Use any valid name/title in hierarchy
                        val name = featureDetails?.movieName ?:
                            featureDetails?.title ?:
                            featureDetails?.parentTitle ?:
                            attr.release ?: ""
                        val lang = attr.language ?: ""
                        val resEpNum = featureDetails?.episodeNumber ?: query.epNumber
                        val resSeasonNum = featureDetails?.seasonNumber ?: query.seasonNumber
                        val year = featureDetails?.year ?: query.year
                        val type = if (resSeasonNum ?: 0 > 0) TvType.TvSeries else TvType.Movie
                        //Log.i(TAG, "Result id/name => ${item.id} / $name")
                        item.attributes?.files?.forEach { file ->
                            val resultData = file.fileId?.toString() ?: ""
                            //Log.i(TAG, "Result file => ${file.fileId} / ${file.fileName}")
                            results.add(
                                SubtitleEntity(
                                    name = name,
                                    lang = lang,
                                    data = resultData,
                                    type = type,
                                    epNumber = resEpNum,
                                    seasonNumber = resSeasonNum,
                                    year = year
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return results
    }
    /*
        Process data returned from search.
        Returns string url for the subtitle file.
     */
    override suspend fun load(data: SubtitleEntity): String {
        try {
            val req = app.post(
                url = "$host/download",
                headers = mapOf(
                    Pair("Authorization", "Bearer ${ouath.access_token}"),
                    Pair("Api-Key", apiKey),
                    Pair("Content-Type", "application/json"),
                    Pair("Accept", "*/*")
                ),
                data = mapOf(
                    Pair("file_id", data.data)
                )
            )
            Log.i(TAG, "Request result  => (${req.code}) ${req.text}")
            //Log.i(TAG, "Request headers => ${req.headers}")
            if (req.isSuccessful) {
                tryParseJson<ResultDownloadLink>(req.text)?.let {
                    val link = it.link ?: ""
                    Log.i(TAG, "Request load link => $link")
                    return link
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return ""
    }
}