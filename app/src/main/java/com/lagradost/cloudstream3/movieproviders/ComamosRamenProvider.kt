package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.ArrayList

class ComamosRamenProvider : MainAPI() {
    override var mainUrl = "https://m.comamosramen.com"
    override var name = "ComamosRamen"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
    )

    data class HomeMain (
        @JsonProperty("props") var props : HomeProps?  = HomeProps(),
    )

    data class HomeProps (
        @JsonProperty("pageProps") var pageProps : HomePageProps? = HomePageProps(),

    )

    data class HomePageProps (
        @JsonProperty("data") var data : HomeData? = HomeData(),
    )

    data class HomeData (
        @JsonProperty("sections") var sections : List<HomeSections> = listOf(),
    )

    data class HomeSections (
        @JsonProperty("data") var data : List<HomeDatum> = listOf(),
        @JsonProperty("name"     ) var name     : String?             = null,
    )
    data class HomeDatum (
        @JsonProperty("_id") var Id                : String,
        @JsonProperty("status") var status            : Status? = Status(),
        @JsonProperty("title") var title             : String,
        @JsonProperty("img") var img               : Img    = Img(),
        @JsonProperty("createdBy") var createdBy         : String? = null,
        @JsonProperty("updatedAt") var updatedAt         : String? = null,
        @JsonProperty("lastEpisodeEdited") var lastEpisodeEdited : String? = null
    )

    data class Status (
        @JsonProperty("isOnAir") var isOnAir : Boolean? = null,
        @JsonProperty("isSubtitling") var isSubtitling : Boolean? = null
    )
    data class Img (
        @JsonProperty("vertical") var vertical   : String? = null,
        @JsonProperty("horizontal") var horizontal : String? = null
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val test = app.get(mainUrl).document
        val aaa = app.get("https://comamosramen.com").document
        listOf(aaa, test).map { doc ->
            doc.select("script[type=application/json]").map { script ->
                if (script.data().contains("pageProps")) {
                    val json = parseJson<HomeMain>(script.data())
                    json.props?.pageProps?.data?.sections?.map { sectionss ->
                        val a = Pair(sectionss.data, sectionss.name)
                        val home = a.first.map { data ->
                            val title = data.title
                            val link = "$mainUrl/v/${data.Id}/${title.replace(" ","-")}"
                            val img = "https://img.comamosramen.com/${data.img.vertical}-high.jpg"
                            val epnumRegex = Regex("(\\d+\$)")
                            val lastepisode = epnumRegex.find(data.lastEpisodeEdited!!)?.value?.toIntOrNull()
                            AnimeSearchResponse(
                                title,
                                link,
                                this.name,
                                TvType.AsianDrama,
                                img,
                                null,
                                if (title.contains("Latino")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                                subEpisodes = lastepisode,
                                dubEpisodes = lastepisode,
                            )
                        }
                        items.add(HomePageList(a.second!!, home))
                    }
                }
            }
        }



        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class SearchOb (
        @JsonProperty("props") var props : SearchProps? = SearchProps(),
    )
    data class SearchProps (
        @JsonProperty("pageProps") var pageProps : SearchPageProps? = SearchPageProps(),
    )

    data class SearchPageProps (
        @JsonProperty("data") var data : DataSS? = DataSS(),
    )
    data class DataSS (
        @JsonProperty("data") var datum : ArrayList<DatumSearch> = arrayListOf()
    )

    data class DatumSearch (
        @JsonProperty("_id") var Id    : String? = null,
        @JsonProperty("img") var img   : Img?    = Img(),
        @JsonProperty("title") var title : String,
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "${mainUrl.replace("m.","")}/buscar/${query}"
        val document = app.get(url).document
        val search = ArrayList<AnimeSearchResponse>()
         document.select("script[type=application/json]").map { script ->
            val json = parseJson<SearchOb>(script.data())
              json.props?.pageProps?.data?.datum?.map {
                 val title = it.title
                 val img = "https://img.comamosramen.com/${it.img?.vertical}-high.jpg"
                 val link = "$mainUrl/v/${it.Id}/${title.replace(" ", "-")}"
                 search.add(AnimeSearchResponse(
                     title,
                     link,
                     this.name,
                     TvType.AsianDrama,
                     img,
                     null,
                     if (title.contains("Latino")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                 ))
             }
        }
        return search
    }


    data class LoadMain (
        @JsonProperty("props") var props : LoadProps? = LoadProps(),
    )

    data class LoadProps (
        @JsonProperty("pageProps" ) var pageProps : LoadPageProps? = LoadPageProps(),
    )

    data class LoadPageProps (
        @JsonProperty("data") var data : LoadData?  = LoadData(),
    )

    data class LoadData (
        @JsonProperty("_id"           ) var Id            : String?            = null,
        @JsonProperty("state"         ) var state         : LoadState?             = LoadState(),
        @JsonProperty("metadata"      ) var metadata      : LoadMetadata?          = LoadMetadata(),
        @JsonProperty("similarTitles" ) var similarTitles : ArrayList<String>  = arrayListOf(),
        @JsonProperty("type"          ) var type          : String?            = null,
        @JsonProperty("subLanguage"   ) var subLanguage   : String?            = null,
        @JsonProperty("status"        ) var status        : Status?            = Status(),
        @JsonProperty("title"         ) var title         : String?            = null,
        @JsonProperty("description"   ) var description   : String?            = null,
        @JsonProperty("img"           ) var img           : Img?               = Img(),
        @JsonProperty("createdBy"     ) var createdBy     : String?            = null,
        @JsonProperty("seasons"       ) var seasons       : ArrayList<Seasons> = arrayListOf(),
        @JsonProperty("createdAt"     ) var createdAt     : String?            = null,
        @JsonProperty("updatedAt"     ) var updatedAt     : String?            = null,
        @JsonProperty("__v"           ) var _v            : Int?               = null
    )

    data class LoadState (
        @JsonProperty("isActive"  ) var isActive  : Boolean? = null,
        @JsonProperty("isBlocked" ) var isBlocked : Boolean? = null
    )

    data class LoadMetadata (
        @JsonProperty("country") var country                : String?           = null,
        @JsonProperty("year") var year                   : Int?              = null,
        @JsonProperty("audio") var audio                  : String?           = null,
        @JsonProperty("tags") var tags                   : ArrayList<String> = arrayListOf(),
        @JsonProperty("genders") var genders                : ArrayList<String> = arrayListOf(),
        @JsonProperty("ost") var ost                    : String?           = null,
        @JsonProperty("amv") var amv                    : String?           = null,
        @JsonProperty("trailer") var trailer                : String?           = null,
        @JsonProperty("produceBy") var produceBy              : String?           = null,
        @JsonProperty("tmdbId") var tmdbId                 : String?           = null,
        @JsonProperty("totalEpisodes") var totalEpisodes          : Int?              = null,
        @JsonProperty("averageDurationEpisode") var averageDurationEpisode : String?           = null,
        @JsonProperty("casting") var casting                : ArrayList<String> = arrayListOf()
    )

    data class Seasons (
        @JsonProperty("state"     ) var state     : LoadState?              = LoadState(),
        @JsonProperty("season"    ) var season    : Int?                = null,
        @JsonProperty("episodes"  ) var episodes  : ArrayList<Episodes> = arrayListOf(),
        @JsonProperty("createdAt" ) var createdAt : String?             = null,
        @JsonProperty("updatedAt" ) var updatedAt : String?             = null,
        @JsonProperty("_id"       ) var Id        : String?             = null
    )

    data class Episodes (
        @JsonProperty("state"     ) var state     : LoadState?             = LoadState(),
        @JsonProperty("createdAt" ) var createdAt : String?            = null,
        @JsonProperty("updatedAt" ) var updatedAt : String?            = null,
        @JsonProperty("episode"   ) var episode   : Int?               = null,
        @JsonProperty("usingBot"  ) var usingBot  : Boolean?           = null,
        @JsonProperty("isOn"      ) var isOn      : String?            = null,
        @JsonProperty("players"   ) var players   : ArrayList<Players> = arrayListOf(),
        @JsonProperty("_id"       ) var Id        : String?            = null
    )

    data class Players (
        @JsonProperty("id"   ) var id   : String? = null,
        @JsonProperty("name" ) var name : String? = null
    )


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val epi = ArrayList<TvSeriesEpisode>()
        val scriptdoc = document.select("script[type=application/json]").map { script -> script.data() }.first()
        val json = parseJson<LoadMain>(scriptdoc)
        val metadataLoad = json.props?.pageProps?.data
        val title = metadataLoad?.title
        val desc = metadataLoad?.description?.substringAfter("Sinopsis")?.trim()
        val movieID = metadataLoad?.Id
        val img = "https://img.comamosramen.com/${metadataLoad?.img?.vertical}-high.jpg"
        val tags = metadataLoad?.metadata?.tags
        val status = if (metadataLoad?.status?.isOnAir == true) ShowStatus.Ongoing else ShowStatus.Completed
        val year = metadataLoad?.metadata?.year
         metadataLoad?.seasons?.map { seasons ->
            val seasonID = seasons.season
            seasons.episodes.map { episodes ->
                val epnum = episodes.episode
                epi.add(TvSeriesEpisode(
                    null,
                    seasonID,
                    epnum,
                    "$mainUrl/v/$movieID/${title?.replace(" ","-")}/$seasonID-$epnum",
                ))
            }
        }
        return TvSeriesLoadResponse(
            title!!,
            url,
            this.name,
            TvType.AsianDrama,
            epi,
            img,
            year,
            desc,
            status,
            null,
            tags
        )
        }

    data class LoadLinksMain (
        @JsonProperty("SeasonID") var SeasonID  : Int?               = null,
        @JsonProperty("EpisodeID") var EpisodeID : Int?               = null,
        @JsonProperty("Servers") var Servers   : ArrayList<Servers> = arrayListOf()
    )

    data class Servers (
        @JsonProperty("id") var id   : String? = null,
        @JsonProperty("name") var name : String? = null
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val script = doc.select("script[type=application/json]").map { it.data() }.first()
        val json = parseJson<LoadMain>(script)
        val dataRegex = Regex("(\\d+)-(\\d+)$")
        val tesatt = dataRegex.find(data)?.value.let { str ->
            str?.split("-")?.mapNotNull { subStr -> subStr.toIntOrNull() }
        }
        val epID = tesatt?.getOrNull(1)
        val seasonid = tesatt?.getOrNull(0)
        val seasonsJson = json.props?.pageProps?.data?.seasons?.map { seasons ->
            val sss = seasons.season
            seasons.episodes.map { ep ->
                Triple(sss, ep.episode, ep.players)
            }.toJson().replace("first","SeasonID")
                .replace("second","EpisodeID")
                .replace("third","Servers")
                .removePrefix("[")
                .removeSuffix("]")
        }
        val serversinfo = "["+seasonsJson?.first()+"]"
        val jsonservers = parseJson<List<LoadLinksMain>>(serversinfo)
         jsonservers.forEach { info ->
            val episodeID = info.EpisodeID
            val seasonID = info.SeasonID
            if (seasonID == seasonid && episodeID == epID) {
                info.Servers.apmap { servers ->
                    val validserver = servers.name
                        ?.replace("SB","https://sbplay2.xyz/e/")
                        ?.replace("dood","https://dood.la/e/")
                        ?.replace("Voe","https://voe.sx/e/")
                        ?.replace("Fembed","https://embedsito.com/v/")
                        ?.replace("Okru","http://ok.ru/videoembed/")
                    val validid = servers.id?.replace("/v/","")?.replace("v/","")
                        ?.replace("/","")?.replace(".html","")
                    val link = validserver+validid
                    loadExtractor(link, data, callback)
                }
            }
        }
        return true
        }
    }
