package com.cricketApp.cric.Chat.cache

import android.content.Context
import android.util.Log
import com.cricketApp.cric.Chat.ChatMessage
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.Chat.PollMessage
import com.cricketApp.cric.Chat.RoomType
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
// Merged item for the UI — Chat and Poll combined and sorted by timestamp
// ─────────────────────────────────────────────────────────────────────────────

data class ChatPaginationState(
    val isLoading: Boolean = false,
    val hasMore: Boolean   = true,
    val error: String?     = null
)

class ChatRepository(context: Context) {

    companion object {
        private const val TAG       = "ChatRepository"
        private const val PAGE_SIZE = 30L
        private const val CACHE_TTL = 48 * 60 * 60 * 1000L  // 48 hours
    }

    private val db  = FirebaseDatabase.getInstance()
    private val dao = ChatDatabase.getInstance(context).chatDao()

    // ── Cursors — per "roomId|filterKey" composite key ───────────────────────
    private val cursors = mutableMapOf<String, Double>()

    private fun cursorKey(roomId: String, filterKey: String) = "$roomId|$filterKey"

    // ── Pagination state ──────────────────────────────────────────────────────
    private val _paginationState = MutableStateFlow(ChatPaginationState())
    val paginationState = _paginationState.asStateFlow()

    // ── Real-time listener ────────────────────────────────────────────────────
    private var realtimeListener: ChildEventListener? = null
    private var realtimeRef: DatabaseReference?       = null

