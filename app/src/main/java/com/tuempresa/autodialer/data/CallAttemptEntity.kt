package com.tuempresa.autodialer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "call_attempts")
data class CallAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val timestampMillis: Long = System.currentTimeMillis(),
    val durationMillis: Long = 0,
    val result: CallResult,
    val resultLabel: String, // Etiqueta legible (Interesado, etc.)
    val alternateNumber: String? = null,
    val whatsappNumber: String? = null,
    val notes: String? = null,
    val syncState: String = SyncState.CREATED_LOCAL.name,
    val lastUpdated: Long = System.currentTimeMillis(),
    val remoteId: String? = null
)

data class DailyStat(
    val date: String, // yyyy-MM-dd
    val totalAttempts: Int,
    val interestedCount: Int
)

data class MonthlyStat(
    val month: String, // yyyy-MM
    val totalAttempts: Int,
    val interestedCount: Int
)

@Dao
interface CallAttemptDao {
    @Insert
    suspend fun insert(entry: CallAttemptEntity): Long

    @Query("SELECT * FROM call_attempts")
    suspend fun getAll(): List<CallAttemptEntity>

    @Query("SELECT * FROM call_attempts WHERE contactId = :contactId ORDER BY timestampMillis ASC")
    suspend fun getForContact(contactId: Long): List<CallAttemptEntity>

    @Query("SELECT * FROM call_attempts WHERE id = :id")
    suspend fun getById(id: Long): CallAttemptEntity?

    @Query("""
        SELECT strftime('%Y-%m-%d', timestampMillis / 1000, 'unixepoch', 'localtime') as date,
               COUNT(*) as totalAttempts,
               SUM(CASE WHEN result = 'INTERESTED' OR resultLabel = 'Interesado' OR resultLabel = 'INTERESTED' THEN 1 ELSE 0 END) as interestedCount
        FROM call_attempts
        WHERE timestampMillis >= :sinceMillis
        GROUP BY date
        ORDER BY date ASC
    """)
    fun observeDailyStats(sinceMillis: Long): Flow<List<DailyStat>>

    @Query("""
        SELECT strftime('%Y-%m-%d', timestampMillis / 1000, 'unixepoch', 'localtime') as date,
               COUNT(*) as totalAttempts,
               SUM(CASE WHEN result = 'INTERESTED' OR resultLabel = 'Interesado' OR resultLabel = 'INTERESTED' THEN 1 ELSE 0 END) as interestedCount
        FROM call_attempts
        GROUP BY date
        ORDER BY date ASC
    """)
    fun observeAllDailyStats(): Flow<List<DailyStat>>

    @Query("""
        SELECT strftime('%Y-%m', timestampMillis / 1000, 'unixepoch', 'localtime') as month,
               COUNT(*) as totalAttempts,
               SUM(CASE WHEN resultLabel = 'Interesado' THEN 1 ELSE 0 END) as interestedCount
        FROM call_attempts
        WHERE strftime('%Y', timestampMillis / 1000, 'unixepoch', 'localtime') = :year
        GROUP BY month
        ORDER BY month ASC
    """)
    fun observeMonthlyStats(year: String): Flow<List<MonthlyStat>>

    // --- Sync ---
    @Query("SELECT * FROM call_attempts WHERE syncState != 'SYNCED'")
    suspend fun getPendingSync(): List<CallAttemptEntity>

    @Query("UPDATE call_attempts SET syncState = :state, remoteId = :remoteId, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateSyncMetadata(id: Long, state: String, remoteId: String?, lastUpdated: Long)

    @Query("SELECT MAX(lastUpdated) FROM call_attempts")
    suspend fun getMaxLastUpdated(): Long?

    @Query("DELETE FROM call_attempts")
    suspend fun deleteAll()
}
