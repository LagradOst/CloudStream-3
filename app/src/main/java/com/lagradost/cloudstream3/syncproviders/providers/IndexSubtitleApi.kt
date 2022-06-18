package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.AbstractSubProvider
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager
import com.lagradost.cloudstream3.utils.SubtitleHelper

class IndexSubtitleApi : AbstractSubProvider {
    val idPrefix = "indexsubtitle"
    val host = "https://indexsubtitle.com"

    companion object {
        const val TAG = "INDEXSUBS"
    }

    private fun Number?.getOrdinal(): String? {
        if (this == null) {
            return null
        }
        return when (this) {
            1 -> "First"
            2 -> "Second"
            3 -> "Third"
            4 -> "Fourth"
            5 -> "Fifth"
            6 -> "Sixth"
            7 -> "Seventh"
            8 -> "Eighth"
            9 -> "Ninth"
            10 -> "Tenth"
            11 -> "Eleventh"
            12 -> "Twelfth"
            13 -> "Thirteenth"
            14 -> "Fourteenth"
            15 -> "Fifteenth"
            16 -> "Sixteenth"
            17 -> "Seventeenth"
            18 -> "Eighteenth"
            19 -> "Nineteenth"
            20 -> "Twentieth"
            21 -> "Twenty-First"
            22 -> "Twenty-Second"
            23 -> "Twenty-Third"
            24 -> "Twenty-Fourth"
            25 -> "Twenty-Fifth"
            26 -> "Twenty-Sixth"
            27 -> "Twenty-Seventh"
            28 -> "Twenty-Eighth"
            29 -> "Twenty-Ninth"
            30 -> "Thirtieth"
            31 -> "Thirty-First"
            32 -> "Thirty-Second"
            33 -> "Thirty-Third"
            34 -> "Thirty-Fourth"
            35 -> "Thirty-Fifth"
            else -> null
        }
    }

    private fun fixUrl(url: String): String {
        if (url.startsWith("http")) {
            return url
        }
        if (url.isEmpty()) {
            return ""
        }

        val startsWithNoHttp = url.startsWith("//")
        if (startsWithNoHttp) {
            return "https:$url"
        } else {
            if (url.startsWith('/')) {
                return host + url
            }
            return "$host/$url"
        }
    }

    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity> {
        val lang = query.lang
        val queryLang = SubtitleHelper.fromTwoLettersToLanguage(lang.toString())
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0

        val urlItem = ArrayList<String>()

        val document = app.get("$host/?search=$queryText").document

        document.select("div.my-3.p-3 div.media").map { block ->
            if (seasonNum > 0) {
                if (block.selectFirst("a")?.attr("href")
                        ?.contains(
                            "${seasonNum.getOrdinal()}",
                            ignoreCase = true
                        )!! && block.selectFirst("strong")!!
                        .text().trim().contains(queryText, ignoreCase = true)
                ) {
                    block.select("div.media").mapNotNull {
                        urlItem.add(
                            fixUrl(
                                it.selectFirst("a")!!.attr("href")
                            )
                        )
                    }
                }
            } else {
                if (block.selectFirst("strong")!!.text().trim()
                        .matches(Regex("(?i)^$queryText\$")) && block.select("span[title=Release]")
                        .text().trim().contains("$yearNum")
                ) {
                    block.select("div.media").mapNotNull {
                        urlItem.add(
                            fixUrl(
                                it.selectFirst("a")!!.attr("href")
                            )
                        )
                    }
                }
            }
        }

        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()

        urlItem.forEach { url ->
            val request = app.get(url)
            if (request.isSuccessful) {
                val maxItem = 10 // maximal item that can takes for avoiding ddos site
                var item = 0
                request.document.select("div.my-3.p-3 div.media").apmap { block ->
                    if (block.select("span.d-block span[data-original-title=Language]").text().trim()
                            .contains("$queryLang") && item < maxItem
                    ) {
                        ++item
                        app.get(
                            fixUrl(
                                block.selectFirst("a")!!.attr("href")
                            )
                        ).document.select("div.my-3.p-3 div.media").map {
                            if(epNum > 0) {
                                if (it.selectFirst("strong.d-block.text-primary")?.text()?.trim()
                                        ?.contains(Regex("(?i)(Season\\s?0?${seasonNum}Episode\\s?0?$epNum)|(S?0?${seasonNum}x?E?0?$epNum[\\s|.])"))!!
                                ) {
                                    results.add(
                                        AbstractSubtitleEntities.SubtitleEntity(
                                            idPrefix = idPrefix,
                                            name = it.selectFirst("strong.d-block.text-primary")?.text()!!,
                                            lang = queryLang.toString(),
                                            data = fixUrl(it.selectFirst("a")!!.attr("href")),
                                            type = if (seasonNum > 0) TvType.TvSeries else TvType.Movie,
                                            epNumber = epNum,
                                            seasonNumber = seasonNum,
                                            year = yearNum
                                        )
                                    )

                                } else {
                                    null
                                }
                            } else {
                                results.add(
                                    AbstractSubtitleEntities.SubtitleEntity(
                                        idPrefix = idPrefix,
                                        name = it.selectFirst("strong.d-block.text-primary")?.text()!!,
                                        lang = queryLang.toString(),
                                        data = fixUrl(it.selectFirst("a")!!.attr("href")),
                                        type = if (seasonNum > 0) TvType.TvSeries else TvType.Movie,
                                        epNumber = epNum,
                                        seasonNumber = seasonNum,
                                        year = yearNum
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    override suspend fun load(data: AbstractSubtitleEntities.SubtitleEntity): String {
        return data.data
    }

}