package com.tuempresa.autodialer.data

/**
 * Taxonomía de estados de sincronización para arquitectura Local-First.
 */
enum class SyncState {
    /** Creado localmente, aún no se ha intentado subir. */
    CREATED_LOCAL,
    
    /** En cola para sincronización (reintento o proceso activo). */
    PENDING,
    
    /** Confirmado en el servidor remoto. */
    SYNCED,
    
    /** El registro existía en el servidor pero se modificó localmente. */
    MODIFIED_LOCAL,
    
    /** El registro se modificó remotamente (descargado pero no procesado). */
    MODIFIED_REMOTE,
    
    /** Marcado para eliminación (Tombstone). Debe borrarse en remoto antes de purgar local. */
    DELETED_TOMBSTONE,
    
    /** Error persistente en la sincronización. */
    ERROR
}
