package com.tuempresa.autodialer.domain

import android.content.Context
import android.util.Log
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.dialer.DialerEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Motor de decisiones para la automatización del CRM.
 * Orquesta los tiempos de espera, reintentos y cumplimiento de cuotas.
 * Cumple con RF-5: Tiempos aleatorios en rangos configurables.
 */
class DecisionEngine(private val context: Context) {
    private val app = context.applicationContext as App
    private val settings get() = app.settingsState.value

    private val _state = MutableStateFlow(AutomationState.IDLE)
    val state = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Sincronizar el bus de eventos global para que la UI reaccione
        _state.onEach { DialerEvents.updateAutomationState(it) }.launchIn(app.applicationScope)
    }

    /**
     * Determina si el ciclo automático debe continuar.
     */
    fun canContinue(): Boolean {
        if (settings.isVacationModeActive) {
            Log.i("DecisionEngine", "Ciclo detenido: Modo Vacaciones activo.")
            if (_state.value != AutomationState.PAUSED) pause()
            return false
        }
        return true
    }

    /**
     * Espera el tiempo de transición configurado (entre contactos distintos).
     * RF-5: Rango por defecto 6–12 segundos.
     */
    suspend fun waitTransition() = coroutineScope {
        if (!canContinue()) return@coroutineScope
        
        _state.value = AutomationState.WAITING_TRANSITION
        val min = settings.transitionDelayMin
        val max = settings.transitionDelayMax
        val delaySec = if (max > min) (min..max).random() else min
        
        Log.d("DecisionEngine", "Esperando transición (entre distintos): $delaySec s")
        
        runCountdown(this, delaySec)
        
        if (_state.value == AutomationState.WAITING_TRANSITION) {
            _state.value = AutomationState.PREPARING
        }
    }

    /**
     * Espera el tiempo de reintento configurado (mismo contacto, segundo intento del día).
     * RF-5: Rango por defecto 3–5 segundos.
     */
    suspend fun waitRedial() = coroutineScope {
        if (!canContinue()) return@coroutineScope

        _state.value = AutomationState.WAITING_RETRY
        val min = settings.redialDelayMin
        val max = settings.redialDelayMax
        val delaySec = if (max > min) (min..max).random() else min

        Log.d("DecisionEngine", "Esperando reintento (mismo contacto): $delaySec s")

        runCountdown(this, delaySec)

        if (_state.value == AutomationState.WAITING_RETRY) {
            _state.value = AutomationState.PREPARING
        }
    }

    private suspend fun runCountdown(scope: CoroutineScope, seconds: Int) {
        timerJob = scope.launch {
            for (i in seconds downTo 1) {
                DialerEvents.updateWaitCountdown(i)
                delay(1000L)
            }
            DialerEvents.updateWaitCountdown(0)
        }
        try {
            timerJob?.join()
        } finally {
            timerJob = null
            DialerEvents.updateWaitCountdown(0)
        }
    }

    fun onPreparing() {
        _state.value = AutomationState.PREPARING
    }

    fun onCallStarted() {
        _state.value = AutomationState.DIALING
    }

    fun onCallActive() {
        _state.value = AutomationState.CALL_ACTIVE
    }

    fun onCallEnded() {
        _state.value = AutomationState.WAITING_OUTCOME
    }

    fun pause() {
        _state.value = AutomationState.PAUSED
        timerJob?.cancel()
    }

    fun resume() {
        if (canContinue()) {
            _state.value = AutomationState.PREPARING
        }
    }

    fun stop() {
        _state.value = AutomationState.IDLE
        timerJob?.cancel()
    }
}
