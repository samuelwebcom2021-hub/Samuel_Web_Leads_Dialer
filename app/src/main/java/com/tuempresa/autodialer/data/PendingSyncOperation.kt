package com.tuempresa.autodialer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync_operations")
data class PendingSyncOperation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val entityType: String, // "FOLDER", "CONTACT", "EXTRA_FIELD", "REMINDER"
    val entityId: String,   // ID local de la entidad
    val operation: String,  // "CREATE", "UPDATE", "DELETE"
    val dataJson: String    // Datos serializados si es necesario, o se leen de Room al sincronizar
)
