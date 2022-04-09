package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import java.lang.Exception

class NginxProvider : MainAPI() {
    override var mainUrl = "null"  // TO CHANGE
    override var name = "Nginx"
    override var storedCredentials: String = "null, test lmao"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)



    fun getAuthHeader(storedCredentials: String): Map<String, String> {
        val basicAuthToken = base64Encode(storedCredentials.toByteArray())  // will this be loaded when not using the provider ??? can increase load
        println("using getAuthHeader: $storedCredentials")
        return mapOf(Pair("Authorization", "Basic $basicAuthToken"))
    }

    override suspend fun load(url: String): LoadResponse {
        val authHeader = getAuthHeader(storedCredentials)  // call again because it isn't reloaded if in main class and storedCredentials loads after
        // url can be tvshow.nfo for series or mediaRootUrl for movies

        val mediaRootDocument = app.get(url, authHeader).document

        val nfoUrl = url + mediaRootDocument.getElementsByAttributeValueContaining("href", ".nfo").attr("href")  // metadata url file

        val metadataDocument = app.get(nfoUrl, authHeader).document  // get the metadata nfo file

        val isMovie = !nfoUrl.contains("tvshow.nfo")

        if (isMovie) {
            val title = metadataDocument.selectFirst("title").text()

            val description = metadataDocument.selectFirst("plot").text()

            val poster = metadataDocument.selectFirst("thumb").text()
            val trailer = metadataDocument.selectFirst("trailer")?.text()?.replace(
                "plugin://plugin.video.youtube/play/?video_id=",
                "https://www.youtube.com/watch?v="
            )
            val partialUrl = mediaRootDocument.getElementsByAttributeValueContaining("href", ".nfo").attr("href").replace(".nfo", ".")
            val date = metadataDocument.selectFirst("year")?.text()?.toIntOrNull()
            val ratingAverage = metadataDocument.selectFirst("value")?.text()?.toIntOrNull()
            val tagsList = metadataDocument.select("genre")
                ?.mapNotNull {   // all the tags like action, thriller ...
                    it?.text()

                }


            val dataList = mediaRootDocument.getElementsByAttributeValueContaining(  // list of all urls of the webpage
                "href",
                partialUrl
            )

            val data = url + dataList.firstNotNullOf { item -> item.takeIf { (!it.attr("href").contains(".nfo") &&  !it.attr("href").contains(".jpg"))} }.attr("href").toString()  // exclude poster and nfo (metadata) file


            return MovieLoadResponse(
                title,
                data,
                this.name,
                TvType.Movie,
                data,
                poster,
                date,
                description,
                ratingAverage,
                tagsList,
                null,
                trailer,
                null,
                null,
            )
        } else  // a tv serie
        {
            val title = metadataDocument.selectFirst("title").text()

            val description = metadataDocument.selectFirst("plot").text()

            val list = ArrayList<Pair<Int, String>>()
            val mediaRootUrl = url.replace("tvshow.nfo", "")
            val posterUrl = mediaRootUrl + "poster.jpg"
            val mediaRootDocument = app.get(mediaRootUrl, authHeader).document
            val seasons =
                mediaRootDocument.getElementsByAttributeValueContaining("href", "Season%20")


            val tagsList = metadataDocument.select("genre")
                ?.mapNotNull {   // all the tags like action, thriller ...; unused variable
                    it?.text()
                }

            //val actorsList = document.select("actor")
            //    ?.mapNotNull {   // all the tags like action, thriller ...; unused variable
            //        it?.text()
            //    }

            seasons.forEach { element ->
                val season =
                    element.attr("href")?.replace("Season%20", "")?.replace("/", "")?.toIntOrNull()
                val href = mediaRootUrl + element.attr("href")
                if (season != null && season > 0 && !href.isNullOrBlank()) {
                    list.add(Pair(season, href))
                }
            }

            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<TvSeriesEpisode>()


            list.apmap { (seasonInt, seasonString) ->
                val seasonDocument = app.get(seasonString, authHeader).document
                val episodes = seasonDocument.getElementsByAttributeValueContaining(
                    "href",
                    ".nfo"
                ) // get metadata
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val nfoDocument = app.get(seasonString + episode.attr("href"), authHeader).document // get episode metadata file
                        val epNum = nfoDocument.selectFirst("episode")?.text()?.toIntOrNull()
                        val poster =
                            seasonString + episode.attr("href").replace(".nfo", "-thumb.jpg")
                        val name = nfoDocument.selectFirst("title").text()
                        // val seasonInt = nfoDocument.selectFirst("season").text().toIntOrNull()
                        val date = nfoDocument.selectFirst("aired")?.text()
                        val description = nfoDocument.selectFirst("plot")?.text()

                        val dataList = seasonDocument.getElementsByAttributeValueContaining(
                            "href",
                            episode.attr("href").replace(".nfo", "")
                        )
                        val data = seasonString + dataList.firstNotNullOf { item -> item.takeIf { (!it.attr("href").contains(".nfo") &&  !it.attr("href").contains(".jpg"))} }.attr("href").toString()  // exclude poster and nfo (metadata) file



                        episodeList.add(
                            TvSeriesEpisode(
                                name,
                                seasonInt,
                                epNum,
                                data,
                                poster,
                                date,
                                null,
                                description,
                            )
                        )
                    }
                }
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                episodeList,
                posterUrl,
                null,
                description,
                null,
                null,
                tagsList,
                null,
                null,
                null,
                null,
            )
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // loadExtractor(data, null) { callback(it.copy(headers=authHeader)) }
        val authHeader = getAuthHeader(storedCredentials)  // call again because it isn't reloaded if in main class and storedCredentials loads after
        callback.invoke (
            ExtractorLink(
                name,
                name,
                data,
                data,  // referer not needed
                Qualities.Unknown.value,
                false,
                authHeader,
            )
        )

        return true
    }



    override suspend fun getMainPage(): HomePageResponse? {
        val authHeader = getAuthHeader(storedCredentials)  // call again because it isn't reloaded if in main class and storedCredentials loads after
        if (mainUrl == "null"){
            throw ErrorLoadingException("No nginx url specified in the settings: Nginx Settigns > Nginx server url, try again in a few seconds")
        }
        println("gettingmainurl: $mainUrl")
        val document = app.get(mainUrl, authHeader).document
        println(document)
        val categories = document.select("a")
        val returnList = categories.mapNotNull {
            val categoryPath = mainUrl + it.attr("href") ?: return@mapNotNull null // get the url of the category; like http://192.168.1.10/media/Movies/
            val categoryTitle = it.text()  // get the category title like Movies or Series
            if (categoryTitle != "../" && categoryTitle != "Music/") {  // exclude parent dir and Music dir
                val categoryDocument = app.get(categoryPath, authHeader).document // queries the page http://192.168.1.10/media/Movies/
                val contentLinks = categoryDocument.select("a")
                val currentList = contentLinks.mapNotNull { head ->
                    if (head.attr("href") != "../") {
                        try {
                            val mediaRootUrl =
                                categoryPath + head.attr("href")// like http://192.168.1.10/media/Series/Chernobyl/
                            println(mediaRootUrl)
                            val mediaDocument = app.get(mediaRootUrl, authHeader).document
                            val nfoFilename = mediaDocument.getElementsByAttributeValueContaining(
                                "href",
                                ".nfo"
                            )[0].attr("href")
                            val isMovieType = nfoFilename != "tvshow.nfo"
                            val nfoPath =
                                mediaRootUrl + nfoFilename // must exist or will raise errors, only the first one is taken
                            val nfoContent =
                                app.get(nfoPath, authHeader).document  // all the metadata


                            if (isMovieType) {
                                val movieName = nfoContent.select("title").text()

                                val posterUrl = mediaRootUrl + "poster.jpg"

                                return@mapNotNull MovieSearchResponse(
                                    movieName,
                                    mediaRootUrl,
                                    this.name,
                                    TvType.Movie,
                                    posterUrl,
                                    null,
                                )
                            } else {  // tv serie
                                val serieName = nfoContent.select("title").text()

                                val posterUrl = mediaRootUrl + "poster.jpg"

                                TvSeriesSearchResponse(
                                    serieName,
                                    nfoPath,
                                    this.name,
                                    TvType.TvSeries,
                                    posterUrl,
                                    null,
                                    null,
                                    null,
                                    null,
                                )


                            }
                        } catch (e: Exception) {  // can cause issues invisible errors
                            null
                            //logError(e) // not working because it changes the return type of currentList to Any
                        }


                    } else null
                }
                if (currentList.isNotEmpty() && categoryTitle != "../") {  // exclude upper dir
                    HomePageList(categoryTitle, currentList)
                } else null
            } else null  // the path is ../ which is parent directory
        }
        // if (returnList.isEmpty()) return null // maybe doing nothing idk
        return HomePageResponse(returnList)
    }
}
