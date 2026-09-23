package com.tuempresa.autodialer.dialer

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.CallAttemptEntity
import com.tuempresa.autodialer.data.CallResult
import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.data.CallState
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import com.tuempresa.autodialer.sync.SyncCoordinator
import com.tuempresa.autodialer.ui.call.CallActivity
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.tuempresa.autodialer.R

@RequiresApi(Build.VERSION_CODES.Q)
class AutoDialerInCallService : InCallService() {

    private val CALL_NOTIFICATION_ID = 43
    private val handler = Handler(Looper.getMainLooper())
    private var hangupRunnable: Runnable? = null
    private var currentCall: Call? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val app get() = application as App
    private val settings get() = app.settingsState.value
    private val db by lazy { app.db }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        val details = call.details
        val number = details.handle?.schemeSpecificPart
        
        val isIncoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        
        // Determinar si es una llamada gestionada por nuestra app
        val accountHandle = details.accountHandle
        
        // Es automatizada si está en el rastreador O si usa la SIM de trabajo configurada
        val isAutomated = !isIncoming && (PendingAutoCallTracker.isAutomated(number) || 
                (accountHandle != null && 
                 accountHandle.componentName.flattenToString() == settings.workPhoneAccountComponent &&
                 accountHandle.id == settings.workPhoneAccountId))

        // No arrancar el timer aquí. Se hará en onStateChanged (RF-15)
        if (isIncoming) {
            scope.launch {
                val sessionExists = db.callSessionDao().observeActiveSession().firstOrNull() != null
                if (!sessionExists) {
                    val callId = "inc_${System.currentTimeMillis()}"
                    val session = CallSessionEntity(
                        callId = callId,
                        folderId = -1,
                        contactId = -1,
                        dialedNumber = number ?: "",
                        sim = accountHandle?.id ?: "Default",
                        state = CallState.RINGING,
                        isIncoming = true,
                        isAutomated = false
                    )
                    db.callSessionDao().insert(session)
                    DialerEvents.updateSession(session)
                }
            }
        }



        // Lanzar CallActivity unificada con alta prioridad
        showCallNotification(number ?: "Contacto")
        
        val intent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                     Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                updateSessionState(state)
                
                // RF-15: El temporizador arranca cuando la llamada está sonando o marcando
                if (isAutomated && (state == Call.STATE_DIALING || state == Call.STATE_RINGING)) {
                    if (hangupRunnable == null) {
                        startHangupTimer(call)
                    }
                }
                
