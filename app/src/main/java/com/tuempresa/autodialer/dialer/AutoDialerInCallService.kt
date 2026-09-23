package com.tuempresa.autodialer.dialer

import android.app.NotificationManager
import android.app.PendingIntent
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
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.CallAttemptEntity
import com.tuempresa.autodialer.data.CallResult
import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.data.CallState
import com.tuempresa.autodialer.sync.SyncCoordinator
import com.tuempresa.autodialer.ui.call.CallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AutoDialerInCallService : InCallService() {

    private val CALL_NOTIFICATION_ID = 43
    private val handler = Handler(Looper.getMainLooper())
    private var hangupRunnable: Runnable? = null
    private var currentCall: Call? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val app get() = application as App
    private val settings get() = app.settingsState.value
    private val db by lazy { app.db }

    companion object {
        const val ACTION_HANGUP = "com.tuempresa.autodialer.action.NOTIF_HANGUP"
        const val ACTION_TOGGLE_SPEAKER = "com.tuempresa.autodialer.action.NOTIF_TOGGLE_SPEAKER"

        private val _activeInCallService = MutableStateFlow<AutoDialerInCallService?>(null)
        val activeInCallService = _activeInCallService.asStateFlow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANGUP -> {
                hangup()
            }
            ACTION_TOGGLE_SPEAKER -> {
                val currentAudio = _audioState.value
                val isSpeaker = currentAudio?.route == CallAudioState.ROUTE_SPEAKER
                setSpeaker(!isSpeaker)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        val details = call.details
        val number = details.handle?.schemeSpecificPart
        
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            call.state == Call.STATE_RINGING
        }
        
        val accountHandle = details.accountHandle
        val isAutomated = !isIncoming && (PendingAutoCallTracker.isAutomated(number) || 
                (accountHandle != null && 
                 accountHandle.componentName.flattenToString() == settings.workPhoneAccountComponent &&
                 accountHandle.id == settings.workPhoneAccountId))

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

        // Obtener el nombre del contacto para la notificación interactiva
        scope.launch {
            val session = db.callSessionDao().observeActiveSession().firstOrNull()
            val contact = if (session != null && session.contactId > 0) db.contactDao().getById(session.contactId) else null
            val contactName = contact?.businessName?.ifBlank { null } ?: number ?: "Contacto"
            showCallNotification(number ?: "", contactName)
        }
        
        try {
            val intent = Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                         Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                         Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("InCallService", "Error al lanzar CallActivity: ${e.message}")
        }

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                updateSessionState(state)
                
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
            if (session == null) return@launch
            
            val updated = session.copy(
                state = newState,
                answerTime = if (newState == CallState.ACTIVE) System.currentTimeMillis() else session.answerTime
            )
            db.callSessionDao().update(updated)
            DialerEvents.updateSession(updated)

            val contact = if (updated.contactId > 0) db.contactDao().getById(updated.contactId) else null
            val contactName = contact?.businessName?.ifBlank { null } ?: updated.dialedNumber
            showCallNotification(updated.dialedNumber, contactName)
        }
    }

    private fun handleCallDisconnected(call: Call) {
        val cause = call.details.disconnectCause
        val result = mapDisconnectCause(cause)
        
        scope.launch {
            val session = db.callSessionDao().observeActiveSession().firstOrNull() 
            if (session == null) return@launch
            
            val endTime = System.currentTimeMillis()
            
            val attempt = CallAttemptEntity(
                contactId = session.contactId,
                timestampMillis = session.startTime,
                durationMillis = if (session.answerTime != null) endTime - session.answerTime else 0,
                result = result,
                resultLabel = result.name,
                notes = session.notes,
                alternateNumber = session.alternateNumber,
                whatsappNumber = session.whatsappNumber
            )
            val attemptId = db.callAttemptDao().insert(attempt)
            SyncCoordinator(this@AutoDialerInCallService).syncCallAttemptUpdate(attemptId)

            val updated = session.copy(
                state = CallState.DISCONNECTED,
                result = result,
                endTime = endTime
            )
            db.callSessionDao().update(updated)
            DialerEvents.updateSession(updated)
        }
    }

    private fun mapDisconnectCause(cause: DisconnectCause?): CallResult {
        return when (cause?.code) {
            DisconnectCause.BUSY -> CallResult.BUSY
            DisconnectCause.REJECTED -> CallResult.REJECTED
            DisconnectCause.REMOTE -> CallResult.ANSWERED
            DisconnectCause.LOCAL -> CallResult.NO_ANSWER
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (enabled) CallEndpoint.TYPE_SPEAKER else CallEndpoint.TYPE_EARPIECE
            val target = _availableEndpoints.value.find { it.endpointType == type }
            val finalTarget = target ?: if (!enabled) _availableEndpoints.value.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE || it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET } else null

            if (finalTarget != null) {
                requestCallEndpointChange(finalTarget, { it.run() }, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                    override fun onResult(result: Void?) {}
                    override fun onError(error: android.telecom.CallEndpointException) {
                        @Suppress("DEPRECATION")
                        setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
                    }
                })
                return
            }
        }
        
        @Suppress("DEPRECATION")
        val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
    }

    fun hangup() {
        currentCall?.disconnect()
    }

    fun answer() {
        currentCall?.answer(0)
    }

    private fun showCallNotification(number: String, contactName: String = "Llamada en curso") {
        val appIntent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val pendingAppIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, AutoDialerInCallService::class.java).apply {
            action = ACTION_HANGUP
        }
        val pendingHangup = PendingIntent.getService(
            this, 1, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speakerIntent = Intent(this, AutoDialerInCallService::class.java).apply {
            action = ACTION_TOGGLE_SPEAKER
        }
        val pendingSpeaker = PendingIntent.getService(
            this, 2, speakerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isSpeaker = _audioState.value?.route == CallAudioState.ROUTE_SPEAKER

        val builder = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(contactName)
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingAppIntent)
            .setFullScreenIntent(pendingAppIntent, true)
            .addAction(R.drawable.ic_call_notification, "📞 COLGAR", pendingHangup)
            .addAction(R.drawable.ic_call_notification, if (isSpeaker) "🔈 AURICULAR" else "🔊 ALTAVOZ", pendingSpeaker)
            .setOngoing(true)
            .setAutoCancel(false)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CALL_NOTIFICATION_ID, builder.build())
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
