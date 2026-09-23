package com.tuempresa.autodialer.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tuempresa.autodialer.core.VacationModeManager
import android.util.Log

class VacationResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("VacationResume", "Desactivando Modo Vacaciones automáticamente...")
        VacationModeManager(context).deactivate()
    }
}
