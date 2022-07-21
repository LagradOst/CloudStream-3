package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// modified code from from https://github.com/saikou-app/saikou/blob/main/app/src/main/java/ani/saikou/others/Kitsu.kt
// GNU General Public License v3.0 https://github.com/saikou-app/saikou/blob/main/LICENSE.md
object Kitsu {
    private suspend fun getKitsuData(query: String): KitsuResponse {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Connection" to "keep-alive",
            "DNT" to "1",
            "Origin" to "https://kitsu.io"
        )

        return app.post(
            "https://kitsu.io/api/graphql",
            headers = headers,
            data = mapOf("query" to query)
        ).parsed()
    }

    private val cache: MutableMap<Pair<String, String>, Map<Int, KitsuResponse.Node>> =
        mutableMapOf()

    var isEnabled = true

    suspend fun getEpisodesDetails(
        malId: String?,
        anilistId: String?,
        isResponseRequired: Boolean = true, // overrides isEnabled
    ): Map<Int, KitsuResponse.Node>? {
        if (!isResponseRequired && !isEnabled) return null
        if (anilistId != null) {
            try {
                val map = getKitsuEpisodesDetails(anilistId, "ANILIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (malId != null) {
            try {
                val map = getKitsuEpisodesDetails(malId, "MYANIMELIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        return null
    }

    @Throws
    suspend fun getKitsuEpisodesDetails(id: String, site: String): Map<Int, KitsuResponse.Node>? {
        require(id.isNotBlank()) {
            "Black id"
        }

        require(site.isNotBlank()) {
            "invalid site"
        }

        if (cache.containsKey(id to site)) {
            return cache[id to site]
        }

        val query =
            """
query {
  lookupMapping(externalId: $id, externalSite: $site) {
    __typename
    ... on Anime {
      id
      episodes(first: 2000) {
        nodes {
          number
          titles {
            canonical
          }
          description
          thumbnail {
            original {
              url
            }
          }
        }
      }
    }
  }
}"""
        val result = getKitsuData(query)
        val map = (result.data?.lookupMapping?.episodes?.nodes ?: return null).mapNotNull { ep ->
            val num = ep?.num ?: return@mapNotNull null
            num to ep
        }.toMap()
        if (map.isNotEmpty()) {
            cache[id to site] = map
        }
        return map
    }

    data class KitsuResponse(
        val data: Data? = null
    ) {
        @Serializable
        data class Data(
            val lookupMapping: LookupMapping? = null
        )

        @Serializable
        data class LookupMapping(
            val id: String? = null,
            val episodes: Episodes? = null
        )

        @Serializable
        data class Episodes(
            val nodes: List<Node?>? = null
        )

        @Serializable
        data class Node(
            @SerialName("number")
            val num: Int? = null,
            val titles: Titles? = null,
            val description: Description? = null,
            val thumbnail: Thumbnail? = null
        )

        @Serializable
        data class Description(
            val en: String? = null
        )

        @Serializable
        data class Thumbnail(
            val original: Original? = null
        )

        @Serializable
        data class Original(
            val url: String? = null
        )

        @Serializable
        data class Titles(
            val canonical: String? = null
        )
    }
}