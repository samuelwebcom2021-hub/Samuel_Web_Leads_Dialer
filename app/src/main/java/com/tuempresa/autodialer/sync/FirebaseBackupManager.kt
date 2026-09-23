package com.tuempresa.autodialer.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import com.tuempresa.autodialer.data.*
import kotlinx.coroutines.tasks.await

/**
 * Respalda toda la información de la app en Firestore.
 * Estructura jerárquica: users/{userId}/folders/{folderId}/contacts/{contactId}
 */
class FirebaseBackupManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserDoc() = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid)
    }

    suspend fun backupFolder(folder: BatchEntity) {
        val userDoc = getUserDoc() ?: return
        val data = hashMapOf(
            "name" to folder.name,
            "isArchived" to folder.isArchived,
            "lastUpdated" to folder.lastUpdated
        )
        userDoc.collection("folders").document(folder.id.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun deleteFolder(folderId: Long) {
        val userDoc = getUserDoc() ?: return
        userDoc.collection("folders").document(folderId.toString()).delete().await()
    }

    suspend fun backupContact(contact: ContactEntity, extraFields: List<ContactExtraFieldEntity>) {
        val userDoc = getUserDoc() ?: return
        
        // Convertir extraFields a un mapa para el esquema remoto
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
            "lastUpdated" to contact.lastUpdated,
            "extraFields" to extraFieldsMap
        )

        userDoc.collection("folders").document(contact.importBatchId.toString())
            .collection("contacts").document(contact.id.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun deleteContact(folderId: Long, contactId: Long) {
        val userDoc = getUserDoc() ?: return
        userDoc.collection("folders").document(folderId.toString())
            .collection("contacts").document(contactId.toString())
            .delete().await()
    }

    suspend fun deleteAgendaItem(agendaId: Long) {
        val userDoc = getUserDoc() ?: return
        userDoc.collection("agenda_items").document(agendaId.toString()).delete().await()
    }

    suspend fun backupAgendaItems(items: List<AgendaItemEntity>) {
        val userDoc = getUserDoc() ?: return
        val batch = db.batch()
        for (item in items) {
            val data = hashMapOf(
                "type" to item.type.name,
                "contactId" to item.contactId,
                "folderId" to item.folderId,
                "scheduledAt" to item.scheduledAt,
                "status" to item.status.name,
                "reason" to item.reason,
                "createdFromCallId" to item.createdFromCallId,
                "operationId" to item.operationId,
                "lastUpdated" to item.lastUpdated
            )
            val ref = userDoc.collection("agenda_items").document(item.id.toString())
            batch.set(ref, data, SetOptions.merge())
        }
        batch.commit().await()
    }

    suspend fun backupSettings(settingsMap: Map<String, Any?>) {
        val userDoc = getUserDoc() ?: return
        userDoc.collection("config").document("settings")
            .set(settingsMap, SetOptions.merge()).await()
    }

    suspend fun backupCallAttempts(attempts: List<CallAttemptEntity>) {
        val userDoc = getUserDoc() ?: return
        val batch = db.batch()
        for (attempt in attempts) {
            val data = hashMapOf(
                "contactId" to attempt.contactId,
                "timestampMillis" to attempt.timestampMillis,
                "durationMillis" to attempt.durationMillis,
                "result" to attempt.result.name,
                "resultLabel" to attempt.resultLabel,
                "alternateNumber" to attempt.alternateNumber,
                "whatsappNumber" to attempt.whatsappNumber,
                "notes" to attempt.notes,
                "lastUpdated" to attempt.lastUpdated
            )
            val ref = userDoc.collection("call_attempts").document(attempt.id.toString())
            batch.set(ref, data, SetOptions.merge())
        }
        batch.commit().await()
    }
}
