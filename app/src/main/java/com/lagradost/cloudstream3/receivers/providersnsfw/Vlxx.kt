package com.lagradost.cloudstream3.providersnsfw

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.movieproviders.SflixProvider
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.AppResponse
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.lang.Exception

class Vlxx : MainAPI() {
    override var name = "Vlxx"
    override var mainUrl = "https://vlxx.sex"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false
    private val ddosGuardKiller = DdosGuardKiller(true)

    private suspend fun getPage(url: String, referer: String): AppResponse {
        var count = 0
        var resp = app.get(url, referer = referer, interceptor = ddosGuardKiller)
        while (resp.code != 200) {
            resp = app.get(url, interceptor = ddosGuardKiller)
            count++
            if (count > 4) {
                return resp
            }
        }
        return resp
    }

    override suspend fun getMainPage(): HomePageResponse {
        val document = getPage(mainUrl, mainUrl).document
        val all = ArrayList<HomePageList>()
        val title = "Homepage"
        val elements = document.select("div#container > div.box > li.video-list")?.mapNotNull {
            val link = fixUrlNull(it.select("a")?.attr("href")) ?: return@mapNotNull null
            val imgArticle = it.select("img.video-image").attr("src")
            val name = it.selectFirst("div.video-name").text()
            val year = null
            MovieSearchResponse(
                name,
                link,
                this.name,
                TvType.JAV,
                imgArticle,
                year
            )
        }?.distinctBy { it.url } ?: listOf()
        if (elements.isNotEmpty()) {
            all.add(
                HomePageList(
                    title, elements
                )
            )
        }
        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = getPage("$mainUrl/search/${query}/", mainUrl).document
        val list = document.select("#container .box .video-list")

        return list?.mapNotNull {
            val link = fixUrlNull(it.select("a")?.attr("href")) ?: return@mapNotNull null
            val imgArticle = it.select(".video-image").attr("src")
            val name = it.selectFirst(".video-name")?.text() ?: ""
            val year = null
            MovieSearchResponse(
                name,
                link,
                this.name,
                TvType.JAV,
                imgArticle,
                year
            )
        }?.distinctBy { it.url } ?: listOf()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pathSplits = data.split("/")
        val id = pathSplits[pathSplits.size - 2]
        Log.d("Blue", "Data -> ${data} id -> ${id}")
        val res = app.post(
            "${mainUrl}/ajax.php",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf("id" to id, "server" to "1", "vlxx_server" to "1"),
            referer = mainUrl
        ).text
        val json = getParamFromJS(res, "var opts = {\\r\\n\\t\\t\\t\\t\\t\\tsources:", "}]")
        Log.d("Blue", "json ${json}")
        json?.let {

            val list = mapper.readValue<List<SflixProvider.Sources>>(it)
            Log.d("Blue", "list ${list}")
            list.forEach {
                it.file?.let { file ->
                    callback.invoke(
                        ExtractorLink(
                            file,
                            name = "${this.name} - ${it.label}",
                            url = file,
                            referer = data,
                            quality = it.label?.let { it1 -> getQualityFromName(it1) } ?: kotlin.run { Qualities.P720.value },
                            file.endsWith("m3u8")
                        )
                    )
                }
            }
        }
        return false

    }

    private fun getParamFromJS(str: String, key: String, keyEnd: String): String? {
        try {
            val firstIndex = str.indexOf(key) + key.length // 4 to index point to first char.
            val temp = str.substring(firstIndex)
            val lastIndex = temp.indexOf(keyEnd) + (keyEnd.length)
            val jsonConfig = temp.substring(0, lastIndex) //
            Log.d("Blue", "jsonConfig ${jsonConfig}")
            //console.log("json string ", jsonConfig)

            val re = jsonConfig.replace("\\r", "")
                .replace("\\t", "")
                .replace("\\\"", "\"")
                .replace("\\\\\\/", "/")
                .replace("\\n", "")

            return re
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null

    }

    override suspend fun load(url: String): LoadResponse {
        val document = getPage(url, url).document
        val title = document.selectFirst(".breadcrumb")?.text() ?: "<No Title>"
        val descript = document.select(".video-content .content")?.text()
        val year = null
        val poster = document.select(".lcol img").attr("src")
        return MovieLoadResponse(
            title,
            url,
            this.name,
            TvType.JAV,
            url,
            poster,
            year,
            descript
        )
    }
}