    // ─────────────────────────────────────────────────────────────────────────
    // Firebase base path helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun basePath(roomId: String, roomType: RoomType) = when (roomType) {
        RoomType.GLOBAL -> "NoBallZone"
        RoomType.TEAM   -> "TeamRooms/$roomId"
        RoomType.LIVE   -> "NoBallZone/liveRooms/$roomId"
    }

    private fun chatsPath(roomId: String, roomType: RoomType)  = "${basePath(roomId, roomType)}/chats"
    private fun pollsPath(roomId: String, roomType: RoomType)  = "${basePath(roomId, roomType)}/polls"
    private fun hitIndexPath(roomId: String, roomType: RoomType)  = "${basePath(roomId, roomType)}/chatsByHit"
    private fun missIndexPath(roomId: String, roomType: RoomType) = "${basePath(roomId, roomType)}/chatsByMiss"
    private fun teamIndexPath(roomId: String, roomType: RoomType, team: String) =
        "${basePath(roomId, roomType)}/chatsByTeam/$team"

    // ─────────────────────────────────────────────────────────────────────────
    // Observe cache — returns combined + sorted Flow
    // ─────────────────────────────────────────────────────────────────────────

    fun observeCombined(roomId: String, filterKey: String): Flow<List<Any>> {
        val chatFlow: Flow<List<ChatMessageEntity>> = when (filterKey) {
            ChatFilterKey.TOP_HIT   -> dao.observeChatsByHit(roomId)
            ChatFilterKey.TOP_MISS  -> dao.observeChatsByMiss(roomId)
            ChatFilterKey.POLLS_ONLY -> flowOf(emptyList())
            else                    -> dao.observeChatsByTime(roomId, filterKey)
        }
        val pollFlow: Flow<List<PollEntity>> = when (filterKey) {
            ChatFilterKey.TOP_HIT  -> dao.observePollsByHit(roomId)
            ChatFilterKey.TOP_MISS -> dao.observePollsByMiss(roomId)
            else                   -> dao.observePollsByTime(roomId, filterKey)
        }

        return combine(chatFlow, pollFlow) { chats, polls ->
            val combined: MutableList<Any> = mutableListOf()
            combined.addAll(chats.map { it.toChatMessage() })
            combined.addAll(polls.map { it.toPollMessage() })
            // Sort combined list
            when (filterKey) {
                ChatFilterKey.TOP_HIT  -> combined.sortedByDescending {
                    when (it) { is ChatMessage -> it.hit; is PollMessage -> it.hit; else -> 0 }
                }
                ChatFilterKey.TOP_MISS -> combined.sortedByDescending {
                    when (it) { is ChatMessage -> it.miss; is PollMessage -> it.miss; else -> 0 }
                }
                else -> combined.sortedByDescending {
                    when (it) { is ChatMessage -> it.timestamp; is PollMessage -> it.timestamp; else -> 0L }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initial load
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun initialLoad(roomId: String, roomType: RoomType, filterKey: String) {
        _paginationState.value = ChatPaginationState(isLoading = true)
        dao.evictOldChats(System.currentTimeMillis() - CACHE_TTL)
        dao.evictOldPolls(System.currentTimeMillis() - CACHE_TTL)
        fetchPage(roomId, roomType, filterKey, isFirstPage = true)
    }

    suspend fun loadNextPage(roomId: String, roomType: RoomType, filterKey: String) {
        if (_paginationState.value.isLoading || !_paginationState.value.hasMore) return
        _paginationState.value = _paginationState.value.copy(isLoading = true)
        fetchPage(roomId, roomType, filterKey, isFirstPage = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core fetch — routes to correct strategy
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchPage(
        roomId: String, roomType: RoomType,
        filterKey: String, isFirstPage: Boolean
    ) {
        try {
            val ck = cursorKey(roomId, filterKey)
            if (isFirstPage) {
                dao.clearChatFilter(roomId, filterKey)
                dao.clearPollFilter(roomId, filterKey)
            }

            val chats: List<ChatMessage>
            val polls: List<PollMessage>

            when (filterKey) {
                ChatFilterKey.ALL -> {
                    val cursor = if (isFirstPage) null else cursors[ck]
                    chats = fetchChatsByTime(roomId, roomType, cursor)
                    polls = fetchPollsByTime(roomId, roomType, cursor)
                }
                ChatFilterKey.TOP_HIT -> {
                    val cursor = if (isFirstPage) null else cursors[ck]
                    chats = fetchChatsByHitIndex(roomId, roomType, cursor)
                    polls = fetchPollsByHitIndex(roomId, roomType, cursor)
                }
                ChatFilterKey.TOP_MISS -> {
                    val cursor = if (isFirstPage) null else cursors[ck]
                    chats = fetchChatsByMissIndex(roomId, roomType, cursor)
                    polls = fetchPollsByMissIndex(roomId, roomType, cursor)
                }
                ChatFilterKey.POLLS_ONLY -> {
                    chats = emptyList()
                    val cursor = if (isFirstPage) null else cursors[ck]
                    polls = fetchPollsByTime(roomId, roomType, cursor)
                }
                else -> {
                    // Team filter
                    val team = filterKey.removePrefix("TEAM_")
                    val cursor = if (isFirstPage) null else cursors[ck]
                    chats = fetchChatsByTeam(roomId, roomType, team, cursor)
                    polls = fetchPollsByTeam(roomId, roomType, team, cursor)
                }
            }

            val totalItems = chats.size + polls.size
            if (totalItems == 0) {
                _paginationState.value = ChatPaginationState(isLoading = false, hasMore = false)
                return
            }

            // Cache
            dao.insertChats(chats.map { ChatMessageEntity.from(it, roomId, filterKey) })
            dao.insertPolls(polls.map { PollEntity.from(it, roomId, filterKey) })

            // Enforce caps
            when (filterKey) {
                ChatFilterKey.TOP_HIT -> {
                    dao.enforceChatLimitByHit(roomId); dao.enforcePollLimitByHit(roomId)
                }
                ChatFilterKey.TOP_MISS -> {
                    dao.enforceChatLimitByMiss(roomId); dao.enforcePollLimitByMiss(roomId)
                }
                else -> {
                    dao.enforceChatLimitByTime(roomId, filterKey)
                    dao.enforcePollLimitByTime(roomId, filterKey)
                }
            }

            // Update cursor — use oldest timestamp from combined results
            updateCursor(filterKey, ck, chats, polls)

            val hasMore = totalItems >= PAGE_SIZE.toInt()
            _paginationState.value = ChatPaginationState(isLoading = false, hasMore = hasMore)

        } catch (e: Exception) {
            Log.e(TAG, "fetchPage error: ${e.message}", e)
            _paginationState.value = ChatPaginationState(isLoading = false, hasMore = false, error = e.message)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Firebase fetchers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchChatsByTime(
        roomId: String, roomType: RoomType, cursor: Double?
    ): List<ChatMessage> {
        val ref = db.getReference(chatsPath(roomId, roomType))
        val query = if (cursor == null) ref.orderByChild("timestamp").limitToLast(PAGE_SIZE.toInt())
        else ref.orderByChild("timestamp").endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        return snap.children.mapNotNull { FirebaseDataHelper.getChatMessageFromSnapshot(it) }
            .sortedByDescending { it.timestamp }
    }

    private suspend fun fetchPollsByTime(
        roomId: String, roomType: RoomType, cursor: Double?
    ): List<PollMessage> {
        val ref = db.getReference(pollsPath(roomId, roomType))
        val query = if (cursor == null) ref.orderByChild("timestamp").limitToLast(PAGE_SIZE.toInt())
        else ref.orderByChild("timestamp").endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        return snap.children.mapNotNull { FirebaseDataHelper.getPollMessageFromSnapshot(it) }
            .sortedByDescending { it.timestamp }
    }

    private suspend fun fetchChatsByHitIndex(
        roomId: String, roomType: RoomType, cursor: Double?
    ): List<ChatMessage> {
        val ref = db.getReference(hitIndexPath(roomId, roomType))
        val query = if (cursor == null) ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        else ref.orderByValue().endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        val idToHit = snap.children.associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }
        return fanOutFetchChats(roomId, roomType, idToHit.keys.toList())
            .map { it.also { c -> c.hit = idToHit[c.id] ?: c.hit } }
            .sortedByDescending { it.hit }
    }

    private suspend fun fetchPollsByHitIndex(
        roomId: String, roomType: RoomType, cursor: Double?
    ): List<PollMessage> {
        // Polls share the same hit index as chats — filter by checking polls path
        val ref = db.getReference(hitIndexPath(roomId, roomType))
        val query = if (cursor == null) ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        else ref.orderByValue().endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        val idToHit = snap.children.associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }
        return fanOutFetchPolls(roomId, roomType, idToHit.keys.toList())
            .map { it.also { p -> p.hit = idToHit[p.id] ?: p.hit } }
            .sortedByDescending { it.hit }
    }

    private suspend fun fetchChatsByMissIndex(
        roomId: String, roomType: RoomType, cursor: Double?
    ): List<ChatMessage> {
        val ref = db.getReference(missIndexPath(roomId, roomType))
        val query = if (cursor == null) ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        else ref.orderByValue().endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        val idToMiss = snap.children.associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }
        return fanOutFetchChats(roomId, roomType, idToMiss.keys.toList())
            .map { it.also { c -> c.miss = idToMiss[c.id] ?: c.miss } }
            .sortedByDescending { it.miss }
    }

    private suspend fun fetchPollsByMissIndex(
        roomId: String, roomType: RoomType, cursor: Double?
    ): List<PollMessage> {
        val ref = db.getReference(missIndexPath(roomId, roomType))
        val query = if (cursor == null) ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        else ref.orderByValue().endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        val idToMiss = snap.children.associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }
        return fanOutFetchPolls(roomId, roomType, idToMiss.keys.toList())
            .map { it.also { p -> p.miss = idToMiss[p.id] ?: p.miss } }
            .sortedByDescending { it.miss }
    }

    private suspend fun fetchChatsByTeam(
        roomId: String, roomType: RoomType, team: String, cursor: Double?
    ): List<ChatMessage> {
        val ref = db.getReference(teamIndexPath(roomId, roomType, team))
        val query = if (cursor == null) ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        else ref.orderByValue().endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        val ids = snap.children.mapNotNull { it.key }
        return fanOutFetchChats(roomId, roomType, ids).sortedByDescending { it.timestamp }
    }

    private suspend fun fetchPollsByTeam(
        roomId: String, roomType: RoomType, team: String, cursor: Double?
    ): List<PollMessage> {
        val ref = db.getReference(teamIndexPath(roomId, roomType, team))
        val query = if (cursor == null) ref.orderByValue().limitToLast(PAGE_SIZE.toInt())
        else ref.orderByValue().endBefore(cursor).limitToLast(PAGE_SIZE.toInt())
        val snap = query.get().await()
        val ids = snap.children.mapNotNull { it.key }
        return fanOutFetchPolls(roomId, roomType, ids).sortedByDescending { it.timestamp }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fan-out fetchers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fanOutFetchChats(
        roomId: String, roomType: RoomType, ids: List<String>
    ): List<ChatMessage> = coroutineScope {
        ids.map { id ->
            async {
                try {
                    val snap = db.getReference("${chatsPath(roomId, roomType)}/$id").get().await()
                    FirebaseDataHelper.getChatMessageFromSnapshot(snap)
                } catch (e: Exception) { null }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fanOutFetchPolls(
        roomId: String, roomType: RoomType, ids: List<String>
    ): List<PollMessage> = coroutineScope {
        ids.map { id ->
            async {
                try {
                    val snap = db.getReference("${pollsPath(roomId, roomType)}/$id").get().await()
                    FirebaseDataHelper.getPollMessageFromSnapshot(snap)
                } catch (e: Exception) { null }
            }
        }.awaitAll().filterNotNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cursor management
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateCursor(
        filterKey: String, ck: String,
        chats: List<ChatMessage>, polls: List<PollMessage>
    ) {
        val cursor: Double? = when (filterKey) {
            ChatFilterKey.TOP_HIT -> {
                val minChat = chats.minOfOrNull { it.hit.toDouble() }
                val minPoll = polls.minOfOrNull { it.hit.toDouble() }
                listOfNotNull(minChat, minPoll).minOrNull()?.let { it - 0.5 }
            }
            ChatFilterKey.TOP_MISS -> {
                val minChat = chats.minOfOrNull { it.miss.toDouble() }
                val minPoll = polls.minOfOrNull { it.miss.toDouble() }
                listOfNotNull(minChat, minPoll).minOrNull()?.let { it - 0.5 }
            }
            else -> {
                val minChat = chats.minOfOrNull { it.timestamp.toDouble() }
                val minPoll = polls.minOfOrNull { it.timestamp.toDouble() }
                listOfNotNull(minChat, minPoll).minOrNull()
            }
        }
        cursor?.let { cursors[ck] = it }
    }

    fun resetCursor(roomId: String, filterKey: String) = cursors.remove(cursorKey(roomId, filterKey))
    fun resetAllCursors() = cursors.clear()

    // ─────────────────────────────────────────────────────────────────────────
    // Real-time listener — new messages at top (ALL filter only)
    // ─────────────────────────────────────────────────────────────────────────

    fun startRealtimeListener(
        roomId: String, roomType: RoomType, filterKey: String,
        onNewChat: (ChatMessage) -> Unit,
        onNewPoll: (PollMessage) -> Unit,
        onRemoved: (String) -> Unit
    ) {
        stopRealtimeListener()
        if (filterKey != ChatFilterKey.ALL) return

        val attachedAt = System.currentTimeMillis()
        val chatRef = db.getReference(chatsPath(roomId, roomType))
        val pollRef = db.getReference(pollsPath(roomId, roomType))

        val chatListener = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                val msg = FirebaseDataHelper.getChatMessageFromSnapshot(snap) ?: return
                if (msg.timestamp > attachedAt) onNewChat(msg)
            }
            override fun onChildRemoved(snap: DataSnapshot) { snap.key?.let { onRemoved(it) } }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) { Log.e(TAG, "Chat listener cancelled: ${e.message}") }
        }

        val pollListener = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                val poll = FirebaseDataHelper.getPollMessageFromSnapshot(snap) ?: return
                if (poll.timestamp > attachedAt) onNewPoll(poll)
            }
            override fun onChildRemoved(snap: DataSnapshot) { snap.key?.let { onRemoved(it) } }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }

        chatRef.orderByChild("timestamp").startAt(attachedAt.toDouble()).addChildEventListener(chatListener)
        pollRef.orderByChild("timestamp").startAt(attachedAt.toDouble()).addChildEventListener(pollListener)

        realtimeRef      = chatRef
        realtimeListener = chatListener
        // Store poll listener separately
        pollRef.addChildEventListener(pollListener)
    }

    fun stopRealtimeListener() {
        realtimeListener?.let { realtimeRef?.removeEventListener(it) }
        realtimeListener = null
        realtimeRef      = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Index node writers — call after posting/updating
    // ─────────────────────────────────────────────────────────────────────────

    fun writeIndexNodes(msg: Any, roomId: String, roomType: RoomType) {
        val base = basePath(roomId, roomType)
        when (msg) {
            is ChatMessage -> {
                val updates = hashMapOf<String, Any>(
                    "$base/chatsByTeam/${msg.team}/${msg.id}" to msg.timestamp,
                    "$base/chatsByHit/${msg.id}"              to msg.hit,
                    "$base/chatsByMiss/${msg.id}"             to msg.miss
                )
                db.reference.updateChildren(updates)
            }
            is PollMessage -> {
                val updates = hashMapOf<String, Any>(
                    "$base/chatsByTeam/${msg.team}/${msg.id}" to msg.timestamp,
                    "$base/chatsByHit/${msg.id}"              to msg.hit,
                    "$base/chatsByMiss/${msg.id}"             to msg.miss
                )
                db.reference.updateChildren(updates)
            }
        }
    }

    fun updateHitMissIndex(msgId: String, hit: Int, miss: Int, roomId: String, roomType: RoomType) {
        val base = basePath(roomId, roomType)
        db.getReference("$base/chatsByHit/$msgId").setValue(hit)
        db.getReference("$base/chatsByMiss/$msgId").setValue(miss)
    }

    fun deleteIndexNodes(msg: Any, roomId: String, roomType: RoomType) {
        val base = basePath(roomId, roomType)
        val team = when (msg) { is ChatMessage -> msg.team; is PollMessage -> msg.team; else -> return }
        val id   = when (msg) { is ChatMessage -> msg.id;   is PollMessage -> msg.id;   else -> return }
        val updates = hashMapOf<String, Any?>(
            "$base/chatsByTeam/$team/$id" to null,
            "$base/chatsByHit/$id"        to null,
            "$base/chatsByMiss/$id"       to null
        )
        db.reference.updateChildren(updates)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache helpers
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun cacheNewChat(msg: ChatMessage, roomId: String, filterKey: String) {
        dao.insertChat(ChatMessageEntity.from(msg, roomId, filterKey))
        dao.enforceChatLimitByTime(roomId, filterKey)
    }

    suspend fun cacheNewPoll(poll: PollMessage, roomId: String, filterKey: String) {
        dao.insertPoll(PollEntity.from(poll, roomId, filterKey))
        dao.enforcePollLimitByTime(roomId, filterKey)
    }

    suspend fun removeCached(id: String) {
        dao.deleteChatById(id)
        dao.deletePollById(id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // One-time migration — backfill index nodes for existing messages
    // Call once, then remove
    // ─────────────────────────────────────────────────────────────────────────

    fun migrateExistingToIndex(roomId: String, roomType: RoomType) {
        val base = basePath(roomId, roomType)
        db.getReference("$base/chats").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updates = hashMapOf<String, Any>()
                for (snap in snapshot.children) {
                    val id   = snap.key ?: continue
                    val hit  = snap.child("hit").getValue(Int::class.java) ?: 0
                    val miss = snap.child("miss").getValue(Int::class.java) ?: 0
                    val team = snap.child("team").getValue(String::class.java) ?: continue
                    val ts   = snap.child("timestamp").getValue(Long::class.java) ?: 0L
                    updates["$base/chatsByHit/$id"]         = hit
                    updates["$base/chatsByMiss/$id"]        = miss
                    updates["$base/chatsByTeam/$team/$id"]  = ts
                }
                db.getReference("$base/polls").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(ps: DataSnapshot) {
                        for (snap in ps.children) {
                            val id   = snap.key ?: continue
                            val hit  = snap.child("hit").getValue(Int::class.java) ?: 0
                            val miss = snap.child("miss").getValue(Int::class.java) ?: 0
                            val team = snap.child("team").getValue(String::class.java) ?: continue
                            val ts   = snap.child("timestamp").getValue(Long::class.java) ?: 0L
                            updates["$base/chatsByHit/$id"]         = hit
                            updates["$base/chatsByMiss/$id"]        = miss
                            updates["$base/chatsByTeam/$team/$id"]  = ts
                        }
                        db.reference.updateChildren(updates)
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }
}