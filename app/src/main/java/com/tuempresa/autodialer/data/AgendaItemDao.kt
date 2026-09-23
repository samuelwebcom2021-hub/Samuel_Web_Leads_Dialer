package com.tuempresa.autodialer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaItemDao {
    @Transaction
    @Query("SELECT * FROM agenda_items ORDER BY scheduledAt ASC")
    fun observeAllWithContact(): Flow<List<AgendaWithContact>>

    @Transaction
    @Query("SELECT * FROM agenda_items WHERE type = :type ORDER BY scheduledAt ASC")
    fun observeByTypeWithContact(type: AgendaItemType): Flow<List<AgendaWithContact>>

    @Query("SELECT * FROM agenda_items ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<AgendaItemEntity>>

    @Query("SELECT * FROM agenda_items WHERE type = :type ORDER BY scheduledAt ASC")
    fun observeByType(type: AgendaItemType): Flow<List<AgendaItemEntity>>

    @Query("SELECT * FROM agenda_items WHERE status IN ('SCHEDULED', 'SNOOZED') AND scheduledAt > :now")
    suspend fun getPendingReminders(now: Long): List<AgendaItemEntity>

    @Query("SELECT * FROM agenda_items WHERE id = :id")
    suspend fun getById(id: Long): AgendaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AgendaItemEntity): Long

    @Update
    suspend fun update(item: AgendaItemEntity)

    @Delete
    suspend fun delete(item: AgendaItemEntity)

    @Query("SELECT * FROM agenda_items WHERE syncState = 'DIRTY' OR remoteId IS NULL")
    suspend fun getPendingSync(): List<AgendaItemEntity>

    @Query("UPDATE agenda_items SET syncState = :state, remoteId = :remoteId, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateSyncMetadata(id: Long, state: String, remoteId: String?, lastUpdated: Long)

    @Query("SELECT MAX(lastUpdated) FROM agenda_items")
    suspend fun getMaxLastUpdated(): Long?
}
