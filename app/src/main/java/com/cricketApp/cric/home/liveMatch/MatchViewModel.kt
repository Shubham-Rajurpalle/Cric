package com.cricketApp.cric.home.liveMatch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MatchViewModel : ViewModel() {

    private val repository = MatchRepository()

    private val _matches = MutableLiveData<List<MatchData>>()
    val matches: LiveData<List<MatchData>> get() = _matches

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> get() = _error

    init {
        startListening()
    }

    private fun startListening() {
        _isLoading.value = true
        _error.value = null

        repository.listenToLiveMatches(
            onResult = { matches ->
                _matches.postValue(matches)
                _isLoading.postValue(false)
            },
            onError = { errorMsg ->
                _error.postValue(errorMsg)
                _isLoading.postValue(false)
            }
        )
    }

    fun refreshMatches() {
        _isLoading.value = true
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}

class MatchViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MatchViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}