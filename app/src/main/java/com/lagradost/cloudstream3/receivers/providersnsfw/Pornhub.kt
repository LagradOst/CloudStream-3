package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.*

class Pornhub:MainAPI() {
    override var mainUrl = "https://www.pornhub.com"
    override var name = "Pornhub"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded //Cause it's a big site
    override val supportedTypes = setOf(TvType.XXX, TvType.JAV, TvType.Hentai)

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair(mainUrl, "Trending Now"),
            Pair("$mainUrl/video?o=tr", "Best rated this month"),
            Pair("$mainUrl/video?c=111", "Japanese"),
            Pair("$mainUrl/categories/hentai", "Hentai"),
        )
        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select("div.sectionWrapper div.wrap").mapNotNull {
                    if (it == null) { return@mapNotNull null }
                    val title = it.selectFirst("span.title a").text()
                    val link = fixUrlNull(it.selectFirst("a").attr("href")) ?: return@mapNotNull null
                    val img = fetchImgUrl(it.selectFirst("img"))
                    MovieSearchResponse(
                        name = title,
                        url = link,
                        apiName = this.name,
                        type = TvType.XXX,
                        posterUrl = img
                    )
                }
                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url).document
        return document.select("div.sectionWrapper div.wrap")?.mapNotNull {
            if (it == null) { return@mapNotNull null }
            val title = it.selectFirst("span.title a")?.text() ?: return@mapNotNull null
            val link = fixUrlNull(it.selectFirst("a").attr("href")) ?: return@mapNotNull null
            val image = fetchImgUrl(it.selectFirst("img"))
            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.XXX,
                posterUrl = image
            )
        }?.distinctBy { it.url } ?: listOf()
    }
    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url).document
        val title = soup.selectFirst(".title span")?.text() ?: ""
        val description = title
        val poster: String? = soup.selectFirst("div.video-wrapper .mainPlayerDiv img").attr("src") ?:
            soup.selectFirst("head meta[property=og:image]").attr("content")
        val tags = soup.select("div.categoriesWrapper a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.XXX,
            dataUrl = url,
            posterUrl = poster,
            tags = tags,
            plot = description
        )
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
       val response = app.get(data, interceptor = WebViewResolver(
           Regex("(master\\.m3u8\\?.*)")
       )
       )
        M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                response.url,
                headers = response.headers.toMap()
            ), true
        )
            .map { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                callback(ExtractorLink(
                    name,
                    "$name $qualityString",
                    stream.streamUrl,
                    mainUrl,
                    getQualityFromName(stream.quality.toString()),
                    true
                ))
            }

        return true
    }

    private fun fetchImgUrl(imgsrc: Element?): String? {
        val img = try { imgsrc?.attr("data-src")
                ?: imgsrc?.attr("data-mediabook")
                ?: imgsrc?.attr("alt")
                ?: imgsrc?.attr("data-mediumthumb")
                ?: imgsrc?.attr("data-thumb_url")
                ?: imgsrc?.attr("src")
        } catch (e:Exception) { null }
        return img
    }
}