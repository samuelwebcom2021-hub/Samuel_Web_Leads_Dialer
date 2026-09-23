package com.tuempresa.autodialer.alarms

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.data.AgendaItemStatus
import com.tuempresa.autodialer.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gestor centralizado para el ciclo de vida de las alarmas sonoras.
 * Resuelve el bug RF-7 (sonido que no se detiene).
 */
object ReminderAlarmManager {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Inicia la reproducción del tono de alarma. */
    fun startAlarmSound(context: Context, ringtoneUri: String? = null) {
        stopAlarmSound() // Asegurar que no haya dos sonando
        try {
            val uri = ringtoneUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("ReminderAlarmManager", "Error al iniciar sonido: ${e.message}")
        }
    }

    /** Detiene físicamente el sonido. */
    fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    /** 
     * Operación central: Detiene el recordatorio por completo.
     * Silencia el audio, quita la notificación y marca como COMPLETADO.
     */
    fun stopReminder(context: Context, agendaId: Long) {
        stopAlarmSound()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(agendaId.toInt())

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val item = db.agendaItemDao().getById(agendaId)
            if (item != null) {
                db.agendaItemDao().update(item.copy(status = AgendaItemStatus.COMPLETED))
            }
        }
    }

    /**
     * RF-7: Pospone el recordatorio X minutos.
     * Silencia el audio actual y reprograma una nueva alarma.
     */
    fun snoozeReminder(context: Context, agendaId: Long, minutes: Int = 10) {
        stopAlarmSound()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(agendaId.toInt())

        val snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000L)

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val item = db.agendaItemDao().getById(agendaId)
            if (item != null) {
                db.agendaItemDao().update(item.copy(
                    status = AgendaItemStatus.SNOOZED,
                    scheduledAt = snoozeTime
                ))
                
                // Reprogramar en el sistema (usando lógica existente o similar)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    putExtra("agenda_id", agendaId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, agendaId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                }
            }
        }
    }
}
