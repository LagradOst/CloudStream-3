package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import kotlin.math.max
import kotlin.math.min

class RepoLinkGenerator(private val episodes: List<ResultEpisode>, private var currentIndex: Int) : IGenerator {
    override fun hasNext(): Boolean {
        return currentIndex < episodes.size - 1
    }

    override fun hasPrev(): Boolean {
        return currentIndex > 0
    }

    override fun next() {
        if (hasNext())
            currentIndex++
    }

    override fun prev() {
        if (hasPrev())
            currentIndex--
    }

    override fun goto(index: Int) {
        // clamps value
        currentIndex = min(episodes.size - 1, max(0, index))
    }

    override fun getCurrentId(): Int {
        return episodes[currentIndex].id
    }

    // this is a simple array that is used to instantly load links if they are already loaded
    var linkCache = Array<Set<ExtractorLink>>(size = episodes.size, init = { setOf() })
    var subsCache = Array<Set<SubtitleData>>(size = episodes.size, init = { setOf() })

    override fun generateLinks(
        clearCache: Boolean,
        isCasting: Boolean,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit
    ): Boolean {
        val index = currentIndex
        val current = episodes[index]

        val currentLinkCache = if (clearCache) mutableSetOf() else linkCache[index].toMutableSet()
        val currentSubsCache = if (clearCache) mutableSetOf() else subsCache[index].toMutableSet()

        currentLinkCache.forEach { link ->
            callback(Pair(link, null))
        }

        currentSubsCache.forEach { sub ->
            subtitleCallback(sub)
        }

        // this stops all execution if links are cached
        // no extra get requests
        if(currentLinkCache.size > 0) {
            return true
        }

        return APIRepository(
            getApiFromNameNull(current.apiName) ?: throw Exception("This provider does not exist")
        ).loadLinks(current.data,
            isCasting,
            { file ->
                val correctFile = PlayerSubtitleHelper.getSubtitleData(file)
                if (!currentSubsCache.contains(correctFile)) {
                    subtitleCallback(correctFile)
                    currentSubsCache.add(correctFile)
                    subsCache[index] = currentSubsCache
                }
            },
            { link ->
                if (!currentLinkCache.contains(link)) {
                    println("ADDED LINK $link")

                    callback(Pair(link, null))
                    linkCache[index] = currentLinkCache
                }
            }
        )
    }
}