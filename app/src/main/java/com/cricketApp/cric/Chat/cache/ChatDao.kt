package com.cricketApp.cric.Chat.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── ChatMessage reads ─────────────────────────────────────────────────────

    /** ALL / TEAM filters — newest first */
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND filterKey = :filterKey ORDER BY timestamp DESC")
    fun observeChatsByTime(roomId: String, filterKey: String): Flow<List<ChatMessageEntity>>

    /** TOP_HIT — most hits first */
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND filterKey = 'TOP_HIT' ORDER BY hit DESC")
    fun observeChatsByHit(roomId: String): Flow<List<ChatMessageEntity>>

    /** TOP_MISS — most misses first */
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND filterKey = 'TOP_MISS' ORDER BY miss DESC")
    fun observeChatsByMiss(roomId: String): Flow<List<ChatMessageEntity>>

    // ── PollMessage reads ─────────────────────────────────────────────────────

    @Query("SELECT * FROM poll_messages WHERE roomId = :roomId AND filterKey = :filterKey ORDER BY timestamp DESC")
    fun observePollsByTime(roomId: String, filterKey: String): Flow<List<PollEntity>>

    @Query("SELECT * FROM poll_messages WHERE roomId = :roomId AND filterKey = 'TOP_HIT' ORDER BY hit DESC")
    fun observePollsByHit(roomId: String): Flow<List<PollEntity>>

    @Query("SELECT * FROM poll_messages WHERE roomId = :roomId AND filterKey = 'TOP_MISS' ORDER BY miss DESC")
    fun observePollsByMiss(roomId: String): Flow<List<PollEntity>>

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPolls(polls: List<PollEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: PollEntity)

    // ── Eviction ─────────────────────────────────────────────────────────────

    @Query("DELETE FROM chat_messages WHERE cachedAt < :cutoff")
    suspend fun evictOldChats(cutoff: Long)

    @Query("DELETE FROM poll_messages WHERE cachedAt < :cutoff")
    suspend fun evictOldPolls(cutoff: Long)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId AND filterKey = :filterKey")
    suspend fun clearChatFilter(roomId: String, filterKey: String)

    @Query("DELETE FROM poll_messages WHERE roomId = :roomId AND filterKey = :filterKey")
    suspend fun clearPollFilter(roomId: String, filterKey: String)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteChatById(id: String)

    @Query("DELETE FROM poll_messages WHERE id = :id")
    suspend fun deletePollById(id: String)

    // ── Cap — 30 per room+filter, respecting sort order ───────────────────────

    @Query("""DELETE FROM chat_messages WHERE roomId = :roomId AND filterKey = :filterKey
        AND id NOT IN (SELECT id FROM chat_messages WHERE roomId = :roomId AND filterKey = :filterKey
        ORDER BY timestamp DESC LIMIT 30)""")
    suspend fun enforceChatLimitByTime(roomId: String, filterKey: String)

    @Query("""DELETE FROM chat_messages WHERE roomId = :roomId AND filterKey = 'TOP_HIT'
        AND id NOT IN (SELECT id FROM chat_messages WHERE roomId = :roomId AND filterKey = 'TOP_HIT'
        ORDER BY hit DESC LIMIT 30)""")
    suspend fun enforceChatLimitByHit(roomId: String)

    @Query("""DELETE FROM chat_messages WHERE roomId = :roomId AND filterKey = 'TOP_MISS'
        AND id NOT IN (SELECT id FROM chat_messages WHERE roomId = :roomId AND filterKey = 'TOP_MISS'
        ORDER BY miss DESC LIMIT 30)""")
    suspend fun enforceChatLimitByMiss(roomId: String)

    @Query("""DELETE FROM poll_messages WHERE roomId = :roomId AND filterKey = :filterKey
        AND id NOT IN (SELECT id FROM poll_messages WHERE roomId = :roomId AND filterKey = :filterKey
        ORDER BY timestamp DESC LIMIT 30)""")
    suspend fun enforcePollLimitByTime(roomId: String, filterKey: String)

    @Query("""DELETE FROM poll_messages WHERE roomId = :roomId AND filterKey = 'TOP_HIT'
        AND id NOT IN (SELECT id FROM poll_messages WHERE roomId = :roomId AND filterKey = 'TOP_HIT'
        ORDER BY hit DESC LIMIT 30)""")
    suspend fun enforcePollLimitByHit(roomId: String)

    @Query("""DELETE FROM poll_messages WHERE roomId = :roomId AND filterKey = 'TOP_MISS'
        AND id NOT IN (SELECT id FROM poll_messages WHERE roomId = :roomId AND filterKey = 'TOP_MISS'
        ORDER BY miss DESC LIMIT 30)""")
    suspend fun enforcePollLimitByMiss(roomId: String)
}