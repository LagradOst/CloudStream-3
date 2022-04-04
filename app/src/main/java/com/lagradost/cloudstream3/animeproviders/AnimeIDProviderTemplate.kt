package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.net.URI
import java.util.*
import kotlin.collections.ArrayList

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */

open class AnimeIDProviderTemplate : MainAPI() {
    open val homePageUrlList = listOf<String>()
    open val animeidExtractorUrl: String? = null

//    // mainUrl is good to have as a holder for the url to make future changes easier.
//    override val mainUrl: String
//        get() = "https://vidembed.cc"
//
//    // name is for how the provider will be named which is visible in the UI, no real rules for this.
//    override val name: String
//        get() = "VidEmbed"

    // hasQuickSearch defines if quickSearch() should be called, this is only when typing the searchbar
    // gives results on the site instead of bringing you to another page.
    // if hasQuickSearch is true and quickSearch() hasn't been overridden you will get errors.
    // VidEmbed actually has quick search on their site, but the function wasn't implemented.
    override val hasQuickSearch = false

    // If getMainPage() is functional, used to display the homepage in app, an optional, but highly encouraged endevour.
    override val hasMainPage = true

    // Searching returns a SearchResponse, which can be one of the following: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    // Each of the classes requires some different data, but always has some critical things like name, poster and url.
    override suspend fun search(query: String): ArrayList<SearchResponse> {
        // Simply looking at devtools network is enough to spot a request like:
        // https://vidembed.cc/search.html?keyword=neverland where neverland is the query, can be written as below.
        val link = "$mainUrl/search.html?keyword=$query"
        val html = app.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".listing.items > .video-block").map { li ->
            // Selects the href in <a href="...">
            val href = fixUrl(li.selectFirst("a").attr("href"))
            val poster = fixUrl(li.selectFirst("img").attr("src"))

            // .text() selects all the text in the element, be careful about doing this while too high up in the html hierarchy
            val title = li.selectFirst(".name").text()
            // Use get(0) and toIntOrNull() to prevent any possible crashes, [0] or toInt() will error the search on unexpected values.
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

            AnimeSearchResponse(
                // .trim() removes unwanted spaces in the start and end.
                if (!title.contains("Episodio")) title else title.split("Episodio")[0].trim(),
                href,
                this.name,
                TvType.Anime,
                poster, year,
                // You can't get the episodes from the search bar.
                if (title.contains("Latino")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed)
            )
        })
    }


    // Load, like the name suggests loads the info page, where all the episodes and data usually is.
    // Like search you should return either of: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
    override suspend fun load(url: String): LoadResponse? {
        // Gets the url returned from searching.
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        var title = soup.selectFirst("h1,h2,h3").text()
        title = if (!title.contains("Episodio")) title else title.split("Episodio")[0].trim()

        val description = soup.selectFirst(".post-entry")?.text()?.trim()
        var poster: String? = null
        val isDubbed = title.contains("Latino")
        val episodes = soup.select(".listing.items.lists > .video-block").map { li ->
            val epTitle = if (li.selectFirst(".name") != null)
                if (li.selectFirst(".name").text().contains("Episodio"))
                    "Episodio " + li.selectFirst(".name").text().split("Episodio")[1].trim()
                else
                    li.selectFirst(".name").text()
            else ""
            var epThumb = li.selectFirst("ul.items li .img img")?.attr("src")
            val epDate = li.selectFirst(".meta > .date").text()

            if (poster == null) {
                poster = li.selectFirst("img")?.attr("onerror")?.split("=")?.get(1)?.replace(Regex("[';]"), "")
            }

            

            val epNum = Regex("""Episodio (\d+)""").find(epTitle)?.destructured?.component1()?.toIntOrNull()

            AnimeEpisode(
                fixUrl(li.selectFirst("a").attr("href")),
                epTitle,
                epThumb,
                epDate,
                null,
                null,
                epNum,
            )
        }.reversed()

        val year = episodes.first().date?.split("-")?.get(0)?.toIntOrNull()

        // Make sure to get the type right to display the correct UI.
        val tvType = if (episodes.size == 1 && episodes[0].name == title) TvType.AnimeMovie else TvType.Anime

        return when (tvType) {
            TvType.Anime -> {
                newAnimeLoadResponse(title, url, tvType) {

                    engName = title
                    posterUrl = poster
                    this.year = year
                    addEpisodes(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed, episodes)
                    showStatus = null
                    plot = description
                    tags = null
                }
            }
            TvType.AnimeMovie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes[0].url,
                    poster,
                    year,
                    description,
                    null,
                    null
                )
            }
            else -> null
        }
    }

    // This loads the homepage, which is basically a collection of search results with labels.
    // Optional function, but make sure to enable hasMainPage if you program this.
    override suspend fun getMainPage(): HomePageResponse {
        val urls = homePageUrlList
        val homePageList = ArrayList<HomePageList>()
        // .pmap {} is used to fetch the different pages in parallel
        urls.apmap { url ->
            val response = app.get(url, timeout = 20).text
            val document = Jsoup.parse(response)
            document.select("div.main-inner")?.forEach { inner ->
                // Always trim your text unless you want the risk of spaces at the start or end.
                val title = inner.select(".widget-title").text().trim()
                val elements = inner.select(".video-block").map {
                    val link = fixUrl(it.select("a").attr("href"))
                    val image = it.select(".picture > img").attr("src")
                    val name = it.select("div.name").text().trim().replace(Regex("""[Ee]pisodio \d+"""), "")
                    val isSeries = (name.contains("Season") || name.contains("Episodio"))


                    if (isSeries) {
                        AnimeSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.Anime,
                            image,
                            null,
                            null,
                        )
                    } else {
                        MovieSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.AnimeMovie,
                            image,
                            null,
                            null,
                        )
                    }
                }

                homePageList.add(
                    HomePageList(
                        title, elements
                    )
                )

            }

        }
        return HomePageResponse(homePageList)
    }

    // loadLinks gets the raw .mp4 or .m3u8 urls from the data parameter in the episodes class generated in load()
    // See TvSeriesEpisode(...) in this provider.
    // The data are usually links, but can be any other string to help aid loading the links.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        // These callbacks are functions you should call when you get a link to a subtitle file or media file.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframe = fixUrl(doc.selectFirst("div.play-video iframe").attr("src"))
         app.get(iframe).document.select("ul.list-server-items li").apmap {
            val url = it.attr("data-video")
            for (extractor in extractorApis) {
                if (url.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(url, data)?.apmap {
                        callback(it)
                    }
                }
            }
        }
        return true
    }
}
