package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.movieproviders.SonarrProvider
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager

class SonarrApi(index: Int) : InAppAuthAPIManager(index) {
    override val name = "Sonarr"
    override val idPrefix = "sonarr"
    override val icon = R.drawable.nginx
    override val requiresServer = true
    override val requiresApiKey = true
    override val requiresPath = true
    override val createAccountUrl = "https://wiki.servarr.com/SONARR"

    companion object {
        const val SONARR_USER_KEY: String = "sonarr_user"
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        return getKey(accountId, SONARR_USER_KEY)
    }

    override fun loginInfo(): AuthAPI.LoginInfo? { // works
        val data = getLatestLoginData() ?: return null
        return AuthAPI.LoginInfo(name = data.username ?: data.server, accountIndex = accountIndex)
    }

    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        println("HERE DUDE !!!!!!!!!!!!!! $data")
        if (data.server.isNullOrBlank()) return false // we require a server
        if (data.apiKey.isNullOrBlank()) return false // we require an apikey
        if (data.path.isNullOrBlank()) return false // we require a Path
        switchToNewAccount()
        setKey(accountId, SONARR_USER_KEY, data)
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
            SonarrProvider.overrideUrl = null
            SonarrProvider.apiKey = null
            SonarrProvider.rootFolderPath = null
            return
        }
        println("HERE DUDE !!!!!!!!!!!!!! $data")
        SonarrProvider.overrideUrl = data.server?.removeSuffix("/")
        SonarrProvider.apiKey = data.apiKey
        SonarrProvider.rootFolderPath = data.path

    }

    override suspend fun initialize() {
        initializeData()
    }
}