package com.tuempresa.autodialer.data

/**
 * Estados del ciclo de vida de un recordatorio o reintento programado.
 */
enum class AgendaItemStatus {
    SCHEDULED,
    TRIGGERED,
    OPENED,
    SNOOZED,
    COMPLETED,
    CANCELLED
}
