package com.tuempresa.autodialer.domain

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.data.CallState
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.dialer.DialerEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Controlador de dominio para gestionar el inicio de llamadas profesionales.
 * Centraliza la creación de la sesión y la integración con TelecomManager.
 */
class CallController(private val context: Context) {

    private val app = context.applicationContext as App
    private val db = app.db
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    /**
     * Inicia una llamada profesional.
     * @return true si la llamada se colocó con éxito, false si hubo error o ya hay una sesión activa.
     */
    suspend fun placeCall(contact: ContactEntity, phoneAccount: PhoneAccountHandle?, sessionId: Long = 0): Boolean = withContext(Dispatchers.IO) {
        // 1. Validación de Concurrencia real
        val activeSession = db.callSessionDao().observeActiveSession().firstOrNull()
        
        // Si hay una sesión activa pero no está en un estado real de llamada (ej: quedó en PREPARING hace mucho)
        // la limpiamos para permitir la nueva.
        if (activeSession != null) {
            val isStale = System.currentTimeMillis() - activeSession.startTime > 60000 && 
                         activeSession.state == CallState.PREPARING
            
            if (isStale) {
                Log.w("CallController", "Limpiando sesión huérfana detectada: ${activeSession.callId}")
                db.callSessionDao().clearAll()
            } else if (DialerEvents.session.value != null) {
                Log.w("CallController", "Ya existe una sesión activa (${activeSession.callId}). Abortando marcación.")
                return@withContext false
            }
        }

        val callId = "call_${System.currentTimeMillis()}"
        val session = CallSessionEntity(
            callId = callId,
            folderId = contact.importBatchId,
            contactId = contact.id,
            dialedNumber = contact.phoneNumber,
            sim = phoneAccount?.id ?: "Default",
            state = CallState.PREPARING,
            startTime = System.currentTimeMillis(),
            isAutomated = true,
            isIncoming = false,
            sessionId = sessionId
        )

        try {
            // 2. Persistir sesión ANTES de llamar
            db.callSessionDao().insert(session)
            DialerEvents.updateSession(session)

            // Registrar para que el InCallService sepa que es una llamada automatizada
            com.tuempresa.autodialer.dialer.PendingAutoCallTracker.track(contact.phoneNumber)

            // Abrir la pantalla de llamadas de nuestra app de inmediato para que no salte la app nativa de Google
            try {
                val callIntent = android.content.Intent(context, com.tuempresa.autodialer.ui.call.CallActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or 
                             android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                             android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(callIntent)
            } catch (e: Exception) {
                Log.e("CallController", "Error al abrir CallActivity directamente: ${e.message}")
            }

            // 3. Colocar la llamada vía Telecom
            if (phoneAccount?.id == "FakeSimID") {
                Log.d("CallController", "[MODO PRUEBA] Simulando llamada exitosa...")
                // Simulamos que la llamada se "colocó" y pasa a DIALING
                val activeSession = session.copy(state = CallState.DIALING)
                db.callSessionDao().update(activeSession)
                DialerEvents.updateSession(activeSession)
                
                // En un entorno de prueba, podríamos disparar un Timer para simular DISCONNECTED después de unos segundos
                // Pero por ahora solo permitimos que la UI vea la sesión "activa".
            } else {
                val uri = Uri.fromParts("tel", contact.phoneNumber, null)
                val extras = Bundle().apply {
                    phoneAccount?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
                }
                telecomManager.placeCall(uri, extras)
            }
            Log.d("CallController", "Llamada colocada con éxito: $callId")
            true
        } catch (e: SecurityException) {
            Log.e("CallController", "Error de permisos al colocar llamada", e)
            handleCallFailure(session, "Error de permisos")
            false
        } catch (e: Exception) {
            Log.e("CallController", "Fallo inesperado al colocar llamada", e)
            handleCallFailure(session, e.message ?: "Error desconocido")
            false
        }
    }

    private suspend fun handleCallFailure(session: CallSessionEntity, reason: String) {
        val failedSession = session.copy(state = CallState.FAILED, notes = reason)
        db.callSessionDao().update(failedSession)
        DialerEvents.updateSession(failedSession)
        // La cola se liberará al detectar el estado FAILED o al limpiar después
    }
}
