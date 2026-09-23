package com.tuempresa.autodialer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long? = null,
    val title: String,
    val description: String = "",
    val triggerTimeMillis: Long,
    val ringtoneUri: String? = null,
    val isCompleted: Boolean = false,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val syncState: String = SyncState.CREATED_LOCAL.name,
    val remoteId: String? = null
)
