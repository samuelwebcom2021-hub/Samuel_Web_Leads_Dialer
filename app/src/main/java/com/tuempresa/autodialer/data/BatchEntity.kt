package com.tuempresa.autodialer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa una carpeta o lote de importación.
 */
@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val isArchived: Boolean = false,
    
    // --- Metadatos de Sincronización (Fase 8) ---
    val syncState: String = SyncState.CREATED_LOCAL.name,
    val lastUpdated: Long = System.currentTimeMillis(),
    val remoteId: String? = null
)
