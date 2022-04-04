package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

/**
 * overrideMainUrl is necessary for for other vidstream clones like vidembed.cc
 * If they diverge it'd be better to make them separate.
 * */
class Pelisplus(val mainUrl: String) {
    val name: String = "Vidstream"

    private fun getExtractorUrl(id: String): String {
        return "$mainUrl/play?id=$id"
    }

    private fun getExtractorUrl2(id: String): String {
        return "$mainUrl/play?id=$id="
    }

    private fun getExtractorUrl3(id: String): String {
        return "$mainUrl/play?id=$id&option=castell"
    }

    private val normalApis = arrayListOf(MultiQuality())

    // https://gogo-stream.com/streaming.php?id=MTE3NDg5
    suspend fun getUrl(id: String, isCasting: Boolean = false, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            normalApis.apmap { api ->
                val url = api.getExtractorUrl(id)
                val source = api.getSafeUrl(url)
                source?.forEach {
                    it.name += " Latino"
                    callback.invoke(it) }
            }
            val extractorUrl = getExtractorUrl(id)
            with(app.get(extractorUrl, timeout = 60)) {
                val document = Jsoup.parse(this.text)
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                //val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
                // All vidstream links passed to extractors
                primaryLinks.distinctBy { it.attr("data-video") }.forEach { element ->
                    val link = element.attr("data-video")
                    //val name = element.text()

                    // Matches vidstream links with extractors
                    extractorApis.filter { !it.requiresReferer || !isCasting }.apmap { api ->
                        if (link.startsWith(api.mainUrl)) {
                            val extractedLinks = api.getSafeUrl(link, extractorUrl)
                            if (extractedLinks?.isNotEmpty() == true) {
                                extractedLinks.forEach {
                                    it.name += " Latino"
                                    callback.invoke(it)
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun getUrl2(id: String, isCasting: Boolean = false, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            normalApis.apmap { api ->
                val url = api.getExtractorUrl(id)
                val source = api.getSafeUrl(url)
                source?.forEach {
                    it.name += " Subtitulado"
                    callback.invoke(it) }
            }
            val extractorUrl = getExtractorUrl2(id)
            with(app.get(extractorUrl, timeout = 60)) {
                val document = Jsoup.parse(this.text)
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                //val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()

                // All vidstream links passed to extractors
                primaryLinks.distinctBy { it.attr("data-video") }.forEach { element ->
                    val link = element.attr("data-video")
                    //val name = element.text()

                    // Matches vidstream links with extractors
                    extractorApis.filter { !it.requiresReferer || !isCasting }.apmap { api ->
                        if (link.startsWith(api.mainUrl)) {
                            val extractedLinks = api.getSafeUrl(link, extractorUrl)
                            if (extractedLinks?.isNotEmpty() == true) {
                                extractedLinks.forEach {
                                    it.name += " Subtitulado"
                                    callback.invoke(it)
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }


    suspend fun getUrl3(id: String, isCasting: Boolean = false, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            normalApis.apmap { api ->
                val url = api.getExtractorUrl(id)
                val source = api.getSafeUrl(url)
                source?.forEach {
                    it.name += " Castellano"
                    callback.invoke(it) }
            }
            val extractorUrl = getExtractorUrl3(id)
            with(app.get(extractorUrl, timeout = 60)) {
                val document = Jsoup.parse(this.text)
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                //val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
                // All vidstream links passed to extractors
                primaryLinks.distinctBy { it.attr("data-video") }.forEach { element ->
                    val link = element.attr("data-video")
                    //val name = element.text()
                    // Matches vidstream links with extractors
                    extractorApis.filter { !it.requiresReferer || !isCasting }.apmap { api ->
                        if (link.startsWith(api.mainUrl)) {
                            val extractedLinks = api.getSafeUrl(link,extractorUrl)
                            if (extractedLinks?.isNotEmpty() == true) {
                                extractedLinks.forEach {
                                    it.name += " Castellano"
                                    callback.invoke(it)
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }

}
