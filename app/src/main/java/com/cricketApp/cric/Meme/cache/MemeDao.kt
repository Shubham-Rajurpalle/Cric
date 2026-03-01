package com.cricketApp.cric.Meme.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemeDao {

    // ── Reads ────────────────────────────────────────────────────────────────

    /** ALL / TEAM filters — newest first */
    @Query("SELECT * FROM memes WHERE filterKey = :filterKey ORDER BY timestamp DESC")
    fun observeMemesByTime(filterKey: String): Flow<List<MemeEntity>>

    /** TOP_HIT filter — most hits first */
    @Query("SELECT * FROM memes WHERE filterKey = 'TOP_HIT' ORDER BY hit DESC")
    fun observeMemesByHit(): Flow<List<MemeEntity>>

    /** TOP_MISS filter — most misses first */
    @Query("SELECT * FROM memes WHERE filterKey = 'TOP_MISS' ORDER BY miss DESC")
    fun observeMemesByMiss(): Flow<List<MemeEntity>>

    /** Count rows for a filter */
    @Query("SELECT COUNT(*) FROM memes WHERE filterKey = :filterKey")
    suspend fun getCount(filterKey: String): Int

    // ── Writes ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memes: List<MemeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meme: MemeEntity)

    @Query("UPDATE memes SET hit = :hit, miss = :miss WHERE id = :memeId")
    suspend fun updateHitMiss(memeId: String, hit: Int, miss: Int)

    @Query("UPDATE memes SET commentCount = :count WHERE id = :memeId")
    suspend fun updateCommentCount(memeId: String, count: Int)

    // ── Eviction ─────────────────────────────────────────────────────────────

    @Query("DELETE FROM memes WHERE cachedAt < :cutoffMillis")
    suspend fun evictOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM memes WHERE filterKey = :filterKey")
    suspend fun clearFilter(filterKey: String)

    @Query("DELETE FROM memes")
    suspend fun clearAll()

    @Query("DELETE FROM memes WHERE id = :memeId")
    suspend fun deleteMeme(memeId: String)

    // ── Cap — eviction respects the filter's sort order ───────────────────────

    /** ALL / TEAM — keep newest 30 by timestamp */
    @Query("""
        DELETE FROM memes 
        WHERE filterKey = :filterKey 
        AND id NOT IN (
            SELECT id FROM memes 
            WHERE filterKey = :filterKey 
            ORDER BY timestamp DESC 
            LIMIT 30
        )
    """)
    suspend fun enforceLimitByTime(filterKey: String)

    /** TOP_HIT — keep top 30 by hit count */
    @Query("""
        DELETE FROM memes 
        WHERE filterKey = 'TOP_HIT' 
        AND id NOT IN (
            SELECT id FROM memes 
            WHERE filterKey = 'TOP_HIT' 
            ORDER BY hit DESC 
            LIMIT 30
        )
    """)
    suspend fun enforceLimitByHit()

    /** TOP_MISS — keep top 30 by miss count */
    @Query("""
        DELETE FROM memes 
        WHERE filterKey = 'TOP_MISS' 
        AND id NOT IN (
            SELECT id FROM memes 
            WHERE filterKey = 'TOP_MISS' 
            ORDER BY miss DESC 
            LIMIT 30
        )
    """)
    suspend fun enforceLimitByMiss()
}