package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object VideoDownloadHelper {
    @Serializable
    data class DownloadEpisodeCached(
        @SerialName("name") val name: String?,
        @SerialName("poster") val poster: String?,
        @SerialName("episode") val episode: Int,
        @SerialName("season") val season: Int?,
        @SerialName("id") override val id: Int,
        @SerialName("parentId") val parentId: Int,
        @SerialName("rating") val rating: Int?,
        @SerialName("description") val description: String?,
        @SerialName("cacheTime") val cacheTime: Long,
    ) : EasyDownloadButton.IMinimumData

    @Serializable
    data class DownloadHeaderCached(
        @SerialName("apiName") val apiName: String,
        @SerialName("url") val url: String,
        @SerialName("type") val type: TvType,
        @SerialName("name") val name: String,
        @SerialName("poster") val poster: String?,
        @SerialName("id") val id: Int,
        @SerialName("cacheTime") val cacheTime: Long,
    )

    @Serializable
    data class ResumeWatching(
        @SerialName("parentId") val parentId: Int,
        @SerialName("episodeId") val episodeId: Int?,
        @SerialName("episode") val episode: Int?,
        @SerialName("season") val season: Int?,
        @SerialName("updateTime") val updateTime: Long,
        @SerialName("isFromDownload") val isFromDownload: Boolean,
    )
}