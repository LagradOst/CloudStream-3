package com.ArjixWasTaken.cloudstream3.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ArjixWasTaken.cloudstream3.APIHolder.apis
import com.ArjixWasTaken.cloudstream3.SearchResponse
import com.ArjixWasTaken.cloudstream3.mvvm.Resource
import com.ArjixWasTaken.cloudstream3.ui.APIRepository
import com.ArjixWasTaken.cloudstream3.ui.APIRepository.Companion.providersActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<ArrayList<SearchResponse>>> = MutableLiveData()
    val searchResponse: LiveData<Resource<ArrayList<SearchResponse>>> get() = _searchResponse

    private val _currentSearch: MutableLiveData<ArrayList<OnGoingSearch>> = MutableLiveData()
    val currentSearch: LiveData<ArrayList<OnGoingSearch>> get() = _currentSearch

    private val repos = apis.map { APIRepository(it) }

    private fun clearSearch() {
        _searchResponse.postValue(Resource.Success(ArrayList()))
    }

    var onGoingSearch : Job? = null
    fun searchAndCancel(query: String) {
        onGoingSearch?.cancel()
        onGoingSearch = search(query)
    }

    private fun search(query: String) = viewModelScope.launch {
        if (query.length <= 1) {
            clearSearch()
            return@launch
        }

        _searchResponse.postValue(Resource.Loading())

        val currentList = ArrayList<OnGoingSearch>()

        _currentSearch.postValue(ArrayList())

        repos.filter { a ->
            (providersActive.size == 0 || providersActive.contains(a.name))
        }.map { a ->
            currentList.add(OnGoingSearch(a.name, a.search(query)))
            _currentSearch.postValue(currentList)
        }
        _currentSearch.postValue(currentList)


        val list = ArrayList<SearchResponse>()
        val nestedList =
            currentList.map { it.data }.filterIsInstance<Resource.Success<List<SearchResponse>>>().map { it.value }

        // I do it this way to move the relevant search results to the top
        var index = 0
        while (true) {
            var added = 0
            for (sublist in nestedList) {
                if (sublist.size > index) {
                    list.add(sublist[index])
                    added++
                }
            }
            if (added == 0) break
            index++
        }

        _searchResponse.postValue(Resource.Success(list))
    }

    fun quickSearch(query: String) {
        return
    }
}