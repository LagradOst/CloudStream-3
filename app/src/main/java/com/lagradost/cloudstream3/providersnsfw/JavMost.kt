package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup

class JavMost : MainAPI() {
    override var name = "JAVMost.com"
    override var mainUrl = "https://www5.javmost.com"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override suspend fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body")
            ?.select("div#page-container > div#content > div#content-update > div")
            ?.select("div.col-md-4.col-sm-6")
        val title = "Homepage"
        // Fetch list of items and map
        val elements: List<SearchResponse> = mainbody!!.map {

            val inner = it.select("div.card")
            val linkA = inner.select("div.card-block > a")
            val link = linkA?.firstOrNull()?.attr("href") ?: ""
            val name = listOfNotNull(linkA?.firstOrNull()?.text(), linkA?.getOrNull(1)?.text()).joinToString(" ")
            //Log.i(this.name, "Result => (name and link) ${name} / ${link}")
            var image = inner?.select("center > a > img")?.attr("data-src")
            if (image == null) {
                image = inner?.select("center > a > img")?.attr("src")
            } else {
                if (image == "http") {
                    image = inner?.select("center > a > img")?.attr("src")
                }
            }
            //Log.i(this.name, "Result => (image) ${image}")
            val year = inner.select("div.card-block > p")?.text()
                ?.substring(0, 20)?.replace("Release", "")?.trim()
                ?.substring(0, 4)?.toIntOrNull()

            MovieSearchResponse(
                name,
                link,
                this.name,
                TvType.JAV,
                image,
                year,
                null,
            )
        }

        all.add(
            HomePageList(
                title, elements
            )
        )

        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val html = app.get("$mainUrl/search/${query}/").text
        val document = Jsoup.parse(html)
        val mainbody = document.getElementsByTag("body")
            ?.select("div#page-container > div#content > div#content-update > div")
            ?.select("div.col-md-4.col-sm-6")
        //Log.i(this.name, "Result => $document")
        if (mainbody != null) {
            return mainbody.map {
                val content = it.select("div.card").firstOrNull()
                val linkImg = content?.select("a")?.firstOrNull()

                val href = fixUrl(linkImg?.attr("href") ?: "")
                var image = linkImg?.select("img")?.attr("data-src")?.trim('\'')
                if (image != null) { image = fixUrl(image) }
                //Log.i(this.name, "Result => (link) ${href}, (img) ${image}")
                val titleContent = content?.select("div.card-block > a")
                //Log.i(this.name, "Result => (titleContent) ${titleContent}")
                val title = when (titleContent?.size) {
                    2 -> listOfNotNull(
                        titleContent[0]?.text(),
                        titleContent[1]?.text()
                    ).joinToString(" ")
                    1 -> titleContent[0]?.text()
                    else -> "<No Title found>"
                } ?: "<No Title found>"
                //Log.i(this.name, "Result => (title) ${title}")
                var year: Int? = null
                val yearP = content?.select("div.card-block")?.firstOrNull()?.select("p")
                //Log.i(this.name, "Result => (yearP) ${yearP}")
                val yearElem = when(yearP != null) {
                    true -> yearP?.filter { yearit -> yearit.text()?.contains("Release") == true }
                    false -> null
                }
                val yearString = when (yearElem?.size!! > 0) {
                    true -> yearElem?.get(0)?.text()?.substring(0, 22)?.trim()
                        ?.replace("Release", "")?.trim()
                    false -> null
                }
                //Log.i(this.name, "Result => (yearString) ${yearString}")
                if (yearString != null)  {
                    val maxSize = if (yearString.length > 4) { 4 } else { yearString.length }
                    year = yearString?.substring(0, maxSize)?.toIntOrNull()
                }
                //Log.i(this.name, "Result => (year) ${year}")

                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.JAV,
                    image,
                    year
                )
            }
        }
        return null
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        //Log.i(this.name, "Url => ${url}")
        val body = document.getElementsByTag("head")

        //Log.i(this.name, "Result => ${body}")
        var poster = body?.select("meta[property=og:image]")?.firstOrNull()?.attr("content")
        if (poster != null) { poster = fixUrl(poster) }
        //Log.i(this.name, "Result (image) => ${poster}")
        val title = body?.select("meta[property=og:title]")?.firstOrNull()?.attr("content") ?: "<No Title>"
        val descript = body?.select("meta[property=og:description]")?.firstOrNull()?.attr("content") ?: "<No Synopsis found>"
        //Log.i(this.name, "Result => ${descript}")
        val streamUrl = ""
        val year = null
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.JAV,
            dataUrl = streamUrl,
            posterUrl = poster,
            year = year,
            plot = descript,
            comingSoon = true
        )
    }
}