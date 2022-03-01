package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class Zplayer: ZplayerV2() {
    override val name: String = "Zplayer"
    override val mainUrl: String = "https://zplayer.live"
}

class Upstream: ZplayerV2() {
    override val name: String = "Upstream" //Here 'cause works
    override val mainUrl: String = "https://upstream.to"
}

open class ZplayerV2 : ExtractorApi() {
    override val name = "Zplayer V2"
    override val mainUrl = "https://v2.zplayer.live"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val doc = app.get(url).document
        val sources = mutableListOf<ExtractorLink>()
        doc.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val testdata = getAndUnpack(script.data())
                val m3u8regex = Regex("((https:|http:)\\/\\/.*\\.m3u8)")
                m3u8regex.findAll(testdata).map {
                    it.value
                }.toList().apmap { urlm3u8 ->
                    if (urlm3u8.contains("m3u8")) {
                        val testurl = app.get(urlm3u8, headers = mapOf("Referer" to url)).text
                        if (testurl.contains("EXTM3U")) {
                            M3u8Helper().m3u8Generation(
                                M3u8Helper.M3u8Stream(
                                    urlm3u8,
                                    headers = mapOf("Referer" to url)
                                ), true
                            )
                                .apmap { stream ->
                                    val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                                    sources.add(  ExtractorLink(
                                        name,
                                        "$name $qualityString",
                                        stream.streamUrl,
                                        url,
                                        getQualityFromName(stream.quality.toString()),
                                        true
                                    ))
                                }
                        }
                    }
                }
            }
        }
        return sources
    }
}