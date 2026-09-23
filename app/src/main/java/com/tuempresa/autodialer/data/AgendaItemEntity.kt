package com.tuempresa.autodialer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que unifica los reintentos automáticos de carpeta y los recordatorios personales.
 */
@Entity(
    tableName = "agenda_items",
    indices = [androidx.room.Index(value = ["operationId"], unique = true)]
)
data class AgendaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AgendaItemType,
    val contactId: Long,
    val folderId: Long,
    val scheduledAt: Long,
    val status: AgendaItemStatus = AgendaItemStatus.SCHEDULED,
    val reason: String? = null,
    val createdFromCallId: String? = null,
    
    // --- Metadatos de Sincronización ---
    val syncState: String = SyncState.CREATED_LOCAL.name,
    val lastUpdated: Long = System.currentTimeMillis(),
    val remoteId: String? = null,
    /** ID único de la operación para evitar duplicados por reentrada o dobles clics. */
    val operationId: String? = null
)
