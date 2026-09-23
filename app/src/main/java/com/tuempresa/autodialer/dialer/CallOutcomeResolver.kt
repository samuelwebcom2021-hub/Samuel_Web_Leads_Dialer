package com.tuempresa.autodialer.dialer

import android.content.Context
import android.provider.CallLog
import com.tuempresa.autodialer.data.CallOutcome

/**
 * LIMITACIÓN TÉCNICA: Android no distingue de forma confiable entre "contestó una persona"
 * y "cayó al buzón de voz" — ambos se ven como llamada conectada. Se usa una aproximación
 * por duración: por debajo del umbral, se trata como no-contestada.
 */
class CallOutcomeResolver(private val context: Context) {

    fun resolve(phoneNumber: String, answeredThresholdSeconds: Int): CallOutcome {
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.DATE)
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC"
        ) ?: return CallOutcome.UNKNOWN

        val targetDigits = phoneNumber.filter { it.isDigit() }.takeLast(10)

        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: continue
                val numberDigits = number.filter { c -> c.isDigit() }.takeLast(10)
                val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                if (numberDigits.isNotEmpty() && numberDigits == targetDigits && type == CallLog.Calls.OUTGOING_TYPE) {
                    val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                    return if (duration >= answeredThresholdSeconds) CallOutcome.ANSWERED
                    else CallOutcome.NO_ANSWER_OR_VOICEMAIL
                }
            }
        }
        return CallOutcome.UNKNOWN
    }
}
