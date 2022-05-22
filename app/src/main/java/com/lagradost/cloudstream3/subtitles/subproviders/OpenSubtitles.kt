package com.lagradost.cloudstream3.subtitles.subproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
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
        @JsonProperty("files") var files: List<ResultFiles>? = listOf()
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

    /*
        Authorize app to connect to API, using username/password.
        Required to run at startup.
        Returns OAuth entity with valid access token.
     */
    override suspend fun authorize(ouath: SubtitleOAuthEntity): SubtitleOAuthEntity {
        val _ouath = SubtitleOAuthEntity(
            user = ouath.user,
            pass = ouath.pass,
            access_token = ouath.access_token
        )
        try {
            val data = app.post(
                url = "$host/login",
                headers = mapOf(
                    Pair("Api-Key", apiKey),
                    Pair("Content-Type", "application/json")
                ),
                data = mapOf(
                    Pair("username", _ouath.user),
                    Pair("password", _ouath.pass)
                )
            )
            if (data.isSuccessful) {
                Log.i(TAG, "Result => ${data.text}")
                tryParseJson<OAuthToken>(data.text)?.let {
                    _ouath.access_token = it.token ?: _ouath.access_token
                }
                Log.i(TAG, "OAuth => ${_ouath.toJson()}")
            }
        } catch (e: Exception) {
            logError(e)
        }
        return _ouath
    }

    /*
        Fetch subtitles using token authenticated on previous method (see authorize).
        Returns list of Subtitles which user can select to download (see load).
     */
    override suspend fun search(query: SubtitleSearch): List<SubtitleEntity> {
        val results = mutableListOf<SubtitleEntity>()
        val imdb_id = query.imdb ?: 0
        val queryText = query.query.replace(" ", "+")
        val search_query_url = when (imdb_id > 0) {
            //Use imdb_id to search if its valid
            true -> "$host/subtitles?imdb_id=$imdb_id&languages=${query.lang}"
            false -> "$host/subtitles?query=$queryText&languages=${query.lang}"
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
                        val name = attr.release ?: ""
                        val lang = attr.language ?: ""
                        val type = item.type ?: ""
                        //Log.i(TAG, "Result id/name => ${item.id} / $name")
                        item.attributes?.files?.forEach { file ->
                            val resultData = file.fileId?.toString() ?: ""
                            //Log.i(TAG, "Result file => ${file.fileId} / ${file.fileName}")
                            results.add(
                                SubtitleEntity(
                                    name = name,
                                    lang = lang,
                                    data = resultData,
                                    type = type
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
            Log.i(TAG, "search^")
        }
        return results
    }
    /*
        Process data returned from search.
        Returns string url for the subtitle file.
     */
    override suspend fun load(ouath: SubtitleOAuthEntity, data: SubtitleEntity): String {
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
                    if (link.isNotEmpty()) {
                        return link
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return ""
    }
}