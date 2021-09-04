package com.ArjixWasTaken.cloudstream3.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ArjixWasTaken.cloudstream3.APIHolder.apis
import com.ArjixWasTaken.cloudstream3.APIHolder.getApiFromNameNull
import com.ArjixWasTaken.cloudstream3.HomePageResponse
import com.ArjixWasTaken.cloudstream3.MainAPI
import com.ArjixWasTaken.cloudstream3.SearchResponse
import com.ArjixWasTaken.cloudstream3.mvvm.Resource
import com.ArjixWasTaken.cloudstream3.ui.APIRepository
import com.ArjixWasTaken.cloudstream3.ui.WatchType
import com.ArjixWasTaken.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKey
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getViewPos
import com.ArjixWasTaken.cloudstream3.utils.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    var repo: APIRepository? = null

    private val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    private val _page = MutableLiveData<Resource<HomePageResponse>>()
    val page: LiveData<Resource<HomePageResponse>> = _page

    private fun autoloadRepo(): APIRepository {
        return APIRepository(apis.first { it.hasMainPage })
    }

    private val _availableWatchStatusTypes = MutableLiveData<Pair<WatchType, List<WatchType>>>()
    val availableWatchStatusTypes: LiveData<Pair<WatchType, List<WatchType>>> = _availableWatchStatusTypes
    private val _bookmarks = MutableLiveData<List<SearchResponse>>()
    val bookmarks: LiveData<List<SearchResponse>> = _bookmarks

    private val _resumeWatching = MutableLiveData<List<SearchResponse>>()
    val resumeWatching: LiveData<List<SearchResponse>> = _resumeWatching

    fun loadResumeWatching(context: Context) = viewModelScope.launch {
        val resumeWatching = withContext(Dispatchers.IO) {
            context.getAllResumeStateIds().mapNotNull { id ->
                context.getLastWatched(id)
            }.sortedBy { -it.updateTime }
        }

        // val resumeWatchingResult = ArrayList<DataStoreHelper.ResumeWatchingResult>()

        val resumeWatchingResult = withContext(Dispatchers.IO) {
            resumeWatching.map { resume ->
                val data = context.getKey<VideoDownloadHelper.DownloadHeaderCached>(
                    DOWNLOAD_HEADER_CACHE,
                    resume.parentId.toString()
                ) ?: return@map null
                val watchPos = context.getViewPos(resume.episodeId)
                DataStoreHelper.ResumeWatchingResult(
                    data.name,
                    data.url,
                    data.apiName,
                    data.type,
                    data.poster,
                    watchPos,
                    resume.episodeId,
                    resume.parentId,
                    resume.episode,
                    resume.season,
                    resume.isFromDownload
                )
            }.filterNotNull()
        }

        _resumeWatching.postValue(resumeWatchingResult)
    }

    fun loadStoredData(context: Context, preferredWatchStatus: WatchType?) = viewModelScope.launch {
        val watchStatusIds = withContext(Dispatchers.IO) {
            context.getAllWatchStateIds().map { id ->
                Pair(id, context.getResultWatchState(id))
            }
        }.distinctBy { it.first }
        val length = WatchType.values().size
        val currentWatchTypes = HashSet<WatchType>()

        for (watch in watchStatusIds) {
            currentWatchTypes.add(watch.second)
            if (currentWatchTypes.size >= length) {
                break
            }
        }

        currentWatchTypes.remove(WatchType.NONE)

        if (currentWatchTypes.size <= 0) {
            _bookmarks.postValue(ArrayList())
            return@launch
        }

        val watchPrefNotNull = preferredWatchStatus ?: currentWatchTypes.first()
        val watchStatus =
            if (currentWatchTypes.contains(watchPrefNotNull)) watchPrefNotNull else currentWatchTypes.first()
        _availableWatchStatusTypes.postValue(
            Pair(
                watchStatus,
                currentWatchTypes.sortedBy { it.internalId }.toList()
            )
        )
        val list = withContext(Dispatchers.IO) {
            watchStatusIds.filter { it.second == watchStatus }
                .mapNotNull { context.getBookmarkedData(it.first) }
                .sortedBy { -it.latestUpdatedTime }
        }
        _bookmarks.postValue(list)
    }

    var onGoingLoad: Job? = null
    fun loadAndCancel(api: MainAPI?) {
        onGoingLoad?.cancel()
        onGoingLoad = load(api)
    }

    private fun load(api: MainAPI?) = viewModelScope.launch {
        repo = if (api?.hasMainPage == true) {
            APIRepository(api)
        } else {
            autoloadRepo()
        }

        _apiName.postValue(repo?.name)
        _page.postValue(Resource.Loading())
        _page.postValue(repo?.getMainPage())
    }

    fun loadAndCancel(preferredApiName: String?) = viewModelScope.launch {
        val api = getApiFromNameNull(preferredApiName)
        loadAndCancel(api)
    }
}