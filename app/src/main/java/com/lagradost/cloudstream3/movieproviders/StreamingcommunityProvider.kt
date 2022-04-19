package com.lagradost.cloudstream3.movieproviders

import android.text.Html
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest


private val hlsHelper = M3u8Helper()

val mapper = jacksonObjectMapper().apply {
    propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

data class Moviedata (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val id: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val name: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val type: String,

    @get:JsonProperty("release_date", required=true)@field:JsonProperty("release_date", required=true)
    val releaseDate: String,

    @get:JsonProperty("seasons_count")@field:JsonProperty("seasons_count")
    val seasonsCount: Long? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val genres: List<Genre>,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val votes: List<Vote>,

    val runtime: Long? = null
) {
    fun toJson() = mapper.writeValueAsString(this)

    companion object {
        fun fromJson(json: String) = mapper.readValue<Moviedata>(json)
    }
}

data class Genre (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val name: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val pivot: Pivot
)

data class Pivot (
    @get:JsonProperty("title_id", required=true)@field:JsonProperty("title_id", required=true)
    val titleID: Long,

    @get:JsonProperty("genre_id", required=true)@field:JsonProperty("genre_id", required=true)
    val genreID: Long
)

data class Vote (
    @get:JsonProperty("title_id", required=true)@field:JsonProperty("title_id", required=true)
    val titleID: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val average: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val count: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val type: String
)

class VideoData(elements: Collection<VideoElement>) : ArrayList<VideoElement>(elements) {
    fun toJson() = mapper.writeValueAsString(this)
    fun len () = this.size

    companion object {
        fun fromJson(json: String) = mapper.readValue<VideoData>(json)
    }
}

data class VideoElement (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val id: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val slug: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val images: List<Image>
)

data class Image (
    @get:JsonProperty("imageable_id", required=true)@field:JsonProperty("imageable_id", required=true)
    val imageableID: Long,

    @get:JsonProperty("imageable_type", required=true)@field:JsonProperty("imageable_type", required=true)
    val imageableType: String,

    @get:JsonProperty("server_id", required=true)@field:JsonProperty("server_id", required=true)
    val serverID: Long,

    @get:JsonProperty("proxy_id", required=true)@field:JsonProperty("proxy_id", required=true)
    val proxyID: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val url: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val type: String
)

class Seasons(elements: Collection<Season>) : ArrayList<Season>(elements) {
    fun toJson() = mapper.writeValueAsString(this)
    companion object {
        fun fromJson(json: String) = mapper.readValue<Seasons>(json)
    }
}

data class Season (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val id: Long,

    val name: Any? = null,
    val plot: Any? = null,
    val date: Any? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val number: Long,

    @get:JsonProperty("title_id", required=true)@field:JsonProperty("title_id", required=true)
    val titleID: Long,

    @get:JsonProperty("created_at", required=true)@field:JsonProperty("created_at", required=true)
    val createdAt: String,

    @get:JsonProperty("updated_at", required=true)@field:JsonProperty("updated_at", required=true)
    val updatedAt: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val episodes: List<Episodejson>
)

data class Episodejson (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val id: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val number: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val name: String? = "",

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val plot: String? = "",

    @get:JsonProperty("season_id", required=true)@field:JsonProperty("season_id", required=true)
    val seasonID: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val images: List<ImageSeason>
)

data class ImageSeason (
    @get:JsonProperty("imageable_id", required=true)@field:JsonProperty("imageable_id", required=true)
    val imageableID: Long,

    @get:JsonProperty("imageable_type", required=true)@field:JsonProperty("imageable_type", required=true)
    val imageableType: String,

    @get:JsonProperty("server_id", required=true)@field:JsonProperty("server_id", required=true)
    val serverID: Long,

    @get:JsonProperty("proxy_id", required=true)@field:JsonProperty("proxy_id", required=true)
    val proxyID: Long,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val url: String,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val type: String,

    @get:JsonProperty("original_url", required=false)@field:JsonProperty("original_url", required=false)
    val originalURL: String
)

class Trailer(elements: Collection<TrailerElement>) : ArrayList<TrailerElement>(elements) {
    fun toJson() = mapper.writeValueAsString(this)
    fun len () = this.size
    companion object {
        fun fromJson(json: String) = mapper.readValue<Trailer>(json)
    }
}

data class TrailerElement (
    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val id: Long? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val url: String? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val host: String? = null,

    @get:JsonProperty("videoable_id", required=true)@field:JsonProperty("videoable_id", required=true)
    val videoableID: Long? = null,

    @get:JsonProperty("videoable_type", required=true)@field:JsonProperty("videoable_type", required=true)
    val videoableType: String? = null,

    @get:JsonProperty("created_at", required=true)@field:JsonProperty("created_at", required=true)
    val createdAt: String? = null,

    @get:JsonProperty("updated_at", required=true)@field:JsonProperty("updated_at", required=true)
    val updatedAt: String? = null,

    val size: Any? = null,

    @get:JsonProperty("created_by")@field:JsonProperty("created_by")
    val createdBy: Any? = null,

    @get:JsonProperty("server_id", required=true)@field:JsonProperty("server_id", required=true)
    val serverID: Long? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val name: String? = null,

    val quality: Any? = null,

    @get:JsonProperty("original_name")@field:JsonProperty("original_name")
    val originalName: Any? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val views: Long? = null,

    @get:JsonProperty(required=true)@field:JsonProperty(required=true)
    val public: Long? = null,

    @get:JsonProperty("proxy_id")@field:JsonProperty("proxy_id")
    val proxyID: Any? = null,

    @get:JsonProperty("proxy_default_id")@field:JsonProperty("proxy_default_id")
    val proxyDefaultID: Any? = null,

    @get:JsonProperty("scws_id")@field:JsonProperty("scws_id")
    val scwsID: Any? = null
)

class StreamingcommunityProvider : MainAPI() {
    override val lang = "it"
    override var mainUrl = "https://streamingcommunity.bar"
    override var name = "Streamingcommunity"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    private fun translatenumber(num: Int): Int {
        return when(num){
            67 -> 1
            71 -> 2
            72 -> 3
            73 -> 4
            74 -> 5
            75 -> 6
            76 -> 7
            77 -> 8
            78 -> 9
            79 -> 10
            133 -> 11
            else -> 0
        }
    }
    private fun translateip(num: Int): String {
        return when(num){
            16 -> "sc-b1-01.scws-content.net"
            17 -> "sc-b1-02.scws-content.net"
            18 -> "sc-b1-03.scws-content.net"
            85 -> "sc-b1-04.scws-content.net"
            95 -> "sc-b1-05.scws-content.net"
            117 -> "sc-b1-06.scws-content.net"
            141 -> "sc-b1-07.scws-content.net"
            142 -> "sc-b1-08.scws-content.net"
            143 -> "sc-b1-09.scws-content.net"
            144 -> "sc-b1-10.scws-content.net"
            else -> ""
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()

        val document = app.get(mainUrl).document

        document.select("slider-title").subList(2,6).map { it ->

            if (it.attr("slider-name") != "In arrivo"){

                val films=it.attr("titles-json")

                val lista = mutableListOf<MovieSearchResponse>()
                val videoData = VideoData.fromJson(films)

                videoData.subList(0,12).map { searchr ->
                    val id = searchr.id
                    val name = searchr.slug
                    val img = searchr.images[0].url
                    val number = translatenumber(searchr.images[0].serverID.toInt())
                    val ip = translateip(searchr.images[0].proxyID.toInt())

                    val data = app.post("$mainUrl/api/titles/preview/$id", referer=mainUrl).text
                    val datajs =  Moviedata.fromJson(data)

                    val type: TvType = if (datajs.type == "movie") {
                        TvType.Movie
                    } else {
                        TvType.TvSeries
                    }

                    lista.add(MovieSearchResponse(
                        datajs.name,
                        "$mainUrl/titles/$id-$name°https://$ip/images/$number/$img",
                        this.name,
                        type,
                        "https://$ip/images/$number/$img",
                        datajs.releaseDate.substringBefore("-").filter { it.isDigit() }.toIntOrNull(),
                        null,
                    ))
                }


                items.add(HomePageList(it.attr("slider-name"), lista))}

        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "%20")
        val url = "$mainUrl/search?q=$queryformatted"
        val document = app.get(url).document

        val films =document.selectFirst("the-search-page").attr("records-json").replace("&quot;", """"""")

        val searchresults = VideoData.fromJson(films)

        return searchresults.map { result ->
            val id = result.id
            val name = result.slug
            val img = result.images[0].url
            val number = translatenumber(result.images[0].serverID.toInt())
            val ip = translateip(result.images[0].proxyID.toInt())

            val data = app.post("$mainUrl/api/titles/preview/$id", referer=mainUrl).text
            val datajs =  Moviedata.fromJson(data)

            if (datajs.type == "movie") {
                val type = TvType.Movie
                MovieSearchResponse(
                    datajs.name,
                    "$mainUrl/titles/$id-$name°https://$ip/images/$number/$img",
                    this.name,
                    type,
                    "https://$ip/images/$number/$img",
                    datajs.releaseDate.substringBefore("-").filter { it.isDigit() }.toIntOrNull(),
                    null,
                )
            } else {
                val type = TvType.TvSeries
                TvSeriesSearchResponse(
                    datajs.name,
                    "$mainUrl/titles/$id-$name°https://$ip/images/$number/$img",
                    this.name,
                    type,
                    "https://$ip/images/$number/$img",
                    datajs.releaseDate.substringBefore("-").filter { it.isDigit() }.toIntOrNull(),
                    null,
                )
            }

        }.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val poster = url.substringAfter("°")
        val url = url.substringBefore("°")
        val document = app.get(url).document

        val id = url.substringBefore("-").filter { it.isDigit() }
        val data = app.post("$mainUrl/api/titles/preview/$id", referer=mainUrl).text
        val datajs =  Moviedata.fromJson(data)

        val type: TvType = if (datajs.type == "movie") {
            TvType.Movie
        } else {
            TvType.TvSeries
        }
        val trailerinfojs = document.select("slider-trailer").attr("videos")
        val trailerinfo =  Trailer.fromJson(trailerinfojs)

        val trailerurl : String? = if (trailerinfo.len()>0){
            "https://www.youtube.com/watch?v=${trailerinfo[0].id}"
        } else{null}

        val year = datajs.releaseDate.substringBefore("-")

        val correlatijs = document.selectFirst("slider-title").attr("titles-json")


        val listacorr = mutableListOf<MovieSearchResponse>()

        val correlatidata = VideoData.fromJson(correlatijs)
        val number : Int = if (correlatidata.len()<=15) {correlatidata.len()} else correlatidata.len()-15

        correlatidata.take(number).map { searchr ->
            val idcorr = searchr.id
            val name = searchr.slug
            val img = searchr.images[0].url
            val number = translatenumber(searchr.images[0].serverID.toInt())
            val ip = translateip(searchr.images[0].proxyID.toInt())

            val datacorrel = app.post("$mainUrl/api/titles/preview/$idcorr", referer=mainUrl).text
            val datajscorrel =  Moviedata.fromJson(datacorrel)

            val typecorr: TvType = if (datajscorrel.type == "movie") {
                TvType.Movie
            } else {
                TvType.TvSeries
            }

            listacorr.add(MovieSearchResponse(
                datajscorrel.name,
                "$mainUrl/titles/$idcorr-$name°https://$ip/images/$number/$img",
                this.name,
                typecorr,
                "https://$ip/images/$number/$img",
                datajscorrel.releaseDate.substringBefore("-").filter { it.isDigit() }.toIntOrNull(),
                null,
            ))
        }


        if (type == TvType.TvSeries) {

            val name = datajs.name
            val episodeList = arrayListOf<Episode>()

            val episodes = Html.fromHtml(document.selectFirst("season-select").attr("seasons")).toString()
            val jsonEpisodes=Seasons.fromJson(episodes)

            jsonEpisodes.map { seasons ->
                val stagione = seasons.number.toInt()
                val sid = seasons.titleID
                val episodio = seasons.episodes
                episodio.map { ep ->
                    val href = "$mainUrl/watch/$sid?e=${ep.id}"
                    val postimage = if (ep.images.isNotEmpty()){ep.images.first().originalURL} else{""}
                    episodeList.add(

                        newEpisode(href) {
                            this.name = ep.name
                            this.season = stagione
                            this.episode = ep.number.toInt()
                            this.description =  ep.plot
                            this.posterUrl = postimage
                        }
                    )
                }
            }


            if (episodeList.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            return newTvSeriesLoadResponse(name, url, type, episodeList){
                this.posterUrl = poster
                this.year = year.filter{ it.isDigit() }.toInt()
                this.plot = document.selectFirst("div.plot-wrap > p").text()
                this.duration = datajs.runtime?.toInt()
                this.rating = (datajs.votes[0].average.toFloatOrNull()?.times(1000))?.toInt()
                this.tags = datajs.genres.map { it.name }
                addTrailer(trailerurl)
                this.recommendations = listacorr
            }




        } else {


            return newMovieLoadResponse(
                document.selectFirst("div > div > h1").text(),
                document.select("a.play-hitzone").attr("href"),
                type,
                document.select("a.play-hitzone").attr("href")
            ){
                posterUrl = fixUrlNull(poster)
                this.year = year.filter{ it.isDigit() }.toInt()
                this.plot = document.selectFirst("p.plot").text()
                this.rating = datajs.votes[0].average.toFloatOrNull()?.times(1000)?.toInt()
                this.tags = datajs.genres.map { it.name }
                this.duration = datajs.runtime?.toInt()
                addTrailer(trailerurl)
                this.recommendations = listacorr
            }

        }
    }

    private fun getM3u8Qualities(
        m3u8Link: String,
        referer: String,
        qualityName: String,
    ): List<ExtractorLink> {
        return hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(m3u8Link, null), true).map { stream ->
            val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
            ExtractorLink(
                this.name,
                "${this.name} $qualityString",
                stream.streamUrl,
                referer,
                getQualityFromName(stream.quality.toString()),
                true,
                stream.headers
            )
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ip = app.get("https://api.ipify.org/").text
        val videors = app.get(data).document
        val scwsidjs = videors.select("video-player").attr("response").replace("&quot;", """"""")
        val jsn =  JSONObject(scwsidjs)
        val scwsid = jsn.getString("scws_id")
        val expire = (System.currentTimeMillis()/1000+172800).toString()

        val uno = "$expire$ip Yc8U6r8KjAKAepEA".toByteArray()
        val due = MessageDigest.getInstance("MD5").digest(uno)
        val tre = base64Encode(due)
        val token = tre.replace("=", "").replace("+", "-").replace("/", "_")


        val link= "https://scws.xyz/master/$scwsid?token=$token&expires=$expire&n=1&n=1"
        getM3u8Qualities(link, data, URI(link).host).forEach(callback)
        return true
    }
}