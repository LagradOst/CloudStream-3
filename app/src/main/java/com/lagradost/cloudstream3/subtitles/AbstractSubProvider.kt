package com.lagradost.cloudstream3.subtitles

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.*

abstract class AbstractSubProvider{
    open val name = ""

    @WorkerThread
    open suspend fun authorize(ouath: SubtitleOAuthEntity): SubtitleOAuthEntity {
        throw NotImplementedError()
    }

    @WorkerThread
    open suspend fun search(ouath: SubtitleOAuthEntity): List<SubtitleEntity> {
        throw NotImplementedError()
    }

    @WorkerThread
    open suspend fun load(data: SubtitleEntity): String {
        throw NotImplementedError()
    }
}