package com.cricketApp.cric.Meme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cricketApp.cric.Meme.cache.MemeEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemeViewModel(application: Application) : AndroidViewModel(application) {

    // ── Factory — required so by viewModels() can instantiate this correctly ──
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MemeViewModel::class.java)) {
                return MemeViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private val repository = MemeRepository(application)

    // ── Current active filter ─────────────────────────────────────────────────
    private val _activeFilter = MutableStateFlow(FilterKey.ALL)
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    // ── Meme list (from Room cache via Flow) ──────────────────────────────────
    private val _memes = MutableStateFlow<List<MemeEntity>>(emptyList())
    val memes: StateFlow<List<MemeEntity>> = _memes.asStateFlow()

    // ── Pagination state ──────────────────────────────────────────────────────
    val paginationState: StateFlow<PaginationState> = repository.paginationState

    // ── Real-time new meme events ─────────────────────────────────────────────
    private val _newMemeEvent = MutableSharedFlow<MemeMessage>(extraBufferCapacity = 10)
    val newMemeEvent: SharedFlow<MemeMessage> = _newMemeEvent.asSharedFlow()

    private val _removedMemeId = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val removedMemeId: SharedFlow<String> = _removedMemeId.asSharedFlow()

    // Track current Room observer job so we can cancel when filter changes
    private var cacheObserverJob: kotlinx.coroutines.Job? = null

    init {
        // Call loadFilter directly — bypasses the duplicate-check guard in applyFilter
        // so ALL filter always loads on first launch
        loadFilter(FilterKey.ALL)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter management
    // ─────────────────────────────────────────────────────────────────────────

    fun applyFilter(filterKey: String) {
        if (_activeFilter.value == filterKey) return
        loadFilter(filterKey)
    }

    private fun loadFilter(filterKey: String) {
        _activeFilter.value = filterKey

        repository.stopRealtimeListener()
        repository.resetCursor(filterKey)

        cacheObserverJob?.cancel()
        observeCache(filterKey)

        viewModelScope.launch {
            repository.initialLoad(filterKey)
            startRealtimeListener(filterKey)
        }
    }

    /** Called on first load or explicit refresh */
    fun refresh() {
        val filter = _activeFilter.value
        repository.resetCursor(filter)
        viewModelScope.launch { repository.initialLoad(filter) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pagination
    // ─────────────────────────────────────────────────────────────────────────

    fun loadNextPage() {
        viewModelScope.launch {
            repository.loadNextPage(_activeFilter.value)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Room cache observation
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeCache(filterKey: String) {
        cacheObserverJob = viewModelScope.launch {
            repository.observeCachedMemes(filterKey)
                .collect { entities -> _memes.value = entities }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real-time listener
    // ─────────────────────────────────────────────────────────────────────────

    private fun startRealtimeListener(filterKey: String) {
        val knownIds = _memes.value.map { it.id }.toSet()
        repository.startRealtimeListener(
            filterKey     = filterKey,
            knownIds      = knownIds,
            onNewMeme     = { meme ->
                viewModelScope.launch {
                    repository.cacheNewMeme(meme, filterKey)
                    _newMemeEvent.emit(meme)
                }
            },
            onMemeRemoved = { memeId ->
                viewModelScope.launch {
                    repository.removeCachedMeme(memeId)
                    _removedMemeId.emit(memeId)
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Write helpers
    // ─────────────────────────────────────────────────────────────────────────

    fun onMemePosted(meme: MemeMessage) { repository.writeIndexNodes(meme) }

    fun onMemeDeleted(meme: MemeMessage) {
        repository.deleteIndexNodes(meme)
        viewModelScope.launch { repository.removeCachedMeme(meme.id) }
    }

    fun onHitMissUpdated(memeId: String, hit: Int, miss: Int) {
        repository.updateHitMissIndex(memeId, hit, miss)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopRealtimeListener()
    }
}