package com.tuempresa.autodialer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un contacto/lead importado desde el Excel.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val businessName: String = "",
    val websiteRaw: String? = null,
    val websiteType: String? = null, // ver WebsiteClassifier.Type
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val status: String = ContactStatus.PENDING.name,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextAttemptAt: Long? = null,
    val lastOutcome: String? = null,
    val importBatchId: Long = 0,
    val importBatchName: String = "",
    /** En qué día de reintento va: 1 = hoy, 2 = mañana, 3 = el definitivo (ver DialerSettings.maxRetryDays). */
    val retryRound: Int = 1,
    /** Número de WhatsApp que te dio el cliente en vivo (si aplica). */
    val whatsappNumber: String? = null,
    /** Número directo o del dueño capturado en vivo. */
    val ownerPhone: String? = null,
    /** Notas comerciales de la llamada. */
    val notes: String? = null,
    /** Mayor que 0 = se llama ANTES que los demás pendientes (para el "número alterno" capturado en vivo). */
    val priority: Int = 0,
    
    // --- Metadatos de Sincronización (Fase 8) ---
    val syncState: String = SyncState.CREATED_LOCAL.name,
    val lastUpdated: Long = System.currentTimeMillis(),
    val remoteId: String? = null
)
