package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*

class Xvideos:MainAPI() {
    override var mainUrl = "https://www.xvideos.com"
    override var name = "Xvideos"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.XXX, TvType.JAV, TvType.Hentai)

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair(mainUrl, "Trending Now"),
            Pair("$mainUrl/best", "Best"),
            Pair("$mainUrl/tags/jav", "JAV"),
        )
        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select("div.thumb-block").mapNotNull {
                    if (it == null) { return@mapNotNull null }
                    val title = it.selectFirst("p.title a")?.text() ?: ""
                    val link = fixUrlNull(it.selectFirst("div.thumb a")?.attr("href")) ?: return@mapNotNull null
                    val image = it.selectFirst("div.thumb a img")?.attr("data-src")
                    MovieSearchResponse(
                        name = title,
                        url = link,
                        apiName = this.name,
                        type = TvType.XXX,
                        posterUrl = image,
                        year = null
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
        val url = "$mainUrl?k=${query}"
        val document = app.get(url).document
        return document.select("div.thumb-block").map {
            val title = try {
                it.selectFirst("p.title a").text()
            } catch (e:Exception) {
                it.selectFirst("p.profile-name a").text()
            } catch (e:Exception) {
                ""
            }
            val href = it.selectFirst("div.thumb a").attr("href")
            val image = if (href.contains("channels") || href.contains("pornstars")) null else it.selectFirst("div.thumb-inside a img").attr("data-src")
            val finaltitle = if (href.contains("channels") || href.contains("pornstars")) "" else title
            MovieSearchResponse(
                finaltitle,
                fixUrl(href),
                this.name,
                TvType.XXX,
                image,
                null
            )

        }.toList()
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = if (url.contains("channels")||url.contains("pornstars")) soup.selectFirst("html.xv-responsive.is-desktop head title").text() else
            soup.selectFirst(".page-title").text()
        val description = title ?: null
        val poster: String? = if (url.contains("channels") || url.contains("pornstars")) soup.selectFirst(".profile-pic img").attr("data-src") else
            soup.selectFirst("head meta[property=og:image]").attr("content")
        val tags = soup.select(".video-tags-list li a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        val episodes = soup.select("div.thumb-block")?.mapNotNull {
            val href = it?.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val name = it.selectFirst("p.title a").text() ?: null
            val epthumb = it.selectFirst("div.thumb a img").attr("data-src")
            Episode(
                name = name,
                data = href,
                posterUrl = epthumb,
            )
        } ?: listOf()
        val tvType = if (url.contains("channels") || url.contains("pornstars")) TvType.TvSeries else TvType.XXX
        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    name = title,
                    url = url,
                    apiName = this.name,
                    type = TvType.XXX,
                    episodes = episodes,
                    posterUrl = poster,
                    plot = description,
                    showStatus = ShowStatus.Ongoing,
                    tags = tags,
                )
            }
            TvType.XXX -> {
                MovieLoadResponse(
                    name = title,
                    url = url,
                    apiName = this.name,
                    type = tvType,
                    dataUrl = url,
                    posterUrl = poster,
                    plot = description,
                    tags = tags,
                )
            }
            else -> null
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("HTML5Player")) {
                val extractedlink = script.data().substringAfter(".setVideoHLS('")
                    .substringBefore("');")
                if (extractedlink.isNotBlank())
                    M3u8Helper().m3u8Generation(
                        M3u8Helper.M3u8Stream(
                            extractedlink,
                            headers = app.get(data).headers.toMap()
                        ), true
                    )
                        .map { stream ->
                            val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                            callback( ExtractorLink(
                                "Xvideos",
                                "Xvideos m3u8 $qualityString",
                                stream.streamUrl,
                                data,
                                getQualityFromName(stream.quality.toString()),
                                true
                            ))
                        }
                val mp4linkhigh = script.data().substringAfter("html5player.setVideoUrlHigh('").substringBefore("');")
                if (mp4linkhigh.isNotBlank())
                    callback(
                        ExtractorLink(
                            "Xvideos",
                            "Xvideos MP4 HIGH",
                            mp4linkhigh,
                            data,
                            Qualities.Unknown.value,
                            false
                        )
                    )
                val mp4linklow = script.data().substringAfter("html5player.setVideoUrlLow('").substringBefore("');")
                if (mp4linklow.isNotBlank())
                    callback(
                        ExtractorLink(
                            "Xvideos",
                            "Xvideos MP4 LOW",
                            mp4linklow,
                            data,
                            Qualities.Unknown.value,
                            false
                        )
                    )
            }
        }
        return true
    }
}