package com.tuempresa.autodialer.scheduling

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.telecom.PhoneAccountHandle
import androidx.core.content.ContextCompat
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.dialer.DialerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RetryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val settings = app.settingsState.value
        if (settings.isVacationModeActive) return

        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contact = if (contactId > 0) app.db.contactDao().getById(contactId) else null
                val startIntent = Intent(context, DialerService::class.java).apply {
                    action = DialerService.ACTION_START
                    if (contact != null) putExtra(DialerService.EXTRA_BATCH_ID, contact.importBatchId)

                    // No hay nadie presente para elegir SIM ahora mismo: se usa la última que
                    // el usuario eligió manualmente (ver DialerSettings.lastPhoneAccountComponent).
                    val component = settings.lastPhoneAccountComponent
                    val id = settings.lastPhoneAccountId
                    if (component != null && id != null) {
                        ComponentName.unflattenFromString(component)?.let { componentName ->
                            val handle = try { PhoneAccountHandle(componentName, id) } catch (e: Exception) { null }
                            handle?.let { putExtra(DialerService.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
                        }
                    }
                }
                ContextCompat.startForegroundService(context, startIntent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
    }
}
