package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class TantifilmExtractor : ExtractorApi() {
    override var name = "Tantifilm"
    override var mainUrl = "https://cercafilm.net"
    override val requiresReferer = false

    data class TantifilmJsonData (
        @JsonProperty("success") val success : Boolean,
        @JsonProperty("player") val player : TantifilmPlayer,
        @JsonProperty("data") val data : List<TantifilmData>,
        @JsonProperty("captions")val captions : List<String>,
        @JsonProperty("is_vr") val is_vr : Boolean
    )

    data class TantifilmPlayer (

         @JsonProperty("poster_file") val poster_file : String,
         @JsonProperty("logo_file") val logo_file : String,
         @JsonProperty("logo_position") val logo_position : String,
         @JsonProperty("logo_link") val logo_link : String,
         @JsonProperty("logo_margin") val logo_margin : Int,
         @JsonProperty("aspectratio") val aspectratio : String,
         @JsonProperty("powered_text") val powered_text : String,
         @JsonProperty("powered_url") val powered_url : String,
         @JsonProperty("css_background") val css_background : String,
         @JsonProperty("css_text") val css_text : String,
         @JsonProperty("css_menu") val css_menu : String,
         @JsonProperty("css_mntext") val css_mntext : String,
         @JsonProperty("css_caption") val css_caption : String,
         @JsonProperty("css_cttext") val css_cttext : String,
         @JsonProperty("css_ctsize") val css_ctsize : Int,
         @JsonProperty("css_ctopacity") val css_ctopacity : Int,
         @JsonProperty("css_ctedge") val css_ctedge : String,
         @JsonProperty("css_icon") val css_icon : String,
         @JsonProperty("css_ichover") val css_ichover : String,
         @JsonProperty("css_tsprogress") val css_tsprogress : String,
         @JsonProperty("css_tsrail") val css_tsrail : String,
         @JsonProperty("css_button") val css_button : String,
         @JsonProperty("css_bttext") val css_bttext : String,
         @JsonProperty("opt_autostart") val opt_autostart : Boolean,
         @JsonProperty("opt_title") val opt_title : Boolean,
         @JsonProperty("opt_quality") val opt_quality : Boolean,
         @JsonProperty("opt_caption") val opt_caption : Boolean,
         @JsonProperty("opt_download") val opt_download : Boolean,
         @JsonProperty("opt_sharing") val opt_sharing : Boolean,
         @JsonProperty("opt_playrate") val opt_playrate : Boolean,
         @JsonProperty("opt_mute") val opt_mute : Boolean,
         @JsonProperty("opt_loop") val opt_loop : Boolean,
         @JsonProperty("opt_vr") val opt_vr : Boolean,
         @JsonProperty("opt_cast") val opt_cast : Optcast,
         @JsonProperty("opt_nodefault") val opt_nodefault : Boolean,
         @JsonProperty("opt_forceposter") val opt_forceposter : Boolean,
         @JsonProperty("opt_parameter") val opt_parameter : Boolean,
         @JsonProperty("restrict_domain") val restrict_domain : String,
         @JsonProperty("restrict_action") val restrict_action : String,
         @JsonProperty("restrict_target") val restrict_target : String,
         @JsonProperty("resume_enable") val resume_enable : Boolean,
         @JsonProperty("resume_text") val resume_text : String,
         @JsonProperty("resume_yes") val resume_yes : String,
         @JsonProperty("resume_no") val resume_no : String,
         @JsonProperty("adb_enable") val adb_enable : Boolean,
         @JsonProperty("adb_offset") val adb_offset : Int,
         @JsonProperty("adb_text") val adb_text : String,
         @JsonProperty("ads_adult") val ads_adult : Boolean,
         @JsonProperty("ads_pop") val ads_pop : Boolean,
         @JsonProperty("ads_vast") val ads_vast : Boolean,
         @JsonProperty("ads_free") val ads_free : Int,
         @JsonProperty("trackingId") val trackingId : String,
         @JsonProperty("income") val income : Boolean,
         @JsonProperty("incomePop") val incomePop : Boolean,
         @JsonProperty("logger") val logger : String,
         @JsonProperty("revenue") val revenue : String,
         @JsonProperty("revenue_fallback") val revenue_fallback : String,
         @JsonProperty("revenue_track") val revenue_track : String
    )

    data class TantifilmData (
        @JsonProperty("file") val file : String,
        @JsonProperty("label") val label : String,
        @JsonProperty("type") val type : String
    )

    data class Optcast (
        @JsonProperty("appid") val appid : Int
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = "https://cercafilm.net/api/source/${url.substringAfterLast("/")}"
        val response = app.post(link).text.replace("""\""","")
        val jsonvideodata = parseJson<TantifilmJsonData>(response)
        return jsonvideodata.data.map {
            ExtractorLink(
                it.file+".${it.type}",
                this.name,
                it.file+".${it.type}",
                mainUrl,
                it.label.filter{ it.isDigit() }.toInt(),
                false
            )
        } // links are valid in 8h

    }
}