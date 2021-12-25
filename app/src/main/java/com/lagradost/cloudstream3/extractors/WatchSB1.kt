package com.lagradost.cloudstream3.extractors
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.Session
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorApi

class WatchSB1 : WatchSB() {
    override val name: String
        get() = "WatchSB"
    override val mainUrl: String
        get() = "https://sbplay2.com"
}
