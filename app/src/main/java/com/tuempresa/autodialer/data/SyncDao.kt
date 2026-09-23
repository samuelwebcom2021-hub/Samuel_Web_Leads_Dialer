package com.tuempresa.autodialer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert
    suspend fun insert(operation: PendingSyncOperation)

    @Delete
    suspend fun delete(operation: PendingSyncOperation)

    @Query("SELECT * FROM pending_sync_operations ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<PendingSyncOperation>

    @Query("SELECT COUNT(*) FROM pending_sync_operations")
    fun observePendingCount(): Flow<Int>
}
