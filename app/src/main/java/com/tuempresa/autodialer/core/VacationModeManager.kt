package com.tuempresa.autodialer.core

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.scheduling.RetryScheduler
import com.tuempresa.autodialer.scheduling.VacationResumeReceiver
import com.tuempresa.autodialer.scheduling.VacationResumeWarningReceiver
import com.tuempresa.autodialer.scheduling.VacationStartReceiver
import com.tuempresa.autodialer.scheduling.VacationStartWarningReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Gestor central para el "Modo Vacaciones".
 * Permite pausar y reanudar todos los procesos de automatización de la app.
 */
class VacationModeManager(private val context: Context) {

    private val app = context.applicationContext as App
    private val settings get() = app.settingsState.value
    private val settingsRepo = app.settingsRepository
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val syncCoordinator = com.tuempresa.autodialer.sync.SyncCoordinator(context)

    fun activate(startAt: Long = 0L, autoResumeAt: Long = 0L) {
        val now = System.currentTimeMillis()
        val tenMinutes = 10 * 60 * 1000L
        
        if (startAt > now) {
            // Modalidad: Pausa programada futura
            CoroutineScope(Dispatchers.IO).launch {
                settingsRepo.updateVacationMode(false, startAt, autoResumeAt)
                syncCoordinator.syncSettings()
            }
            scheduleActivation(startAt)
            
            if (startAt > now + tenMinutes) {
                scheduleWarning(startAt - tenMinutes, VacationStartWarningReceiver::class.java, WARNING_START_CODE)
            }
            return
        }

        // Modalidad: Activación inmediata
        cancelActivation()
        cancelWarning(WARNING_START_CODE, VacationStartWarningReceiver::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            settingsRepo.updateVacationMode(true, now, autoResumeAt)
            syncCoordinator.syncSettings()
            // Cancelar alarmas activas
            app.db.contactDao().getAllScheduledRetries().forEach { contact ->
                RetryScheduler.cancel(context, contact.id)
            }
        }

        if (autoResumeAt > now) {
            // Modalidad: Reanudación automática programada
            scheduleResume(autoResumeAt)
            if (autoResumeAt > now + tenMinutes) {
                scheduleWarning(autoResumeAt - tenMinutes, VacationResumeWarningReceiver::class.java, WARNING_RESUME_CODE)
            }
        } else {
            cancelResume()
            cancelWarning(WARNING_RESUME_CODE, VacationResumeWarningReceiver::class.java)
        }
    }

    fun deactivate() {
        val startMillis = settings.vacationStartMillis
        val now = System.currentTimeMillis()
        
        // Calcular duración real de la pausa en días (redondeado hacia arriba)
        val durationMillis = if (startMillis > 0) now - startMillis else 0
        val daysPaused = if (durationMillis > 0) {
            TimeUnit.MILLISECONDS.toDays(durationMillis).coerceAtLeast(1)
        } else 0
        
        cancelActivation()
        cancelResume()
        cancelWarning(WARNING_START_CODE, VacationStartWarningReceiver::class.java)
        cancelWarning(WARNING_RESUME_CODE, VacationResumeWarningReceiver::class.java)

        showResumeNotification()

        CoroutineScope(Dispatchers.IO).launch {
            settingsRepo.updateVacationMode(false, 0L, 0L)
            syncCoordinator.syncSettings()
            reprogramPendingCalls(daysPaused)
        }
    }

    private suspend fun reprogramPendingCalls(daysToShift: Long) {
        val contacts = app.db.contactDao().getAllScheduledRetries()
        contacts.forEach { contact ->
            val originalTime = contact.nextAttemptAt ?: return@forEach
            
            val calendar = Calendar.getInstance().apply {
                timeInMillis = originalTime
                // Desplazamos proporcionalmente a los días que estuvo en pausa
                add(Calendar.DAY_OF_YEAR, daysToShift.toInt())
                
                // Si la nueva fecha ya pasó, reprogramamos para mañana a la misma hora
                if (timeInMillis <= System.currentTimeMillis()) {
                    val targetHour = get(Calendar.HOUR_OF_DAY)
                    val targetMin = get(Calendar.MINUTE)
                    timeInMillis = System.currentTimeMillis()
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                }
            }
            
            val newTime = calendar.timeInMillis
            app.db.contactDao().update(contact.copy(nextAttemptAt = newTime))
            RetryScheduler.scheduleExactAlarm(context, contact.id, newTime)
        }
    }

    private fun scheduleActivation(timeMillis: Long) {
        val intent = Intent(context, VacationStartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, START_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
    }

    private fun cancelActivation() {
        val intent = Intent(context, VacationStartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, START_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleResume(timeMillis: Long) {
        val intent = Intent(context, VacationResumeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, RESUME_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
    }

    private fun cancelResume() {
        val intent = Intent(context, VacationResumeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, RESUME_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleWarning(timeMillis: Long, receiverClass: Class<*>, requestCode: Int) {
        val intent = Intent(context, receiverClass)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
    }

    private fun cancelWarning(requestCode: Int, receiverClass: Class<*>) {
        val intent = Intent(context, receiverClass)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun showResumeNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, com.tuempresa.autodialer.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_scheduled)
            .setContentTitle("Modo Vacaciones finalizado")
            .setContentText("Tu actividad ha sido reanudada. Tus reintentos han sido reprogramados proporcionalmente.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    companion object {
        private const val START_REQUEST_CODE = 998
        private const val RESUME_REQUEST_CODE = 999
        private const val WARNING_START_CODE = 996
        private const val WARNING_RESUME_CODE = 997
    }
}
