package com.tuempresa.autodialer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders ORDER BY triggerTimeMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND triggerTimeMillis > :now ORDER BY triggerTimeMillis ASC")
    suspend fun getFutureReminders(now: Long): List<ReminderEntity>

    // --- Sync ---
    @Query("SELECT * FROM reminders WHERE syncState != 'SYNCED'")
    suspend fun getPendingSync(): List<ReminderEntity>

    @Query("UPDATE reminders SET syncState = :state, remoteId = :remoteId, lastUpdateTime = :lastUpdated WHERE id = :id")
    suspend fun updateSyncMetadata(id: Long, state: String, remoteId: String?, lastUpdated: Long)

    @Query("SELECT MAX(lastUpdateTime) FROM reminders")
    suspend fun getMaxLastUpdated(): Long?

    @Query("UPDATE reminders SET syncState = 'DELETED_TOMBSTONE', lastUpdateTime = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
