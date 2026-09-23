package com.tuempresa.autodialer.dialer

import android.os.SystemClock

/**
 * Rastreador para correlacionar llamadas salientes con la cola de automatización.
 * DialerService registra aquí el número justo antes de marcar; InCallService lo consulta.
 */
object PendingAutoCallTracker {
    private var pendingNumber: String? = null
    private var timestamp: Long = 0
    private const val WINDOW_MILLIS = 5000L // 5 segundos de ventana

    @Synchronized
    fun track(phoneNumber: String) {
        pendingNumber = normalize(phoneNumber)
        timestamp = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun isAutomated(phoneNumber: String?): Boolean {
        if (phoneNumber == null) return false
        val normalized = normalize(phoneNumber)
        val now = SystemClock.elapsedRealtime()
        return normalized == pendingNumber && (now - timestamp) < WINDOW_MILLIS
    }

    @Synchronized
    fun clear() {
        pendingNumber = null
        timestamp = 0
    }

    private fun normalize(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(10) // Ajustado a 10 dígitos (Colombia standard)
}
