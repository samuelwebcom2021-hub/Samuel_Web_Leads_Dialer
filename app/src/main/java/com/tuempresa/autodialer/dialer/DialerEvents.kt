package com.tuempresa.autodialer.dialer

import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.domain.AutomationState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

sealed class DialerEvent {
    data class Progress(
        val contactId: Long,
        val currentPhone: String?,
        val businessName: String,
        val attempt: Int,
        val retryRound: Int,
        val remaining: Int
    ) : DialerEvent()
    data class NeedsOutcomeChoice(val contactId: Long, val phoneNumber: String, val businessName: String) : DialerEvent()
    data class NeedsAltNumberChoice(val contactId: Long, val altNumber: String, val businessName: String) : DialerEvent()
    data class NeedsRetryTimeChoice(val contactId: Long, val phoneNumber: String) : DialerEvent()
    data class ContactDiscarded(val phoneNumber: String, val businessName: String) : DialerEvent()
    data class DailyQuotaReached(val countToday: Int, val maxPerDay: Int) : DialerEvent()
    object CallEnded : DialerEvent()
    object QueueFinished : DialerEvent()
}

/**
 * Bus de eventos entre el servicio y la UI.
 * A partir de la Fase 2, [session] es la Fuente Única de Verdad (SotT).
 */
object DialerEvents {
    private val _session = MutableStateFlow<CallSessionEntity?>(null)
    val session = _session.asStateFlow()

    private val _automationState = MutableStateFlow(AutomationState.IDLE)
    val automationState = _automationState.asStateFlow()

    private val _remainingWaitSeconds = MutableStateFlow(0)
    val remainingWaitSeconds = _remainingWaitSeconds.asStateFlow()

    /** Actualiza la sesión activa. La UI reaccionará automáticamente. */
    fun updateSession(session: CallSessionEntity?) {
        _session.value = session
    }

    /** Actualiza el estado de la automatización. */
    fun updateAutomationState(state: AutomationState) {
        _automationState.value = state
    }

    /** Actualiza el contador de espera para la UI. */
    fun updateWaitCountdown(seconds: Int) {
        _remainingWaitSeconds.value = seconds
    }

    private val _events = MutableSharedFlow<DialerEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    // Usamos StateFlow para resoluciones para evitar Race Conditions (Bug carga infinita)
    private val _lastResolution = MutableStateFlow<Long?>(null)

    suspend fun emit(event: DialerEvent) = _events.emit(event)

    /** Lo llama la UI/ViewModel cuando el usuario ya decidió algo para [contactId]. */
    suspend fun emitResolution(contactId: Long) {
        _lastResolution.value = contactId
    }

    /** Lo usa el servicio: se suspende hasta que llegue la resolución de ESTE contacto puntual. */
    suspend fun awaitResolution(contactId: Long) {
        // Si ya se emitió (replay), .first lo captura.
        _lastResolution.first { it == contactId }
        // Limpiamos para la siguiente llamada
        _lastResolution.value = null
    }
}