                if (state == Call.STATE_ACTIVE) {
                    cancelHangupTimer()
                    if (isAutomated && settings.soundAlertOnAnswer) {
                        playAnswerAlert()
                    }
                } else if (state == Call.STATE_DISCONNECTED) {
                    call.unregisterCallback(this)
                    handleCallDisconnected(call)
                    if (currentCall == call) currentCall = null
                    cancelHangupTimer()
                    cancelCallNotification()
                }
            }
        })
        
        // Primera actualización al añadir la llamada
        updateSessionState(call.state)
    }

    private fun updateSessionState(androidState: Int) {
        val newState = when (androidState) {
            Call.STATE_CONNECTING, Call.STATE_DIALING -> CallState.DIALING
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_ACTIVE -> CallState.ACTIVE
            Call.STATE_HOLDING -> CallState.HOLDING
            Call.STATE_DISCONNECTING -> CallState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallState.DISCONNECTED
            else -> return
        }

        scope.launch {
            val session = db.callSessionDao().observeActiveSession().firstOrNull() 
            if (session == null) {
                Log.d("InCallService", "updateSessionState: No hay sesión activa en Room para actualizar.")
                return@launch
            }
            
            val updated = session.copy(
                state = newState,
                answerTime = if (newState == CallState.ACTIVE) System.currentTimeMillis() else session.answerTime
            )
            db.callSessionDao().update(updated)
            DialerEvents.updateSession(updated)
        }
    }

    private fun handleCallDisconnected(call: Call) {
        val cause = call.details.disconnectCause
        val result = mapDisconnectCause(cause)
        
        scope.launch {
            val session = db.callSessionDao().observeActiveSession().firstOrNull() 
            if (session == null) {
                Log.d("InCallService", "handleCallDisconnected: No hay sesión activa en Room para finalizar.")
                return@launch
            }
            
            val endTime = System.currentTimeMillis()
            
            // 1. Crear el histórico (CallAttempt)
            val attempt = CallAttemptEntity(
                contactId = session.contactId,
                timestampMillis = session.startTime,
                durationMillis = if (session.answerTime != null) endTime - session.answerTime else 0,
                result = result,
                resultLabel = result.name, // Se refinará en post-call UI
                notes = session.notes,
                alternateNumber = session.alternateNumber,
                whatsappNumber = session.whatsappNumber
            )
            val attemptId = db.callAttemptDao().insert(attempt)
            SyncCoordinator(this@AutoDialerInCallService).syncCallAttemptUpdate(attemptId)

            // 2. Actualizar sesión a DISCONNECTED (Post-Call) en lugar de borrarla
            val updated = session.copy(
                state = CallState.DISCONNECTED,
                result = result,
                endTime = endTime
            )
            db.callSessionDao().update(updated)
            DialerEvents.updateSession(updated)
            
            Log.d("InCallService", "Llamada finalizada: $result. Transición a Post-Call.")
        }
    }

    private fun mapDisconnectCause(cause: DisconnectCause?): CallResult {
        return when (cause?.code) {
            DisconnectCause.BUSY -> CallResult.BUSY
            DisconnectCause.REJECTED -> CallResult.REJECTED
            DisconnectCause.REMOTE -> CallResult.ANSWERED // Generalmente significa que el otro colgó tras hablar
            DisconnectCause.LOCAL -> CallResult.NO_ANSWER // Colgado por el usuario/robot antes de contestar
            DisconnectCause.MISSED -> CallResult.NO_ANSWER
            DisconnectCause.ERROR -> CallResult.FAILED
            else -> CallResult.NO_ANSWER
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (currentCall == call) {
            currentCall = null
            cancelHangupTimer()
        }
    }

    private fun startHangupTimer(call: Call) {
        cancelHangupTimer()
        val timeout = settings.autoHangupSeconds * 1000L
        hangupRunnable = Runnable {
            @Suppress("DEPRECATION")
            val state = call.state
            if (state != Call.STATE_ACTIVE && state != Call.STATE_DISCONNECTED) {
                Log.i("InCallService", "Timeout de 20s alcanzado en estado CONECTANDO. Colgando automáticamente.")
                // RF-15: Marcar en la sesión que fue por TIMEOUT_AUTO para el motor
                scope.launch {
                    val session = db.callSessionDao().observeActiveSession().firstOrNull()
                    if (session != null) {
                        db.callSessionDao().update(session.copy(notes = "TIMEOUT_AUTO"))
                    }
                    call.disconnect()
                }
            }
        }
        handler.postDelayed(hangupRunnable!!, timeout)
    }

    private fun cancelHangupTimer() {
        hangupRunnable?.let { handler.removeCallbacks(it) }
        hangupRunnable = null
    }

    companion object {
        private val _activeInCallService = MutableStateFlow<AutoDialerInCallService?>(null)
        val activeInCallService = _activeInCallService.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        _activeInCallService.value = this
    }

    override fun onDestroy() {
        _activeInCallService.value = null
        super.onDestroy()
    }

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState = _audioState.asStateFlow()

    private val _availableEndpoints = MutableStateFlow<List<CallEndpoint>>(emptyList())
    val availableEndpoints = _availableEndpoints.asStateFlow()

    private val _currentEndpoint = MutableStateFlow<CallEndpoint?>(null)
    val currentEndpoint = _currentEndpoint.asStateFlow()

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        _audioState.value = audioState
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(endpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(endpoints)
        _availableEndpoints.value = endpoints
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(endpoint: CallEndpoint) {
        super.onCallEndpointChanged(endpoint)
        _currentEndpoint.value = endpoint
    }

    fun setMute(muted: Boolean) {
        setMuted(muted)
    }

    fun setSpeaker(enabled: Boolean) {
        Log.d("InCallService", "Solicitando altavoz: $enabled")
        
        // RF-08: Asegurar que el cambio sea bidireccional y confiable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (enabled) CallEndpoint.TYPE_SPEAKER else CallEndpoint.TYPE_EARPIECE
            val target = _availableEndpoints.value.find { it.endpointType == type }
            
            // Si el earpiece no está disponible (ej: tablet), buscar el endpoint de comunicación por defecto
            val finalTarget = target ?: if (!enabled) _availableEndpoints.value.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE || it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET } else null

            if (finalTarget != null) {
                requestCallEndpointChange(finalTarget, { it.run() }, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                    override fun onResult(result: Void?) { Log.d("InCallService", "Cambiado exitosamente a tipo: ${finalTarget.endpointType}") }
                    override fun onError(error: android.telecom.CallEndpointException) { 
                        Log.e("InCallService", "Error al cambiar audio vía Endpoint", error)
                        @Suppress("DEPRECATION")
                        setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
                    }
                })
                return
            }
        }
        
        // Fallback para versiones anteriores o si falló el endpoint
        @Suppress("DEPRECATION")
        val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
    }

    fun switchEndpoint(endpoint: CallEndpoint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestCallEndpointChange(endpoint, { it.run() }, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                override fun onResult(result: Void?) {}
                override fun onError(error: android.telecom.CallEndpointException) {}
            })
        }
    }

    fun hangup() {
        currentCall?.disconnect()
    }

    fun answer() {
        currentCall?.answer(0)
    }

    fun isCurrentCallIncoming(): Boolean {
        return currentCall?.state == Call.STATE_RINGING
    }

    private fun getCallNumber(): String? {
        return currentCall?.details?.handle?.schemeSpecificPart
    }

    private fun showCallNotification(number: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle("Llamada en curso")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CALL_NOTIFICATION_ID, notification)
    }

    private fun cancelCallNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(CALL_NOTIFICATION_ID)
    }

    private fun playAnswerAlert() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (audioManager.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone.play()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }

            handler.postDelayed({ if (ringtone.isPlaying) ringtone.stop() }, 2000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
