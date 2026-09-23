package com.tuempresa.autodialer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: BatchEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(batches: List<BatchEntity>)

    @Update
    suspend fun update(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getById(id: Long): BatchEntity?

    @Query("SELECT * FROM batches ORDER BY id DESC")
    fun observeAll(): Flow<List<BatchEntity>>

    @Query("DELETE FROM batches WHERE id = :batchId")
    suspend fun deleteById(batchId: Long)

    // --- Sincronización (Fase 8) ---

    @Query("SELECT * FROM batches WHERE syncState != 'SYNCED'")
    suspend fun getPendingSync(): List<BatchEntity>

    @Query("UPDATE batches SET syncState = :state, remoteId = :remoteId, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateSyncMetadata(id: Long, state: String, remoteId: String?, lastUpdated: Long)

    @Query("UPDATE batches SET syncState = 'DELETED_TOMBSTONE', lastUpdated = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT MAX(lastUpdated) FROM batches")
    suspend fun getMaxLastUpdated(): Long?

    @Query("DELETE FROM batches")
    suspend fun deleteAll()
}
