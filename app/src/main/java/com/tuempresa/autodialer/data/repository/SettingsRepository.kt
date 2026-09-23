package com.tuempresa.autodialer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MAX_ATTEMPTS = intPreferencesKey("max_attempts")
        val MAX_RETRY_DAYS = intPreferencesKey("max_retry_days")
        val MAX_INTERESTED_PER_DAY = intPreferencesKey("max_interested_per_day")
        val ANSWERED_THRESHOLD = intPreferencesKey("answered_threshold")
        val AUTO_HANGUP_SECONDS = intPreferencesKey("auto_hangup_seconds")
        val RETRY_HOUR = intPreferencesKey("retry_hour")
        val RETRY_MINUTE = intPreferencesKey("retry_minute")
        val REDIAL_DELAY_MIN = intPreferencesKey("redial_delay_min")
        val REDIAL_DELAY_MAX = intPreferencesKey("redial_delay_max")
        val TRANSITION_DELAY_MIN = intPreferencesKey("transition_delay_min")
        val TRANSITION_DELAY_MAX = intPreferencesKey("transition_delay_max")
        val WORK_PHONE_ACCOUNT_COMPONENT = stringPreferencesKey("work_phone_account_component")
        val WORK_PHONE_ACCOUNT_ID = stringPreferencesKey("work_phone_account_id")
        val LAST_PHONE_ACCOUNT_COMPONENT = stringPreferencesKey("phone_account_component")
        val LAST_PHONE_ACCOUNT_ID = stringPreferencesKey("phone_account_id")
        val SIM_SELECTION_MODE = stringPreferencesKey("sim_selection_mode")
        val AUTO_SCHEDULING_ENABLED = booleanPreferencesKey("auto_scheduling_enabled")
        val SOUND_ALERT_ON_ANSWER = booleanPreferencesKey("sound_alert_on_answer")
        val FIREBASE_BACKUP_ENABLED = booleanPreferencesKey("firebase_backup_enabled")
        val IS_VACATION_MODE_ACTIVE = booleanPreferencesKey("vacation_mode_active")
        val VACATION_START_MILLIS = longPreferencesKey("vacation_start_millis")
        val VACATION_AUTO_RESUME_MILLIS = longPreferencesKey("vacation_auto_resume_millis")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val FINAL_OUTCOME_STATUS = stringPreferencesKey("final_outcome_status")
        val LAST_QUOTA_PAUSE_DATE = stringPreferencesKey("last_quota_pause_date")
    }

    val settingsFlow: Flow<com.tuempresa.autodialer.data.repository.DialerSettingsData> = context.dataStore.data
        .map { preferences ->
            com.tuempresa.autodialer.data.repository.DialerSettingsData(
                maxAttemptsPerContact = preferences[Keys.MAX_ATTEMPTS] ?: 2,
                maxRetryDays = preferences[Keys.MAX_RETRY_DAYS] ?: 3,
                maxInterestedPerDay = preferences[Keys.MAX_INTERESTED_PER_DAY] ?: 0,
                answeredThresholdSeconds = preferences[Keys.ANSWERED_THRESHOLD] ?: 12,
                autoHangupSeconds = preferences[Keys.AUTO_HANGUP_SECONDS] ?: 20,
                retryHour = preferences[Keys.RETRY_HOUR] ?: 9,
                retryMinute = preferences[Keys.RETRY_MINUTE] ?: 0,
                redialDelayMin = preferences[Keys.REDIAL_DELAY_MIN] ?: 3,
                redialDelayMax = preferences[Keys.REDIAL_DELAY_MAX] ?: 6,
                transitionDelayMin = preferences[Keys.TRANSITION_DELAY_MIN] ?: 8,
                transitionDelayMax = preferences[Keys.TRANSITION_DELAY_MAX] ?: 12,
                workPhoneAccountComponent = preferences[Keys.WORK_PHONE_ACCOUNT_COMPONENT],
                workPhoneAccountId = preferences[Keys.WORK_PHONE_ACCOUNT_ID],
                lastPhoneAccountComponent = preferences[Keys.LAST_PHONE_ACCOUNT_COMPONENT],
                lastPhoneAccountId = preferences[Keys.LAST_PHONE_ACCOUNT_ID],
                simSelectionMode = preferences[Keys.SIM_SELECTION_MODE] ?: "ASK_ALWAYS",
                autoSchedulingEnabled = preferences[Keys.AUTO_SCHEDULING_ENABLED] ?: true,
                soundAlertOnAnswer = preferences[Keys.SOUND_ALERT_ON_ANSWER] ?: true,
                firebaseBackupEnabled = preferences[Keys.FIREBASE_BACKUP_ENABLED] ?: true,
                isVacationModeActive = preferences[Keys.IS_VACATION_MODE_ACTIVE] ?: false,
                vacationStartMillis = preferences[Keys.VACATION_START_MILLIS] ?: 0L,
                vacationAutoResumeMillis = preferences[Keys.VACATION_AUTO_RESUME_MILLIS] ?: 0L,
                googleAccountEmail = preferences[Keys.GOOGLE_ACCOUNT_EMAIL],
                hasSeenOnboarding = preferences[Keys.HAS_SEEN_ONBOARDING] ?: false,
                finalOutcomeStatus = preferences[Keys.FINAL_OUTCOME_STATUS] ?: "FINAL_NO_ANSWER",
                lastQuotaPauseDate = preferences[Keys.LAST_QUOTA_PAUSE_DATE]
            )
        }
