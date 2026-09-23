package com.tuempresa.autodialer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.ContactStatus
import com.tuempresa.autodialer.dialer.DialerEvents
import androidx.room.withTransaction
import com.tuempresa.autodialer.data.local.parser.CsvParser
import com.tuempresa.autodialer.data.local.parser.ExcelParser
import com.tuempresa.autodialer.data.local.parser.FileParser
import com.tuempresa.autodialer.data.local.parser.RawFileData
import com.tuempresa.autodialer.data.mapping.ColumnDetector
import com.tuempresa.autodialer.data.mapping.ContactMapper
import com.tuempresa.autodialer.data.markDirty
import com.tuempresa.autodialer.scheduling.RetryScheduler
import com.tuempresa.autodialer.sync.SyncCoordinator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DuplicateInfo(val phoneNumber: String, val businessName: String, val existingBatchName: String)

data class RowError(val rowIndex: Int, val businessName: String?, val rawPhone: String?, val reason: String)

data class ImportAnalysis(
    val candidates: List<ContactEntity>,
    val duplicates: List<DuplicateInfo>,
    val errors: List<RowError>,
    val totalRows: Int,
    val countryMatchCount: Int = 0,
    val countryAssumedCount: Int = 0
)

class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as App).db
    private val dao = db.contactDao()
    private val syncCoordinator = SyncCoordinator(app)
    private val syncRepo = com.tuempresa.autodialer.data.repository.SyncRepository(app)
    private val settingsRepo = (app as App).settingsRepository

    val settingsState = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.tuempresa.autodialer.data.repository.DialerSettingsData(
            maxAttemptsPerContact = 2,
            maxRetryDays = 3,
            maxInterestedPerDay = 0,
            answeredThresholdSeconds = 12,
            autoHangupSeconds = 20,
            retryHour = 9,
            retryMinute = 0,
            redialDelayMin = 3,
            redialDelayMax = 6,
            transitionDelayMin = 8,
            transitionDelayMax = 12,
            workPhoneAccountComponent = null,
            workPhoneAccountId = null,
            lastPhoneAccountComponent = null,
            lastPhoneAccountId = null,
            simSelectionMode = "ASK_ALWAYS",
            autoSchedulingEnabled = true,
            soundAlertOnAnswer = true,
            firebaseBackupEnabled = true,
            isVacationModeActive = false,
            vacationStartMillis = 0L,
            vacationAutoResumeMillis = 0L,
            googleAccountEmail = null,
            hasSeenOnboarding = false,
            finalOutcomeStatus = "FINAL_NO_ANSWER",
            lastQuotaPauseDate = null
        )
    )
    val settings get() = settingsState.value

    private val _currentUser = MutableStateFlow(FirebaseAuth.getInstance().currentUser)
    val currentUser = _currentUser.asStateFlow()

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
            val email = auth.currentUser?.email
            if (settings.googleAccountEmail != email) {
                viewModelScope.launch { settingsRepo.updateGoogleAccountEmail(email) }
            }
        }
    }

    val syncPendingCount = syncRepo.observePendingCount().asLiveData()
    val lastSyncTime = db.syncLogDao().observeRecent().asLiveData()

    fun triggerSyncNow() {
        syncRepo.triggerSyncNow()
    }

    fun getParser(uri: Uri): FileParser {
        val type = getApplication<App>().contentResolver.getType(uri)
        val name = uri.toString()
        return if (type == "text/csv" || type == "text/comma-separated-values" || name.endsWith(".csv", true)) {
            CsvParser(getApplication())
        } else {
            ExcelParser(getApplication())
        }
    }

    suspend fun peekFile(uri: Uri) = withContext(Dispatchers.IO) {
        getParser(uri).peek(uri)
    }

    fun detectColumns(headers: List<String>) = ColumnDetector.detect(headers)

    fun syncSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            syncCoordinator.syncContactUpdate(0) // Logic for settings sync placeholder
            // In a real scenario, we would enqueue a "SETTINGS" op
            db.syncDao().insert(com.tuempresa.autodialer.data.PendingSyncOperation(
                entityType = "SETTINGS",
                entityId = "current",
                operation = "UPDATE",
                dataJson = ""
            ))
            com.tuempresa.autodialer.data.repository.SyncRepository(getApplication()).triggerSyncWorker()
        }
    }
    fun interestedInBatch(batchId: Long) = dao.observeInterestedByBatch(batchId).asLiveData()
    fun scheduledInBatch(batchId: Long) = dao.observeScheduledByBatch(batchId).asLiveData()
    fun round2InBatch(batchId: Long) = dao.observeRound2ByBatch(batchId).asLiveData()
    fun finalRoundInBatch(batchId: Long) = dao.observeFinalRoundByBatch(batchId, settings.maxRetryDays).asLiveData()

    val allScheduled = dao.observeAllScheduled().asLiveData()
    val allRound2 = dao.observeAllRound2().asLiveData()
    val allFinalRound = dao.observeAllFinalRound(settings.maxRetryDays).asLiveData()

    val contacts = dao.observeAll().asLiveData()
    val batches = dao.observeBatches().asLiveData()
    val syncLogs = db.syncLogDao().observeRecent().asLiveData()

    fun updateMaxAttempts(value: Int) = viewModelScope.launch { settingsRepo.updateMaxAttempts(value) }
    fun updateMaxRetryDays(value: Int) = viewModelScope.launch { settingsRepo.updateMaxRetryDays(value) }
    fun updateAutoHangupSeconds(value: Int) = viewModelScope.launch { settingsRepo.updateAutoHangupSeconds(value) }
    fun updateRedialDelay(min: Int, max: Int) = viewModelScope.launch { settingsRepo.updateRedialDelay(min, max) }
    fun updateTransitionDelay(min: Int, max: Int) = viewModelScope.launch { settingsRepo.updateTransitionDelay(min, max) }
    fun updateRetryTime(hour: Int, minute: Int) = viewModelScope.launch { settingsRepo.updateRetryTime(hour, minute) }
    fun updateSoundAlertOnAnswer(enabled: Boolean) = viewModelScope.launch { settingsRepo.updateSoundAlertOnAnswer(enabled) }
    fun updateVacationMode(active: Boolean) = viewModelScope.launch { settingsRepo.updateVacationMode(active) }
    fun updateWorkSim(component: String?, id: String?) = viewModelScope.launch { settingsRepo.updateWorkSim(component, id) }
    fun updateSimSelectionMode(mode: String) = viewModelScope.launch { settingsRepo.updateSimSelectionMode(mode) }
    fun updateLastPhoneAccount(component: String?, id: String?) = viewModelScope.launch { settingsRepo.updateLastPhoneAccount(component, id) }
    fun updateFinalOutcomeStatus(status: String) = viewModelScope.launch { settingsRepo.updateFinalOutcomeStatus(status) }
    fun updateLastQuotaPauseDate(date: String?) = viewModelScope.launch { settingsRepo.updateLastQuotaPauseDate(date) }

    val dailyStats = db.callAttemptDao().observeAllDailyStats().asLiveData()

    // RF-6: Agenda unificada
    val folderRetries = db.agendaItemDao().observeByTypeWithContact(com.tuempresa.autodialer.data.AgendaItemType.FOLDER_RETRY).asLiveData()
    val personalReminders = db.agendaItemDao().observeByTypeWithContact(com.tuempresa.autodialer.data.AgendaItemType.PERSONAL_REMINDER).asLiveData()
    
    fun getMonthlyStats(year: String) = db.callAttemptDao().observeMonthlyStats(year).asLiveData()


    suspend fun analyzeFile(
        uri: Uri,
        countryPrefix: String,
        phoneIndex: Int,
        nameIndex: Int,
        webIndex: Int,
        ratingIndex: Int,
        reviewsIndex: Int
    ): ImportAnalysis = withContext(Dispatchers.IO) {
        val rawData = getParser(uri).parseAll(uri)
        val candidates = mutableListOf<ContactEntity>()
        val errors = mutableListOf<RowError>()
        var matchCount = 0
        var assumedCount = 0
        
        rawData.rows.forEachIndexed { rowIndex, row ->
            val rawPhone = row.getOrNull(phoneIndex).orEmpty().trim()
            val hadPrefix = rawPhone.startsWith("+") || rawPhone.startsWith(countryPrefix.trimStart('+'))
            
            when (val result = ContactMapper.map(
                rawData.headers, row, phoneIndex, nameIndex, webIndex, ratingIndex, reviewsIndex, countryPrefix
            )) {
                is ContactMapper.MapResult.Success -> {
                    candidates.add(result.contact)
                    if (hadPrefix) matchCount++ else assumedCount++
                }
                is ContactMapper.MapResult.Error -> {
                    val businessName = if (nameIndex >= 0) row.getOrNull(nameIndex) else null
                    errors.add(RowError(rowIndex + 2, businessName, result.rawValue, result.reason))
                }
            }
        }

        val existing = dao.getAll().associateBy { it.phoneNumber }
        val duplicates = candidates.mapNotNull { c ->
            existing[c.phoneNumber]?.let { DuplicateInfo(c.phoneNumber, c.businessName, it.importBatchName) }
        }

        // Eliminar automáticamente los duplicados de los candidatos a importar
        val uniqueCandidates = candidates.filter { !existing.containsKey(it.phoneNumber) }

        ImportAnalysis(uniqueCandidates, duplicates, errors, rawData.rows.size, matchCount, assumedCount)
    }

    suspend fun commitImport(
        fileName: String,
        uri: Uri,
        phoneIndex: Int, nameIndex: Int, webIndex: Int, ratingIndex: Int, reviewsIndex: Int,
        countryPrefix: String
    ): Long = withContext(Dispatchers.IO) {
        val batchId = System.currentTimeMillis()
        val batch = com.tuempresa.autodialer.data.BatchEntity(id = batchId, name = fileName)
        
        val rawData = getParser(uri).parseAll(uri)
        
        performCommit(batch, rawData, phoneIndex, nameIndex, webIndex, ratingIndex, reviewsIndex, countryPrefix)
    }

    private suspend fun performCommit(
        batch: com.tuempresa.autodialer.data.BatchEntity,
        rawData: RawFileData,
        phoneIndex: Int, nameIndex: Int, webIndex: Int, ratingIndex: Int, reviewsIndex: Int,
        countryPrefix: String
    ): Long = withContext(Dispatchers.IO) {
        val insertedContacts = mutableListOf<ContactEntity>()
        val existingPhones = dao.getAll().map { it.phoneNumber }.toSet()
        db.withTransaction {
            db.batchDao().insert(batch)
            rawData.rows.forEach { row ->
                val result = ContactMapper.map(
                    rawData.headers, row, phoneIndex, nameIndex, webIndex, ratingIndex, reviewsIndex, countryPrefix
                )
                if (result is ContactMapper.MapResult.Success) {
                    if (existingPhones.contains(result.contact.phoneNumber)) {
                        // Saltar duplicado automáticamente
                        return@forEach
                    }
                    val contact = result.contact.copy(importBatchId = batch.id, importBatchName = batch.name)
                    val contactId = dao.insertAll(listOf(contact)).first()
                    val savedContact = contact.copy(id = contactId)
                    insertedContacts.add(savedContact)
                    
                    val fields = result.extraFields.map { it.copy(contactId = contactId) }
                    dao.insertExtraFields(fields)
                }
            }
        }
        
        // Encolar sincronización con Firebase (Paso 9)
        syncCoordinator.syncNewBatch(batch, insertedContacts)
        
        batch.id
    }

    fun deleteBatch(batchId: Long) {
        viewModelScope.launch(Dispatchers.IO) { 
            dao.deleteBatch(batchId)
            // Soft delete para sync
            db.batchDao().softDelete(batchId, System.currentTimeMillis())
            syncCoordinator.syncNewBatch(com.tuempresa.autodialer.data.BatchEntity(id=batchId, name=""), emptyList())
        }
    }

    suspend fun getContactById(id: Long): ContactEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    fun setPriority(contactId: Long, priority: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = dao.getById(contactId) ?: return@launch
            dao.update(contact.copy(priority = priority).markDirty())
        }
    }

    /**
     * El usuario dice si el contacto quedó interesado o no. Si es "no interesado", ya queda
     * resuelto de una. Si es "interesado", se espera a que se programe un recordatorio 
     * manual para desbloquear la cola.
     */
    fun resolveOutcome(contactId: Long, interested: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = dao.getById(contactId) ?: return@launch
            val newStatus = if (interested) ContactStatus.INTERESTED else ContactStatus.NOT_INTERESTED
            val updated = contact.copy(status = newStatus.name).markDirty()
            dao.update(updated)
            
            // Encolar sincronización (Paso 9)
            syncCoordinator.syncContactUpdate(contactId)
            
            // RF-6 (Ajuste): No crear recordatorio automático. 
            // La UI (MainActivity) disparará el selector si es "Interesado".
            
            if (!interested) DialerEvents.emitResolution(contactId)
        }
    }

    /** Cancelaste el recordatorio — igual hay que desbloquear la cola para que siga. */
    fun skipCalendarReminder(contactId: Long) {
        viewModelScope.launch(Dispatchers.IO) { DialerEvents.emitResolution(contactId) }
    }

    fun scheduleRetryForTomorrow(contactId: Long, hourOfDay: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = dao.getById(contactId) ?: return@launch
            val calendar = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val triggerAt = calendar.timeInMillis
            val updated = contact.copy(
                status = ContactStatus.SCHEDULED_RETRY.name, attemptCount = 0,
                retryRound = contact.retryRound + 1, nextAttemptAt = triggerAt
            ).markDirty()
            dao.update(updated)
            syncCoordinator.syncContactUpdate(contactId)
            
            // RF-6: Crear ítem en Agenda para el reintento programado manualmente
            val agendaItem = com.tuempresa.autodialer.data.AgendaItemEntity(
                type = com.tuempresa.autodialer.data.AgendaItemType.FOLDER_RETRY,
                contactId = contactId,
                folderId = contact.importBatchId,
                scheduledAt = triggerAt,
                reason = "Reintento manual programado"
            )
            db.agendaItemDao().insert(agendaItem)
            
            RetryScheduler.scheduleExactAlarm(getApplication(), contactId, triggerAt)
            DialerEvents.emitResolution(contactId)
        }
    }

    /**
     * Editar una hora ya programada desde la pestaña "Programados". Si el contacto todavía
     * estaba esperando que eligieras hora (AWAITING_RETRY_TIME), esto además desbloquea la
     * cola, igual que [scheduleRetryForTomorrow]. Si ya tenía hora puesta, solo la reemplaza.
     */
    fun editScheduledTime(contactId: Long, hourOfDay: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = dao.getById(contactId) ?: return@launch
            val wasAwaitingChoice = contact.status == ContactStatus.AWAITING_RETRY_TIME.name

            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val triggerAt = calendar.timeInMillis
            val newRound = if (wasAwaitingChoice) contact.retryRound + 1 else contact.retryRound
            val updated = contact.copy(
                status = ContactStatus.SCHEDULED_RETRY.name,
                attemptCount = if (wasAwaitingChoice) 0 else contact.attemptCount,
                retryRound = newRound,
                nextAttemptAt = triggerAt
            ).markDirty()
            dao.update(updated)
            syncCoordinator.syncContactUpdate(contactId)
            RetryScheduler.scheduleExactAlarm(getApplication(), contactId, triggerAt) // reemplaza la alarma anterior (mismo ID)
            if (wasAwaitingChoice) DialerEvents.emitResolution(contactId)
        }
    }

    /** Para la ficha detallada de un contacto: todos los intentos registrados, en orden. */
    suspend fun attemptHistory(contactId: Long) = withContext(Dispatchers.IO) {
        db.callAttemptDao().getForContact(contactId)
    }

    /**
     * Número alterno del dueño/encargado, escrito en vivo durante la llamada. Se agrega como
     * un contacto nuevo en la MISMA carpeta.
     * @param immediate Si es true, se le pone prioridad 1 para llamarlo justo después.
     * @return el ID del contacto nuevo.
     */
    suspend fun saveAlternateNumber(originalContactId: Long, rawPhone: String, immediate: Boolean = false): Long? = withContext(Dispatchers.IO) {
        val original = dao.getById(originalContactId) ?: return@withContext null
        val normalized = normalizeLivePhone(rawPhone, original.phoneNumber) ?: return@withContext null
        val alternate = ContactEntity(
            phoneNumber = normalized,
            businessName = "${original.businessName} (alterno)".trim(),
            importBatchId = original.importBatchId,
            importBatchName = original.importBatchName,
            priority = if (immediate) 1 else 0
        )
        val ids = dao.insertAll(listOf(alternate))
        val withId = alternate.copy(id = ids.first())
        syncCoordinator.syncContactUpdate(withId.id)
        withId.id
    }

    /** Te equivocaste o ya no hace falta — lo quita de la cola antes de que se llegue a marcar. */
    fun cancelAlternateNumber(contactId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(contactId)
            // Marcar como eliminado para sync si fuera necesario, 
            // pero los alternos son volátiles usualmente.
        }
    }

    /** Número de WhatsApp que dio el cliente en vivo — se guarda para seguimiento. */
    fun saveWhatsappNumber(contactId: Long, rawPhone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = dao.getById(contactId) ?: return@launch
            val normalized = normalizeLivePhone(rawPhone, contact.phoneNumber) ?: return@launch
            val updated = contact.copy(whatsappNumber = normalized).markDirty()
            dao.update(updated)
            syncCoordinator.syncContactUpdate(contactId)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
            db.batchDao().deleteAll()
            db.reminderDao().deleteAll()
            db.callAttemptDao().deleteAll()
            db.callSessionDao().clearAll()
            // Reset sync state
            db.syncLogDao().clear()
        }
    }

    private fun normalizeLivePhone(raw: String, referencePhone: String): String? {
        val cleaned = raw.trim().replace(Regex("[\\s()\\-.]"), "")
        if (cleaned.isBlank()) return null
        if (cleaned.startsWith("+")) return cleaned
        val prefix = Regex("^\\+\\d{1,3}").find(referencePhone)?.value ?: ""
        return prefix + cleaned.trimStart('0')
    }

}
