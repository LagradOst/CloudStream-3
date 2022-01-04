package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri

interface IGenerator {
    fun hasNext(): Boolean
    fun hasPrev(): Boolean
    fun next()
    fun prev()
    fun goto(index : Int)

    fun getCurrentId() : Int

    /* not safe, must use try catch */
    fun generateLinks(
        clearCache : Boolean,
        isCasting: Boolean,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit
    ) : Boolean
}