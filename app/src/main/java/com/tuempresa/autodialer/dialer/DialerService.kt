package com.tuempresa.autodialer.dialer

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.CallOutcome
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.ContactStatus
import com.tuempresa.autodialer.data.markDirty
import com.tuempresa.autodialer.sync.SyncCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

import com.tuempresa.autodialer.domain.CallController
import com.tuempresa.autodialer.domain.DecisionEngine
import com.tuempresa.autodialer.domain.AutomationState
import kotlinx.coroutines.flow.filter

class DialerService : Service() {

    private lateinit var scope: CoroutineScope
    private var loopJob: Job? = null
    private var currentBatchId: Long = 0
    private var selectedPhoneAccount: PhoneAccountHandle? = null
    private var currentSessionId: Long = 0

    private val callController by lazy { CallController(this) }
    private val decisionEngine by lazy { DecisionEngine(this) }

    /** Si hay una llamada activa, aquí vive la señal para "sáltate esta" (null = no hay ninguna en curso). */
    private var currentSkipSignal: CompletableDeferred<Unit>? = null

    private val app get() = application as App
    private val dao by lazy { app.db.contactDao() }
    private val db by lazy { app.db }
    private val settingsRepository by lazy { app.settingsRepository }
    private val settings get() = app.settingsState.value
    private val outcomeResolver by lazy { CallOutcomeResolver(this) }
    private val syncCoordinator by lazy { SyncCoordinator(this) }

    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        // Este listener dejará de funcionar pronto, pero lo mantengo por ahora
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.IO + Job())
        observeSettings()
        observeSessionForEngine()
        getSharedPreferences("dialer_settings", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(settingsListener)
    }

    private fun observeSettings() {
        scope.launch {
            app.settingsState.collect {
                if (it.isVacationModeActive) {
                    decisionEngine.pause()
                }
            }
        }
    }

    private fun observeSessionForEngine() {
        scope.launch {
            DialerEvents.session.collect { session ->
                if (session?.isAutomated == true) {
                    when (session.state) {
                        com.tuempresa.autodialer.data.CallState.ACTIVE -> decisionEngine.onCallActive()
                        com.tuempresa.autodialer.data.CallState.DIALING, 
                        com.tuempresa.autodialer.data.CallState.RINGING -> decisionEngine.onCallStarted()
                        com.tuempresa.autodialer.data.CallState.DISCONNECTED -> decisionEngine.onCallEnded()
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                decisionEngine.pause()
                updateNotification("Pausado — toca Reanudar en la app")
                return START_STICKY
            }
            ACTION_RESUME -> {
                decisionEngine.resume()
                if (loopJob?.isActive != true) { applyStartExtras(intent); startLoop() }
                return START_STICKY
            }
            ACTION_SKIP -> {
                currentSkipSignal?.complete(Unit)
                return START_STICKY
            }
            ACTION_STOP -> {
                stopLoop()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                applyStartExtras(intent)
                startLoop()
            }
            else -> {
                // Si no hay acción, ignorar para evitar reinicios accidentales
            }
        }
        return START_STICKY
    }

    private fun applyStartExtras(intent: Intent?) {
        val batchId = intent?.getLongExtra(EXTRA_BATCH_ID, -1) ?: -1
        if (batchId > 0) currentBatchId = batchId

        selectedPhoneAccount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PHONE_ACCOUNT_HANDLE, PhoneAccountHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PHONE_ACCOUNT_HANDLE) as? PhoneAccountHandle
        } ?: restoreLastPhoneAccount()
    }

    /** 
     * Prioriza la SIM de trabajo configurada (FIXED). 
     * Si no hay una fija, usa la última cuenta guardada (la que eligió el usuario al iniciar).
     */
    private fun restoreLastPhoneAccount(): PhoneAccountHandle? {
        val handle = if (settings.simSelectionMode == "FIXED") {
            SimSelector.getHandleFromStrings(
                settings.workPhoneAccountComponent,
                settings.workPhoneAccountId
            )
        } else {
            SimSelector.getHandleFromStrings(
                settings.lastPhoneAccountComponent,
                settings.lastPhoneAccountId
            )
        }

        return if (handle != null && SimSelector.isHandleValid(this, handle)) {
            handle
        } else {
            null
        }
    }

    private fun startLoop() {
        // Llamar a startForeground INMEDIATAMENTE para evitar RemoteServiceException en Android 8+
        val notification = buildNotification("Preparando llamadas…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!decisionEngine.canContinue()) {
            scope.launch { DialerEvents.emit(DialerEvent.QueueFinished) }
            stopLoop()
            return
        }

        if (loopJob?.isActive == true) return

        loopJob = scope.launch {
            val sessionId = System.currentTimeMillis()
            currentSessionId = sessionId
            Log.d("DialerService", "Iniciando nueva sesión: $sessionId")
            
            // RN-08: Limpieza profunda al iniciar para evitar el bug de "vuelve a cero"
            withContext(Dispatchers.IO) {
                db.callSessionDao().clearAll()
                DialerEvents.updateSession(null)
                // Revertir cualquier contacto que haya quedado "En progreso" por un crash anterior
                dao.revertInProgressToPending(currentBatchId)
            }

            decisionEngine.resume()
            while (true) {
                awaitUnpaused()
                if (!decisionEngine.canContinue()) break

                if (checkDailyInterestedQuota()) continue // se acaba de pausar solo por el límite; vuelve a esperar arriba

                val now = System.currentTimeMillis()
                decisionEngine.onPreparing()
                val remaining = dao.countRemainingForBatch(currentBatchId, now)
                val contact = dao.nextInQueueForBatch(currentBatchId, now) ?: break

                // Bucle de intentos para el MISMO contacto (máximo 2 por día)
                var currentContact = contact
                while (true) {
                    DialerEvents.emit(
                        DialerEvent.Progress(
                            currentContact.id,
                            currentContact.phoneNumber,
                            currentContact.businessName,
                            currentContact.attemptCount + 1,
                            currentContact.retryRound,
                            remaining
                        )
                    )
                    updateNotification("Llamando a ${currentContact.phoneNumber} (quedan $remaining)")

                    decisionEngine.onCallStarted()
                    callContact(currentContact, sessionId)
                    decisionEngine.onCallEnded()

                    // RN-08: Verificar si la sesión sigue siendo válida antes de procesar el resultado
                    if (currentSessionId != sessionId) {
                        Log.d("DialerService", "RN-08: Sesión $sessionId invalidada. Deteniendo bucle de intentos.")
                        break
                    }

                    // Después de colgar, ver si hay que reintentar EL MISMO (segundo intento)
                    val updated = dao.getById(currentContact.id) ?: break
                    val isPending = updated.status == ContactStatus.PENDING.name
                    if (isPending && updated.attemptCount < settings.maxAttemptsPerContact) {
                        currentContact = updated
                        
                        // Pausa aleatoria para el reintento del mismo contacto
                        if (currentSessionId == sessionId) {
                            decisionEngine.waitRedial()
                        }
                        
                        continue // Vuelve a marcar el mismo
                    } else {
                        // Salta al siguiente contacto con pausa aleatoria entre Min y Max
                        if (currentSessionId == sessionId) {
                            decisionEngine.waitTransition()
                        }
                        break // Sale del bucle interno para buscar el siguiente en el DB
                    }
                }
            }
            if (currentSessionId == sessionId) {
                DialerEvents.emit(DialerEvent.QueueFinished)
            }
            stopLoop()
        }
    }

    /** Se queda aquí sin hacer nada mientras esté pausado — no interrumpe ninguna llamada, solo evita empezar la siguiente. */
    private suspend fun awaitUnpaused() = decisionEngine.state.first { it != AutomationState.PAUSED }

    /**
     * Revisa si ya se llegó al límite diario de "Interesados" (de TODAS las carpetas juntas).
     * Si se llegó, pausa la cola solita y avisa — pero solo UNA vez por día: si tú decides
     * "Reanudar" de todos modos, la app respeta tu decisión y no vuelve a interrumpirte hoy.
     * @return true si se acaba de pausar por esto (para que el loop vuelva a esperar).
     */
    private suspend fun checkDailyInterestedQuota(): Boolean {
        val maxPerDay = settings.maxInterestedPerDay
        if (maxPerDay <= 0) return false // 0 = sin límite

        val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (settings.lastQuotaPauseDate == todayKey) return false // ya se avisó hoy, no insistir de nuevo

        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val countToday = dao.countInterestedSince(startOfDay)
        if (countToday < maxPerDay) return false

        scope.launch { settingsRepository.updateLastQuotaPauseDate(todayKey) }
        decisionEngine.pause()
        updateNotification("Límite diario de interesados alcanzado — pausado hasta que reanudes")
        DialerEvents.emit(DialerEvent.DailyQuotaReached(countToday, maxPerDay))
        return true
    }

    private suspend fun callContact(contact: ContactEntity, sessionId: Long) {
        val newAttemptCount = contact.attemptCount + 1
        val inProgress = contact.copy(
            status = ContactStatus.IN_PROGRESS.name,
            attemptCount = newAttemptCount,
            lastAttemptAt = System.currentTimeMillis()
        ).markDirty()
        dao.update(inProgress)
        syncCoordinator.syncContactUpdate(inProgress.id)

        // 1. Colocar la llamada usando el nuevo controlador (Crea CallSession)
        val success = callController.placeCall(inProgress, selectedPhoneAccount, sessionId)
        if (!success) {
            Log.e("DialerService", "Fallo al colocar llamada para ${contact.phoneNumber}. Forzando limpieza de sesión.")
            withContext(NonCancellable) {
                db.callSessionDao().clearAll()
                DialerEvents.updateSession(null)
            }
            revertToPending(contact, "Error al colocar la llamada")
            delay(2000L) // Evitar bucle infinito ultra-rápido si Telecom falla
            return
        }

        // Ventana para "Saltar este contacto": una señal de una sola vez, exclusiva de ESTA llamada.
        val skipSignal = CompletableDeferred<Unit>()
        currentSkipSignal = skipSignal
        try {
            // 2. Esperar reactivamente a que la sesión termine o se salte
            val wasSkipped = waitForCallEndOrSkip(skipSignal)
            if (wasSkipped) {
                // RF-8: Saltar contacto -> Marcarlo como SKIPPED
                val skippedContact = inProgress.copy(status = ContactStatus.SKIPPED.name).markDirty()
                dao.update(skippedContact)
                syncCoordinator.syncContactUpdate(skippedContact.id)
                // Colgar la llamada físicamente si existe
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AutoDialerInCallService.activeInCallService.value?.hangup()
                }
            } else {
                // 3. La sesión terminó (DISCONNECTED), resolver resultado
                // RF-15: Verificar si fue colgado por timeout automático
                val lastSession = db.callSessionDao().observeActiveSession().first()
                val wasTimeout = lastSession?.notes == "TIMEOUT_AUTO"
                
                val outcome = outcomeResolver.resolve(contact.phoneNumber, settings.answeredThresholdSeconds)
                applyOutcome(inProgress, outcome, wasTimeout)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { revertToPending(contact, "Detenido a mitad de la llamada") }
            throw e
        } finally {
            currentSkipSignal = null
            withContext(NonCancellable) {
                DialerEvents.emit(DialerEvent.CallEnded)
                // Asegurar que no quede rastro de sesión si hubo un error
                db.callSessionDao().clearAll()
                DialerEvents.updateSession(null)
            }
        }
    }

    /** Espera a que la sesión en Room pase a DISCONNECTED/FAILED O señal de saltar. */
    private suspend fun waitForCallEndOrSkip(skipSignal: CompletableDeferred<Unit>): Boolean = coroutineScope {
        val sessionEndDeferred = async { 
            db.callSessionDao().observeActiveSession().first { 
                it == null || it.state == com.tuempresa.autodialer.data.CallState.DISCONNECTED || 
                it.state == com.tuempresa.autodialer.data.CallState.FAILED
            }
        }
        
        // RF-15: Timeout de seguridad de 30s por si Telecom nunca conecta ni falla (bug de sistema)
        val safetyTimeout = async {
            delay(30000)
            Log.w("DialerService", "Timeout de seguridad alcanzado (30s) esperando fin de llamada.")
            true
        }

        val skipped = select<Boolean> {
            sessionEndDeferred.onAwait { false }
            skipSignal.onAwait { true }
            safetyTimeout.onAwait { 
                // Si llegamos aquí, forzamos colgado
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AutoDialerInCallService.activeInCallService.value?.hangup()
                }
                false 
            }
        }
        sessionEndDeferred.cancel()
        safetyTimeout.cancel()
        skipped
    }

    private suspend fun revertToPending(contact: ContactEntity, reason: String) {
        val reverted = contact.copy(status = ContactStatus.PENDING.name).markDirty()
        dao.update(reverted)
        syncCoordinator.syncContactUpdate(reverted.id)
    }

    /**
     * Cuando el contacto necesita que TÚ decidas algo (interesado/no, u hora de mañana),
     * el servicio emite el evento y se queda esperando la resolución de ESE contacto antes
     * de seguir — así nunca avanza a llamar al siguiente mientras todavía estás decidiendo
     * sobre el anterior.
     */
    private suspend fun applyOutcome(contact: ContactEntity, outcome: CallOutcome, wasTimeout: Boolean = false) {
        // Buscamos la sesión persistida antes de que se limpie
        val lastSession = db.callSessionDao().observeActiveSession().first()
        val lastAltNumber = lastSession?.alternateNumber
        val lastWaNumber = lastSession?.whatsappNumber
        val lastNotes = lastSession?.notes

        if (!lastAltNumber.isNullOrBlank()) {
            DialerEvents.emit(DialerEvent.NeedsAltNumberChoice(contact.id, lastAltNumber, contact.businessName))
            DialerEvents.awaitResolution(contact.id)
        }

        val baseUpdatedContact = contact.copy(
            ownerPhone = lastAltNumber,
            whatsappNumber = lastWaNumber,
            notes = lastNotes
        )

        when {
            outcome == CallOutcome.ANSWERED -> {
                val updated = baseUpdatedContact.copy(status = ContactStatus.AWAITING_OUTCOME.name, lastOutcome = outcome.name).markDirty()
                dao.update(updated)
                syncCoordinator.syncContactUpdate(updated.id)
                DialerEvents.emit(DialerEvent.NeedsOutcomeChoice(contact.id, contact.phoneNumber, contact.businessName))
                DialerEvents.awaitResolution(contact.id)
            }
            wasTimeout && contact.attemptCount < settings.maxAttemptsPerContact -> {
                // RF-15: UNICO caso de reintento automático: Fue timeout y tiene intentos restantes
                val updated = baseUpdatedContact.copy(status = ContactStatus.PENDING.name, lastOutcome = "TIMEOUT_AUTO").markDirty()
                dao.update(updated)
                syncCoordinator.syncContactUpdate(updated.id)
                Log.d("DialerService", "Timeout automático. Reintentando contacto: ${contact.phoneNumber}")
            }
            else -> {
                // Si fue manual, o si ya agotó intentos
                if (contact.attemptCount < settings.maxAttemptsPerContact) {
                    // Fue manual (no timeout) pero tiene intentos. 
                    // No ponemos en PENDING. Pedimos resultado (pudiendo elegir "Reintentar luego")
                    val updated = baseUpdatedContact.copy(status = ContactStatus.AWAITING_OUTCOME.name, lastOutcome = outcome.name).markDirty()
                    dao.update(updated)
                    syncCoordinator.syncContactUpdate(updated.id)
                    DialerEvents.emit(DialerEvent.NeedsOutcomeChoice(contact.id, contact.phoneNumber, contact.businessName))
                    DialerEvents.awaitResolution(contact.id)
                } else {
                    // Agotó intentos por hoy
                    if (contact.retryRound >= settings.maxRetryDays) {
                        // RF-5: Tras el límite de días, se aplica un resultado final configurable
                        val finalStatus = settings.finalOutcomeStatus
                        val updated = baseUpdatedContact.copy(status = finalStatus, lastOutcome = outcome.name).markDirty()
                        dao.update(updated)
                        syncCoordinator.syncContactUpdate(updated.id)
                        DialerEvents.emit(DialerEvent.ContactDiscarded(contact.phoneNumber, contact.businessName))
                    } else {
                        if (settings.autoSchedulingEnabled) {
                            autoScheduleNextDay(baseUpdatedContact, outcome)
                        } else {
                            val updated = baseUpdatedContact.copy(status = ContactStatus.AWAITING_RETRY_TIME.name, lastOutcome = outcome.name).markDirty()
                            dao.update(updated)
                            syncCoordinator.syncContactUpdate(updated.id)
                            DialerEvents.emit(DialerEvent.NeedsRetryTimeChoice(contact.id, contact.phoneNumber))
                            DialerEvents.awaitResolution(contact.id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun autoScheduleNextDay(contact: ContactEntity, outcome: CallOutcome) {
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, settings.retryHour)
            set(java.util.Calendar.MINUTE, settings.retryMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val triggerAt = calendar.timeInMillis
        val nextRound = contact.retryRound + 1
        val updated = contact.copy(
            status = ContactStatus.SCHEDULED_RETRY.name,
            attemptCount = 0,
            retryRound = nextRound,
            nextAttemptAt = triggerAt,
            lastOutcome = outcome.name
        ).markDirty()
        dao.update(updated)
        syncCoordinator.syncContactUpdate(updated.id)
        
        // RF-6: Crear ítem en Agenda para el reintento de carpeta con operationId
        val opId = "retry_${contact.id}_${triggerAt}"
        val agendaItem = com.tuempresa.autodialer.data.AgendaItemEntity(
            type = com.tuempresa.autodialer.data.AgendaItemType.FOLDER_RETRY,
            contactId = contact.id,
            folderId = contact.importBatchId,
            scheduledAt = triggerAt,
            reason = "Sin respuesta (Intento ${contact.attemptCount})",
            operationId = opId
        )
        db.agendaItemDao().insert(agendaItem)
        
        com.tuempresa.autodialer.scheduling.RetryScheduler.scheduleExactAlarm(this, contact.id, triggerAt)
    }

    private fun stopLoop() {
        Log.d("DialerService", "[RN-08] Iniciando limpieza determinista de sesión: $currentSessionId")
        
        // 1. Invalida el sessionId para que cualquier tarea asíncrona en vuelo se descarte
        val sessionIdToStop = currentSessionId
        currentSessionId = 0 
        
        // 2. Cancelar el job principal de la cola
        loopJob?.cancel()
        loopJob = null
        
        // 3. Detener el motor de decisiones (cancela esperas y reintentos)
        decisionEngine.stop()
        
        // 4. Interrumpir la llamada física si existe vía InCallService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val service = AutoDialerInCallService.activeInCallService.value
            if (service != null) {
                Log.d("DialerService", "RN-08: Desconectando llamada activa")
                service.hangup()
            }
        }
        
        // 5. Limpiar rastreador de llamadas automáticas
        PendingAutoCallTracker.clear()
        
        // 6. Limpiar sesión en base de datos de forma segura
        scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                db.callSessionDao().clearAll()
                DialerEvents.updateSession(null)
            }
        }

        Log.d("DialerService", "[RN-08] Limpieza completada para sesión: $sessionIdToStop. Estado: IDLE")
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("Marcador automático activo")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        getSharedPreferences("dialer_settings", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(settingsListener)
        loopJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.tuempresa.autodialer.action.START"
        const val ACTION_STOP = "com.tuempresa.autodialer.action.STOP"
        const val ACTION_PAUSE = "com.tuempresa.autodialer.action.PAUSE"
        const val ACTION_RESUME = "com.tuempresa.autodialer.action.RESUME"
        const val ACTION_SKIP = "com.tuempresa.autodialer.action.SKIP"
        const val EXTRA_BATCH_ID = "extra_batch_id"
        const val EXTRA_PHONE_ACCOUNT_HANDLE = "extra_phone_account_handle"
        private const val NOTIFICATION_ID = 42
    }
}
