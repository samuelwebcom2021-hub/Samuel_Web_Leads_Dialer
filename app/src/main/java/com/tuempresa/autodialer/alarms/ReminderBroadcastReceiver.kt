package com.tuempresa.autodialer.alarms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.AppDatabase
import com.tuempresa.autodialer.data.AgendaItemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val agendaId = intent.getLongExtra("agenda_id", -1)
        if (agendaId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val agendaItem = db.agendaItemDao().getById(agendaId)
            if (agendaItem == null) return@launch

            // Marcar como disparado
            db.agendaItemDao().update(agendaItem.copy(status = AgendaItemStatus.TRIGGERED))

            val contact = db.contactDao().getById(agendaItem.contactId)
            
            // 1. Mostrar Notificación con acciones
            showNotification(context, agendaId, contact?.businessName ?: "Recordatorio", agendaItem.reason ?: "")

            // 2. Reproducir Tono (Centralizado)
            ReminderAlarmManager.startAlarmSound(context)
        }
    }

    private fun showNotification(context: Context, agendaId: Long, title: String, description: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Acción: DETENER
        val stopIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_STOP
            putExtra(ReminderActionReceiver.EXTRA_AGENDA_ID, agendaId)
        }
        val stopPending = PendingIntent.getBroadcast(context, agendaId.toInt() + 100, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Acción: SNOOZE
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderActionReceiver.EXTRA_AGENDA_ID, agendaId)
        }
        val snoozePending = PendingIntent.getBroadcast(context, agendaId.toInt() + 200, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Acción: LLAMAR
        val callIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_CALL
            putExtra(ReminderActionReceiver.EXTRA_AGENDA_ID, agendaId)
        }
        val callPending = PendingIntent.getBroadcast(context, agendaId.toInt() + 300, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // PendingIntent para Full Screen (Nueva Activity interactiva)
        val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
            putExtra("agenda_id", agendaId)
            putExtra("title", title)
            putExtra("reason", description)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPending = PendingIntent.getActivity(context, agendaId.toInt() + 400, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, App.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_scheduled)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(R.drawable.ic_delete, "DETENER", stopPending)
            .addAction(R.drawable.ic_tab_scheduled, "POSPONER 10 MIN", snoozePending)
            .addAction(R.drawable.ic_call_notification, "LLAMAR AHORA", callPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        manager.notify(agendaId.toInt(), notification)
    }
}
