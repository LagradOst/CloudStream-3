package com.lagradost.cloudstream3.subtitles

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.*

abstract class AbstractSubProvider{
    open val name = ""
    open var ouath: SubtitleOAuthEntity = SubtitleOAuthEntity()

    fun overrideCredentials(data: SubtitleOAuthEntity) {
        this.ouath = data
    }

    @WorkerThread
    open suspend fun authorize() {
        throw NotImplementedError()
    }

    @WorkerThread
    open suspend fun search(query: SubtitleSearch): List<SubtitleEntity> {
        throw NotImplementedError()
    }

    @WorkerThread
    open suspend fun load(data: SubtitleEntity): String {
        throw NotImplementedError()
    }
}