package com.cricketApp.cric.home.liveMatch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MatchViewModel : ViewModel() {
    private val repository = MatchRepository()
    private val _matches = MutableLiveData<List<MatchData>>()
    val matches: LiveData<List<MatchData>> get() = _matches

    private val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> get() = _error

    init {
        loadLiveMatches()
    }

    private fun loadLiveMatches() {
        _isLoading.value = true
        _error.value = null

        repository.fetchLiveMatches { matchList ->
            if (matchList != null) {
                _matches.postValue(matchList)
            } else {
                _error.postValue("Failed to load matches")
            }
            _isLoading.postValue(false)
        }
    }

    fun refreshMatches() {
        loadLiveMatches()
    }
}