package com.cricketApp.cric.home.upcomingMatch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UpcomingMatchViewModel : ViewModel() {
    private val repository = UpcomingMatchRepository()
    private val _matches = MutableLiveData<List<MatchData>>()
    val matches: LiveData<List<MatchData>> get() = _matches
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadUpcomingMatches()
    }

    fun reloadMatches() {
        loadUpcomingMatches()
    }

    private fun loadUpcomingMatches() {
        _isLoading.value = true

        try {
            repository.fetchUpcomingMatches { matchList ->
                _isLoading.postValue(false)
                if (matchList != null) {
                    _matches.postValue(matchList)
                } else {
                    _error.postValue("Failed to load matches. Please try again.")
                }
            }
        } catch (e: Exception) {
            _isLoading.postValue(false)
            _error.value = "Failed to load matches. Please try again."
        }
    }
}