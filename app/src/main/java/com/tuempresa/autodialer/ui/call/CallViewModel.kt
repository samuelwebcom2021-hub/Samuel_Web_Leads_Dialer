package com.tuempresa.autodialer.ui.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.markDirty
import com.tuempresa.autodialer.dialer.DialerEvent
import com.tuempresa.autodialer.dialer.DialerEvents
import com.tuempresa.autodialer.domain.AutomationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CallViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as App).db
    private val contactDao = db.contactDao()

    val session = DialerEvents.session
    val automationState = DialerEvents.automationState

    private val _progress = MutableStateFlow<DialerEvent.Progress?>(null)
    val progress = _progress.asStateFlow()

    init {
        DialerEvents.events.filterIsInstance<DialerEvent.Progress>()
            .onEach { _progress.value = it }
            .launchIn(viewModelScope)

        session.onEach { session ->
            if (session != null) {
                _alternateNumber.value = session.alternateNumber ?: ""
                _whatsappNumber.value = session.whatsappNumber ?: ""
                _notes.value = session.notes ?: ""
            }
        }.launchIn(viewModelScope)
    }

    val activeContact = session.flatMapLatest { session ->
        if (session != null && session.contactId > 0) {
            contactDao.observeById(session.contactId)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // RF-5: Captura de datos en tiempo real
    private val _alternateNumber = MutableStateFlow("")
    val alternateNumber = _alternateNumber.asStateFlow()

    private val _whatsappNumber = MutableStateFlow("")
    val whatsappNumber = _whatsappNumber.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes = _notes.asStateFlow()

    fun updateCapturedData(alt: String, whatsapp: String, note: String) {
        _alternateNumber.value = alt
        _whatsappNumber.value = whatsapp
        _notes.value = note
    }

    fun flushCapturedData() {
        val currentSession = session.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = currentSession.copy(
                alternateNumber = _alternateNumber.value,
                whatsappNumber = _whatsappNumber.value,
                notes = _notes.value
            )
            db.callSessionDao().update(updated)
            DialerEvents.updateSession(updated)
        }
    }

    val nextContact = session.flatMapLatest { session ->
        flowOf(null as ContactEntity?)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folderName = session.map { it?.folderId }.flatMapLatest { folderId ->
        if (folderId != null && folderId > 0) {
            db.batchDao().observeAll().map { batches ->
                batches.find { it.id == folderId }?.name ?: "Campañas"
            }
        } else {
            flowOf("Llamada Manual")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Campaña")

    enum class PostCallState { OPTIONS, FOLLOW_UP }
    private val _postCallState = MutableStateFlow(PostCallState.OPTIONS)
    val postCallState = _postCallState.asStateFlow()

    fun resolveOutcome(result: com.tuempresa.autodialer.data.CallResult) {
        val currentSession = session.value ?: return
        if (currentSession.contactId <= 0) {
            DialerEvents.updateSession(null)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Guardar el resultado en Room antes de emitir resolución
            val contact = contactDao.getById(currentSession.contactId)
            if (contact != null) {
                // RF-10: Si hay whatsapp, se marca INTERESTED automáticamente (esto se llama desde CallScreen)
                val newStatus = when(result) {
                    com.tuempresa.autodialer.data.CallResult.INTERESTED -> com.tuempresa.autodialer.data.ContactStatus.INTERESTED
                    com.tuempresa.autodialer.data.CallResult.NOT_INTERESTED -> com.tuempresa.autodialer.data.ContactStatus.NOT_INTERESTED
                    com.tuempresa.autodialer.data.CallResult.WRONG_NUMBER -> com.tuempresa.autodialer.data.ContactStatus.WRONG_NUMBER
                    else -> com.tuempresa.autodialer.data.ContactStatus.PENDING
                }
                contactDao.update(contact.copy(
                    status = newStatus.name, 
                    lastOutcome = result.name,
                    ownerPhone = currentSession.alternateNumber,
                    whatsappNumber = currentSession.whatsappNumber,
                    notes = currentSession.notes
                ).markDirty())
            }
            
            if (result == com.tuempresa.autodialer.data.CallResult.INTERESTED) {
                _postCallState.value = PostCallState.FOLLOW_UP
            } else {
                finishSession()
            }
        }
    }

    fun finishSession() {
        val currentSession = session.value ?: return
        viewModelScope.launch {
            DialerEvents.emitResolution(currentSession.contactId)
            DialerEvents.updateSession(null)
        }
    }

    fun scheduleFollowUp(calendar: java.util.Calendar, type: String) {
        val currentSession = session.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val contact = contactDao.getById(currentSession.contactId) ?: return@launch
            
            // RF-11.2: Crear recordatorio personal con operationId para evitar duplicados
            val operationId = "followup_${contact.id}_${calendar.timeInMillis}"
            
            val agendaItem = com.tuempresa.autodialer.data.AgendaItemEntity(
                type = com.tuempresa.autodialer.data.AgendaItemType.PERSONAL_REMINDER,
                contactId = contact.id,
                folderId = contact.importBatchId,
                scheduledAt = calendar.timeInMillis,
                reason = "Seguimiento: $type",
                operationId = operationId
            )
            
            // Usar insert con REPLACE (definido en DAO) para asegurar idempotencia
            val agendaId = db.agendaItemDao().insert(agendaItem)
            
            // Programar alarma real
            com.tuempresa.autodialer.scheduling.AgendaScheduler.schedule(getApplication(), agendaId, calendar.timeInMillis)
            
            finishSession()
        }
    }

    fun callOwnerNow() {
        val currentSession = session.value ?: return
        val number = currentSession.alternateNumber ?: return
        // Implementación de llamada directa al dueño (puede disparar una nueva sesión manual)
        // Por ahora, solo guardamos como interesado y cerramos.
        resolveOutcome(com.tuempresa.autodialer.data.CallResult.INTERESTED)
    }
}
