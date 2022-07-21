package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.splitQuery
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.serialization.SerialName
import java.net.URL
import java.util.*
import kotlinx.serialization.Serializable

class AniListApi(index: Int) : AccountManager(index), SyncAPI {
    override var name = "AniList"
    override val key = "6871"
    override val redirectUrl = "anilistlogin"
    override val idPrefix = "anilist"
    override var mainUrl = "https://anilist.co"
    override val icon = R.drawable.ic_anilist_icon
    override val requiresLogin = false
    override val createAccountUrl = "$mainUrl/signup"

    override fun loginInfo(): AuthAPI.LoginInfo? {
        // context.getUser(true)?.
        getKey<AniListUser>(accountId, ANILIST_USER_KEY)?.let { user ->
            return AuthAPI.LoginInfo(
                profilePicture = user.picture,
                name = user.name,
                accountIndex = accountIndex
            )
        }
        return null
    }

    override fun logOut() {
        removeAccountKeys()
    }

    override fun authenticate() {
        val request = "https://anilist.co/api/v2/oauth/authorize?client_id=$key&response_type=token"
        openBrowser(request)
    }

    override suspend fun handleRedirect(url: String): Boolean {
        val sanitizer =
            splitQuery(URL(url.replace(appString, "https").replace("/#", "?"))) // FIX ERROR
        val token = sanitizer["access_token"]!!
        val expiresIn = sanitizer["expires_in"]!!

        val endTime = unixTime + expiresIn.toLong()

        switchToNewAccount()
        setKey(accountId, ANILIST_UNIXTIME_KEY, endTime)
        setKey(accountId, ANILIST_TOKEN_KEY, token)
        setKey(ANILIST_SHOULD_UPDATE_LIST, true)
        val user = getUser()
        return user != null
    }

    override fun getIdFromUrl(url: String): String {
        return url.removePrefix("$mainUrl/anime/").removeSuffix("/")
    }

    private fun getUrlFromId(id: Int): String {
        return "$mainUrl/anime/$id"
    }

    override suspend fun search(name: String): List<SyncAPI.SyncSearchResult>? {
        val data = searchShows(name) ?: return null
        return data.data?.Page?.media?.map {
            SyncAPI.SyncSearchResult(
                it.title.romaji ?: return null,
                this.name,
                it.id.toString(),
                getUrlFromId(it.id),
                it.bannerImage
            )
        }
    }

