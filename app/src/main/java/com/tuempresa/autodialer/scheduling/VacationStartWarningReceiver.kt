package com.tuempresa.autodialer.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R

class VacationStartWarningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = NotificationCompat.Builder(context, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_scheduled)
            .setContentTitle("Aviso: Vacaciones en 10 min")
            .setContentText("Tu Modo Vacaciones se activará automáticamente en 10 minutos.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(2001, notification)
    }
}
