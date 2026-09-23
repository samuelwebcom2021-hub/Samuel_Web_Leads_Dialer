package com.tuempresa.autodialer.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Recibe las acciones de los botones de la notificación de recordatorio.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP = "com.tuempresa.autodialer.ACTION_STOP_REMINDER"
        const val ACTION_SNOOZE = "com.tuempresa.autodialer.ACTION_SNOOZE_REMINDER"
        const val ACTION_CALL = "com.tuempresa.autodialer.ACTION_CALL_REMINDER"
        const val EXTRA_AGENDA_ID = "agenda_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val agendaId = intent.getLongExtra(EXTRA_AGENDA_ID, -1)
        if (agendaId == -1L) return

        when (intent.action) {
            ACTION_STOP -> {
                ReminderAlarmManager.stopReminder(context, agendaId)
                Toast.makeText(context, "Recordatorio finalizado", Toast.LENGTH_SHORT).show()
            }
            ACTION_SNOOZE -> {
                ReminderAlarmManager.snoozeReminder(context, agendaId, 10)
                Toast.makeText(context, "Pospuesto 10 minutos", Toast.LENGTH_SHORT).show()
            }
            ACTION_CALL -> {
                ReminderAlarmManager.stopReminder(context, agendaId)
                // Lógica para abrir la app y llamar (se integrará en la fase de UI/UX)
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                context.startActivity(launchIntent)
            }
        }
    }
}
