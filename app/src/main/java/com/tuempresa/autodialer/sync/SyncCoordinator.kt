package com.tuempresa.autodialer.sync

import android.content.Context
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.repository.SyncRepository

/**
 * Orquesta la sincronización entre Room y Firebase.
 * Se apoya en SyncRepository para encolar operaciones offline.
 */
class SyncCoordinator(private val context: Context) {

    private val app get() = context.applicationContext as App
    private val syncRepo by lazy { SyncRepository(context) }

    suspend fun syncNewBatch(batch: com.tuempresa.autodialer.data.BatchEntity, contacts: List<ContactEntity>) {
        // El motor de sync detectará los estados 'CREATED_LOCAL' automáticamente.
        syncRepo.triggerSyncNow()
    }

    suspend fun syncContactUpdate(contactId: Long) {
        syncRepo.enqueueSync("CONTACT", contactId.toString(), "UPDATE")
    }

    suspend fun syncAgendaItemUpdate(agendaId: Long) {
        syncRepo.enqueueSync("AGENDA_ITEM", agendaId.toString(), "UPDATE")
    }

    suspend fun syncAgendaItemDeletion(agendaId: Long) {
        syncRepo.enqueueSync("AGENDA_ITEM", agendaId.toString(), "DELETE")
    }

    suspend fun syncCallAttemptUpdate(attemptId: Long) {
        syncRepo.enqueueSync("CALL_ATTEMPT", attemptId.toString(), "UPDATE")
    }

    suspend fun syncSettings() {
        syncRepo.enqueueSync("SETTINGS", "current", "UPDATE")
    }
}
