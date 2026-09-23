package com.tuempresa.autodialer.data

/**
 * Estados técnicos del ciclo de vida de una llamada según Android Telecom.
 */
enum class CallState {
    IDLE,           // Sin llamada activa
    PREPARING,      // Preparando la marcación
    DIALING,        // Marcando
    RINGING,        // Sonando (entrante o saliente esperando respuesta)
    ACTIVE,         // Llamada en curso (contestada)
    HOLDING,        // En espera
    DISCONNECTING,  // Colgando
    DISCONNECTED,   // Finalizada correctamente
    FAILED          // Falló (error técnico, permiso, etc.)
}
