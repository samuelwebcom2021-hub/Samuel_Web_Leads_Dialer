package com.tuempresa.autodialer.domain

/**
 * Estados del motor de automatización (DecisionEngine).
 * Representa en qué fase del ciclo de marcado automático nos encontramos.
 */
enum class AutomationState {
    IDLE,               // Motor apagado o en reposo
    PREPARING,          // Buscando el siguiente contacto en la DB
    WAITING_TRANSITION, // Espera aleatoria antes de marcar (transitionDelay)
    DIALING,            // Llamada colocada, esperando respuesta o timeout
    CALL_ACTIVE,        // Llamada contestada, usuario hablando
    WAITING_OUTCOME,    // Llamada finalizada, esperando que el usuario elija resultado (Interesado, etc)
    WAITING_RETRY,      // Espera aleatoria para reintento del mismo contacto (redialDelay)
    PAUSED              // Pausado manualmente, por Cuota Diaria o Modo Vacaciones
}
