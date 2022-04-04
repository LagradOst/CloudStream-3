package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.TvType

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */
class AnimeIDProvider : AnimeIDProviderTemplate() {
    override val lang = "es"
    // mainUrl is good to have as a holder for the url to make future changes easier.
    override var mainUrl = "https://animeid.to"

    // name is for how the provider will be named which is visible in the UI, no real rules for this.
    override var name = "AnimeID"

    override val homePageUrlList: List<String> = listOf(
        "$mainUrl/movies",
        "$mainUrl/ongoing-series",
        "$mainUrl/popular"
    )

    // This is just extra metadata about what type of movies the provider has.
    // Needed for search functionality.
    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
}
