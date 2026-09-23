package com.tuempresa.autodialer

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.tuempresa.autodialer.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class App : Application() {

    val db by lazy { AppDatabase.getInstance(this) }
    val settingsRepository by lazy { com.tuempresa.autodialer.data.repository.SettingsRepository(this) }
    val applicationScope = CoroutineScope(SupervisorJob())

    val settingsState: StateFlow<com.tuempresa.autodialer.data.repository.DialerSettingsData> by lazy {
        settingsRepository.settingsFlow.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            com.tuempresa.autodialer.data.repository.DialerSettingsData(
                maxAttemptsPerContact = 2,
                maxRetryDays = 3,
                maxInterestedPerDay = 0,
                answeredThresholdSeconds = 12,
                autoHangupSeconds = 20,
                retryHour = 9,
                retryMinute = 0,
                redialDelayMin = 3,
                redialDelayMax = 6,
                transitionDelayMin = 8,
                transitionDelayMax = 12,
                workPhoneAccountComponent = null,
                workPhoneAccountId = null,
                lastPhoneAccountComponent = null,
                lastPhoneAccountId = null,
                simSelectionMode = "ASK_ALWAYS",
                autoSchedulingEnabled = true,
                soundAlertOnAnswer = true,
                firebaseBackupEnabled = true,
                isVacationModeActive = false,
                vacationStartMillis = 0L,
                vacationAutoResumeMillis = 0L,
                googleAccountEmail = null,
                hasSeenOnboarding = false,
                finalOutcomeStatus = "FINAL_NO_ANSWER",
                lastQuotaPauseDate = null
            )
        )
    }

    override fun onCreate() {
        super.onCreate()
        setupStax()
        setupLogging()
        createNotificationChannels()
    }

    private fun setupLogging() {
        System.setProperty("log4j2.disable.jmx", "true")
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF")
    }

    private fun setupStax() {
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // Canal de Marcador Automático (Bajo impacto / silencioso)
        val dialerChannel = NotificationChannel(
            CHANNEL_ID,
            "Marcador automático",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progreso de las llamadas automáticas en curso"
        }
        manager.createNotificationChannel(dialerChannel)

        // Canal de Alarmas de Seguimiento (Alta Prioridad con Sonido / Banner Flotante / Vibración)
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarmas y Recordatorios de Seguimiento",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas sonoras y notificaciones flotantes de recordatorios"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(alarmChannel)
    }

    companion object {
        const val CHANNEL_ID = "dialer_channel"
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }
}
