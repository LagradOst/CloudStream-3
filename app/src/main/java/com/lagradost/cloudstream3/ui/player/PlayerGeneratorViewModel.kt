package com.lagradost.cloudstream3.ui.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import kotlinx.coroutines.launch

class PlayerGeneratorViewModel : ViewModel() {
    private var generator: IGenerator? = null

    private val _currentLinks = MutableLiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>>(setOf())
    val currentLinks: LiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>> = _currentLinks

    private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
    val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

    private val _loadingLinks = MutableLiveData<Resource<Boolean?>>()
    val loadingLinks: LiveData<Resource<Boolean?>> = _loadingLinks

    fun getId() : Int? {
        return generator?.getCurrentId()
    }

    fun loadLinks(episode: Int) {
        generator?.goto(episode)
        loadLinks()
    }

    fun loadLinksPrev() {
        if (generator?.hasPrev() == true) {
            generator?.prev()
            loadLinks()
        }
    }

    fun loadLinksNext() {
        if (generator?.hasNext() == true) {
            generator?.next()
            loadLinks()
        }
    }

    fun preLoadNextLinks() = viewModelScope.launch {
        safeApiCall {
            if (generator?.hasNext() == true) {
                generator?.next()
                generator?.generateLinks(clearCache = false, isCasting = false, {}, {})
                generator?.prev()
            }
        }
    }

    fun attachGenerator(newGenerator: IGenerator?) {
        if (generator == null) {
            generator = newGenerator
        }
    }

    fun loadLinks(clearCache: Boolean = false, isCasting: Boolean = false) = viewModelScope.launch {
        val currentLinks = mutableSetOf<Pair<ExtractorLink?, ExtractorUri?>>()
        val currentSubs = mutableSetOf<SubtitleData>()
        _loadingLinks.postValue(Resource.Loading())
        val loadingState = safeApiCall {
            generator?.generateLinks(clearCache = clearCache, isCasting = isCasting, {
                currentLinks.add(it)
                _currentLinks.postValue(currentLinks)
            }, {
                currentSubs.add(it)
                _currentSubs.postValue(currentSubs)
            })
        }

        _loadingLinks.postValue(loadingState)

        _currentLinks.postValue(currentLinks)
        _currentSubs.postValue(currentSubs)
    }
}