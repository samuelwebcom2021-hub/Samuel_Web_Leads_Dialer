package com.tuempresa.autodialer.data

/**
 * Ciclo de vida de un contacto dentro de la cola de marcado automático.
 *
 *  PENDING             -> todavía no se ha intentado llamar (o se reintenta enseguida, intento 2)
 *  IN_PROGRESS         -> la llamada está sonando/en curso ahora mismo
 *  AWAITING_OUTCOME    -> contestaron (o colgaste tú); esperando que TÚ digas si quedó
 *                         interesado o no — eso la IA no lo puede decidir sola
 *  INTERESTED          -> marcaste al contacto como interesado
 *  NOT_INTERESTED      -> marcaste al contacto como no interesado
 *  AWAITING_RETRY_TIME -> falló el intento 2 (no contestó); esperando que elijas la hora
 *                         de mañana para reintentar
 *  SCHEDULED_RETRY     -> ya se eligió la hora del día siguiente; hay una alarma programada
 *  CANCELLED           -> el usuario lo sacó manualmente de la cola
 */
enum class ContactStatus {
    PENDING,
    IN_PROGRESS,
    AWAITING_OUTCOME,
    INTERESTED,
    NOT_INTERESTED,
    AWAITING_RETRY_TIME,
    SCHEDULED_RETRY,
    CANCELLED,
    SKIPPED,
    FINAL_NO_ANSWER,
    WRONG_NUMBER
}

/** Resultado técnico detectado al terminar una llamada saliente (esto sí lo puede inferir la app sola). */
enum class CallOutcome {
    ANSWERED,                 // duración por encima del umbral -> alguien contestó
    NO_ANSWER_OR_VOICEMAIL,   // colgó rápido / no contestó / posible buzón
    UNKNOWN
}
