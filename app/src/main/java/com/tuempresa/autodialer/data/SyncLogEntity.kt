package com.tuempresa.autodialer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val description: String,
    val success: Boolean,
    val errorMessage: String? = null
)

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(entry: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY timestampMillis DESC LIMIT 100")
    fun observeRecent(): Flow<List<SyncLogEntity>>

    @Query("DELETE FROM sync_log")
    suspend fun clear()
}
