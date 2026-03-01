package com.cricketApp.cric.Meme

import com.cricketApp.cric.Meme.cache.MemeDatabase
import com.cricketApp.cric.Meme.cache.MemeEntity
import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
// Filter key constants — used as Room filterKey and Firebase index paths
// ─────────────────────────────────────────────────────────────────────────────

object FilterKey {
    const val ALL      = "ALL"
    const val TOP_HIT  = "TOP_HIT"
    const val TOP_MISS = "TOP_MISS"
    fun team(t: String) = "TEAM_$t"   // e.g. "TEAM_CSK"
}

// ─────────────────────────────────────────────────────────────────────────────
// Pagination state
// ─────────────────────────────────────────────────────────────────────────────

data class PaginationState(
    val isLoading: Boolean       = false,
    val hasMore: Boolean         = true,
    val error: String?           = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Repository
// ─────────────────────────────────────────────────────────────────────────────

class MemeRepository(context: Context) {

    companion object {
        private const val TAG        = "MemeRepository"
        private const val PAGE_SIZE  = 15L
        private const val CACHE_TTL  = 48 * 60 * 60 * 1000L  // 48 hours

        // Firebase paths
        private const val MEMES_PATH          = "NoBallZone/memes"
        private const val INDEX_TEAM_PATH     = "NoBallZone/memesByTeam"
        private const val INDEX_HIT_PATH      = "NoBallZone/memesByHit"
        private const val INDEX_MISS_PATH     = "NoBallZone/memesByMiss"
    }

    private val db  = FirebaseDatabase.getInstance()
    private val dao = MemeDatabase.getInstance(context).memeDao()

    // ── Pagination cursors (per filter) ──────────────────────────────────────
    // For ALL / team filters: cursor = oldest timestamp loaded so far
    // For TOP_HIT / TOP_MISS: cursor = lowest score loaded so far
    private val cursors = mutableMapOf<String, Double>()

    // ── Pagination state exposed to ViewModel ────────────────────────────────
    private val _paginationState = MutableStateFlow(PaginationState())
    val paginationState = _paginationState.asStateFlow()

    // ── Real-time listener handle (new memes at top) ─────────────────────────
    private var realtimeListener: ChildEventListener? = null
    private var realtimeRef: DatabaseReference?       = null

    // ─────────────────────────────────────────────────────────────────────────
    // Observe cache (Room → UI)
    // ─────────────────────────────────────────────────────────────────────────

    fun observeCachedMemes(filterKey: String): Flow<List<MemeEntity>> = when (filterKey) {
        FilterKey.TOP_HIT  -> dao.observeMemesByHit()
        FilterKey.TOP_MISS -> dao.observeMemesByMiss()
        else               -> dao.observeMemesByTime(filterKey)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initial load  —  stale-while-revalidate
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun initialLoad(filterKey: String) {
        _paginationState.value = PaginationState(isLoading = true)

        // Evict stale entries first
        dao.evictOlderThan(System.currentTimeMillis() - CACHE_TTL)

        // Fetch first page from Firebase and cache it
        fetchPage(filterKey, isFirstPage = true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load next page
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun loadNextPage(filterKey: String) {
        if (_paginationState.value.isLoading || !_paginationState.value.hasMore) return
        _paginationState.value = _paginationState.value.copy(isLoading = true)
        fetchPage(filterKey, isFirstPage = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core fetch logic — routes to correct Firebase index
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchPage(filterKey: String, isFirstPage: Boolean) {
        try {
            val memes: List<MemeMessage> = when {
                filterKey == FilterKey.ALL     -> fetchAllPage(isFirstPage)
                filterKey == FilterKey.TOP_HIT  -> fetchTopHitPage(isFirstPage)
                filterKey == FilterKey.TOP_MISS -> fetchTopMissPage(isFirstPage)
                filterKey.startsWith("TEAM_")  -> {
                    val team = filterKey.removePrefix("TEAM_")
                    fetchTeamPage(team, isFirstPage)
                }
                else -> emptyList()
            }

            if (isFirstPage) {
                // Replace cache for this filter
                dao.clearFilter(filterKey)
            }

            if (memes.isEmpty()) {
                _paginationState.value = PaginationState(isLoading = false, hasMore = false)
                return
            }

            // Cache results, then enforce 30-item cap respecting sort order
            dao.insertAll(memes.map { MemeEntity.fromMemeMessage(it, filterKey) })
            when (filterKey) {
                FilterKey.TOP_HIT  -> dao.enforceLimitByHit()
                FilterKey.TOP_MISS -> dao.enforceLimitByMiss()
                else               -> dao.enforceLimitByTime(filterKey)
            }

            // Update cursor
            updateCursor(filterKey, memes)

            val hasMore = memes.size >= PAGE_SIZE.toInt()
            _paginationState.value = PaginationState(isLoading = false, hasMore = hasMore)

        } catch (e: Exception) {
            Log.e(TAG, "fetchPage error: ${e.message}", e)
            _paginationState.value = PaginationState(
                isLoading = false,
                hasMore   = false,
                error     = e.message
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ALL memes — paginate by timestamp descending
    // Firebase: orderByChild("timestamp").endBefore(cursor).limitToLast(PAGE_SIZE)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchAllPage(isFirstPage: Boolean): List<MemeMessage> {
        val ref   = db.getReference(MEMES_PATH)
        val query = if (isFirstPage || !cursors.containsKey(FilterKey.ALL)) {
            ref.orderByChild("timestamp").limitToLast(PAGE_SIZE.toInt())
        } else {
            ref.orderByChild("timestamp")
                .endBefore(cursors[FilterKey.ALL]!!)
                .limitToLast(PAGE_SIZE.toInt())
        }

        val snapshot = query.get().await()
        return snapshotToMemeList(snapshot).sortedByDescending { it.timestamp }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP HIT — query /memesByHit index, then fan-out fetch full data
    // Index node shape: memesByHit/{memeId} = hitCount (Int)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchTopHitPage(isFirstPage: Boolean): List<MemeMessage> {
        val ref   = db.getReference(INDEX_HIT_PATH)
        val query = if (isFirstPage || !cursors.containsKey(FilterKey.TOP_HIT)) {
            ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        } else {
            ref.orderByValue()
                .endBefore(cursors[FilterKey.TOP_HIT]!!)
                .limitToLast(PAGE_SIZE.toInt())
        }

        val indexSnapshot = query.get().await()
        // Build id→hitCount map from index so we use accurate counts, not stale main-node values
        val idToHit = indexSnapshot.children
            .associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }

        return fanOutFetchMemes(idToHit.keys.toList())
            .map { meme -> meme.also { it.hit = idToHit[meme.id] ?: meme.hit } }
            .sortedByDescending { it.hit }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP MISS — same pattern as TOP HIT
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchTopMissPage(isFirstPage: Boolean): List<MemeMessage> {
        val ref   = db.getReference(INDEX_MISS_PATH)
        val query = if (isFirstPage || !cursors.containsKey(FilterKey.TOP_MISS)) {
            ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        } else {
            ref.orderByValue()
                .endBefore(cursors[FilterKey.TOP_MISS]!!)
                .limitToLast(PAGE_SIZE.toInt())
        }

        val indexSnapshot = query.get().await()
        // Build id→missCount map from index so we use accurate counts, not stale main-node values
        val idToMiss = indexSnapshot.children
            .associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }

        return fanOutFetchMemes(idToMiss.keys.toList())
            .map { meme -> meme.also { it.miss = idToMiss[meme.id] ?: meme.miss } }
            .sortedByDescending { it.miss }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEAM — query /memesByTeam/{team} index
    // Index node shape: memesByTeam/{team}/{memeId} = timestamp (Long)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchTeamPage(team: String, isFirstPage: Boolean): List<MemeMessage> {
        val filterKey = FilterKey.team(team)
        val ref       = db.getReference("$INDEX_TEAM_PATH/$team")
        val query     = if (isFirstPage || !cursors.containsKey(filterKey)) {
            ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        } else {
            ref.orderByValue()
                .endBefore(cursors[filterKey]!!)
                .limitToLast(PAGE_SIZE.toInt())
        }

        val indexSnapshot = query.get().await()
        val ids = indexSnapshot.children.mapNotNull { it.key }
        return fanOutFetchMemes(ids).sortedByDescending { it.timestamp }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fan-out: fetch full meme data for a list of IDs in parallel
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fanOutFetchMemes(ids: List<String>): List<MemeMessage> =
        coroutineScope {
            ids.map { id ->
                async {
                    try {
                        val snap = db.getReference("$MEMES_PATH/$id").get().await()
                        com.cricketApp.cric.Chat.FirebaseDataHelper.getMemeMessageFromSnapshot(snap)
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Snapshot → MemeMessage list helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun snapshotToMemeList(snapshot: DataSnapshot): List<MemeMessage> =
        snapshot.children.mapNotNull {
            com.cricketApp.cric.Chat.FirebaseDataHelper.getMemeMessageFromSnapshot(it)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Cursor management
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateCursor(filterKey: String, memes: List<MemeMessage>) {
        val cursor: Double? = when {
            filterKey == FilterKey.TOP_HIT  ->
                // -0.5 so endBefore includes all memes with the same hit count on next page
                memes.minOfOrNull { it.hit.toDouble() }?.let { it - 0.5 }
            filterKey == FilterKey.TOP_MISS ->
                memes.minOfOrNull { it.miss.toDouble() }?.let { it - 0.5 }
            else ->
                memes.minOfOrNull { it.timestamp.toDouble() }
        }
        cursor?.let { cursors[filterKey] = it }
    }

    fun resetCursor(filterKey: String) {
        cursors.remove(filterKey)
    }

    fun resetAllCursors() = cursors.clear()

    // ─────────────────────────────────────────────────────────────────────────
    // Real-time listener — only for new memes arriving at the top (ALL filter)
    // ─────────────────────────────────────────────────────────────────────────

    fun startRealtimeListener(
        filterKey: String,
        knownIds: Set<String>,
        onNewMeme: (MemeMessage) -> Unit,
        onMemeRemoved: (String) -> Unit
    ) {
        stopRealtimeListener()

        // Only run real-time for ALL filter; filtered views are snapshot-only
        if (filterKey != FilterKey.ALL) return

        val ref      = db.getReference(MEMES_PATH)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                val meme = com.cricketApp.cric.Chat.FirebaseDataHelper
                    .getMemeMessageFromSnapshot(snapshot) ?: return
                if (meme.id !in knownIds) onNewMeme(meme)
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { onMemeRemoved(it) }
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildMoved(s: DataSnapshot, p: String?)   {}
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "Realtime listener cancelled: ${e.message}")
            }
        }

        ref.addChildEventListener(listener)
        realtimeRef      = ref
        realtimeListener = listener
    }

    fun stopRealtimeListener() {
        realtimeListener?.let { realtimeRef?.removeEventListener(it) }
        realtimeListener = null
        realtimeRef      = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Write index nodes when a meme is posted
    // Call this from MemeFragment.postMeme() alongside the main meme write
    // ─────────────────────────────────────────────────────────────────────────

    fun writeIndexNodes(meme: MemeMessage) {
        val updates = hashMapOf<String, Any>(
            "$INDEX_TEAM_PATH/${meme.team}/${meme.id}" to meme.timestamp,
            "$INDEX_HIT_PATH/${meme.id}"               to meme.hit,
            "$INDEX_MISS_PATH/${meme.id}"              to meme.miss
        )
        db.reference.updateChildren(updates)
    }

    /** Call whenever hit/miss changes so the index stays fresh */
    fun updateHitMissIndex(memeId: String, hit: Int, miss: Int) {
        db.getReference(INDEX_HIT_PATH).child(memeId).setValue(hit)
        db.getReference(INDEX_MISS_PATH).child(memeId).setValue(miss)
    }

    /** Remove index nodes when a meme is deleted */
    fun deleteIndexNodes(meme: MemeMessage) {
        val updates = hashMapOf<String, Any?>(
            "$INDEX_TEAM_PATH/${meme.team}/${meme.id}" to null,
            "$INDEX_HIT_PATH/${meme.id}"               to null,
            "$INDEX_MISS_PATH/${meme.id}"              to null
        )
        db.reference.updateChildren(updates)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache helpers for reactive updates from adapter
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun cacheNewMeme(meme: MemeMessage, filterKey: String) {
        dao.insert(MemeEntity.fromMemeMessage(meme, filterKey))
        when (filterKey) {
            FilterKey.TOP_HIT  -> dao.enforceLimitByHit()
            FilterKey.TOP_MISS -> dao.enforceLimitByMiss()
            else               -> dao.enforceLimitByTime(filterKey)
        }
    }

    suspend fun removeCachedMeme(memeId: String) {
        dao.deleteMeme(memeId)
    }
}