// ... (adding update methods)
    suspend fun updateLastPhoneAccount(component: String?, id: String?) = context.dataStore.edit {
        if (component == null) it.remove(Keys.LAST_PHONE_ACCOUNT_COMPONENT) else it[Keys.LAST_PHONE_ACCOUNT_COMPONENT] = component
        if (id == null) it.remove(Keys.LAST_PHONE_ACCOUNT_ID) else it[Keys.LAST_PHONE_ACCOUNT_ID] = id
    }
    suspend fun updateFinalOutcomeStatus(status: String) = context.dataStore.edit { it[Keys.FINAL_OUTCOME_STATUS] = status }
    suspend fun updateLastQuotaPauseDate(date: String?) = context.dataStore.edit {
        if (date == null) it.remove(Keys.LAST_QUOTA_PAUSE_DATE) else it[Keys.LAST_QUOTA_PAUSE_DATE] = date
    }

    suspend fun updateMaxAttempts(value: Int) = context.dataStore.edit { it[Keys.MAX_ATTEMPTS] = value }
    suspend fun updateMaxRetryDays(value: Int) = context.dataStore.edit { it[Keys.MAX_RETRY_DAYS] = value }
    suspend fun updateMaxInterestedPerDay(value: Int) = context.dataStore.edit { it[Keys.MAX_INTERESTED_PER_DAY] = value }
    suspend fun updateAnsweredThreshold(value: Int) = context.dataStore.edit { it[Keys.ANSWERED_THRESHOLD] = value }
    suspend fun updateAutoHangupSeconds(value: Int) = context.dataStore.edit { it[Keys.AUTO_HANGUP_SECONDS] = value }
    suspend fun updateRetryTime(hour: Int, minute: Int) = context.dataStore.edit { 
        it[Keys.RETRY_HOUR] = hour
        it[Keys.RETRY_MINUTE] = minute
    }
    suspend fun updateRedialDelay(min: Int, max: Int) = context.dataStore.edit {
        it[Keys.REDIAL_DELAY_MIN] = min
        it[Keys.REDIAL_DELAY_MAX] = max
    }
    suspend fun updateTransitionDelay(min: Int, max: Int) = context.dataStore.edit {
        it[Keys.TRANSITION_DELAY_MIN] = min
        it[Keys.TRANSITION_DELAY_MAX] = max
    }
    suspend fun updateWorkSim(component: String?, id: String?) = context.dataStore.edit {
        if (component == null) it.remove(Keys.WORK_PHONE_ACCOUNT_COMPONENT) else it[Keys.WORK_PHONE_ACCOUNT_COMPONENT] = component
        if (id == null) it.remove(Keys.WORK_PHONE_ACCOUNT_ID) else it[Keys.WORK_PHONE_ACCOUNT_ID] = id
    }
    suspend fun updateSimSelectionMode(mode: String) = context.dataStore.edit { it[Keys.SIM_SELECTION_MODE] = mode }
    suspend fun updateAutoSchedulingEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_SCHEDULING_ENABLED] = enabled }
    suspend fun updateSoundAlertOnAnswer(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND_ALERT_ON_ANSWER] = enabled }
    suspend fun updateFirebaseBackupEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.FIREBASE_BACKUP_ENABLED] = enabled }
    suspend fun updateVacationMode(active: Boolean, start: Long = 0L, autoResume: Long = 0L) = context.dataStore.edit {
        it[Keys.IS_VACATION_MODE_ACTIVE] = active
        it[Keys.VACATION_START_MILLIS] = start
        it[Keys.VACATION_AUTO_RESUME_MILLIS] = autoResume
    }
    suspend fun updateGoogleAccountEmail(email: String?) = context.dataStore.edit {
        if (email == null) it.remove(Keys.GOOGLE_ACCOUNT_EMAIL) else it[Keys.GOOGLE_ACCOUNT_EMAIL] = email
    }
    suspend fun updateHasSeenOnboarding(value: Boolean) = context.dataStore.edit { it[Keys.HAS_SEEN_ONBOARDING] = value }
}

data class DialerSettingsData(
    val maxAttemptsPerContact: Int,
    val maxRetryDays: Int,
    val maxInterestedPerDay: Int,
    val answeredThresholdSeconds: Int,
    val autoHangupSeconds: Int,
    val retryHour: Int,
    val retryMinute: Int,
    val redialDelayMin: Int,
    val redialDelayMax: Int,
    val transitionDelayMin: Int,
    val transitionDelayMax: Int,
    val workPhoneAccountComponent: String?,
    val workPhoneAccountId: String?,
    val lastPhoneAccountComponent: String?,
    val lastPhoneAccountId: String?,
    val simSelectionMode: String,
    val autoSchedulingEnabled: Boolean,
    val soundAlertOnAnswer: Boolean,
    val firebaseBackupEnabled: Boolean,
    val isVacationModeActive: Boolean,
    val vacationStartMillis: Long,
    val vacationAutoResumeMillis: Long,
    val googleAccountEmail: String?,
    val hasSeenOnboarding: Boolean,
    val finalOutcomeStatus: String,
    val lastQuotaPauseDate: String?
)
