package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.movieproviders.RadarrProvider
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager

class RadarrApi(index: Int) : InAppAuthAPIManager(index) {
    override val name = "Radarr"
    override val idPrefix = "radarr"
    override val icon = R.drawable.nginx
    override val requiresServer = true
    override val requiresApiKey = true
    override val requiresPath = true
    override val createAccountUrl = "https://wiki.servarr.com/radarr"

    companion object {
        const val RADARR_USER_KEY: String = "radarr_user"
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        return getKey(accountId, RADARR_USER_KEY) //TODO ("not restoring data after restart")
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        val data = getLatestLoginData() ?: return null
        return AuthAPI.LoginInfo(name = data.username ?: data.server, accountIndex = accountIndex)
    }

    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        if (data.server.isNullOrBlank()) return false // we require a server
        if (data.apiKey.isNullOrBlank()) return false // we require an apikey
        if (data.path.isNullOrBlank()) return false // we require a Path
        switchToNewAccount()
        setKey(accountId, RADARR_USER_KEY, data)
        registerAccount()
        initialize()
        return true
    }

    override fun logOut() {
        removeAccountKeys()
        initializeData()
    }

    private fun initializeData() {
        val data = getLatestLoginData() ?: run {
            RadarrProvider.overrideUrl = null
            RadarrProvider.apiKey = null
            RadarrProvider.rootFolderPath = null

            return
        }
        RadarrProvider.overrideUrl = data.server?.removeSuffix("/")
        RadarrProvider.apiKey = data.apiKey
        RadarrProvider.rootFolderPath = data.path

    }

    override suspend fun initialize() {
        initializeData()
    }
}