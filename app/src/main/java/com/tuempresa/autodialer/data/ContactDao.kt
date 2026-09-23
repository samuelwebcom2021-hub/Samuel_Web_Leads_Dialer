package com.tuempresa.autodialer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class BatchSummary(
    val importBatchId: Long,
    val importBatchName: String,
    val total: Int,
    val interested: Int,
    val notInterested: Int,
    val pending: Int
)

data class PhoneBatchRow(val phoneNumber: String, val importBatchName: String)

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtraFields(fields: List<ContactExtraFieldEntity>)

    @Query("DELETE FROM contact_extra_fields WHERE contactId = :contactId")
    suspend fun deleteExtraFieldsForContact(contactId: Long)

    @Query("SELECT * FROM contact_extra_fields WHERE contactId = :contactId")
    suspend fun getExtraFields(contactId: Long): List<ContactExtraFieldEntity>

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY id ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<ContactEntity>

    @Query(
        """
        SELECT c.importBatchId, c.importBatchName, COUNT(c.id) as total,
               SUM(CASE WHEN c.status = 'INTERESTED' THEN 1 ELSE 0 END) as interested,
               SUM(CASE WHEN c.status = 'NOT_INTERESTED' THEN 1 ELSE 0 END) as notInterested,
               SUM(CASE WHEN c.status = 'PENDING' OR c.status = 'SCHEDULED_RETRY' THEN 1 ELSE 0 END) as pending
        FROM contacts c
        GROUP BY c.importBatchId
        ORDER BY c.importBatchId DESC
        """
    )
    fun observeBatches(): Flow<List<BatchSummary>>



    @Query("UPDATE contacts SET status = 'PENDING' WHERE importBatchId = :batchId AND status = 'IN_PROGRESS'")
    suspend fun revertInProgressToPending(batchId: Long)

    @Query("DELETE FROM contacts WHERE importBatchId = :batchId")
    suspend fun deleteBatch(batchId: Long)

    /** Siguiente contacto a llamar DENTRO de un lote/carpeta específico. Los "número alterno"
     *  (priority > 0, capturados en vivo durante una llamada) se llaman ANTES que el resto.
     *  Después de esos, los que YA tienen hora programada (día 2/3) se llaman antes que los
     *  pendientes nuevos del Excel — así lo pediste. */
    @Query(
        """
        SELECT * FROM contacts
        WHERE importBatchId = :batchId
          AND (status = 'PENDING' OR (status = 'SCHEDULED_RETRY' AND nextAttemptAt <= :nowMillis))
        ORDER BY priority DESC,
                 CASE WHEN status = 'SCHEDULED_RETRY' THEN 0 ELSE 1 END ASC,
                 id ASC
        LIMIT 1
        """
    )
    suspend fun nextInQueueForBatch(batchId: Long, nowMillis: Long): ContactEntity?

    @Query(
        """
        SELECT COUNT(*) FROM contacts
        WHERE importBatchId = :batchId
          AND (status = 'PENDING' OR (status = 'SCHEDULED_RETRY' AND nextAttemptAt <= :nowMillis))
        """
    )
    suspend fun countRemainingForBatch(batchId: Long, nowMillis: Long): Int

    /** Cuántos contactos (de TODAS las carpetas) llevan marcados "Interesado" desde cierta hora — para el límite diario. */
    @Query("SELECT COUNT(*) FROM contacts WHERE status = 'INTERESTED' AND lastAttemptAt >= :startOfDayMillis")
    suspend fun countInterestedSince(startOfDayMillis: Long): Int

    @Query("SELECT * FROM contacts WHERE status = 'AWAITING_RETRY_TIME' ORDER BY id ASC LIMIT 1")
    suspend fun nextAwaitingRetryChoice(): ContactEntity?

    @Query("SELECT * FROM contacts WHERE status = 'AWAITING_OUTCOME' ORDER BY id ASC LIMIT 1")
    suspend fun nextAwaitingOutcomeChoice(): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeById(id: Long): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE status = 'SCHEDULED_RETRY'")
    suspend fun getAllScheduledRetries(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE status IN ('AWAITING_RETRY_TIME', 'SCHEDULED_RETRY') ORDER BY nextAttemptAt ASC")
    fun observeAllScheduled(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE status IN ('AWAITING_RETRY_TIME', 'SCHEDULED_RETRY') AND retryRound = 2 ORDER BY nextAttemptAt ASC")
    fun observeAllRound2(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE status IN ('AWAITING_RETRY_TIME', 'SCHEDULED_RETRY') AND retryRound >= :finalRound ORDER BY nextAttemptAt ASC")
    fun observeAllFinalRound(finalRound: Int): Flow<List<ContactEntity>>



    /** Cuántos contactos de esta carpeta TODAVÍA necesitan alguna acción (para saber si ya se completó). */
    @Query(
        """
        SELECT COUNT(*) FROM contacts
        WHERE importBatchId = :batchId
          AND status IN ('PENDING','SCHEDULED_RETRY','AWAITING_RETRY_TIME','AWAITING_OUTCOME','IN_PROGRESS')
        """
    )
    suspend fun countActionableInBatch(batchId: Long): Int

    @Query("SELECT * FROM contacts WHERE importBatchId = :batchId")
    suspend fun getContactsInBatch(batchId: Long): List<ContactEntity>

    /** Para la pestaña "Interesados", dentro de una carpeta. */
    @Query("SELECT * FROM contacts WHERE importBatchId = :batchId AND status = 'INTERESTED' ORDER BY id ASC")
    fun observeInterestedByBatch(batchId: Long): Flow<List<ContactEntity>>

    /** Para la pestaña "Programados", dentro de una carpeta (esperando hora o ya con hora puesta). */
    @Query(
        """
        SELECT * FROM contacts
        WHERE importBatchId = :batchId AND status IN ('AWAITING_RETRY_TIME', 'SCHEDULED_RETRY')
        ORDER BY nextAttemptAt ASC
        """
    )
    fun observeScheduledByBatch(batchId: Long): Flow<List<ContactEntity>>

    /** Solo los que están en su 2do intento (ronda 2) — esperando hora o ya programados, dentro de una carpeta. */
    @Query(
        """
        SELECT * FROM contacts
        WHERE importBatchId = :batchId AND status IN ('AWAITING_RETRY_TIME', 'SCHEDULED_RETRY') AND retryRound = 2
        ORDER BY nextAttemptAt ASC
        """
    )
    fun observeRound2ByBatch(batchId: Long): Flow<List<ContactEntity>>

    /** El último intento posible (el "definitivo"): si vuelve a fallar, se descarta solo. El número exacto de días es configurable en Ajustes (maxRetryDays). */
    @Query(
        """
        SELECT * FROM contacts
        WHERE importBatchId = :batchId AND status IN ('AWAITING_RETRY_TIME', 'SCHEDULED_RETRY') AND retryRound >= :finalRound
        ORDER BY nextAttemptAt ASC
        """
    )
    fun observeFinalRoundByBatch(batchId: Long, finalRound: Int): Flow<List<ContactEntity>>

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Para detectar duplicados al importar: qué teléfonos ya existen y en qué carpeta. */
    @Query("SELECT phoneNumber, importBatchName FROM contacts")
    suspend fun getAllPhoneAndBatch(): List<PhoneBatchRow>

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    // --- Sincronización (Fase 8) ---

    @Query("SELECT * FROM contacts WHERE syncState != 'SYNCED'")
    suspend fun getPendingSync(): List<ContactEntity>

    @Query("UPDATE contacts SET syncState = :state, remoteId = :remoteId, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateSyncMetadata(id: Long, state: String, remoteId: String?, lastUpdated: Long)

    @Query("UPDATE contacts SET syncState = 'DELETED_TOMBSTONE', lastUpdated = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM contacts WHERE syncState = 'DELETED_TOMBSTONE'")
    suspend fun purgeDeleted()

    @Query("SELECT MAX(lastUpdated) FROM contacts WHERE importBatchId = :folderId")
    suspend fun getMaxLastUpdatedForFolder(folderId: Long): Long?
}
