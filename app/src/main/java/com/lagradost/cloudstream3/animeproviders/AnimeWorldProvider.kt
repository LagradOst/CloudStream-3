package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

class AnimeWorldProvider : MainAPI() {
    override var mainUrl = "https://www.animeworld.tv"
    override var name = "AnimeWorld"
    override var lang = "it"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        private var cookies = emptyMap<String, String>()

        private suspend fun request(url: String): NiceResponse {
            if (cookies.isEmpty()) {
                cookies = getCookies(url)
            }
            return app.get(url, cookies = cookies)
        }

        private suspend fun getCookies(url: String): Map<String, String> {
            val rhino = Context.enter()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()

            val slowAes = app.get("https://www.animeworld.tv/aes.min.js").text
//            val slowAes = """
//                var _0x2465=["\x72\x6F\x74\x61\x74\x65","\x73\x62\x6F\x78","\x52\x63\x6F\x6E","\x6E\x75\x6D\x62\x65\x72\x4F\x66\x52\x6F\x75\x6E\x64\x73","\x63\x6F\x72\x65","\x53\x49\x5A\x45\x5F\x32\x35\x36","\x6B\x65\x79\x53\x69\x7A\x65","\x72\x73\x62\x6F\x78","\x73\x68\x69\x66\x74\x52\x6F\x77","\x6D\x69\x78\x43\x6F\x6C\x75\x6D\x6E","\x67\x61\x6C\x6F\x69\x73\x5F\x6D\x75\x6C\x74\x69\x70\x6C\x69\x63\x61\x74\x69\x6F\x6E","\x73\x75\x62\x42\x79\x74\x65\x73","\x73\x68\x69\x66\x74\x52\x6F\x77\x73","\x6D\x69\x78\x43\x6F\x6C\x75\x6D\x6E\x73","\x61\x64\x64\x52\x6F\x75\x6E\x64\x4B\x65\x79","\x63\x72\x65\x61\x74\x65\x52\x6F\x75\x6E\x64\x4B\x65\x79","\x72\x6F\x75\x6E\x64","\x69\x6E\x76\x52\x6F\x75\x6E\x64","\x53\x49\x5A\x45\x5F\x31\x32\x38","\x53\x49\x5A\x45\x5F\x31\x39\x32","\x65\x78\x70\x61\x6E\x64\x4B\x65\x79","\x6D\x61\x69\x6E","\x69\x6E\x76\x4D\x61\x69\x6E","\x73\x6C\x69\x63\x65","\x6C\x65\x6E\x67\x74\x68","\x69\x76\x20\x6C\x65\x6E\x67\x74\x68\x20\x6D\x75\x73\x74\x20\x62\x65\x20\x31\x32\x38\x20\x62\x69\x74\x73\x2E","\x43\x42\x43","\x6D\x6F\x64\x65\x4F\x66\x4F\x70\x65\x72\x61\x74\x69\x6F\x6E","\x70\x61\x64\x42\x79\x74\x65\x73\x49\x6E","\x63\x65\x69\x6C","\x67\x65\x74\x42\x6C\x6F\x63\x6B","\x43\x46\x42","\x65\x6E\x63\x72\x79\x70\x74","\x61\x65\x73","\x70\x75\x73\x68","\x4F\x46\x42","\x64\x65\x63\x72\x79\x70\x74","\x75\x6E\x70\x61\x64\x42\x79\x74\x65\x73\x4F\x75\x74","\x73\x70\x6C\x69\x63\x65"];var slowAES={aes:{keySize:{SIZE_128:16,SIZE_192:24,SIZE_256:32},sbox:[0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16],rsbox:[0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d],rotate:function(_0x85f6x2){var _0x85f6x3=_0x85f6x2[0];for(var _0x85f6x4=0;_0x85f6x4< 3;_0x85f6x4++){_0x85f6x2[_0x85f6x4]= _0x85f6x2[_0x85f6x4+ 1]};_0x85f6x2[3]= _0x85f6x3;return _0x85f6x2},Rcon:[0x8d,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d,0x9a,0x2f,0x5e,0xbc,0x63,0xc6,0x97,0x35,0x6a,0xd4,0xb3,0x7d,0xfa,0xef,0xc5,0x91,0x39,0x72,0xe4,0xd3,0xbd,0x61,0xc2,0x9f,0x25,0x4a,0x94,0x33,0x66,0xcc,0x83,0x1d,0x3a,0x74,0xe8,0xcb,0x8d,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d,0x9a,0x2f,0x5e,0xbc,0x63,0xc6,0x97,0x35,0x6a,0xd4,0xb3,0x7d,0xfa,0xef,0xc5,0x91,0x39,0x72,0xe4,0xd3,0xbd,0x61,0xc2,0x9f,0x25,0x4a,0x94,0x33,0x66,0xcc,0x83,0x1d,0x3a,0x74,0xe8,0xcb,0x8d,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d,0x9a,0x2f,0x5e,0xbc,0x63,0xc6,0x97,0x35,0x6a,0xd4,0xb3,0x7d,0xfa,0xef,0xc5,0x91,0x39,0x72,0xe4,0xd3,0xbd,0x61,0xc2,0x9f,0x25,0x4a,0x94,0x33,0x66,0xcc,0x83,0x1d,0x3a,0x74,0xe8,0xcb,0x8d,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d,0x9a,0x2f,0x5e,0xbc,0x63,0xc6,0x97,0x35,0x6a,0xd4,0xb3,0x7d,0xfa,0xef,0xc5,0x91,0x39,0x72,0xe4,0xd3,0xbd,0x61,0xc2,0x9f,0x25,0x4a,0x94,0x33,0x66,0xcc,0x83,0x1d,0x3a,0x74,0xe8,0xcb,0x8d,0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d,0x9a,0x2f,0x5e,0xbc,0x63,0xc6,0x97,0x35,0x6a,0xd4,0xb3,0x7d,0xfa,0xef,0xc5,0x91,0x39,0x72,0xe4,0xd3,0xbd,0x61,0xc2,0x9f,0x25,0x4a,0x94,0x33,0x66,0xcc,0x83,0x1d,0x3a,0x74,0xe8,0xcb],G2X:[0x00,0x02,0x04,0x06,0x08,0x0a,0x0c,0x0e,0x10,0x12,0x14,0x16,0x18,0x1a,0x1c,0x1e,0x20,0x22,0x24,0x26,0x28,0x2a,0x2c,0x2e,0x30,0x32,0x34,0x36,0x38,0x3a,0x3c,0x3e,0x40,0x42,0x44,0x46,0x48,0x4a,0x4c,0x4e,0x50,0x52,0x54,0x56,0x58,0x5a,0x5c,0x5e,0x60,0x62,0x64,0x66,0x68,0x6a,0x6c,0x6e,0x70,0x72,0x74,0x76,0x78,0x7a,0x7c,0x7e,0x80,0x82,0x84,0x86,0x88,0x8a,0x8c,0x8e,0x90,0x92,0x94,0x96,0x98,0x9a,0x9c,0x9e,0xa0,0xa2,0xa4,0xa6,0xa8,0xaa,0xac,0xae,0xb0,0xb2,0xb4,0xb6,0xb8,0xba,0xbc,0xbe,0xc0,0xc2,0xc4,0xc6,0xc8,0xca,0xcc,0xce,0xd0,0xd2,0xd4,0xd6,0xd8,0xda,0xdc,0xde,0xe0,0xe2,0xe4,0xe6,0xe8,0xea,0xec,0xee,0xf0,0xf2,0xf4,0xf6,0xf8,0xfa,0xfc,0xfe,0x1b,0x19,0x1f,0x1d,0x13,0x11,0x17,0x15,0x0b,0x09,0x0f,0x0d,0x03,0x01,0x07,0x05,0x3b,0x39,0x3f,0x3d,0x33,0x31,0x37,0x35,0x2b,0x29,0x2f,0x2d,0x23,0x21,0x27,0x25,0x5b,0x59,0x5f,0x5d,0x53,0x51,0x57,0x55,0x4b,0x49,0x4f,0x4d,0x43,0x41,0x47,0x45,0x7b,0x79,0x7f,0x7d,0x73,0x71,0x77,0x75,0x6b,0x69,0x6f,0x6d,0x63,0x61,0x67,0x65,0x9b,0x99,0x9f,0x9d,0x93,0x91,0x97,0x95,0x8b,0x89,0x8f,0x8d,0x83,0x81,0x87,0x85,0xbb,0xb9,0xbf,0xbd,0xb3,0xb1,0xb7,0xb5,0xab,0xa9,0xaf,0xad,0xa3,0xa1,0xa7,0xa5,0xdb,0xd9,0xdf,0xdd,0xd3,0xd1,0xd7,0xd5,0xcb,0xc9,0xcf,0xcd,0xc3,0xc1,0xc7,0xc5,0xfb,0xf9,0xff,0xfd,0xf3,0xf1,0xf7,0xf5,0xeb,0xe9,0xef,0xed,0xe3,0xe1,0xe7,0xe5],G3X:[0x00,0x03,0x06,0x05,0x0c,0x0f,0x0a,0x09,0x18,0x1b,0x1e,0x1d,0x14,0x17,0x12,0x11,0x30,0x33,0x36,0x35,0x3c,0x3f,0x3a,0x39,0x28,0x2b,0x2e,0x2d,0x24,0x27,0x22,0x21,0x60,0x63,0x66,0x65,0x6c,0x6f,0x6a,0x69,0x78,0x7b,0x7e,0x7d,0x74,0x77,0x72,0x71,0x50,0x53,0x56,0x55,0x5c,0x5f,0x5a,0x59,0x48,0x4b,0x4e,0x4d,0x44,0x47,0x42,0x41,0xc0,0xc3,0xc6,0xc5,0xcc,0xcf,0xca,0xc9,0xd8,0xdb,0xde,0xdd,0xd4,0xd7,0xd2,0xd1,0xf0,0xf3,0xf6,0xf5,0xfc,0xff,0xfa,0xf9,0xe8,0xeb,0xee,0xed,0xe4,0xe7,0xe2,0xe1,0xa0,0xa3,0xa6,0xa5,0xac,0xaf,0xaa,0xa9,0xb8,0xbb,0xbe,0xbd,0xb4,0xb7,0xb2,0xb1,0x90,0x93,0x96,0x95,0x9c,0x9f,0x9a,0x99,0x88,0x8b,0x8e,0x8d,0x84,0x87,0x82,0x81,0x9b,0x98,0x9d,0x9e,0x97,0x94,0x91,0x92,0x83,0x80,0x85,0x86,0x8f,0x8c,0x89,0x8a,0xab,0xa8,0xad,0xae,0xa7,0xa4,0xa1,0xa2,0xb3,0xb0,0xb5,0xb6,0xbf,0xbc,0xb9,0xba,0xfb,0xf8,0xfd,0xfe,0xf7,0xf4,0xf1,0xf2,0xe3,0xe0,0xe5,0xe6,0xef,0xec,0xe9,0xea,0xcb,0xc8,0xcd,0xce,0xc7,0xc4,0xc1,0xc2,0xd3,0xd0,0xd5,0xd6,0xdf,0xdc,0xd9,0xda,0x5b,0x58,0x5d,0x5e,0x57,0x54,0x51,0x52,0x43,0x40,0x45,0x46,0x4f,0x4c,0x49,0x4a,0x6b,0x68,0x6d,0x6e,0x67,0x64,0x61,0x62,0x73,0x70,0x75,0x76,0x7f,0x7c,0x79,0x7a,0x3b,0x38,0x3d,0x3e,0x37,0x34,0x31,0x32,0x23,0x20,0x25,0x26,0x2f,0x2c,0x29,0x2a,0x0b,0x08,0x0d,0x0e,0x07,0x04,0x01,0x02,0x13,0x10,0x15,0x16,0x1f,0x1c,0x19,0x1a],G9X:[0x00,0x09,0x12,0x1b,0x24,0x2d,0x36,0x3f,0x48,0x41,0x5a,0x53,0x6c,0x65,0x7e,0x77,0x90,0x99,0x82,0x8b,0xb4,0xbd,0xa6,0xaf,0xd8,0xd1,0xca,0xc3,0xfc,0xf5,0xee,0xe7,0x3b,0x32,0x29,0x20,0x1f,0x16,0x0d,0x04,0x73,0x7a,0x61,0x68,0x57,0x5e,0x45,0x4c,0xab,0xa2,0xb9,0xb0,0x8f,0x86,0x9d,0x94,0xe3,0xea,0xf1,0xf8,0xc7,0xce,0xd5,0xdc,0x76,0x7f,0x64,0x6d,0x52,0x5b,0x40,0x49,0x3e,0x37,0x2c,0x25,0x1a,0x13,0x08,0x01,0xe6,0xef,0xf4,0xfd,0xc2,0xcb,0xd0,0xd9,0xae,0xa7,0xbc,0xb5,0x8a,0x83,0x98,0x91,0x4d,0x44,0x5f,0x56,0x69,0x60,0x7b,0x72,0x05,0x0c,0x17,0x1e,0x21,0x28,0x33,0x3a,0xdd,0xd4,0xcf,0xc6,0xf9,0xf0,0xeb,0xe2,0x95,0x9c,0x87,0x8e,0xb1,0xb8,0xa3,0xaa,0xec,0xe5,0xfe,0xf7,0xc8,0xc1,0xda,0xd3,0xa4,0xad,0xb6,0xbf,0x80,0x89,0x92,0x9b,0x7c,0x75,0x6e,0x67,0x58,0x51,0x4a,0x43,0x34,0x3d,0x26,0x2f,0x10,0x19,0x02,0x0b,0xd7,0xde,0xc5,0xcc,0xf3,0xfa,0xe1,0xe8,0x9f,0x96,0x8d,0x84,0xbb,0xb2,0xa9,0xa0,0x47,0x4e,0x55,0x5c,0x63,0x6a,0x71,0x78,0x0f,0x06,0x1d,0x14,0x2b,0x22,0x39,0x30,0x9a,0x93,0x88,0x81,0xbe,0xb7,0xac,0xa5,0xd2,0xdb,0xc0,0xc9,0xf6,0xff,0xe4,0xed,0x0a,0x03,0x18,0x11,0x2e,0x27,0x3c,0x35,0x42,0x4b,0x50,0x59,0x66,0x6f,0x74,0x7d,0xa1,0xa8,0xb3,0xba,0x85,0x8c,0x97,0x9e,0xe9,0xe0,0xfb,0xf2,0xcd,0xc4,0xdf,0xd6,0x31,0x38,0x23,0x2a,0x15,0x1c,0x07,0x0e,0x79,0x70,0x6b,0x62,0x5d,0x54,0x4f,0x46],GBX:[0x00,0x0b,0x16,0x1d,0x2c,0x27,0x3a,0x31,0x58,0x53,0x4e,0x45,0x74,0x7f,0x62,0x69,0xb0,0xbb,0xa6,0xad,0x9c,0x97,0x8a,0x81,0xe8,0xe3,0xfe,0xf5,0xc4,0xcf,0xd2,0xd9,0x7b,0x70,0x6d,0x66,0x57,0x5c,0x41,0x4a,0x23,0x28,0x35,0x3e,0x0f,0x04,0x19,0x12,0xcb,0xc0,0xdd,0xd6,0xe7,0xec,0xf1,0xfa,0x93,0x98,0x85,0x8e,0xbf,0xb4,0xa9,0xa2,0xf6,0xfd,0xe0,0xeb,0xda,0xd1,0xcc,0xc7,0xae,0xa5,0xb8,0xb3,0x82,0x89,0x94,0x9f,0x46,0x4d,0x50,0x5b,0x6a,0x61,0x7c,0x77,0x1e,0x15,0x08,0x03,0x32,0x39,0x24,0x2f,0x8d,0x86,0x9b,0x90,0xa1,0xaa,0xb7,0xbc,0xd5,0xde,0xc3,0xc8,0xf9,0xf2,0xef,0xe4,0x3d,0x36,0x2b,0x20,0x11,0x1a,0x07,0x0c,0x65,0x6e,0x73,0x78,0x49,0x42,0x5f,0x54,0xf7,0xfc,0xe1,0xea,0xdb,0xd0,0xcd,0xc6,0xaf,0xa4,0xb9,0xb2,0x83,0x88,0x95,0x9e,0x47,0x4c,0x51,0x5a,0x6b,0x60,0x7d,0x76,0x1f,0x14,0x09,0x02,0x33,0x38,0x25,0x2e,0x8c,0x87,0x9a,0x91,0xa0,0xab,0xb6,0xbd,0xd4,0xdf,0xc2,0xc9,0xf8,0xf3,0xee,0xe5,0x3c,0x37,0x2a,0x21,0x10,0x1b,0x06,0x0d,0x64,0x6f,0x72,0x79,0x48,0x43,0x5e,0x55,0x01,0x0a,0x17,0x1c,0x2d,0x26,0x3b,0x30,0x59,0x52,0x4f,0x44,0x75,0x7e,0x63,0x68,0xb1,0xba,0xa7,0xac,0x9d,0x96,0x8b,0x80,0xe9,0xe2,0xff,0xf4,0xc5,0xce,0xd3,0xd8,0x7a,0x71,0x6c,0x67,0x56,0x5d,0x40,0x4b,0x22,0x29,0x34,0x3f,0x0e,0x05,0x18,0x13,0xca,0xc1,0xdc,0xd7,0xe6,0xed,0xf0,0xfb,0x92,0x99,0x84,0x8f,0xbe,0xb5,0xa8,0xa3],GDX:[0x00,0x0d,0x1a,0x17,0x34,0x39,0x2e,0x23,0x68,0x65,0x72,0x7f,0x5c,0x51,0x46,0x4b,0xd0,0xdd,0xca,0xc7,0xe4,0xe9,0xfe,0xf3,0xb8,0xb5,0xa2,0xaf,0x8c,0x81,0x96,0x9b,0xbb,0xb6,0xa1,0xac,0x8f,0x82,0x95,0x98,0xd3,0xde,0xc9,0xc4,0xe7,0xea,0xfd,0xf0,0x6b,0x66,0x71,0x7c,0x5f,0x52,0x45,0x48,0x03,0x0e,0x19,0x14,0x37,0x3a,0x2d,0x20,0x6d,0x60,0x77,0x7a,0x59,0x54,0x43,0x4e,0x05,0x08,0x1f,0x12,0x31,0x3c,0x2b,0x26,0xbd,0xb0,0xa7,0xaa,0x89,0x84,0x93,0x9e,0xd5,0xd8,0xcf,0xc2,0xe1,0xec,0xfb,0xf6,0xd6,0xdb,0xcc,0xc1,0xe2,0xef,0xf8,0xf5,0xbe,0xb3,0xa4,0xa9,0x8a,0x87,0x90,0x9d,0x06,0x0b,0x1c,0x11,0x32,0x3f,0x28,0x25,0x6e,0x63,0x74,0x79,0x5a,0x57,0x40,0x4d,0xda,0xd7,0xc0,0xcd,0xee,0xe3,0xf4,0xf9,0xb2,0xbf,0xa8,0xa5,0x86,0x8b,0x9c,0x91,0x0a,0x07,0x10,0x1d,0x3e,0x33,0x24,0x29,0x62,0x6f,0x78,0x75,0x56,0x5b,0x4c,0x41,0x61,0x6c,0x7b,0x76,0x55,0x58,0x4f,0x42,0x09,0x04,0x13,0x1e,0x3d,0x30,0x27,0x2a,0xb1,0xbc,0xab,0xa6,0x85,0x88,0x9f,0x92,0xd9,0xd4,0xc3,0xce,0xed,0xe0,0xf7,0xfa,0xb7,0xba,0xad,0xa0,0x83,0x8e,0x99,0x94,0xdf,0xd2,0xc5,0xc8,0xeb,0xe6,0xf1,0xfc,0x67,0x6a,0x7d,0x70,0x53,0x5e,0x49,0x44,0x0f,0x02,0x15,0x18,0x3b,0x36,0x21,0x2c,0x0c,0x01,0x16,0x1b,0x38,0x35,0x22,0x2f,0x64,0x69,0x7e,0x73,0x50,0x5d,0x4a,0x47,0xdc,0xd1,0xc6,0xcb,0xe8,0xe5,0xf2,0xff,0xb4,0xb9,0xae,0xa3,0x80,0x8d,0x9a,0x97],GEX:[0x00,0x0e,0x1c,0x12,0x38,0x36,0x24,0x2a,0x70,0x7e,0x6c,0x62,0x48,0x46,0x54,0x5a,0xe0,0xee,0xfc,0xf2,0xd8,0xd6,0xc4,0xca,0x90,0x9e,0x8c,0x82,0xa8,0xa6,0xb4,0xba,0xdb,0xd5,0xc7,0xc9,0xe3,0xed,0xff,0xf1,0xab,0xa5,0xb7,0xb9,0x93,0x9d,0x8f,0x81,0x3b,0x35,0x27,0x29,0x03,0x0d,0x1f,0x11,0x4b,0x45,0x57,0x59,0x73,0x7d,0x6f,0x61,0xad,0xa3,0xb1,0xbf,0x95,0x9b,0x89,0x87,0xdd,0xd3,0xc1,0xcf,0xe5,0xeb,0xf9,0xf7,0x4d,0x43,0x51,0x5f,0x75,0x7b,0x69,0x67,0x3d,0x33,0x21,0x2f,0x05,0x0b,0x19,0x17,0x76,0x78,0x6a,0x64,0x4e,0x40,0x52,0x5c,0x06,0x08,0x1a,0x14,0x3e,0x30,0x22,0x2c,0x96,0x98,0x8a,0x84,0xae,0xa0,0xb2,0xbc,0xe6,0xe8,0xfa,0xf4,0xde,0xd0,0xc2,0xcc,0x41,0x4f,0x5d,0x53,0x79,0x77,0x65,0x6b,0x31,0x3f,0x2d,0x23,0x09,0x07,0x15,0x1b,0xa1,0xaf,0xbd,0xb3,0x99,0x97,0x85,0x8b,0xd1,0xdf,0xcd,0xc3,0xe9,0xe7,0xf5,0xfb,0x9a,0x94,0x86,0x88,0xa2,0xac,0xbe,0xb0,0xea,0xe4,0xf6,0xf8,0xd2,0xdc,0xce,0xc0,0x7a,0x74,0x66,0x68,0x42,0x4c,0x5e,0x50,0x0a,0x04,0x16,0x18,0x32,0x3c,0x2e,0x20,0xec,0xe2,0xf0,0xfe,0xd4,0xda,0xc8,0xc6,0x9c,0x92,0x80,0x8e,0xa4,0xaa,0xb8,0xb6,0x0c,0x02,0x10,0x1e,0x34,0x3a,0x28,0x26,0x7c,0x72,0x60,0x6e,0x44,0x4a,0x58,0x56,0x37,0x39,0x2b,0x25,0x0f,0x01,0x13,0x1d,0x47,0x49,0x5b,0x55,0x7f,0x71,0x63,0x6d,0xd7,0xd9,0xcb,0xc5,0xef,0xe1,0xf3,0xfd,0xa7,0xa9,0xbb,0xb5,0x9f,0x91,0x83,0x8d],core:function(_0x85f6x2,_0x85f6x5){_0x85f6x2= this[_0x2465[0]](_0x85f6x2);for(var _0x85f6x4=0;_0x85f6x4< 4;++_0x85f6x4){_0x85f6x2[_0x85f6x4]= this[_0x2465[1]][_0x85f6x2[_0x85f6x4]]};_0x85f6x2[0]= _0x85f6x2[0]^ this[_0x2465[2]][_0x85f6x5];return _0x85f6x2},expandKey:function(_0x85f6x6,_0x85f6x7){var _0x85f6x8=(16* (this[_0x2465[3]](_0x85f6x7)+ 1));var _0x85f6x9=0;var _0x85f6xa=1;var _0x85f6xb=[];var _0x85f6xc=[];for(var _0x85f6x4=0;_0x85f6x4< _0x85f6x8;_0x85f6x4++){_0x85f6xc[_0x85f6x4]= 0};for(var _0x85f6xd=0;_0x85f6xd< _0x85f6x7;_0x85f6xd++){_0x85f6xc[_0x85f6xd]= _0x85f6x6[_0x85f6xd]};_0x85f6x9+= _0x85f6x7;while(_0x85f6x9< _0x85f6x8){for(var _0x85f6xe=0;_0x85f6xe< 4;_0x85f6xe++){_0x85f6xb[_0x85f6xe]= _0x85f6xc[(_0x85f6x9- 4)+ _0x85f6xe]};if(_0x85f6x9% _0x85f6x7== 0){_0x85f6xb= this[_0x2465[4]](_0x85f6xb,_0x85f6xa++)};if(_0x85f6x7== this[_0x2465[6]][_0x2465[5]]&& ((_0x85f6x9% _0x85f6x7)== 16)){for(var _0x85f6xf=0;_0x85f6xf< 4;_0x85f6xf++){_0x85f6xb[_0x85f6xf]= this[_0x2465[1]][_0x85f6xb[_0x85f6xf]]}};for(var _0x85f6x10=0;_0x85f6x10< 4;_0x85f6x10++){_0x85f6xc[_0x85f6x9]= _0x85f6xc[_0x85f6x9- _0x85f6x7]^ _0x85f6xb[_0x85f6x10];_0x85f6x9++}};return _0x85f6xc},addRoundKey:function(_0x85f6x11,_0x85f6x12){for(var _0x85f6x4=0;_0x85f6x4< 16;_0x85f6x4++){_0x85f6x11[_0x85f6x4]^= _0x85f6x12[_0x85f6x4]};return _0x85f6x11},createRoundKey:function(_0x85f6xc,_0x85f6x13){var _0x85f6x12=[];for(var _0x85f6x4=0;_0x85f6x4< 4;_0x85f6x4++){for(var _0x85f6xd=0;_0x85f6xd< 4;_0x85f6xd++){_0x85f6x12[_0x85f6xd* 4+ _0x85f6x4]= _0x85f6xc[_0x85f6x13+ _0x85f6x4* 4+ _0x85f6xd]}};return _0x85f6x12},subBytes:function(_0x85f6x11,_0x85f6x14){for(var _0x85f6x4=0;_0x85f6x4< 16;_0x85f6x4++){_0x85f6x11[_0x85f6x4]= _0x85f6x14?this[_0x2465[7]][_0x85f6x11[_0x85f6x4]]:this[_0x2465[1]][_0x85f6x11[_0x85f6x4]]};return _0x85f6x11},shiftRows:function(_0x85f6x11,_0x85f6x14){for(var _0x85f6x4=0;_0x85f6x4< 4;_0x85f6x4++){_0x85f6x11= this[_0x2465[8]](_0x85f6x11,_0x85f6x4* 4,_0x85f6x4,_0x85f6x14)};return _0x85f6x11},shiftRow:function(_0x85f6x11,_0x85f6x15,_0x85f6x16,_0x85f6x14){for(var _0x85f6x4=0;_0x85f6x4< _0x85f6x16;_0x85f6x4++){if(_0x85f6x14){var _0x85f6x17=_0x85f6x11[_0x85f6x15+ 3];for(var _0x85f6xd=3;_0x85f6xd> 0;_0x85f6xd--){_0x85f6x11[_0x85f6x15+ _0x85f6xd]= _0x85f6x11[_0x85f6x15+ _0x85f6xd- 1]};_0x85f6x11[_0x85f6x15]= _0x85f6x17}else {var _0x85f6x17=_0x85f6x11[_0x85f6x15];for(var _0x85f6xd=0;_0x85f6xd< 3;_0x85f6xd++){_0x85f6x11[_0x85f6x15+ _0x85f6xd]= _0x85f6x11[_0x85f6x15+ _0x85f6xd+ 1]};_0x85f6x11[_0x85f6x15+ 3]= _0x85f6x17}};return _0x85f6x11},galois_multiplication:function(_0x85f6x18,_0x85f6x19){var _0x85f6x1a=0;for(var _0x85f6x1b=0;_0x85f6x1b< 8;_0x85f6x1b++){if((_0x85f6x19& 1)== 1){_0x85f6x1a^= _0x85f6x18};if(_0x85f6x1a> 0x100){_0x85f6x1a^= 0x100};var _0x85f6x1c=(_0x85f6x18& 0x80);_0x85f6x18<<= 1;if(_0x85f6x18> 0x100){_0x85f6x18^= 0x100};if(_0x85f6x1c== 0x80){_0x85f6x18^= 0x1b};if(_0x85f6x18> 0x100){_0x85f6x18^= 0x100};_0x85f6x19>>= 1;if(_0x85f6x19> 0x100){_0x85f6x19^= 0x100}};return _0x85f6x1a},mixColumns:function(_0x85f6x11,_0x85f6x14){var _0x85f6x1d=[];for(var _0x85f6x4=0;_0x85f6x4< 4;_0x85f6x4++){for(var _0x85f6xd=0;_0x85f6xd< 4;_0x85f6xd++){_0x85f6x1d[_0x85f6xd]= _0x85f6x11[(_0x85f6xd* 4)+ _0x85f6x4]};_0x85f6x1d= this[_0x2465[9]](_0x85f6x1d,_0x85f6x14);for(var _0x85f6xe=0;_0x85f6xe< 4;_0x85f6xe++){_0x85f6x11[(_0x85f6xe* 4)+ _0x85f6x4]= _0x85f6x1d[_0x85f6xe]}};return _0x85f6x11},mixColumn:function(_0x85f6x1d,_0x85f6x14){var _0x85f6x1e=[];if(_0x85f6x14){_0x85f6x1e= [14,9,13,11]}else {_0x85f6x1e= [2,1,1,3]};var _0x85f6x1f=[];for(var _0x85f6x4=0;_0x85f6x4< 4;_0x85f6x4++){_0x85f6x1f[_0x85f6x4]= _0x85f6x1d[_0x85f6x4]};_0x85f6x1d[0]= this[_0x2465[10]](_0x85f6x1f[0],_0x85f6x1e[0])^ this[_0x2465[10]](_0x85f6x1f[3],_0x85f6x1e[1])^ this[_0x2465[10]](_0x85f6x1f[2],_0x85f6x1e[2])^ this[_0x2465[10]](_0x85f6x1f[1],_0x85f6x1e[3]);_0x85f6x1d[1]= this[_0x2465[10]](_0x85f6x1f[1],_0x85f6x1e[0])^ this[_0x2465[10]](_0x85f6x1f[0],_0x85f6x1e[1])^ this[_0x2465[10]](_0x85f6x1f[3],_0x85f6x1e[2])^ this[_0x2465[10]](_0x85f6x1f[2],_0x85f6x1e[3]);_0x85f6x1d[2]= this[_0x2465[10]](_0x85f6x1f[2],_0x85f6x1e[0])^ this[_0x2465[10]](_0x85f6x1f[1],_0x85f6x1e[1])^ this[_0x2465[10]](_0x85f6x1f[0],_0x85f6x1e[2])^ this[_0x2465[10]](_0x85f6x1f[3],_0x85f6x1e[3]);_0x85f6x1d[3]= this[_0x2465[10]](_0x85f6x1f[3],_0x85f6x1e[0])^ this[_0x2465[10]](_0x85f6x1f[2],_0x85f6x1e[1])^ this[_0x2465[10]](_0x85f6x1f[1],_0x85f6x1e[2])^ this[_0x2465[10]](_0x85f6x1f[0],_0x85f6x1e[3]);return _0x85f6x1d},round:function(_0x85f6x11,_0x85f6x12){_0x85f6x11= this[_0x2465[11]](_0x85f6x11,false);_0x85f6x11= this[_0x2465[12]](_0x85f6x11,false);_0x85f6x11= this[_0x2465[13]](_0x85f6x11,false);_0x85f6x11= this[_0x2465[14]](_0x85f6x11,_0x85f6x12);return _0x85f6x11},invRound:function(_0x85f6x11,_0x85f6x12){_0x85f6x11= this[_0x2465[12]](_0x85f6x11,true);_0x85f6x11= this[_0x2465[11]](_0x85f6x11,true);_0x85f6x11= this[_0x2465[14]](_0x85f6x11,_0x85f6x12);_0x85f6x11= this[_0x2465[13]](_0x85f6x11,true);return _0x85f6x11},main:function(_0x85f6x11,_0x85f6xc,_0x85f6x20){_0x85f6x11= this[_0x2465[14]](_0x85f6x11,this[_0x2465[15]](_0x85f6xc,0));for(var _0x85f6x4=1;_0x85f6x4< _0x85f6x20;_0x85f6x4++){_0x85f6x11= this[_0x2465[16]](_0x85f6x11,this[_0x2465[15]](_0x85f6xc,16* _0x85f6x4))};_0x85f6x11= this[_0x2465[11]](_0x85f6x11,false);_0x85f6x11= this[_0x2465[12]](_0x85f6x11,false);_0x85f6x11= this[_0x2465[14]](_0x85f6x11,this[_0x2465[15]](_0x85f6xc,16* _0x85f6x20));return _0x85f6x11},invMain:function(_0x85f6x11,_0x85f6xc,_0x85f6x20){_0x85f6x11= this[_0x2465[14]](_0x85f6x11,this[_0x2465[15]](_0x85f6xc,16* _0x85f6x20));for(var _0x85f6x4=_0x85f6x20- 1;_0x85f6x4> 0;_0x85f6x4--){_0x85f6x11= this[_0x2465[17]](_0x85f6x11,this[_0x2465[15]](_0x85f6xc,16* _0x85f6x4))};_0x85f6x11= this[_0x2465[12]](_0x85f6x11,true);_0x85f6x11= this[_0x2465[11]](_0x85f6x11,true);_0x85f6x11= this[_0x2465[14]](_0x85f6x11,this[_0x2465[15]](_0x85f6xc,0));return _0x85f6x11},numberOfRounds:function(_0x85f6x7){var _0x85f6x20;switch(_0x85f6x7){case this[_0x2465[6]][_0x2465[18]]:_0x85f6x20= 10;break;case this[_0x2465[6]][_0x2465[19]]:_0x85f6x20= 12;break;case this[_0x2465[6]][_0x2465[5]]:_0x85f6x20= 14;break;default:return null;break};return _0x85f6x20},encrypt:function(_0x85f6x21,_0x85f6x6,_0x85f6x7){var _0x85f6x22=[];var _0x85f6x23=[];var _0x85f6x20=this[_0x2465[3]](_0x85f6x7);for(var _0x85f6x4=0;_0x85f6x4< 4;_0x85f6x4++){for(var _0x85f6xd=0;_0x85f6xd< 4;_0x85f6xd++){_0x85f6x23[(_0x85f6x4+ (_0x85f6xd* 4))]= _0x85f6x21[(_0x85f6x4* 4)+ _0x85f6xd]}};var _0x85f6xc=this[_0x2465[20]](_0x85f6x6,_0x85f6x7);_0x85f6x23= this[_0x2465[21]](_0x85f6x23,_0x85f6xc,_0x85f6x20);for(var _0x85f6xe=0;_0x85f6xe< 4;_0x85f6xe++){for(var _0x85f6xf=0;_0x85f6xf< 4;_0x85f6xf++){_0x85f6x22[(_0x85f6xe* 4)+ _0x85f6xf]= _0x85f6x23[(_0x85f6xe+ (_0x85f6xf* 4))]}};return _0x85f6x22},decrypt:function(_0x85f6x21,_0x85f6x6,_0x85f6x7){var _0x85f6x22=[];var _0x85f6x23=[];var _0x85f6x20=this[_0x2465[3]](_0x85f6x7);for(var _0x85f6x4=0;_0x85f6x4< 4;_0x85f6x4++){for(var _0x85f6xd=0;_0x85f6xd< 4;_0x85f6xd++){_0x85f6x23[(_0x85f6x4+ (_0x85f6xd* 4))]= _0x85f6x21[(_0x85f6x4* 4)+ _0x85f6xd]}};var _0x85f6xc=this[_0x2465[20]](_0x85f6x6,_0x85f6x7);_0x85f6x23= this[_0x2465[22]](_0x85f6x23,_0x85f6xc,_0x85f6x20);for(var _0x85f6xe=0;_0x85f6xe< 4;_0x85f6xe++){for(var _0x85f6xf=0;_0x85f6xf< 4;_0x85f6xf++){_0x85f6x22[(_0x85f6xe* 4)+ _0x85f6xf]= _0x85f6x23[(_0x85f6xe+ (_0x85f6xf* 4))]}};return _0x85f6x22}},modeOfOperation:{OFB:0,CFB:1,CBC:2},getBlock:function(_0x85f6x24,_0x85f6x25,_0x85f6x26,_0x85f6x27){if(_0x85f6x26- _0x85f6x25> 16){_0x85f6x26= _0x85f6x25+ 16};return _0x85f6x24[_0x2465[23]](_0x85f6x25,_0x85f6x26)},encrypt:function(_0x85f6x24,_0x85f6x27,_0x85f6x6,_0x85f6x28){var _0x85f6x7=_0x85f6x6[_0x2465[24]];if(_0x85f6x28[_0x2465[24]]% 16){throw _0x2465[25]};var _0x85f6x29=[];var _0x85f6x21=[];var _0x85f6x22=[];var _0x85f6x2a=[];var _0x85f6x2b=[];var _0x85f6x2c=true;if(_0x85f6x27== this[_0x2465[27]][_0x2465[26]]){this[_0x2465[28]](_0x85f6x24)};if(_0x85f6x24!== null){for(var _0x85f6xd=0;_0x85f6xd< Math[_0x2465[29]](_0x85f6x24[_0x2465[24]]/ 16);_0x85f6xd++){var _0x85f6x25=_0x85f6xd* 16;var _0x85f6x26=_0x85f6xd* 16+ 16;if(_0x85f6xd* 16+ 16> _0x85f6x24[_0x2465[24]]){_0x85f6x26= _0x85f6x24[_0x2465[24]]};_0x85f6x29= this[_0x2465[30]](_0x85f6x24,_0x85f6x25,_0x85f6x26,_0x85f6x27);if(_0x85f6x27== this[_0x2465[27]][_0x2465[31]]){if(_0x85f6x2c){_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x28,_0x85f6x6,_0x85f6x7);_0x85f6x2c= false}else {_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x21,_0x85f6x6,_0x85f6x7)};for(var _0x85f6x4=0;_0x85f6x4< 16;_0x85f6x4++){_0x85f6x2a[_0x85f6x4]= _0x85f6x29[_0x85f6x4]^ _0x85f6x22[_0x85f6x4]};for(var _0x85f6xe=0;_0x85f6xe< _0x85f6x26- _0x85f6x25;_0x85f6xe++){_0x85f6x2b[_0x2465[34]](_0x85f6x2a[_0x85f6xe])};_0x85f6x21= _0x85f6x2a}else {if(_0x85f6x27== this[_0x2465[27]][_0x2465[35]]){if(_0x85f6x2c){_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x28,_0x85f6x6,_0x85f6x7);_0x85f6x2c= false}else {_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x21,_0x85f6x6,_0x85f6x7)};for(var _0x85f6x4=0;_0x85f6x4< 16;_0x85f6x4++){_0x85f6x2a[_0x85f6x4]= _0x85f6x29[_0x85f6x4]^ _0x85f6x22[_0x85f6x4]};for(var _0x85f6xe=0;_0x85f6xe< _0x85f6x26- _0x85f6x25;_0x85f6xe++){_0x85f6x2b[_0x2465[34]](_0x85f6x2a[_0x85f6xe])};_0x85f6x21= _0x85f6x22}else {if(_0x85f6x27== this[_0x2465[27]][_0x2465[26]]){for(var _0x85f6x4=0;_0x85f6x4< 16;_0x85f6x4++){_0x85f6x21[_0x85f6x4]= _0x85f6x29[_0x85f6x4]^ ((_0x85f6x2c)?_0x85f6x28[_0x85f6x4]:_0x85f6x2a[_0x85f6x4])};_0x85f6x2c= false;_0x85f6x2a= this[_0x2465[33]][_0x2465[32]](_0x85f6x21,_0x85f6x6,_0x85f6x7);for(var _0x85f6xe=0;_0x85f6xe< 16;_0x85f6xe++){_0x85f6x2b[_0x2465[34]](_0x85f6x2a[_0x85f6xe])}}}}}};return _0x85f6x2b},decrypt:function(_0x85f6x2d,_0x85f6x27,_0x85f6x6,_0x85f6x28){var _0x85f6x7=_0x85f6x6[_0x2465[24]];if(_0x85f6x28[_0x2465[24]]% 16){throw _0x2465[25]};var _0x85f6x2a=[];var _0x85f6x21=[];var _0x85f6x22=[];var _0x85f6x29=[];var _0x85f6x2e=[];var _0x85f6x2c=true;if(_0x85f6x2d!== null){for(var _0x85f6xd=0;_0x85f6xd< Math[_0x2465[29]](_0x85f6x2d[_0x2465[24]]/ 16);_0x85f6xd++){var _0x85f6x25=_0x85f6xd* 16;var _0x85f6x26=_0x85f6xd* 16+ 16;if(_0x85f6xd* 16+ 16> _0x85f6x2d[_0x2465[24]]){_0x85f6x26= _0x85f6x2d[_0x2465[24]]};_0x85f6x2a= this[_0x2465[30]](_0x85f6x2d,_0x85f6x25,_0x85f6x26,_0x85f6x27);if(_0x85f6x27== this[_0x2465[27]][_0x2465[31]]){if(_0x85f6x2c){_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x28,_0x85f6x6,_0x85f6x7);_0x85f6x2c= false}else {_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x21,_0x85f6x6,_0x85f6x7)};for(i= 0;i< 16;i++){_0x85f6x29[i]= _0x85f6x22[i]^ _0x85f6x2a[i]};for(var _0x85f6xe=0;_0x85f6xe< _0x85f6x26- _0x85f6x25;_0x85f6xe++){_0x85f6x2e[_0x2465[34]](_0x85f6x29[_0x85f6xe])};_0x85f6x21= _0x85f6x2a}else {if(_0x85f6x27== this[_0x2465[27]][_0x2465[35]]){if(_0x85f6x2c){_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x28,_0x85f6x6,_0x85f6x7);_0x85f6x2c= false}else {_0x85f6x22= this[_0x2465[33]][_0x2465[32]](_0x85f6x21,_0x85f6x6,_0x85f6x7)};for(i= 0;i< 16;i++){_0x85f6x29[i]= _0x85f6x22[i]^ _0x85f6x2a[i]};for(var _0x85f6xe=0;_0x85f6xe< _0x85f6x26- _0x85f6x25;_0x85f6xe++){_0x85f6x2e[_0x2465[34]](_0x85f6x29[_0x85f6xe])};_0x85f6x21= _0x85f6x22}else {if(_0x85f6x27== this[_0x2465[27]][_0x2465[26]]){_0x85f6x22= this[_0x2465[33]][_0x2465[36]](_0x85f6x2a,_0x85f6x6,_0x85f6x7);for(i= 0;i< 16;i++){_0x85f6x29[i]= ((_0x85f6x2c)?_0x85f6x28[i]:_0x85f6x21[i])^ _0x85f6x22[i]};_0x85f6x2c= false;for(var _0x85f6xe=0;_0x85f6xe< _0x85f6x26- _0x85f6x25;_0x85f6xe++){_0x85f6x2e[_0x2465[34]](_0x85f6x29[_0x85f6xe])};_0x85f6x21= _0x85f6x2a}}}};if(_0x85f6x27== this[_0x2465[27]][_0x2465[26]]){this[_0x2465[37]](_0x85f6x2e)}};return _0x85f6x2e},padBytesIn:function(_0x85f6x2f){var _0x85f6x30=_0x85f6x2f[_0x2465[24]];var _0x85f6x31=16- (_0x85f6x30% 16);for(var _0x85f6x4=0;_0x85f6x4< _0x85f6x31;_0x85f6x4++){_0x85f6x2f[_0x2465[34]](_0x85f6x31)}},unpadBytesOut:function(_0x85f6x2f){var _0x85f6x32=0;var _0x85f6x31=-1;var _0x85f6x33=16;if(_0x85f6x2f[_0x2465[24]]> 16){for(var _0x85f6x4=_0x85f6x2f[_0x2465[24]]- 1;_0x85f6x4>= _0x85f6x2f[_0x2465[24]]- 1- _0x85f6x33;_0x85f6x4--){if(_0x85f6x2f[_0x85f6x4]<= _0x85f6x33){if(_0x85f6x31==  -1){_0x85f6x31= _0x85f6x2f[_0x85f6x4]};if(_0x85f6x2f[_0x85f6x4]!= _0x85f6x31){_0x85f6x32= 0;break};_0x85f6x32++}else {break};if(_0x85f6x32== _0x85f6x31){break}};if(_0x85f6x32> 0){_0x85f6x2f[_0x2465[38]](_0x85f6x2f[_0x2465[24]]- _0x85f6x32,_0x85f6x32)}}}}
//            """.trimIndent()
            val decodeBase64 = "atob = function(s) {\n" +
                    "    var e={},i,b=0,c,x,l=0,a,r='',w=String.fromCharCode,L=s.length;\n" +
                    "    var A=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\";\n" +
                    "    for(i=0;i<64;i++){e[A.charAt(i)]=i;}\n" +
                    "    for(x=0;x<L;x++){\n" +
                    "        c=e[s.charAt(x)];b=(b<<6)+c;l+=6;\n" +
                    "        while(l>=8){((a=(b>>>(l-=8))&0xff)||(x<(L-2)))&&(r+=w(a));}\n" +
                    "    }\n" +
                    "    return r;\n" +
                    "};"
            val doc = "var document = {};"

            val siteScriptRegex = Regex("""script>(.*)\+";\s*path""")
            val html = app.get(url).text
            val siteScript = siteScriptRegex.find(html)?.groupValues?.getOrNull(1)

            rhino.evaluateString(
                scope,
                "$doc$decodeBase64$slowAes;$siteScript;",
                "JavaScript",
                1,
                null
            )
            val jsEval = scope.get("document", scope) as? Scriptable
            val cookies = jsEval?.get("cookie", jsEval) as? ConsString
            return cookies?.split(";")?.associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }?.filter { it.key.isNotBlank() && it.value.isNotBlank() } ?: emptyMap()
        }

        fun getType(t: String?): TvType {
            return when (t?.lowercase()) {
                "movie" -> TvType.AnimeMovie
                "ova" -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "finito" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }

    private fun Element.toSearchResult(showEpisode: Boolean = true): AnimeSearchResponse {
        fun String.parseHref(): String {
            val h = this.split('.').toMutableList()
            h[1] = h[1].substringBeforeLast('/')
            return h.joinToString(".")
        }

        val anchor = this.select("a.name").firstOrNull() ?: throw ErrorLoadingException("Error")
        val title = anchor.text().removeSuffix(" (ITA)")
        val otherTitle = anchor.attr("data-jtitle").removeSuffix(" (ITA)")

        val url = fixUrl(anchor.attr("href").parseHref())
        val poster = this.select("a.poster img").attr("src")

        val statusElement = this.select("div.status") // .first()
        val dub = statusElement.select(".dub").isNotEmpty()

        val episode = if (showEpisode) statusElement.select(".ep").text().split(' ').last()
            .toIntOrNull() else null
        val type = when {
            statusElement.select(".movie").isNotEmpty() -> TvType.AnimeMovie
            statusElement.select(".ova").isNotEmpty() -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub, episode)
            this.otherName = otherTitle
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val document = request(mainUrl).document
        val list = ArrayList<HomePageList>()

        val widget = document.select(".widget.hotnew")
        widget.select(".tabs [data-name=\"sub\"], .tabs [data-name=\"dub\"]").forEach { tab ->
            val tabId = tab.attr("data-name")
            val tabName = tab.text().removeSuffix("-ITA")
            val animeList = widget.select("[data-name=\"$tabId\"] .film-list .item").map {
                it.toSearchResult()
            }
            list.add(HomePageList(tabName, animeList))
        }
        widget.select(".tabs [data-name=\"trending\"]").forEach { tab ->
            val tabId = tab.attr("data-name")
            val tabName = tab.text()
            val animeList = widget.select("[data-name=\"$tabId\"] .film-list .item").map {
                it.toSearchResult(showEpisode = false)
            }.distinctBy { it.url }
            list.add(HomePageList(tabName, animeList))
        }
        return HomePageResponse(list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = request("$mainUrl/search?keyword=$query").document
        return document.select(".film-list > .item").map {
            it.toSearchResult(showEpisode = false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document

        val widget = document.select("div.widget.info")
        val title = widget.select(".info .title").text().removeSuffix(" (ITA)")
        val otherTitle = widget.select(".info .title").attr("data-jtitle").removeSuffix(" (ITA)")
        val description =
            widget.select(".desc .long").first()?.text() ?: widget.select(".desc").text()
        val poster = document.select(".thumb img").attr("src")

        val type: TvType = getType(widget.select("dd").first()?.text())
        val genres = widget.select(".meta").select("a[href*=\"/genre/\"]").map { it.text() }
        val rating = widget.select("#average-vote").text()

        val trailerUrl = document.select(".trailer[data-url]").attr("data-url")
        val malId = document.select("#mal-button").attr("href")
            .split('/').last().toIntOrNull()
        val anlId = document.select("#anilist-button").attr("href")
            .split('/').last().toIntOrNull()

        var dub = false
        var year: Int? = null
        var status: ShowStatus? = null
        var duration: String? = null

        for (meta in document.select(".meta dt, .meta dd")) {
            val text = meta.text()
            if (text.contains("Audio"))
                dub = meta.nextElementSibling()?.text() == "Italiano"
            else if (year == null && text.contains("Data"))
                year = meta.nextElementSibling()?.text()?.split(' ')?.last()?.toIntOrNull()
            else if (status == null && text.contains("Stato"))
                status = getStatus(meta.nextElementSibling()?.text())
            else if (status == null && text.contains("Durata"))
                duration = meta.nextElementSibling()?.text()
        }

        val servers = document.select(".widget.servers")
        val episodes = servers.select(".server[data-name=\"9\"] .episode").map {
            val id = it.select("a").attr("data-id")
            val number = it.select("a").attr("data-episode-num").toIntOrNull()
            Episode(
                "$mainUrl/api/episode/info?id=$id",
                episode = number
            )
        }
        val comingSoon = episodes.isEmpty()

        val recommendations = document.select(".film-list.interesting .item").map {
            it.toSearchResult(showEpisode = false)
        }

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            japName = otherTitle
            addPoster(poster)
            this.year = year
            addEpisodes(if (dub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
            addMalId(malId)
            addAniListId(anlId)
            addRating(rating)
            addDuration(duration)
            addTrailer(trailerUrl)
            this.recommendations = recommendations
            this.comingSoon = comingSoon
        }
    }

    data class Json(
        @JsonProperty("grabber") val grabber: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("target") val target: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = tryParseJson<Json>(
            request(data).text
        )?.grabber

        if (url.isNullOrEmpty())
            return false

        callback.invoke(
            ExtractorLink(
                name,
                name,
                url,
                referer = mainUrl,
                quality = Qualities.Unknown.value
            )
        )
        return true
    }
}
