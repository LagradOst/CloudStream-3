package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Base64
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.splitQuery
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL
import java.security.SecureRandom
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/** max 100 via https://myanimelist.net/apiconfig/references/api/v2#tag/anime */
const val MAL_MAX_SEARCH_LIMIT = 25

class MALApi(index: Int) : AccountManager(index), SyncAPI {
    override var name = "MAL"
    override val key = "1714d6f2f4f7cc19644384f8c4629910"
    override val redirectUrl = "mallogin"
    override val idPrefix = "mal"
    override var mainUrl = "https://myanimelist.net"
    val apiUrl = "https://api.myanimelist.net"
    override val icon = R.drawable.mal_logo
    override val requiresLogin = false

    override val createAccountUrl = "$mainUrl/register.php"

    override fun logOut() {
        removeAccountKeys()
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        //getMalUser(true)?
        getKey<MalUser>(accountId, MAL_USER_KEY)?.let { user ->
            return AuthAPI.LoginInfo(
                profilePicture = user.picture,
                name = user.name,
                accountIndex = accountIndex
            )
        }
        return null
    }

    private fun getAuth(): String? {
        return getKey(
            accountId,
            MAL_TOKEN_KEY
        )
    }

    override suspend fun search(name: String): List<SyncAPI.SyncSearchResult> {
        val url = "$apiUrl/v2/anime?q=$name&limit=$MAL_MAX_SEARCH_LIMIT"
        val auth = getAuth() ?: return emptyList()
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth",
            ), cacheTime = 0
        ).text
        return parseJson<MalSearch>(res).data.map {
            val node = it.node
            SyncAPI.SyncSearchResult(
                node.title,
                this.name,
                node.id.toString(),
                "$mainUrl/anime/${node.id}/",
                node.main_picture?.large ?: node.main_picture?.medium
            )
        }
    }

    override fun getIdFromUrl(url: String): String {
        return Regex("""/anime/((.*)/|(.*))""").find(url)!!.groupValues.first()
    }

    override suspend fun score(id: String, status: SyncAPI.SyncStatus): Boolean {
        return setScoreRequest(
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(status.status),
            status.score,
            status.watchedEpisodes
        )
    }

    @Serializable
    data class MalAnime(
        @SerialName("id") val id: Int?,
        @SerialName("title") val title: String?,
        @SerialName("main_picture") val mainPicture: MainPicture?,
        @SerialName("alternative_titles") val alternativeTitles: AlternativeTitles?,
        @SerialName("start_date") val startDate: String?,
        @SerialName("end_date") val endDate: String?,
        @SerialName("synopsis") val synopsis: String?,
        @SerialName("mean") val mean: Double?,
        @SerialName("rank") val rank: Int?,
        @SerialName("popularity") val popularity: Int?,
        @SerialName("num_list_users") val numListUsers: Int?,
        @SerialName("num_scoring_users") val numScoringUsers: Int?,
        @SerialName("nsfw") val nsfw: String?,
        @SerialName("created_at") val createdAt: String?,
        @SerialName("updated_at") val updatedAt: String?,
        @SerialName("media_type") val mediaType: String?,
        @SerialName("status") val status: String?,
        @SerialName("genres") val genres: ArrayList<Genres>?,
        @SerialName("my_list_status") val myListStatus: MyListStatus?,
        @SerialName("num_episodes") val numEpisodes: Int?,
        @SerialName("start_season") val startSeason: StartSeason?,
        @SerialName("broadcast") val broadcast: Broadcast?,
        @SerialName("source") val source: String?,
        @SerialName("average_episode_duration") val averageEpisodeDuration: Int?,
        @SerialName("rating") val rating: String?,
        @SerialName("pictures") val pictures: ArrayList<MainPicture>?,
        @SerialName("background") val background: String?,
        @SerialName("related_anime") val relatedAnime: ArrayList<RelatedAnime>?,
        @SerialName("related_manga") val relatedManga: ArrayList<String>?,
        @SerialName("recommendations") val recommendations: ArrayList<Recommendations>?,
        @SerialName("studios") val studios: ArrayList<Studios>?,
        @SerialName("statistics") val statistics: Statistics?,
    )

    @Serializable
    data class Recommendations(
        @SerialName("node") val node: Node? = null,
        @SerialName("num_recommendations") val numRecommendations: Int? = null
    )

    @Serializable
    data class Studios(
        @SerialName("id") val id: Int? = null,
        @SerialName("name") val name: String? = null
    )

    @Serializable
    data class MyListStatus(
        @SerialName("status") val status: String? = null,
        @SerialName("score") val score: Int? = null,
        @SerialName("num_episodes_watched") val numEpisodesWatched: Int? = null,
        @SerialName("is_rewatching") val isRewatching: Boolean? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    @Serializable
    data class RelatedAnime(
        @SerialName("node") val node: Node? = null,
        @SerialName("relation_type") val relationType: String? = null,
        @SerialName("relation_type_formatted") val relationTypeFormatted: String? = null
    )

    @Serializable
    data class Status(
        @SerialName("watching") val watching: String? = null,
        @SerialName("completed") val completed: String? = null,
        @SerialName("on_hold") val onHold: String? = null,
        @SerialName("dropped") val dropped: String? = null,
        @SerialName("plan_to_watch") val planToWatch: String? = null
    )

    @Serializable
    data class Statistics(
        @SerialName("status") val status: Status? = null,
        @SerialName("num_list_users") val numListUsers: Int? = null
    )

    private fun parseDate(string: String?): Long? {
        return try {
            SimpleDateFormat("yyyy-MM-dd")?.parse(string ?: return null)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun toSearchResult(node: Node?): SyncAPI.SyncSearchResult? {
        return SyncAPI.SyncSearchResult(
            name = node?.title ?: return null,
            apiName = this.name,
            syncId = node.id.toString(),
            url = "$mainUrl/anime/${node.id}",
            posterUrl = node.main_picture?.large
        )
    }

    override suspend fun getResult(id: String): SyncAPI.SyncResult? {
        val internalId = id.toIntOrNull() ?: return null
        val url =
            "$apiUrl/v2/anime/$internalId?fields=id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_list_users,num_scoring_users,nsfw,created_at,updated_at,media_type,status,genres,my_list_status,num_episodes,start_season,broadcast,source,average_episode_duration,rating,pictures,background,related_anime,related_manga,recommendations,studios,statistics"

        val auth = getAuth()
        val res = app.get(
            url, headers = if (auth == null) emptyMap() else mapOf(
                "Authorization" to "Bearer $auth"
            )
        ).text
        return parseJson<MalAnime>(res).let { malAnime ->
            SyncAPI.SyncResult(
                id = internalId.toString(),
                totalEpisodes = malAnime.numEpisodes,
                title = malAnime.title,
                publicScore = malAnime.mean?.toFloat()?.times(1000)?.toInt(),
                duration = malAnime.averageEpisodeDuration,
                synopsis = malAnime.synopsis,
                airStatus = when (malAnime.status) {
                    "finished_airing" -> ShowStatus.Completed
                    "currently_airing" -> ShowStatus.Ongoing
                    //"not_yet_aired"
                    else -> null
                },
                nextAiring = null,
                studio = malAnime.studios?.mapNotNull { it.name },
                genres = malAnime.genres?.map { it.name },
                trailers = null,
                startDate = parseDate(malAnime.startDate),
                endDate = parseDate(malAnime.endDate),
                recommendations = malAnime.recommendations?.mapNotNull { rec ->
                    val node = rec.node ?: return@mapNotNull null
                    toSearchResult(node)
                },
                nextSeason = malAnime.relatedAnime?.firstOrNull {
                    return@firstOrNull it.relationType == "sequel"
                }?.let { toSearchResult(it.node) },
                prevSeason = malAnime.relatedAnime?.firstOrNull {
                    return@firstOrNull it.relationType == "prequel"
                }?.let { toSearchResult(it.node) },
                actors = null,
            )
        }
    }

    override suspend fun getStatus(id: String): SyncAPI.SyncStatus? {
        val internalId = id.toIntOrNull() ?: return null

        val data =
            getDataAboutMalId(internalId)?.my_list_status //?: throw ErrorLoadingException("No my_list_status")
        return SyncAPI.SyncStatus(
            score = data?.score,
            status = malStatusAsString.indexOf(data?.status),
            isFavorite = null,
            watchedEpisodes = data?.num_episodes_watched,
        )
    }

    companion object {
        private val malStatusAsString =
            arrayOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")

        const val MAL_USER_KEY: String = "mal_user" // user data like profile
        const val MAL_CACHED_LIST: String = "mal_cached_list"
        const val MAL_SHOULD_UPDATE_LIST: String = "mal_should_update_list"
        const val MAL_UNIXTIME_KEY: String = "mal_unixtime" // When token expires
        const val MAL_REFRESH_TOKEN_KEY: String = "mal_refresh_token" // refresh token
        const val MAL_TOKEN_KEY: String = "mal_token" // anilist token for api
    }

    override suspend fun handleRedirect(url: String): Boolean {
        val sanitizer =
            splitQuery(URL(url.replace(appString, "https").replace("/#", "?"))) // FIX ERROR
        val state = sanitizer["state"]!!
        if (state == "RequestID$requestId") {
            val currentCode = sanitizer["code"]!!

            val res = app.post(
                "$mainUrl/v1/oauth2/token",
                data = mapOf(
                    "client_id" to key,
                    "code" to currentCode,
                    "code_verifier" to codeVerifier,
                    "grant_type" to "authorization_code"
                )
            ).text

            if (res.isNotBlank()) {
                switchToNewAccount()
                storeToken(res)
                val user = getMalUser()
                setKey(MAL_SHOULD_UPDATE_LIST, true)
                return user != null
            }
        }
        return false
    }

    override fun authenticate() {
        // It is recommended to use a URL-safe string as code_verifier.
        // See section 4 of RFC 7636 for more details.

        val secureRandom = SecureRandom()
        val codeVerifierBytes = ByteArray(96) // base64 has 6bit per char; (8/6)*96 = 128
        secureRandom.nextBytes(codeVerifierBytes)
        codeVerifier =
            Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=').replace("+", "-")
                .replace("/", "_").replace("\n", "")
        val codeChallenge = codeVerifier
        val request =
            "$mainUrl/v1/oauth2/authorize?response_type=code&client_id=$key&code_challenge=$codeChallenge&state=RequestID$requestId"
        openBrowser(request)
    }

    private var requestId = 0
    private var codeVerifier = ""

    private fun storeToken(response: String) {
        try {
            if (response != "") {
                val token = parseJson<ResponseToken>(response)
                setKey(accountId, MAL_UNIXTIME_KEY, (token.expires_in + unixTime))
                setKey(accountId, MAL_REFRESH_TOKEN_KEY, token.refresh_token)
                setKey(accountId, MAL_TOKEN_KEY, token.access_token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun refreshToken() {
        try {
            val res = app.post(
                "$mainUrl/v1/oauth2/token",
                data = mapOf(
                    "client_id" to key,
                    "grant_type" to "refresh_token",
                    "refresh_token" to getKey(
                        accountId,
                        MAL_REFRESH_TOKEN_KEY
                    )!!
                )
            ).text
            storeToken(res)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val allTitles = hashMapOf<Int, MalTitleHolder>()

    @Serializable
    data class MalList(
        @SerialName("data") val data: List<Data>,
        @SerialName("paging") val paging: Paging
    )

    @Serializable
    data class MainPicture(
        @SerialName("medium") val medium: String,
        @SerialName("large") val large: String
    )

    @Serializable
    data class Node(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String,
        @SerialName("main_picture") val main_picture: MainPicture?,
        @SerialName("alternative_titles") val alternative_titles: AlternativeTitles?,
        @SerialName("media_type") val media_type: String?,
        @SerialName("num_episodes") val num_episodes: Int?,
        @SerialName("status") val status: String?,
        @SerialName("start_date") val start_date: String?,
        @SerialName("end_date") val end_date: String?,
        @SerialName("average_episode_duration") val average_episode_duration: Int?,
        @SerialName("synopsis") val synopsis: String?,
        @SerialName("mean") val mean: Double?,
        @SerialName("genres") val genres: List<Genres>?,
        @SerialName("rank") val rank: Int?,
        @SerialName("popularity") val popularity: Int?,
        @SerialName("num_list_users") val num_list_users: Int?,
        @SerialName("num_favorites") val num_favorites: Int?,
        @SerialName("num_scoring_users") val num_scoring_users: Int?,
        @SerialName("start_season") val start_season: StartSeason?,
        @SerialName("broadcast") val broadcast: Broadcast?,
        @SerialName("nsfw") val nsfw: String?,
        @SerialName("created_at") val created_at: String?,
        @SerialName("updated_at") val updated_at: String?
    )

    @Serializable
    data class ListStatus(
        @SerialName("status") val status: String?,
        @SerialName("score") val score: Int,
        @SerialName("num_episodes_watched") val num_episodes_watched: Int,
        @SerialName("is_rewatching") val is_rewatching: Boolean,
        @SerialName("updated_at") val updated_at: String,
    )

    @Serializable
    data class Data(
        @SerialName("node") val node: Node,
        @SerialName("list_status") val list_status: ListStatus?,
    )

    @Serializable
    data class Paging(
        @SerialName("next") val next: String?
    )

    @Serializable
    data class AlternativeTitles(
        @SerialName("synonyms") val synonyms: List<String>,
        @SerialName("en") val en: String,
        @SerialName("ja") val ja: String
    )

    @Serializable
    data class Genres(
        @SerialName("id") val id: Int,
        @SerialName("name") val name: String
    )

    @Serializable
    data class StartSeason(
        @SerialName("year") val year: Int,
        @SerialName("season") val season: String
    )

    @Serializable
    data class Broadcast(
        @SerialName("day_of_the_week") val day_of_the_week: String?,
        @SerialName("start_time") val start_time: String?
    )

    private fun getMalAnimeListCached(): Array<Data>? {
        return getKey(MAL_CACHED_LIST) as? Array<Data>
    }

    suspend fun getMalAnimeListSmart(): Array<Data>? {
        if (getAuth() == null) return null
        return if (getKey(MAL_SHOULD_UPDATE_LIST, true) == true) {
            val list = getMalAnimeList()
            setKey(MAL_CACHED_LIST, list)
            setKey(MAL_SHOULD_UPDATE_LIST, false)
            list
        } else {
            getMalAnimeListCached()
        }
    }

    private suspend fun getMalAnimeList(): Array<Data> {
        checkMalToken()
        var offset = 0
        val fullList = mutableListOf<Data>()
        val offsetRegex = Regex("""offset=(\d+)""")
        while (true) {
            val data: MalList = getMalAnimeListSlice(offset) ?: break
            fullList.addAll(data.data)
            offset =
                data.paging.next?.let { offsetRegex.find(it)?.groupValues?.get(1)?.toInt() }
                    ?: break
        }
        return fullList.toTypedArray()
    }

    fun convertToStatus(string: String): MalStatusType {
        return fromIntToAnimeStatus(malStatusAsString.indexOf(string))
    }

    private suspend fun getMalAnimeListSlice(offset: Int = 0): MalList? {
        val user = "@me"
        val auth = getAuth() ?: return null
        // Very lackluster docs
        // https://myanimelist.net/apiconfig/references/api/v2#operation/users_user_id_animelist_get
        val url =
            "$apiUrl/v2/users/$user/animelist?fields=list_status,num_episodes,media_type,status,start_date,end_date,synopsis,alternative_titles,mean,genres,rank,num_list_users,nsfw,average_episode_duration,num_favorites,popularity,num_scoring_users,start_season,favorites_info,broadcast,created_at,updated_at&nsfw=1&limit=100&offset=$offset"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth",
            ), cacheTime = 0
        ).text
        return tryParseJson(res)
    }

    private suspend fun getDataAboutMalId(id: Int): SmallMalAnime? {
        // https://myanimelist.net/apiconfig/references/api/v2#operation/anime_anime_id_get
        val url =
            "$apiUrl/v2/anime/$id?fields=id,title,num_episodes,my_list_status"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer " + (getAuth() ?: return null)
            ), cacheTime = 0
        ).text

        return parseJson<SmallMalAnime>(res)
    }

    suspend fun setAllMalData() {
        val user = "@me"
        var isDone = false
        var index = 0
        allTitles.clear()
        checkMalToken()
        while (!isDone) {
            val res = app.get(
                "$apiUrl/v2/users/$user/animelist?fields=list_status&limit=1000&offset=${index * 1000}",
                headers = mapOf(
                    "Authorization" to "Bearer " + (getAuth() ?: return)
                ), cacheTime = 0
            ).text
            val values = parseJson<MalRoot>(res)
            val titles =
                values.data.map { MalTitleHolder(it.list_status, it.node.id, it.node.title) }
            for (t in titles) {
                allTitles[t.id] = t
            }
            isDone = titles.size < 1000
            index++
        }
    }

    fun convertJapanTimeToTimeRemaining(date: String, endDate: String? = null): String? {
        // No time remaining if the show has already ended
        try {
            endDate?.let {
                if (SimpleDateFormat("yyyy-MM-dd").parse(it).time < System.currentTimeMillis()) return@convertJapanTimeToTimeRemaining null
            }
        } catch (e: ParseException) {
            logError(e)
        }

        // Unparseable date: "2021 7 4 other null"
        // Weekday: other, date: null
        if (date.contains("null") || date.contains("other")) {
            return null
        }

        val currentDate = Calendar.getInstance()
        val currentMonth = currentDate.get(Calendar.MONTH) + 1
        val currentWeek = currentDate.get(Calendar.WEEK_OF_MONTH)
        val currentYear = currentDate.get(Calendar.YEAR)

        val dateFormat = SimpleDateFormat("yyyy MM W EEEE HH:mm")
        dateFormat.timeZone = TimeZone.getTimeZone("Japan")
        val parsedDate =
            dateFormat.parse("$currentYear $currentMonth $currentWeek $date") ?: return null
        val timeDiff = (parsedDate.time - System.currentTimeMillis()) / 1000

        // if it has already aired this week add a week to the timer
        val updatedTimeDiff =
            if (timeDiff > -60 * 60 * 24 * 7 && timeDiff < 0) timeDiff + 60 * 60 * 24 * 7 else timeDiff
        return secondsToReadable(updatedTimeDiff.toInt(), "Now")

    }

    private suspend fun checkMalToken() {
        if (unixTime > (getKey(
                accountId,
                MAL_UNIXTIME_KEY
            ) ?: 0L)
        ) {
            refreshToken()
        }
    }

    private suspend fun getMalUser(setSettings: Boolean = true): MalUser? {
        checkMalToken()
        val res = app.get(
            "$apiUrl/v2/users/@me",
            headers = mapOf(
                "Authorization" to "Bearer " + (getAuth() ?: return null)
            ), cacheTime = 0
        ).text

        val user = parseJson<MalUser>(res)
        if (setSettings) {
            setKey(accountId, MAL_USER_KEY, user)
            registerAccount()
        }
        return user
    }

    enum class MalStatusType(var value: Int) {
        Watching(0),
        Completed(1),
        OnHold(2),
        Dropped(3),
        PlanToWatch(4),
        None(-1)
    }

    private fun fromIntToAnimeStatus(inp: Int): MalStatusType {//= AniListStatusType.values().first { it.value == inp }
        return when (inp) {
            -1 -> MalStatusType.None
            0 -> MalStatusType.Watching
            1 -> MalStatusType.Completed
            2 -> MalStatusType.OnHold
            3 -> MalStatusType.Dropped
            4 -> MalStatusType.PlanToWatch
            5 -> MalStatusType.Watching
            else -> MalStatusType.None
        }
    }

    private suspend fun setScoreRequest(
        id: Int,
        status: MalStatusType? = null,
        score: Int? = null,
        num_watched_episodes: Int? = null,
    ): Boolean {
        val res = setScoreRequest(
            id,
            if (status == null) null else malStatusAsString[maxOf(0, status.value)],
            score,
            num_watched_episodes
        )

        return if (res.isNullOrBlank()) {
            false
        } else {
            val malStatus = parseJson<MalStatus>(res)
            if (allTitles.containsKey(id)) {
                val currentTitle = allTitles[id]!!
                allTitles[id] = MalTitleHolder(malStatus, id, currentTitle.name)
            } else {
                allTitles[id] = MalTitleHolder(malStatus, id, "")
            }
            true
        }
    }

    private suspend fun setScoreRequest(
        id: Int,
        status: String? = null,
        score: Int? = null,
        num_watched_episodes: Int? = null,
    ): String? {
        val data = mapOf(
            "status" to status,
            "score" to score?.toString(),
            "num_watched_episodes" to num_watched_episodes?.toString()
        ).filter { it.value != null } as Map<String, String>

        return app.put(
            "$apiUrl/v2/anime/$id/my_list_status",
            headers = mapOf(
                "Authorization" to "Bearer " + (getAuth() ?: return null)
            ),
            data = data
        ).text
    }


    @Serializable
    data class ResponseToken(
        @SerialName("token_type") val token_type: String,
        @SerialName("expires_in") val expires_in: Int,
        @SerialName("access_token") val access_token: String,
        @SerialName("refresh_token") val refresh_token: String,
    )

    @Serializable
    data class MalRoot(
        @SerialName("data") val data: List<MalDatum>,
    )

    @Serializable
    data class MalDatum(
        @SerialName("node") val node: MalNode,
        @SerialName("list_status") val list_status: MalStatus,
    )

    @Serializable
    data class MalNode(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String,
        /*
        also, but not used
        main_picture ->
            public string medium;
			public string large;
         */
    )

    @Serializable
    data class MalStatus(
        @SerialName("status") val status: String,
        @SerialName("score") val score: Int,
        @SerialName("num_episodes_watched") val num_episodes_watched: Int,
        @SerialName("is_rewatching") val is_rewatching: Boolean,
        @SerialName("updated_at") val updated_at: String,
    )

    @Serializable
    data class MalUser(
        @SerialName("id") val id: Int,
        @SerialName("name") val name: String,
        @SerialName("location") val location: String,
        @SerialName("joined_at") val joined_at: String,
        @SerialName("picture") val picture: String?,
    )

    @Serializable
    data class MalMainPicture(
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
    )

    // Used for getDataAboutId()
    @Serializable
    data class SmallMalAnime(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: String?,
        @SerialName("num_episodes") val num_episodes: Int,
        @SerialName("my_list_status") val my_list_status: MalStatus?,
        @SerialName("main_picture") val main_picture: MalMainPicture?,
    )

    @Serializable
    data class MalSearchNode(
        @SerialName("node") val node: Node,
    )

    @Serializable
    data class MalSearch(
        @SerialName("data") val data: List<MalSearchNode>,
        //paging
    )

    data class MalTitleHolder(
        val status: MalStatus,
        val id: Int,
        val name: String,
    )
}
