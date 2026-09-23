package com.tuempresa.autodialer.dialer

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Espera hasta que la llamada en curso termine (OFFHOOK/RINGING -> vuelve a IDLE). */
class CallStateMonitor(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    suspend fun awaitCallEnd() = suspendCancellableCoroutine<Unit> { cont ->
        var wasConnectedOrRinging = false

        val onStateChanged: (Int) -> Unit = { state ->
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                    wasConnectedOrRinging = true
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (wasConnectedOrRinging && cont.isActive) cont.resume(Unit)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onStateChanged(state)
            }
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            cont.invokeOnCancellation { telephonyManager.unregisterTelephonyCallback(callback) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Necesario para minSdk 26")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onStateChanged(state)
            }
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            cont.invokeOnCancellation {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
}
