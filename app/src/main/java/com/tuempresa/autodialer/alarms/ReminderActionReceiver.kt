package com.tuempresa.autodialer.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.tuempresa.autodialer.data.AppDatabase
import com.tuempresa.autodialer.domain.CallController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                
                // Ejecutar la llamada directa al contacto de la agenda y desplegar CallActivity
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val agendaItem = db.agendaItemDao().getById(agendaId)
                        if (agendaItem != null) {
                            val contact = db.contactDao().getById(agendaItem.contactId)
                            if (contact != null) {
                                val callController = CallController(context)
                                callController.placeCall(contact, null)
                            } else {
                                launchAppFallback(context)
                            }
                        } else {
                            launchAppFallback(context)
                        }
                    } catch (e: Exception) {
                        launchAppFallback(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun launchAppFallback(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(launchIntent)
        }
    }
}
