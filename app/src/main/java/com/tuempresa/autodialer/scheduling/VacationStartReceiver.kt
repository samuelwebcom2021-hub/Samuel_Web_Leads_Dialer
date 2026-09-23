package com.tuempresa.autodialer.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tuempresa.autodialer.core.VacationModeManager
import android.util.Log

class VacationStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("VacationStart", "Activando Modo Vacaciones automáticamente (Inicio Programado)...")
        val manager = VacationModeManager(context)
        val app = context.applicationContext as com.tuempresa.autodialer.App
        manager.activate(0L, app.settingsState.value.vacationAutoResumeMillis)
        
        // Notificación de inicio de vacaciones
        showStartNotification(context)
    }

    private fun showStartNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = androidx.core.app.NotificationCompat.Builder(context, com.tuempresa.autodialer.App.CHANNEL_ID)
            .setSmallIcon(com.tuempresa.autodialer.R.drawable.ic_tab_scheduled)
            .setContentTitle("Modo Vacaciones activado")
            .setContentText("La pausa global ha comenzado automáticamente según lo programado.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(1002, notification)
    }
}
