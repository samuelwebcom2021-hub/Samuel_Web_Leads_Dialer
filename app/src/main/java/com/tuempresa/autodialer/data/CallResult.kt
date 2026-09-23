package com.tuempresa.autodialer.data

/**
 * Resultados de negocio de una llamada finalizada.
 */
enum class CallResult {
    ANSWERED,       // Contestada
    NO_ANSWER,      // No contestó (timeout o colgado por el robot)
    BUSY,           // Ocupado
    REJECTED,       // Rechazada por el cliente
    INVALID_NUMBER, // Número no existe o formato incorrecto
    FAILED,         // Error técnico durante la llamada
    
    // Resultados de negocio (Post-Call)
    INTERESTED,
    NOT_INTERESTED,
    RETRY_LATER,
    WRONG_NUMBER
}
