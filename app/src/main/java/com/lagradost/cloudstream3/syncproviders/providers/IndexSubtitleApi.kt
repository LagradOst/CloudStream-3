package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.subtitles.AbstractSubProvider
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.utils.SubtitleHelper

class IndexSubtitleApi : AbstractSubProvider {
    val idPrefix = "indexsubtitle"
    val host = "https://indexsubtitle.com"

    companion object {
        const val TAG = "INDEXSUBS"
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

    private fun getOrdinal(num: Int?) : String? {
        return when (num) {
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

    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity> {
        val imdbId = query.imdb ?: 0
        val lang = query.lang
        val queryLang = SubtitleHelper.fromTwoLettersToLanguage(lang.toString())
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0

        val urlItems = ArrayList<String>()

        val document = app.get("$host/?search=$queryText").document

        document.select("div.my-3.p-3 div.media").map { block ->
            if (seasonNum > 0) {
                val name = block.select("strong.text-primary").text().trim()
                val season = getOrdinal(seasonNum)
                if ((block.selectFirst("a")?.attr("href")
                        ?.contains(
                            "$season",
                            ignoreCase = true
                        )!! || name.contains(
                        "$season",
                        ignoreCase = true
                    )) && name.contains(queryText, ignoreCase = true)
                ) {
                    block.select("div.media").mapNotNull {
                        urlItems.add(
                            fixUrl(
                                it.selectFirst("a")!!.attr("href")
                            )
                        )
                    }
                }
            } else {
                if (block.selectFirst("strong")!!.text().trim()
                        .matches(Regex("(?i)^$queryText\$"))
                ) {
                    if (block.select("span[title=Release]").isNullOrEmpty()) {
                        block.select("div.media").mapNotNull {
                            val urlItem = fixUrl(
                                it.selectFirst("a")!!.attr("href")
                            )
                            val itemDoc = app.get(urlItem).document
                            val id = imdbUrlToIdNullable(itemDoc.selectFirst("div.d-flex span.badge.badge-primary")?.parent()
                                    ?.attr("href"))?.toLongOrNull()
                            val year = itemDoc.selectFirst("div.d-flex span.badge.badge-success")
                                    ?.ownText()
                                    ?.trim()?.toIntOrNull()
                            Log.i(TAG, "id => $id \nyear => $year||$yearNum")
                            if (imdbId > 0) {
                                if (id == imdbId) {
                                    urlItems.add(urlItem)
                                }
                            } else {
                                if (year == yearNum) {
                                    urlItems.add(urlItem)
                                }
                            }
                        }
                    } else {
                        if (block.select("span[title=Release]").text().trim()
                                .contains("$yearNum")
                        ) {
                            block.select("div.media").mapNotNull {
                                urlItems.add(
                                    fixUrl(
                                        it.selectFirst("a")!!.attr("href")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        Log.i(TAG, "urlItems => $urlItems")
        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()

        urlItems.forEach { url ->
            val request = app.get(url)
            if (request.isSuccessful) {
                val maxItem = 10 // prevents ddos site
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
//                                Log.i(TAG, "title tv sub => ${it.selectFirst("strong.d-block.text-primary")?.text()!!}")
                                if (it.selectFirst("strong.d-block.text-primary")?.text()?.trim()
                                        ?.contains(Regex("(?i)((Season)?\\s?0?${seasonNum}?\\s?(Episode)\\s?0?${epNum}[^0-9])|(?i)((S?0?${seasonNum}?E0?${epNum}[^0-9])|(0?${seasonNum}[a-z]0?${epNum}[^0-9]))"))!!
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