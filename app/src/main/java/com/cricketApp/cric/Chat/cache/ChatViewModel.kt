package com.cricketApp.cric.Chat.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cricketApp.cric.Chat.RoomType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    val roomId: String,
    val roomType: RoomType
) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    // ── Active filter ─────────────────────────────────────────────────────────
    private val _activeFilter = MutableStateFlow(ChatFilterKey.ALL)
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    // ── Combined messages list ────────────────────────────────────────────────
    private val _messages = MutableStateFlow<List<Any>>(emptyList())
    val messages: StateFlow<List<Any>> = _messages.asStateFlow()

    // ── Pagination state ──────────────────────────────────────────────────────
    val paginationState = repository.paginationState

    // ── Real-time events ──────────────────────────────────────────────────────
    private val _newMessageEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val newMessageEvent: SharedFlow<Unit> = _newMessageEvent.asSharedFlow()

    private val _removedId = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val removedId: SharedFlow<String> = _removedId.asSharedFlow()

    private var cacheObserverJob: Job? = null

    // ── Init — load ALL filter on start ──────────────────────────────────────
    init {
        loadFilter(ChatFilterKey.ALL)
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun applyFilter(filterKey: String) {
        if (_activeFilter.value == filterKey) return
        loadFilter(filterKey)
    }

    private fun loadFilter(filterKey: String) {
        _activeFilter.value = filterKey
        repository.stopRealtimeListener()
        repository.resetCursor(roomId, filterKey)

        cacheObserverJob?.cancel()
        cacheObserverJob = viewModelScope.launch {
            repository.observeCombined(roomId, filterKey).collect { list ->
                _messages.value = list
            }
        }

        viewModelScope.launch {
            repository.initialLoad(roomId, roomType, filterKey)
            startRealtimeListener(filterKey)
        }
    }

    fun loadNextPage() {
        viewModelScope.launch {
            repository.loadNextPage(roomId, roomType, _activeFilter.value)
        }
    }

    fun refresh() {
        val filter = _activeFilter.value
        repository.resetCursor(roomId, filter)
        viewModelScope.launch { repository.initialLoad(roomId, roomType, filter) }
    }

    private fun startRealtimeListener(filterKey: String) {
        repository.startRealtimeListener(
            roomId    = roomId,
            roomType  = roomType,
            filterKey = filterKey,
            onNewChat = { msg ->
                viewModelScope.launch {
                    repository.cacheNewChat(msg, roomId, filterKey)
                    _newMessageEvent.tryEmit(Unit)
                }
            },
            onNewPoll = { poll ->
                viewModelScope.launch {
                    repository.cacheNewPoll(poll, roomId, filterKey)
                    _newMessageEvent.tryEmit(Unit)
                }
            },
            onRemoved = { id ->
                viewModelScope.launch {
                    repository.removeCached(id)
                    _removedId.tryEmit(id)
                }
            }
        )
    }

    fun onMessagePosted(msg: Any) {
        repository.writeIndexNodes(msg, roomId, roomType)
    }

    fun onHitMissUpdated(msgId: String, hit: Int, miss: Int) {
        repository.updateHitMissIndex(msgId, hit, miss, roomId, roomType)
    }

    fun onMessageDeleted(msg: Any) {
        repository.deleteIndexNodes(msg, roomId, roomType)
    }

    fun migrateExistingToIndex() {
        repository.migrateExistingToIndex(roomId, roomType)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopRealtimeListener()
    }

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory(
        private val application: Application,
        private val roomId: String,
        private val roomType: RoomType
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, roomId, roomType) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}