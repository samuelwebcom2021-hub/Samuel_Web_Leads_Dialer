package com.tuempresa.autodialer.data

/**
 * Categorías de ítems en la agenda.
 */
enum class AgendaItemType {
    /** Generado automáticamente cuando un contacto no responde tras los intentos del día. */
    FOLDER_RETRY,
    /** Generado manualmente por el usuario tras obtener información en una llamada. */
    PERSONAL_REMINDER
}
