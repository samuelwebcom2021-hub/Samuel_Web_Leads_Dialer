package com.tuempresa.autodialer.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.SyncLogEntity

/**
 * Worker periódico que dispara el motor de sincronización.
 */
class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val db = (appContext.applicationContext as App).db
    private val syncEngine = SyncEngine(appContext)

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Starting scheduled synchronization...")
            
            syncEngine.performFullSync()
            
            db.syncLogDao().insert(SyncLogEntity(
                description = "Sincronización completada exitosamente",
                success = true
            ))
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            db.syncLogDao().insert(SyncLogEntity(
                description = "Error en la sincronización: ${e.localizedMessage}",
                success = false
            ))
            Result.retry()
        }
    }
}
