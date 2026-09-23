package com.tuempresa.autodialer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: CallSessionEntity)

    @Update
    suspend fun update(session: CallSessionEntity)

    @Query("SELECT * FROM call_sessions LIMIT 1")
    fun observeActiveSession(): Flow<CallSessionEntity?>

    @Query("SELECT * FROM call_sessions WHERE callId = :callId")
    suspend fun getByCallId(callId: String): CallSessionEntity?

    @Query("DELETE FROM call_sessions")
    suspend fun clearAll()

    @Query("DELETE FROM call_sessions WHERE callId = :callId")
    suspend fun deleteByCallId(callId: String)
}