    override suspend fun getResult(id: String): SyncAPI.SyncResult {
        val internalId = (Regex("anilist\\.co/anime/(\\d*)").find(id)?.groupValues?.getOrNull(1)
            ?: id).toIntOrNull() ?: throw ErrorLoadingException("Invalid internalId")
        val season = getSeason(internalId).data.Media

        return SyncAPI.SyncResult(
            season.id.toString(),
            nextAiring = season.nextAiringEpisode?.let {
                NextAiring(
                    it.episode ?: return@let null,
                    (it.timeUntilAiring ?: return@let null) + unixTime
                )
            },
            title = season.title?.userPreferred,
            synonyms = season.synonyms,
            isAdult = season.isAdult,
            totalEpisodes = season.episodes,
            synopsis = season.description,
            actors = season.characters?.edges?.mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                ActorData(
                    actor = Actor(
                        name = node.name?.userPreferred ?: node.name?.full ?: node.name?.native
                        ?: return@mapNotNull null,
                        image = node.image?.large ?: node.image?.medium
                    ),
                    role = when (edge.role) {
                        "MAIN" -> ActorRole.Main
                        "SUPPORTING" -> ActorRole.Supporting
                        "BACKGROUND" -> ActorRole.Background
                        else -> null
                    },
                    voiceActor = edge.voiceActors?.firstNotNullOfOrNull { staff ->
                        Actor(
                            name = staff.name?.userPreferred ?: staff.name?.full
                            ?: staff.name?.native
                            ?: return@mapNotNull null,
                            image = staff.image?.large ?: staff.image?.medium
                        )
                    }
                )
            },
            publicScore = season.averageScore?.times(100),
            recommendations = season.recommendations?.edges?.mapNotNull { rec ->
                val recMedia = rec.node.mediaRecommendation
                SyncAPI.SyncSearchResult(
                    name = recMedia.title?.userPreferred ?: return@mapNotNull null,
                    this.name,
                    recMedia.id?.toString() ?: return@mapNotNull null,
                    getUrlFromId(recMedia.id),
                    recMedia.coverImage?.large ?: recMedia.coverImage?.medium
                )
            },
            trailers = when (season.trailer?.site?.lowercase()?.trim()) {
                "youtube" -> listOf("https://www.youtube.com/watch?v=${season.trailer.id}")
                else -> null
            }
            //TODO REST
        )
    }

    override suspend fun getStatus(id: String): SyncAPI.SyncStatus? {
        val internalId = id.toIntOrNull() ?: return null
        val data = getDataAboutId(internalId) ?: return null

        return SyncAPI.SyncStatus(
            score = data.score,
            watchedEpisodes = data.progress,
            status = data.type?.value ?: return null,
            isFavorite = data.isFavourite,
            maxEpisodes = data.episodes,
        )
    }

    override suspend fun score(id: String, status: SyncAPI.SyncStatus): Boolean {
        return postDataAboutId(
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(status.status),
            status.score,
            status.watchedEpisodes
        )
    }

    companion object {
        private val aniListStatusString =
            arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        const val ANILIST_UNIXTIME_KEY: String = "anilist_unixtime" // When token expires
        const val ANILIST_TOKEN_KEY: String = "anilist_token" // anilist token for api
        const val ANILIST_USER_KEY: String = "anilist_user" // user data like profile
        const val ANILIST_CACHED_LIST: String = "anilist_cached_list"
        const val ANILIST_SHOULD_UPDATE_LIST: String = "anilist_should_update_list"

        private fun fixName(name: String): String {
            return name.lowercase(Locale.ROOT).replace(" ", "")
                .replace("[^a-zA-Z0-9]".toRegex(), "")
        }

        private suspend fun searchShows(name: String): GetSearchRoot? {
            try {
                val query = """
                query (${"$"}id: Int, ${"$"}page: Int, ${"$"}search: String, ${"$"}type: MediaType) {
                    Page (page: ${"$"}page, perPage: 10) {
                        media (id: ${"$"}id, search: ${"$"}search, type: ${"$"}type) {
                            id
                            idMal
                            seasonYear
                            startDate { year month day }
                            title {
                                romaji
                            }
                            averageScore
                            meanScore
                            nextAiringEpisode {
                                timeUntilAiring
                                episode
                            }
                            trailer { id site thumbnail }
                            bannerImage
                            recommendations {
                                nodes {
                                    id
                                    mediaRecommendation {
                                        id
                                        title {
                                            english
                                            romaji
                                        }
                                        idMal
                                        coverImage { medium large }
                                        averageScore
                                    }
                                }
                            }
                            relations {
                                edges {
                                    id
                                    relationType(version: 2)
                                    node {
                                        format
                                        id
                                        idMal
                                        coverImage { medium large }
                                        averageScore
                                        title {
                                            english
                                            romaji
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                """
                val data =
                    mapOf(
                        "query" to query,
                        "variables" to
                                mapOf(
                                    "search" to name,
                                    "page" to 1,
                                    "type" to "ANIME"
                                ).toJson()
                    )

                val res = app.post(
                    "https://graphql.anilist.co/",
                    //headers = mapOf(),
                    data = data,//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                    timeout = 5000 // REASONABLE TIMEOUT
                ).text.replace("\\", "")
                return parseJson(res)
            } catch (e: Exception) {
                logError(e)
            }
            return null
        }

        // Should use https://gist.github.com/purplepinapples/5dc60f15f2837bf1cea71b089cfeaa0a
        suspend fun getShowId(malId: String?, name: String, year: Int?): GetSearchMedia? {
            // Strips these from the name
            val blackList = listOf(
                "TV Dubbed",
                "(Dub)",
                "Subbed",
                "(TV)",
                "(Uncensored)",
                "(Censored)",
                "(\\d+)" // year
            )
            val blackListRegex =
                Regex(
                    """ (${
                        blackList.joinToString(separator = "|").replace("(", "\\(")
                            .replace(")", "\\)")
                    })"""
                )
            //println("NAME $name NEW NAME ${name.replace(blackListRegex, "")}")
            val shows = searchShows(name.replace(blackListRegex, ""))

            shows?.data?.Page?.media?.find {
                malId ?: "NONE" == it.idMal.toString()
            }?.let { return it }

            val filtered =
                shows?.data?.Page?.media?.filter {
                    (
                            it.startDate.year ?: year.toString() == year.toString()
                                    || year == null
                            )
                }
            filtered?.forEach {
                it.title.romaji?.let { romaji ->
                    if (fixName(romaji) == fixName(name)) return it
                }
            }

            return filtered?.firstOrNull()
        }

        // Changing names of these will show up in UI
        enum class AniListStatusType(var value: Int) {
            Watching(0),
            Completed(1),
            Paused(2),
            Dropped(3),
            Planning(4),
            ReWatching(5),
            None(-1)
        }

        fun fromIntToAnimeStatus(inp: Int): AniListStatusType {//= AniListStatusType.values().first { it.value == inp }
            return when (inp) {
                -1 -> AniListStatusType.None
                0 -> AniListStatusType.Watching
                1 -> AniListStatusType.Completed
                2 -> AniListStatusType.Paused
                3 -> AniListStatusType.Dropped
                4 -> AniListStatusType.Planning
                5 -> AniListStatusType.ReWatching
                else -> AniListStatusType.None
            }
        }

        fun convertAnilistStringToStatus(string: String): AniListStatusType {
            return fromIntToAnimeStatus(aniListStatusString.indexOf(string))
        }

        private suspend fun getSeason(id: Int): SeasonResponse {
            val q = """
               query (${'$'}id: Int = $id) {
                   Media (id: ${'$'}id, type: ANIME) {
                       id
                       idMal
                       coverImage {
                           extraLarge
                           large
                           medium
                           color
                       }
                       title {
                           romaji
                           english
                           native
                           userPreferred
                       }
                       duration
                       episodes
                       genres
                       synonyms
                       averageScore
                       isAdult
                       description(asHtml: false)
                       characters(sort: ROLE page: 1 perPage: 20) {
                           edges {
                               role
                               voiceActors {
                                   name {
                                       userPreferred
                                       full
                                       native
                                   }
                                   age
                                   image {
                                       large
                                       medium
                                   }
                               }
                               node {
                                   name {
                                       userPreferred
                                       full
                                       native
                                   }
                                   age
                                   image {
                                       large
                                       medium
                                   }
                               }
                           }
                       }
                       trailer {
                           id
                           site
                           thumbnail
                       }
                       relations {
                           edges {
                                id
                                relationType(version: 2)
                                node {
                                     id
                                     coverImage {
                                         extraLarge
                                         large
                                         medium
                                         color
                                     }
                                }
                           }
                       }
                       recommendations {
                           edges {
                               node {
                                   mediaRecommendation {
                                       id
                                       coverImage {
                                           extraLarge
                                           large
                                           medium
                                           color
                                       }
                                       title {
                                           romaji
                                           english
                                           native
                                           userPreferred
                                       }
                                   }
                               }
                           }
                       }
                       nextAiringEpisode {
                           timeUntilAiring
                           episode
                       }
                       format
                   }
               }
        """
            val data = app.post(
                "https://graphql.anilist.co",
                data = mapOf("query" to q),
                cacheTime = 0,
            ).text

            return tryParseJson(data) ?: throw ErrorLoadingException("Error parsing $data")
        }
    }

    fun initGetUser() {
        if (getAuth() == null) return
        ioSafe {
            getUser()
        }
    }

    private fun checkToken(): Boolean {
        return unixTime > getKey(
            accountId,
            ANILIST_UNIXTIME_KEY, 0L
        )!!
    }

    private suspend fun getDataAboutId(id: Int): AniListTitleHolder? {
        val q =
            """query (${'$'}id: Int = $id) { # Define which variables will be used in the query (id)
                Media (id: ${'$'}id, type: ANIME) { # Insert our variables into the query arguments (id) (type: ANIME is hard-coded in the query)
                    id
                    episodes
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_10)
                    }
                    title {
                        english
                        romaji
                    }
                }
            }"""

        val data = postApi(q, true)
        val d = parseJson<GetDataRoot>(data ?: return null)

        val main = d.data?.Media
        if (main?.mediaListEntry != null) {
            return AniListTitleHolder(
                title = main.title,
                id = id,
                isFavourite = main.isFavourite,
                progress = main.mediaListEntry.progress,
                episodes = main.episodes,
                score = main.mediaListEntry.score,
                type = fromIntToAnimeStatus(aniListStatusString.indexOf(main.mediaListEntry.status)),
            )
        } else {
            return AniListTitleHolder(
                title = main?.title,
                id = id,
                isFavourite = main?.isFavourite,
                progress = 0,
                episodes = main?.episodes,
                score = 0,
                type = AniListStatusType.None,
            )
        }

    }

    private fun getAuth(): String? {
        return getKey(
            accountId,
            ANILIST_TOKEN_KEY
        )
    }

    private suspend fun postApi(q: String, cache: Boolean = false): String? {
        return if (!checkToken()) {
            app.post(
                "https://graphql.anilist.co/",
                headers = mapOf(
                    "Authorization" to "Bearer " + (getAuth() ?: return null),
                    if (cache) "Cache-Control" to "max-stale=$maxStale" else "Cache-Control" to "no-cache"
                ),
                cacheTime = 0,
                data = mapOf("query" to q),//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                timeout = 5 // REASONABLE TIMEOUT
            ).text.replace("\\/", "/")
        } else {
            null
        }
    }

    @Serializable
    data class MediaRecommendation(
        @SerialName("id") val id: Int,
        @SerialName("title") val title: Title?,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("coverImage") val coverImage: CoverImage?,
        @SerialName("averageScore") val averageScore: Int?
    )

    @Serializable
    data class FullAnilistList(
        @SerialName("data") val data: Data?
    )

    @Serializable
    data class CompletedAt(
        @SerialName("year") val year: Int,
        @SerialName("month") val month: Int,
        @SerialName("day") val day: Int
    )

    @Serializable
    data class StartedAt(
        @SerialName("year") val year: String?,
        @SerialName("month") val month: String?,
        @SerialName("day") val day: String?
    )

    @Serializable
    data class Title(
        @SerialName("english") val english: String?,
        @SerialName("romaji") val romaji: String?
    )

    @Serializable
    data class CoverImage(
        @SerialName("medium") val medium: String?,
        @SerialName("large") val large: String?
    )

    @Serializable
    data class Media(
        @SerialName("id") val id: Int,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("season") val season: String?,
        @SerialName("seasonYear") val seasonYear: Int,
        @SerialName("format") val format: String?,
        //@SerialName("source") val source: String,
        @SerialName("episodes") val episodes: Int,
        @SerialName("title") val title: Title,
        //@SerialName("description") val description: String,
        @SerialName("coverImage") val coverImage: CoverImage,
        @SerialName("synonyms") val synonyms: List<String>,
        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    @Serializable
    data class Entries(
        @SerialName("status") val status: String?,
        @SerialName("completedAt") val completedAt: CompletedAt,
        @SerialName("startedAt") val startedAt: StartedAt,
        @SerialName("updatedAt") val updatedAt: Int,
        @SerialName("progress") val progress: Int,
        @SerialName("score") val score: Int,
        @SerialName("private") val private: Boolean,
        @SerialName("media") val media: Media
    )

    @Serializable
    data class Lists(
        @SerialName("status") val status: String?,
        @SerialName("entries") val entries: List<Entries>
    )

    @Serializable
    data class MediaListCollection(
        @SerialName("lists") val lists: List<Lists>
    )

    @Serializable
    data class Data(
        @SerialName("MediaListCollection") val MediaListCollection: MediaListCollection
    )

    fun getAnilistListCached(): Array<Lists>? {
        return getKey(ANILIST_CACHED_LIST) as? Array<Lists>
    }

    suspend fun getAnilistAnimeListSmart(): Array<Lists>? {
        if (getAuth() == null) return null

        if (checkToken()) return null
        return if (getKey(ANILIST_SHOULD_UPDATE_LIST, true) == true) {
            val list = getFullAnilistList()?.data?.MediaListCollection?.lists?.toTypedArray()
            if (list != null) {
                setKey(ANILIST_CACHED_LIST, list)
                setKey(ANILIST_SHOULD_UPDATE_LIST, false)
            }
            list
        } else {
            getAnilistListCached()
        }
    }

    private suspend fun getFullAnilistList(): FullAnilistList? {
        var userID: Int? = null
        /** WARNING ASSUMES ONE USER! **/
        getKeys(ANILIST_USER_KEY)?.forEach { key ->
            getKey<AniListUser>(key, null)?.let {
                userID = it.id
            }
        }

        val fixedUserID = userID ?: return null
        val mediaType = "ANIME"

        val query = """
                query (${'$'}userID: Int = $fixedUserID, ${'$'}MEDIA: MediaType = $mediaType) {
                    MediaListCollection (userId: ${'$'}userID, type: ${'$'}MEDIA) { 
                        lists {
                            status
                            entries
                            {
                                status
                                completedAt { year month day }
                                startedAt { year month day }
                                updatedAt
                                progress
                                score
                                private
                                media
                                {
                                    id
                                    idMal
                                    season
                                    seasonYear
                                    format
                                    episodes
                                    chapters
                                    title
                                    {
                                        english
                                        romaji
                                    }
                                    coverImage { medium }
                                    synonyms
                                    nextAiringEpisode {
                                        timeUntilAiring
                                        episode
                                    }
                                }
                            }
                        }
                    }
                    }
            """
        val text = postApi(query)
        return tryParseJson(text)
    }

    suspend fun toggleLike(id: Int): Boolean {
        val q = """mutation (${'$'}animeId: Int = $id) {
				ToggleFavourite (animeId: ${'$'}animeId) {
					anime {
						nodes {
							id
							title {
								romaji
							}
						}
					}
				}
			}"""
        val data = postApi(q)
        return data != ""
    }

    private suspend fun postDataAboutId(
        id: Int,
        type: AniListStatusType,
        score: Int?,
        progress: Int?
    ): Boolean {
        val q =
            """mutation (${'$'}id: Int = $id, ${'$'}status: MediaListStatus = ${
                aniListStatusString[maxOf(
                    0,
                    type.value
                )]
            }, ${if (score != null) "${'$'}scoreRaw: Int = ${score * 10}" else ""} , ${if (progress != null) "${'$'}progress: Int = $progress" else ""}) {
                SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                    id
                    status
                    progress
                    score
                }
                }"""
        val data = postApi(q)
        return data != ""
    }

    private suspend fun getUser(setSettings: Boolean = true): AniListUser? {
        val q = """
				{
  					Viewer {
    					id
    					name
						avatar {
							large
						}
                        favourites {
                            anime {
                                nodes {
                                    id
                                }
                            }
                        }
  					}
				}"""
        val data = postApi(q)
        if (data.isNullOrBlank()) return null
        val userData = parseJson<AniListRoot>(data)
        val u = userData.data?.Viewer
        val user = AniListUser(
            u?.id,
            u?.name,
            u?.avatar?.large,
        )
        if (setSettings) {
            setKey(accountId, ANILIST_USER_KEY, user)
            registerAccount()
        }
        /* // TODO FIX FAVS
        for(i in u.favourites.anime.nodes) {
            println("FFAV:" + i.id)
        }*/
        return user
    }

    suspend fun getAllSeasons(id: Int): List<SeasonResponse?> {
        val seasons = mutableListOf<SeasonResponse?>()
        suspend fun getSeasonRecursive(id: Int) {
            val season = getSeason(id)
            seasons.add(season)
            if (season.data.Media.format?.startsWith("TV") == true) {
                season.data.Media.relations?.edges?.forEach {
                    if (it.node?.format != null) {
                        if (it.relationType == "SEQUEL" && it.node.format.startsWith("TV")) {
                            getSeasonRecursive(it.node.id)
                            return@forEach
                        }
                    }
                }
            }
        }
        getSeasonRecursive(id)
        return seasons.toList()
    }

    @Serializable
    data class SeasonResponse(
        @SerialName("data") val data: SeasonData,
    )

    @Serializable
    data class SeasonData(
        @SerialName("Media") val Media: SeasonMedia,
    )

    @Serializable
    data class SeasonMedia(
        @SerialName("id") val id: Int?,
        @SerialName("title") val title: MediaTitle?,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("format") val format: String?,
        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @SerialName("relations") val relations: SeasonEdges?,
        @SerialName("coverImage") val coverImage: MediaCoverImage?,
        @SerialName("duration") val duration: Int?,
        @SerialName("episodes") val episodes: Int?,
        @SerialName("genres") val genres: List<String>?,
        @SerialName("synonyms") val synonyms: List<String>?,
        @SerialName("averageScore") val averageScore: Int?,
        @SerialName("isAdult") val isAdult: Boolean?,
        @SerialName("trailer") val trailer: MediaTrailer?,
        @SerialName("description") val description: String?,
        @SerialName("characters") val characters: CharacterConnection?,
        @SerialName("recommendations") val recommendations: RecommendationConnection?,
    )

    @Serializable
    data class RecommendationConnection(
        @SerialName("edges") val edges: List<RecommendationEdge> = emptyList(),
        @SerialName("nodes") val nodes: List<Recommendation> = emptyList(),
        //@SerialName("pageInfo") val pageInfo: PageInfo,
    )

    @Serializable
    data class RecommendationEdge(
        //@SerialName("rating") val rating: Int,
        @SerialName("node") val node: Recommendation,
    )

    @Serializable
    data class Recommendation(
        @SerialName("mediaRecommendation") val mediaRecommendation: SeasonMedia,
    )

    @Serializable
    data class CharacterName(
        @SerialName("name") val first: String?,
        @SerialName("middle") val middle: String?,
        @SerialName("last") val last: String?,
        @SerialName("full") val full: String?,
        @SerialName("native") val native: String?,
        @SerialName("alternative") val alternative: List<String>?,
        @SerialName("alternativeSpoiler") val alternativeSpoiler: List<String>?,
        @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class CharacterImage(
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
    )

    @Serializable
    data class Character(
        @SerialName("name") val name: CharacterName?,
        @SerialName("age") val age: String?,
        @SerialName("image") val image: CharacterImage?,
    )

    @Serializable
    data class CharacterEdge(
        @SerialName("id") val id: Int?,
        /**
        MAIN
        A primary character role in the media

        SUPPORTING
        A supporting character role in the media

        BACKGROUND
        A background character in the media
         */
        @SerialName("role") val role: String?,
        @SerialName("name") val name: String?,
        @SerialName("voiceActors") val voiceActors: List<Staff>?,
        @SerialName("favouriteOrder") val favouriteOrder: Int?,
        @SerialName("media") val media: List<SeasonMedia>?,
        @SerialName("node") val node: Character?,
    )

    @Serializable
    data class StaffImage(
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
    )

    @Serializable
    data class StaffName(
        @SerialName("name") val first: String?,
        @SerialName("middle") val middle: String?,
        @SerialName("last") val last: String?,
        @SerialName("full") val full: String?,
        @SerialName("native") val native: String?,
        @SerialName("alternative") val alternative: List<String>?,
        @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class Staff(
        @SerialName("image") val image: StaffImage?,
        @SerialName("name") val name: StaffName?,
        @SerialName("age") val age: Int?,
    )

    @Serializable
    data class CharacterConnection(
        @SerialName("edges") val edges: List<CharacterEdge>?,
        @SerialName("nodes") val nodes: List<Character>?,
        //@SerialName("pageInfo")  pageInfo: PageInfo
    )

    @Serializable
    data class MediaTrailer(
        @SerialName("id") val id: String?,
        @SerialName("site") val site: String?,
        @SerialName("thumbnail") val thumbnail: String?,
    )

    @Serializable
    data class MediaCoverImage(
        @SerialName("extraLarge") val extraLarge: String?,
        @SerialName("large") val large: String?,
        @SerialName("medium") val medium: String?,
        @SerialName("color") val color: String?,
    )

    @Serializable
    data class SeasonNextAiringEpisode(
        @SerialName("episode") val episode: Int?,
        @SerialName("timeUntilAiring") val timeUntilAiring: Int?,
    )

    @Serializable
    data class SeasonEdges(
        @SerialName("edges") val edges: List<SeasonEdge>?,
    )

    @Serializable
    data class SeasonEdge(
        @SerialName("id") val id: Int?,
        @SerialName("relationType") val relationType: String?,
        @SerialName("node") val node: SeasonNode?,
    )

    @Serializable
    data class AniListFavoritesMediaConnection(
        @SerialName("nodes") val nodes: List<LikeNode>,
    )

    @Serializable
    data class AniListFavourites(
        @SerialName("anime") val anime: AniListFavoritesMediaConnection,
    )

    @Serializable
    data class MediaTitle(
        @SerialName("romaji") val romaji: String?,
        @SerialName("english") val english: String?,
        @SerialName("native") val native: String?,
        @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class SeasonNode(
        @SerialName("id") val id: Int,
        @SerialName("format") val format: String?,
        @SerialName("title") val title: Title?,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("coverImage") val coverImage: CoverImage?,
        @SerialName("averageScore") val averageScore: Int?
//        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    @Serializable
    data class AniListAvatar(
        @SerialName("large") val large: String?,
    )

    @Serializable
    data class AniListViewer(
        @SerialName("id") val id: Int?,
        @SerialName("name") val name: String?,
        @SerialName("avatar") val avatar: AniListAvatar?,
        @SerialName("favourites") val favourites: AniListFavourites?,
    )

    @Serializable
    data class AniListData(
        @SerialName("Viewer") val Viewer: AniListViewer?,
    )

    @Serializable
    data class AniListRoot(
        @SerialName("data") val data: AniListData?,
    )

    @Serializable
    data class AniListUser(
        @SerialName("id") val id: Int?,
        @SerialName("name") val name: String?,
        @SerialName("picture") val picture: String?,
    )

    @Serializable
    data class LikeNode(
        @SerialName("id") val id: Int?,
        //@SerialName("idMal") public int idMal;
    )

    @Serializable
    data class LikePageInfo(
        @SerialName("total") val total: Int?,
        @SerialName("currentPage") val currentPage: Int?,
        @SerialName("lastPage") val lastPage: Int?,
        @SerialName("perPage") val perPage: Int?,
        @SerialName("hasNextPage") val hasNextPage: Boolean?,
    )

    @Serializable
    data class LikeAnime(
        @SerialName("nodes") val nodes: List<LikeNode>?,
        @SerialName("pageInfo") val pageInfo: LikePageInfo?,
    )

    @Serializable
    data class LikeFavourites(
        @SerialName("anime") val anime: LikeAnime?,
    )

    @Serializable
    data class LikeViewer(
        @SerialName("favourites") val favourites: LikeFavourites?,
    )

    @Serializable
    data class LikeData(
        @SerialName("Viewer") val Viewer: LikeViewer?,
    )

    @Serializable
    data class LikeRoot(
        @SerialName("data") val data: LikeData?,
    )

    @Serializable
    data class AniListTitleHolder(
        @SerialName("title") val title: Title?,
        @SerialName("isFavourite") val isFavourite: Boolean?,
        @SerialName("id") val id: Int?,
        @SerialName("progress") val progress: Int?,
        @SerialName("episodes") val episodes: Int?,
        @SerialName("score") val score: Int?,
        @SerialName("type") val type: AniListStatusType?,
    )

    @Serializable
    data class GetDataMediaListEntry(
        @SerialName("progress") val progress: Int?,
        @SerialName("status") val status: String?,
        @SerialName("score") val score: Int?,
    )

    @Serializable
    data class Nodes(
        @SerialName("id") val id: Int?,
        @SerialName("mediaRecommendation") val mediaRecommendation: MediaRecommendation?
    )

    @Serializable
    data class GetDataMedia(
        @SerialName("isFavourite") val isFavourite: Boolean?,
        @SerialName("episodes") val episodes: Int?,
        @SerialName("title") val title: Title?,
        @SerialName("mediaListEntry") val mediaListEntry: GetDataMediaListEntry?
    )

    @Serializable
    data class Recommendations(
        @SerialName("nodes") val nodes: List<Nodes>?
    )

    @Serializable
    data class GetDataData(
        @SerialName("Media") val Media: GetDataMedia?,
    )

    @Serializable
    data class GetDataRoot(
        @SerialName("data") val data: GetDataData?,
    )

    @Serializable
    data class GetSearchTitle(
        @SerialName("romaji") val romaji: String?,
    )

    @Serializable
    data class TrailerObject(
        @SerialName("id") val id: String?,
        @SerialName("thumbnail") val thumbnail: String?,
        @SerialName("site") val site: String?,
    )

    @Serializable
    data class GetSearchMedia(
        @SerialName("id") val id: Int,
        @SerialName("idMal") val idMal: Int?,
        @SerialName("seasonYear") val seasonYear: Int,
        @SerialName("title") val title: GetSearchTitle,
        @SerialName("startDate") val startDate: StartedAt,
        @SerialName("averageScore") val averageScore: Int?,
        @SerialName("meanScore") val meanScore: Int?,
        @SerialName("bannerImage") val bannerImage: String?,
        @SerialName("trailer") val trailer: TrailerObject?,
        @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @SerialName("recommendations") val recommendations: Recommendations?,
        @SerialName("relations") val relations: SeasonEdges?
    )

    @Serializable
    data class GetSearchPage(
        @SerialName("Page") val Page: GetSearchData?,
    )

    @Serializable
    data class GetSearchData(
        @SerialName("media") val media: List<GetSearchMedia>?,
    )

    @Serializable
    data class GetSearchRoot(
        @SerialName("data") val data: GetSearchPage?,
    )
}