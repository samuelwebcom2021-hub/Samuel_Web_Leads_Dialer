package com.tuempresa.autodialer.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import androidx.room.withTransaction

/**
 * Motor central de sincronización.
 * Orquesta la subida de cambios locales y la resolución de conflictos.
 */
class SyncEngine(private val context: Context) {

    private val db = (context.applicationContext as App).db
    private val contactDao = db.contactDao()
    private val batchDao = db.batchDao()
    private val backupManager = FirebaseBackupManager()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserRef() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid)
    }

    suspend fun performFullSync() = withContext(Dispatchers.IO) {
        if (auth.currentUser == null) {
            Log.w("SyncEngine", "Sync skipped: No user logged in")
            return@withContext
        }
        uploadLocalChanges()
        processPendingOperations()
        downloadRemoteChanges()
    }

    private suspend fun uploadLocalChanges() {
        syncFoldersUpload()
        syncContactsUpload()
        syncAgendaItemsUpload()
        syncCallAttemptsUpload()
    }

    private suspend fun syncAgendaItemsUpload() {
        val pending = db.agendaItemDao().getPendingSync()
        if (pending.isEmpty()) return
        backupManager.backupAgendaItems(pending)
        for (item in pending) {
            db.agendaItemDao().updateSyncMetadata(
                item.id, 
                SyncState.SYNCED.name, 
                item.id.toString(), 
                item.lastUpdated
            )
        }
    }

    private suspend fun syncCallAttemptsUpload() {
        val pending = db.callAttemptDao().getPendingSync()
        if (pending.isEmpty()) return
        backupManager.backupCallAttempts(pending)
        for (a in pending) {
            db.callAttemptDao().updateSyncMetadata(
                a.id, 
                SyncState.SYNCED.name, 
                a.id.toString(), 
                a.lastUpdated
            )
        }
    }

    private suspend fun processPendingOperations() {
        val pendingOps = db.syncDao().getAllPending()
        if (pendingOps.isEmpty()) return
        
        for (op in pendingOps) {
            try {
                when (op.entityType) {
                    "AGENDA_ITEM" -> syncAgendaItem(op)
                    "CALL_ATTEMPT" -> syncCallAttempt(op)
                    "SETTINGS" -> syncSettings(op)
                }
                db.syncDao().delete(op)
            } catch (e: Exception) {
                Log.e("SyncEngine", "Failed to process operation ${op.id}", e)
            }
        }
    }

    private suspend fun syncAgendaItem(op: PendingSyncOperation) {
        val agendaId = op.entityId.toLongOrNull() ?: return
        val item = db.agendaItemDao().getById(agendaId)
        if (item != null) {
            if (op.operation == "DELETE") {
                backupManager.deleteAgendaItem(agendaId)
                db.agendaItemDao().delete(item)
            } else {
                backupManager.backupAgendaItems(listOf(item))
                db.agendaItemDao().updateSyncMetadata(
                    item.id, 
                    SyncState.SYNCED.name, 
                    item.id.toString(), 
                    item.lastUpdated
                )
            }
        }
    }

    private suspend fun syncCallAttempt(op: PendingSyncOperation) {
        val attemptId = op.entityId.toLongOrNull() ?: return
        val attempt = db.callAttemptDao().getById(attemptId)
        if (attempt != null) {
            backupManager.backupCallAttempts(listOf(attempt))
            db.callAttemptDao().updateSyncMetadata(
                attempt.id, 
                SyncState.SYNCED.name, 
                attempt.id.toString(), 
                attempt.lastUpdated
            )
        }
    }

    private suspend fun syncSettings(op: PendingSyncOperation) {
        val settings = (context.applicationContext as App).settingsState.value
        val settingsMap = hashMapOf(
            "autoHangupSeconds" to settings.autoHangupSeconds,
            "redialDelayMin" to settings.redialDelayMin,
            "redialDelayMax" to settings.redialDelayMax,
            "maxAttemptsPerContact" to settings.maxAttemptsPerContact,
            "isVacationModeActive" to settings.isVacationModeActive,
            "soundAlertOnAnswer" to settings.soundAlertOnAnswer,
            "retryHour" to settings.retryHour,
            "retryMinute" to settings.retryMinute
        )
        backupManager.backupSettings(settingsMap)
    }

    private suspend fun syncFoldersUpload() {
        val pendingBatches = batchDao.getPendingSync()
        for (folder in pendingBatches) {
            try {
                if (folder.syncState == SyncState.DELETED_TOMBSTONE.name) {
                    backupManager.deleteFolder(folder.id)
                    batchDao.deleteById(folder.id)
                } else {
                    backupManager.backupFolder(folder)
                    batchDao.updateSyncMetadata(
                        folder.id, 
                        SyncState.SYNCED.name, 
                        folder.id.toString(), 
                        folder.lastUpdated
                    )
                }
            } catch (e: Exception) {
                Log.e("SyncEngine", "Failed to upload folder ${folder.id}", e)
                batchDao.updateSyncMetadata(folder.id, SyncState.ERROR.name, null, folder.lastUpdated)
            }
        }
    }

    private suspend fun syncContactsUpload() {
        val pendingContacts = contactDao.getPendingSync()
        if (pendingContacts.isEmpty()) return

        pendingContacts.chunked(50).forEach { batchList ->
            val firestoreBatch = firestore.batch()
            val userRef = getUserRef() ?: return@forEach

            for (contact in batchList) {
                try {
                    val contactRef = userRef.collection("folders").document(contact.importBatchId.toString())
                        .collection("contacts").document(contact.id.toString())

                    if (contact.syncState == SyncState.DELETED_TOMBSTONE.name) {
                        firestoreBatch.delete(contactRef)
                    } else {
                        val extraFields = contactDao.getExtraFields(contact.id)
                        val extraFieldsMap = extraFields.associate { it.key to it.value }
                        val data = hashMapOf(
                            "phoneNumber" to contact.phoneNumber,
                            "businessName" to contact.businessName,
                            "websiteRaw" to contact.websiteRaw,
                            "websiteType" to contact.websiteType,
                            "rating" to contact.rating,
                            "reviewCount" to contact.reviewCount,
                            "status" to contact.status,
                            "attemptCount" to contact.attemptCount,
                            "lastAttemptAt" to contact.lastAttemptAt,
                            "nextAttemptAt" to contact.nextAttemptAt,
                            "lastOutcome" to contact.lastOutcome,
                            "retryRound" to contact.retryRound,
                            "whatsappNumber" to contact.whatsappNumber,
                            "ownerPhone" to contact.ownerPhone,
                            "notes" to contact.notes,
                            "priority" to contact.priority,
                            "importBatchId" to contact.importBatchId,
                            "importBatchName" to contact.importBatchName,
                            "lastUpdated" to contact.lastUpdated,
                            "extraFields" to extraFieldsMap
                        )
                        firestoreBatch.set(contactRef, data, com.google.firebase.firestore.SetOptions.merge())
                    }
                } catch (e: Exception) {
                    Log.e("SyncEngine", "Error preparing contact ${contact.id}", e)
                }
            }

            try {
                firestoreBatch.commit().await()
                for (contact in batchList) {
                    if (contact.syncState == SyncState.DELETED_TOMBSTONE.name) {
                        contactDao.deleteById(contact.id)
                    } else {
                        contactDao.updateSyncMetadata(
                            contact.id,
                            SyncState.SYNCED.name,
                            contact.id.toString(),
                            contact.lastUpdated
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncEngine", "Failed to commit firestore batch", e)
            }
        }
    }

    private suspend fun downloadRemoteChanges() {
        val userRef = getUserRef() ?: return
        
        try {
            val lastSync = batchDao.getMaxLastUpdated() ?: 0L
            val remoteFolders = userRef.collection("folders")
                .whereGreaterThan("lastUpdated", lastSync)
                .get().await()

            for (doc in remoteFolders.documents) {
                if (!currentCoroutineContext().isActive) break
                val remoteLastUpdated = doc.getLong("lastUpdated") ?: 0L
                val folderId = doc.id.toLong()
                
                val folder = BatchEntity(
                    id = folderId,
                    name = doc.getString("name") ?: "Carpeta remota",
                    isArchived = doc.getBoolean("isArchived") ?: false,
                    syncState = SyncState.SYNCED.name,
                    lastUpdated = remoteLastUpdated,
                    remoteId = doc.id
                )
                batchDao.insert(folder)
            }

            val allFoldersDocs = userRef.collection("folders").get().await()
            val folders = allFoldersDocs.documents.map { it.id.toLong() }

            coroutineScope {
                folders.forEach { folderId ->
                    async { downloadContactsForFolder(folderId) }
                }
                async { downloadAgendaItems() }
                async { downloadCallAttempts() }
            }

        } catch (e: Exception) {
            Log.e("SyncEngine", "Download failed", e)
        }
    }

    private suspend fun downloadContactsForFolder(folderId: Long) {
        if (!currentCoroutineContext().isActive) return
        val userRef = getUserRef() ?: return
        
        try {
            val lastSync = contactDao.getMaxLastUpdatedForFolder(folderId) ?: 0L
            val remoteContacts = userRef.collection("folders").document(folderId.toString())
                .collection("contacts")
                .whereGreaterThan("lastUpdated", lastSync)
                .get().await()

            if (remoteContacts.isEmpty) return

            db.withTransaction {
                for (doc in remoteContacts.documents) {
                    val remoteLastUpdated = doc.getLong("lastUpdated") ?: 0L
                    val contactId = doc.id.toLong()
                    
                    val contact = ContactEntity(
                        id = contactId,
                        phoneNumber = doc.getString("phoneNumber") ?: "",
                        businessName = doc.getString("businessName") ?: "",
                        websiteRaw = doc.getString("websiteRaw"),
                        websiteType = doc.getString("websiteType"),
                        rating = doc.getDouble("rating"),
                        reviewCount = doc.getLong("reviewCount")?.toInt(),
                        status = doc.getString("status") ?: ContactStatus.PENDING.name,
                        attemptCount = doc.getLong("attemptCount")?.toInt() ?: 0,
                        lastAttemptAt = doc.getLong("lastAttemptAt"),
                        nextAttemptAt = doc.getLong("nextAttemptAt"),
                        lastOutcome = doc.getString("lastOutcome"),
                        importBatchId = folderId,
                        importBatchName = doc.getString("importBatchName") ?: "",
                        retryRound = doc.getLong("retryRound")?.toInt() ?: 1,
                        whatsappNumber = doc.getString("whatsappNumber"),
                        ownerPhone = doc.getString("ownerPhone"),
                        notes = doc.getString("notes"),
                        priority = doc.getLong("priority")?.toInt() ?: 0,
                        syncState = SyncState.SYNCED.name,
                        lastUpdated = remoteLastUpdated,
                        remoteId = doc.id
                    )
                    contactDao.insertAll(listOf(contact))

                    contactDao.deleteExtraFieldsForContact(contactId)
                    @Suppress("UNCHECKED_CAST")
                    val extraFieldsMap = doc.get("extraFields") as? Map<String, String> ?: emptyMap()
                    val extraFieldEntities = extraFieldsMap.map { (k, v) ->
                        ContactExtraFieldEntity(contactId = contactId, key = k, value = v)
                    }
                    if (extraFieldEntities.isNotEmpty()) {
                        contactDao.insertExtraFields(extraFieldEntities)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error downloading contacts for folder $folderId", e)
        }
    }

    private suspend fun downloadCallAttempts() {
        if (!currentCoroutineContext().isActive) return
        val userRef = getUserRef() ?: return
        try {
            val lastSync = db.callAttemptDao().getMaxLastUpdated() ?: 0L
            val remote = userRef.collection("call_attempts")
                .whereGreaterThan("lastUpdated", lastSync)
                .get().await()
            
            if (remote.isEmpty) return
            
            db.withTransaction {
                for (doc in remote.documents) {
                    val attempt = CallAttemptEntity(
                        id = doc.id.toLong(),
                        contactId = doc.getLong("contactId") ?: 0L,
                        timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                        durationMillis = doc.getLong("durationMillis") ?: 0L,
                        result = CallResult.valueOf(doc.getString("result") ?: CallResult.FAILED.name),
                        resultLabel = doc.getString("resultLabel") ?: "",
                        alternateNumber = doc.getString("alternateNumber"),
                        whatsappNumber = doc.getString("whatsappNumber"),
                        notes = doc.getString("notes"),
                        syncState = SyncState.SYNCED.name,
                        lastUpdated = doc.getLong("lastUpdated") ?: 0L,
                        remoteId = doc.id
                    )
                    db.callAttemptDao().insert(attempt)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error downloading call attempts", e)
        }
    }

    private suspend fun downloadAgendaItems() {
        if (!currentCoroutineContext().isActive) return
        val userRef = getUserRef() ?: return
        try {
            val lastSync = db.agendaItemDao().getMaxLastUpdated() ?: 0L
            val remote = userRef.collection("agenda_items")
                .whereGreaterThan("lastUpdated", lastSync)
                .get().await()
            
            if (remote.isEmpty) return
            
            db.withTransaction {
                for (doc in remote.documents) {
                    val item = AgendaItemEntity(
                        id = doc.id.toLong(),
                        type = AgendaItemType.valueOf(doc.getString("type") ?: AgendaItemType.PERSONAL_REMINDER.name),
                        contactId = doc.getLong("contactId") ?: -1L,
                        folderId = doc.getLong("folderId") ?: -1L,
                        scheduledAt = doc.getLong("scheduledAt") ?: 0L,
                        status = AgendaItemStatus.valueOf(doc.getString("status") ?: AgendaItemStatus.SCHEDULED.name),
                        reason = doc.getString("reason"),
                        createdFromCallId = doc.getString("createdFromCallId"),
                        syncState = SyncState.SYNCED.name,
                        lastUpdated = doc.getLong("lastUpdated") ?: 0L,
                        remoteId = doc.id
                    )
                    db.agendaItemDao().insert(item)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error downloading agenda items", e)
        }
    }
}
