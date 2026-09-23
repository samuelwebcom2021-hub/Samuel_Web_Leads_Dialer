package com.tuempresa.autodialer.data.repository

import android.content.Context
import androidx.work.*
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.sync.SyncWorker
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar el estado de sincronización y disparar el Worker.
 */
class SyncRepository(private val context: Context) {

    private val db = (context.applicationContext as App).db

    /** 
     * Observa el conteo de elementos pendientes (basado en la tabla de operaciones pendientes). 
     * Nota: En el futuro esto podría observar el syncState de contacts/batches directamente.
     */
    fun observePendingCount(): Flow<Int> {
        return db.syncDao().observePendingCount()
    }

    /** Dispara la sincronización inmediata. */
    fun triggerSyncNow() {
        triggerSyncWorker()
    }

    fun triggerSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("FirebaseSync")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "FirebaseSyncWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    /** 
     * Compatibilidad con SyncCoordinator: encola una operación genérica.
     * El SyncEngine procesará tanto estas operaciones como los estados en las entidades.
     */
    suspend fun enqueueSync(
        entityType: String,
        entityId: String,
        operation: String,
        dataJson: String = ""
    ) {
        val pendingOp = com.tuempresa.autodialer.data.PendingSyncOperation(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            dataJson = dataJson
        )
        db.syncDao().insert(pendingOp)
        triggerSyncWorker()
    }
}
