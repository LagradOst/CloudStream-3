package com.lagradost.cloudstream3.subtitles

import com.lagradost.cloudstream3.subtitles.subproviders.OpenSubtitles
import kotlinx.coroutines.runBlocking

// ALL OF THIS IS WORK IN PROGRESS AND SUBJECT TO CHANGE
data class LazySubtitle(
    val lClass: Class<AbstractSubProvider>
) {
    var subtitleProvider: AbstractSubProvider? = null
        get() {
            return field ?: lClass.newInstance().let {
                it.name
                runBlocking {
                    val authorization = it.authorize(
                        AbstractSubtitleEntities.SubtitleOAuthEntity(

                        )
                    )
                }
                it
            }.also { field = it }
        }
}


object SubtitleHelper {
    val subtitleProviders = listOf(
        OpenSubtitles::class.java,
    ).map {
        LazySubtitle(it as Class<AbstractSubProvider>)
    }
}