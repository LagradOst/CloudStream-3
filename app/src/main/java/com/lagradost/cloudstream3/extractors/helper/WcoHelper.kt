package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.app
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class WcoHelper {
    companion object {
        private const val BACKUP_KEY_DATA = "github_keys_backup"

        @Serializable
        data class ExternalKeys(
            @SerialName("wco_key")
            val wcoKey: String? = null,
            @SerialName("wco_cipher_key")
            val wcocipher: String? = null
        )

        @Serializable
        data class NewExternalKeys(
            @SerialName("cipherKey")
            val cipherkey: String? = null,
            @SerialName("encryptKey")
            val encryptKey: String? = null,
            @SerialName("mainKey")
            val mainKey: String? = null,
        )

        private var keys: ExternalKeys? = null
        private var newKeys: NewExternalKeys? = null
        private suspend fun getKeys() {
            keys = keys
                ?: app.get("https://raw.githubusercontent.com/LagradOst/CloudStream-3/master/docs/keys.json")
                    .parsedSafe<ExternalKeys>()?.also { setKey(BACKUP_KEY_DATA, it) } ?: getKey(
                    BACKUP_KEY_DATA
                )
        }

        suspend fun getWcoKey(): ExternalKeys? {
            getKeys()
            return keys
        }

        private suspend fun getNewKeys() {
            newKeys = newKeys
                ?: app.get("https://raw.githubusercontent.com/chekaslowakiya/BruhFlow/main/keys.json")
                    .parsedSafe<NewExternalKeys>()?.also { setKey(BACKUP_KEY_DATA, it) } ?: getKey(
                    BACKUP_KEY_DATA
                )
        }

        suspend fun getNewWcoKey(): NewExternalKeys? {
            getNewKeys()
            return newKeys
        }
    }
}