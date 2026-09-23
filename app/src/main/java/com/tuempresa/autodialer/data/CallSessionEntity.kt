package com.tuempresa.autodialer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad central que representa la llamada activa en este momento.
 * Solo debe existir UNA fila en esta tabla mientras hay una llamada.
 * 
 * Normalizada: No guarda nombres en texto plano; la UI debe resolverlos
 * a partir de contactId y folderId.
 */
@Entity(tableName = "call_sessions")
data class CallSessionEntity(
    @PrimaryKey val callId: String, // ID único provisto por Telecom
    val folderId: Long,
    val contactId: Long,
    val dialedNumber: String,
    val sim: String,
    val state: CallState,
    val result: CallResult? = null,
    val startTime: Long = System.currentTimeMillis(),
    val answerTime: Long? = null,
    val endTime: Long? = null,
    val isAutomated: Boolean = false,
    val isIncoming: Boolean = false,
    val alternateNumber: String? = null,
    val whatsappNumber: String? = null,
    val notes: String? = null,
    /** Identificador de la sesión de automatización actual para evitar reentradas. */
    val sessionId: Long = 0
)
