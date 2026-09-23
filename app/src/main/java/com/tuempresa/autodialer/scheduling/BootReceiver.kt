package com.tuempresa.autodialer.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.AgendaItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && 
            action != Intent.ACTION_TIME_CHANGED && 
            action != Intent.ACTION_TIMEZONE_CHANGED) return
            
        Log.d("BootReceiver", "Sistema reiniciado o cambio de hora detectado: $action. Reprogramando alarmas...")
        
        val app = context.applicationContext as App
        if (app.settingsState.value.isVacationModeActive) {
            Log.i("BootReceiver", "Modo Vacaciones activo. Saltando reprogramación de reintentos automáticos.")
            // Pero los recordatorios personales SI deberían sonar (son comerciales).
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = app.db
            val now = System.currentTimeMillis()
            
            // 1. Reprogramar reintentos automáticos de carpeta (RetryScheduler)
            if (!app.settingsState.value.isVacationModeActive) {
                db.contactDao().getAllScheduledRetries().forEach { contact ->
                    val triggerAt = contact.nextAttemptAt ?: return@forEach
                    if (triggerAt > now) {
                        RetryScheduler.scheduleExactAlarm(context, contact.id, triggerAt)
                    }
                }
            }
            
            // 2. Reprogramar recordatorios de la Agenda (AgendaScheduler)
            db.agendaItemDao().getPendingReminders(now).forEach { item ->
                if (item.type == AgendaItemType.PERSONAL_REMINDER) {
                    AgendaScheduler.schedule(context, item.id, item.scheduledAt)
                } else if (item.type == AgendaItemType.FOLDER_RETRY && !app.settingsState.value.isVacationModeActive) {
                    // Si por algún motivo usamos AgendaScheduler para reintentos, lo cubrimos aquí también
                    // Aunque actualmente el motor usa RetryScheduler directamente.
                    RetryScheduler.scheduleExactAlarm(context, item.contactId, item.scheduledAt)
                }
            }
        }
    }
}
