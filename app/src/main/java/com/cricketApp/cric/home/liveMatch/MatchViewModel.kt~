package com.cricketApp.cric.home.liveMatch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MatchViewModel : ViewModel() {
    private val repository = MatchRepository()
    private val _matches = MutableLiveData<List<MatchData>>()
    val matches: LiveData<List<MatchData>> get() = _matches

    init {
        loadLiveMatches()
    }

    private fun loadLiveMatches() {
        repository.fetchLiveMatches { matchList ->
            _matches.postValue(matchList ?: emptyList()) // Use postValue for background updates
        }
    }
}

