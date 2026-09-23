package com.tuempresa.autodialer.data

/**
 * Extensiones para facilitar el manejo de estados de sincronización.
 */

fun ContactEntity.markDirty() = this.copy(
    syncState = SyncState.MODIFIED_LOCAL.name,
    lastUpdated = System.currentTimeMillis()
)

fun BatchEntity.markDirty() = this.copy(
    syncState = SyncState.MODIFIED_LOCAL.name,
    lastUpdated = System.currentTimeMillis()
)

fun ReminderEntity.markDirty() = this.copy(
    syncState = SyncState.MODIFIED_LOCAL.name,
    lastUpdateTime = System.currentTimeMillis()
)

fun CallAttemptEntity.markDirty() = this.copy(
    syncState = SyncState.MODIFIED_LOCAL.name,
    lastUpdated = System.currentTimeMillis()
)

fun ContactEntity.markSynced(remoteId: String) = this.copy(
    syncState = SyncState.SYNCED.name,
    remoteId = remoteId
)

fun BatchEntity.markSynced(remoteId: String) = this.copy(
    syncState = SyncState.SYNCED.name,
    remoteId = remoteId
)
