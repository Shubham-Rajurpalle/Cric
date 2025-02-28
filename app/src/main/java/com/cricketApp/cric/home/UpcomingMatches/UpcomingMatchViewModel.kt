package com.cricketApp.cric.home.upcomingMatch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UpcomingMatchViewModel : ViewModel() {
    private val repository = UpcomingMatchRepository()
    private val _matches = MutableLiveData<List<MatchData>>()
    val matches: LiveData<List<MatchData>> get() = _matches

    init {
        loadUpcomingMatches()
    }

    private fun loadUpcomingMatches() {
        repository.fetchUpcomingMatches { matchList ->
            _matches.postValue(matchList ?: emptyList())
        }
    }
}
