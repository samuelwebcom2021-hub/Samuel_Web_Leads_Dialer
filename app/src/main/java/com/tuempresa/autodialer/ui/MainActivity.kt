package com.tuempresa.autodialer.ui

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.alarms.ReminderBroadcastReceiver
import com.tuempresa.autodialer.auth.GoogleAuthManager
import com.tuempresa.autodialer.data.AgendaWithContact
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.dialer.DialerEvent
import com.tuempresa.autodialer.dialer.DialerEvents
import com.tuempresa.autodialer.dialer.DialerService
import com.tuempresa.autodialer.dialer.SimSelector
import com.tuempresa.autodialer.ui.agenda.AgendaScreen
import com.tuempresa.autodialer.ui.carpetas.FolderDetailScreen
import com.tuempresa.autodialer.ui.carpetas.FoldersScreen
import com.tuempresa.autodialer.ui.interesados.ContactDetailScreen
import com.tuempresa.autodialer.ui.interesados.InterestedScreen
import com.tuempresa.autodialer.ui.search.SearchScreen
import com.tuempresa.autodialer.ui.settings.SettingsScreen
import com.tuempresa.autodialer.ui.theme.AutoDialerTheme
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val viewModel: ContactsViewModel by viewModels()
    private lateinit var authManager: GoogleAuthManager
    private lateinit var vacationManager: com.tuempresa.autodialer.core.VacationModeManager

    private var currentBatchIdState = mutableStateOf<Long?>(null)
    private var currentSection = mutableStateOf(R.id.nav_folders)
    private var isDialingState = mutableStateOf(false)
    private var isPausedState = mutableStateOf(false)
    private var globalSearchQuery = mutableStateOf("")
    
    private val isDialerRoleHeld = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val COUNTRY_OPTIONS = listOf(
        "Colombia (+57)" to "+57",
        "México (+52)" to "+52",
        "Estados Unidos (+1)" to "+1",
        "España (+34)" to "+34",
        "Otro (escribir código)" to null
    )

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) proceedToSimSelection()
        else Toast.makeText(this, "Se necesitan todos los permisos de llamada para iniciar", Toast.LENGTH_LONG).show()
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Tono seleccionado ✓", Toast.LENGTH_SHORT).show()
        }
    }

    private val openExcelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al tomar permisos persistentes de URI: ${e.message}")
            }
            startImportFlow(uri)
        }
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val isHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else false

        if (result.resultCode == RESULT_OK || isHeld) {
            Toast.makeText(this, "Rol de marcador concedido ✓", Toast.LENGTH_SHORT).show()
            checkPermissionsAndStart()
        } else {
            showDialerRoleExplanationDialog()
        }
    }

    // ---------- Ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        authManager = GoogleAuthManager(this)
        vacationManager = com.tuempresa.autodialer.core.VacationModeManager(this)

        val animatedBackground = findViewById<AnimatedBackgroundView>(R.id.animatedBackground)
        lifecycle.addObserver(animatedBackground)

        setupComposeContainer()
        setupBottomNav()
        observeDialerEvents()
    }

    private fun setupComposeContainer() {
        val composeContainer = findViewById<ComposeView>(R.id.composeContainer)
        composeContainer.setContent {
            AutoDialerTheme {
                val navController = rememberNavController()
                val section by currentSection
                
                LaunchedEffect(section) {
                    navController.navigate(getRouteForSection(section)) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                val contacts by viewModel.contacts.observeAsState(emptyList())
                val batches by viewModel.batches.observeAsState(emptyList())
                val folderRetries by viewModel.folderRetries.observeAsState(emptyList())
                val personalReminders by viewModel.personalReminders.observeAsState(emptyList())
                val settings by viewModel.settingsState.collectAsState()
                val waitSeconds by com.tuempresa.autodialer.dialer.DialerEvents.remainingWaitSeconds.collectAsState()

                NavHost(navController, startDestination = "folders") {
                    composable("folders") { _ ->
                        val batchId by currentBatchIdState
                        val queryValue by globalSearchQuery
                        
                        if (queryValue.isNotBlank() && batchId == null) {
                            // Mostrar resultados de búsqueda global
                            val filteredContacts = contacts.filter { 
                                it.businessName.contains(queryValue, ignoreCase = true) || 
                                it.phoneNumber.contains(queryValue, ignoreCase = true)
                            }
                            SearchScreen(
                                query = queryValue,
                                results = filteredContacts,
                                onBackClick = { globalSearchQuery.value = "" },
                                onContactClick = { contact -> showContactDetail(contact) }
                            )
                        } else if (batchId == null) {
                            FoldersScreen(
                                batches = batches,
                                onFolderClick = { id -> currentBatchIdState.value = id },
                                onDeleteFolder = { batch ->
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("¿Eliminar carpeta?")
                                        .setMessage("¿Estás seguro de que deseas eliminar la carpeta \"${batch.importBatchName}\" y todos sus contactos?")
                                        .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteBatch(batch.importBatchId) }
                                        .setNegativeButton("Cancelar", null)
                                        .show()
                                },
                                onImportClick = { openExcelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv")) },
                                onGlobalSearch = { query -> globalSearchQuery.value = query }
                            )
                        } else {
                            val folderName = batches.find { it.importBatchId == batchId }?.importBatchName ?: "Carpeta"
                            FolderDetailScreen(
                                folderName = folderName,
                                contacts = contacts.filter { it.importBatchId == batchId },
                                isDialing = isDialingState.value,
                                isPaused = isPausedState.value,
                                remainingWaitSeconds = waitSeconds,
                                onBackClick = { currentBatchIdState.value = null },
                                onStartStopClick = { if (isDialingState.value) stopDialing() else startDialingFlow() },
                                onPauseClick = { togglePause() },
                                onSkipClick = { skipCurrentContact() },
                                onContactClick = { contact -> showContactDetail(contact) },
                                onSearch = { /* local search */ }
                            )
                        }
                    }
                    composable("interested") { _ ->
                        InterestedScreen(contacts.filter { it.status == "INTERESTED" }) { contact -> 
                            navController.navigate("contact_detail/${contact.id}")
                        }
                    }
                    composable("contact_detail/{contactId}") { backStackEntry ->
                        val contactId = backStackEntry.arguments?.getString("contactId")?.toLongOrNull() ?: -1L
                        ContactDetailScreen(
                            contactId = contactId,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("scheduled") { _ ->
                        AgendaScreen(folderRetries, personalReminders) { item -> showAgendaDetail(item) }
                    }
                    composable("stats") { _ ->
                        com.tuempresa.autodialer.ui.estadisticas.StatsScreen()
                    }
                    composable("settings") { _ ->
                        val held by isDialerRoleHeld.collectAsState()
                        val user by viewModel.currentUser.collectAsState()
                        val pendingCount by viewModel.syncPendingCount.observeAsState(0)
                        val logs by viewModel.lastSyncTime.observeAsState(emptyList())
                        val lastSync = logs.firstOrNull { it.success }?.timestampMillis?.let {
                            java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                        } ?: "Nunca"

                        SettingsScreen(
                            settings = settings,
                            isDialerRoleHeld = held,
                            currentUser = user,
                            onRequestDialerRole = { requestDialerRole() },
                            onSelectSim = { showWorkSimSelection() },
                            workSimLabel = getWorkSimLabel(),
                            onVacationClick = { showVacationActivationDialog() },
                            onGoogleClick = { onGoogleButtonClicked() },
                            onSyncLogClick = { showSyncLogDialog() },
                            syncStatus = if (user != null) "$pendingCount cambios" else null,
                            lastSyncTime = lastSync,
                            onSyncNow = { viewModel.triggerSyncNow() },
                            onUpdateMaxAttempts = { valAtt -> viewModel.updateMaxAttempts(valAtt) },
                            onUpdateMaxRetryDays = { valDays -> viewModel.updateMaxRetryDays(valDays) },
                            onUpdateAutoHangup = { valSec -> viewModel.updateAutoHangupSeconds(valSec) },
                            onUpdateRedialDelay = { minVal, maxVal -> viewModel.updateRedialDelay(minVal, maxVal) },
                            onUpdateTransitionDelay = { minVal, maxVal -> viewModel.updateTransitionDelay(minVal, maxVal) },
                            onUpdateSoundAlert = { valSound -> viewModel.updateSoundAlertOnAnswer(valSound) },
                            onRetryTimeClick = { showGlobalRetryTimePicker() }
                        )
                    }
                }
            }
        }
    }

    private fun getRouteForSection(id: Int): String = when (id) {
        R.id.nav_folders -> "folders"
        R.id.nav_interested -> "interested"
        R.id.nav_scheduled -> "scheduled"
        R.id.nav_stats -> "stats"
        R.id.nav_settings -> "settings"
        else -> "folders"
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            currentSection.value = item.itemId
            true
        }
    }

    override fun onResume() {
        super.onResume()
        checkDialerRole()
        checkSpecialPermissions()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = (application as App).db
            val nextRetry = db.contactDao().nextAwaitingRetryChoice()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                nextRetry?.let { showRetryTimePicker(it.id, it.phoneNumber) }
            }
        }
    }

    private fun checkDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            isDialerRoleHeld.value = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }
    }

    private fun checkSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("MainActivity", "Permiso de alarmas exactas no concedido")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                Log.w("MainActivity", "Permiso de pantalla completa no concedido")
            }
        }
    }

    private fun requestSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun getWorkSimLabel(): String {
        val settings = viewModel.settingsState.value
        val simMode = settings.simSelectionMode
        return if (simMode == "ASK_ALWAYS") {
            "Preguntar siempre"
        } else {
            val handle = SimSelector.getHandleFromStrings(
                settings.workPhoneAccountComponent,
                settings.workPhoneAccountId
            )
            if (handle != null) {
                val available = SimSelector.listAvailableSims(this)
                available.find { it.handle == handle }?.label ?: "SIM no disponible"
            } else {
                "No elegida"
            }
        }
    }

    private fun launchDialerRolePicker() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    dialerRoleLauncher.launch(intent)
                    return
                }
            }
            @Suppress("DEPRECATION")
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            dialerRoleLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error lanzando solicitud de marcador: ${e.message}")
            checkPermissionsAndStart()
        }
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                AlertDialog.Builder(this)
                    .setTitle("Administrar Marcador")
                    .setMessage("AutoDialerCRM ya es tu aplicación predeterminada.\n\n¿Deseas cambiarla en los ajustes del sistema?")
                    .setPositiveButton("Ir a Ajustes") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                launchDialerRolePicker()
            }
        } else {
            launchDialerRolePicker()
        }
    }

    private fun showWorkSimSelection() {
        val sims = com.tuempresa.autodialer.dialer.SimSelector.listAvailableSims(this)
        val options = mutableListOf("Preguntar siempre")
        options.addAll(sims.map { it.label })
        
        AlertDialog.Builder(this)
            .setTitle("Configuración de SIM")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    viewModel.updateSimSelectionMode("ASK_ALWAYS")
                    viewModel.updateWorkSim(null, null)
                } else {
                    val chosen = sims[which - 1].handle
                    viewModel.updateSimSelectionMode("FIXED")
                    viewModel.updateWorkSim(chosen.componentName.flattenToString(), chosen.id)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startImportFlow(uri: Uri) {
        val fileName = resolveFileName(uri)
        val labels = COUNTRY_OPTIONS.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("¿Qué código de país debo aceptar?")
            .setItems(labels) { _, which ->
                val chosen = COUNTRY_OPTIONS[which].second
                if (chosen != null) continueImportFlow(uri, fileName, chosen) else askCustomCountryCode(uri, fileName)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resolveFileName(uri: Uri): String {
        var name = "Excel importado"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) name = it.getString(idx) ?: name
        }
        return name
    }

    private fun askCustomCountryCode(uri: Uri, fileName: String) {
        val input = EditText(this).apply { hint = "ej. +54"; setPadding(40, 30, 40, 30) }
        AlertDialog.Builder(this)
            .setTitle("Código de país")
            .setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val code = input.text.toString().trim()
                if (code.startsWith("+") && code.length in 2..4) continueImportFlow(uri, fileName, code)
                else Toast.makeText(this, "Escribe el código con el formato +NN", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun continueImportFlow(uri: Uri, fileName: String, countryCode: String) {
        lifecycleScope.launch {
            val rawData = runCatching { viewModel.peekFile(uri) }.getOrNull()
            if (rawData == null || rawData.headers.isEmpty()) {
                Toast.makeText(this@MainActivity, "No se pudo leer el archivo", Toast.LENGTH_LONG).show()
                return@launch
            }
            showColumnMappingDialog(uri, fileName, countryCode, rawData)
        }
    }

    private fun showColumnMappingDialog(uri: Uri, fileName: String, countryCode: String, rawData: com.tuempresa.autodialer.data.local.parser.RawFileData) {
        val detection = viewModel.detectColumns(rawData.headers)
        val optionalLabels = listOf(getString(R.string.none_option)) + rawData.headers

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_column_mapping, null)
        val spinnerPhone = dialogView.findViewById<Spinner>(R.id.spinnerPhone)
        val spinnerBusiness = dialogView.findViewById<Spinner>(R.id.spinnerBusiness)
        val spinnerWebsite = dialogView.findViewById<Spinner>(R.id.spinnerWebsite)
        val spinnerRating = dialogView.findViewById<Spinner>(R.id.spinnerRating)
        val spinnerReviewCount = dialogView.findViewById<Spinner>(R.id.spinnerReviewCount)

        spinnerPhone.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rawData.headers)
        spinnerPhone.setSelection(detection.phoneIndex.coerceAtLeast(0))

        val mappings = listOf(
            spinnerBusiness to detection.nameIndex,
            spinnerWebsite to detection.websiteIndex,
            spinnerRating to detection.ratingIndex,
            spinnerReviewCount to detection.reviewCountIndex
        )

        mappings.forEach { (spinner, index) ->
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionalLabels)
            spinner.setSelection(if (index >= 0) index + 1 else 0)
        }

        AlertDialog.Builder(this)
            .setTitle("Mapeo de Columnas")
            .setView(dialogView)
            .setPositiveButton("Siguiente") { _, _ ->
                val pIdx = spinnerPhone.selectedItemPosition
                val bIdx = spinnerBusiness.selectedItemPosition - 1
                val wIdx = spinnerWebsite.selectedItemPosition - 1
                val rIdx = spinnerRating.selectedItemPosition - 1
                val rcIdx = spinnerReviewCount.selectedItemPosition - 1
                showImportPreview(uri, fileName, countryCode, pIdx, bIdx, wIdx, rIdx, rcIdx)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showImportPreview(uri: Uri, fileName: String, countryCode: String, pIdx: Int, bIdx: Int, wIdx: Int, rIdx: Int, rcIdx: Int) {
        findViewById<View>(R.id.analyzingOverlay).visibility = View.VISIBLE
        lifecycleScope.launch {
            val analysis = viewModel.analyzeFile(uri, countryCode, pIdx, bIdx, wIdx, rIdx, rcIdx)
            findViewById<View>(R.id.analyzingOverlay).visibility = View.GONE
            
            val countryName = COUNTRY_OPTIONS.find { it.second == countryCode }?.first?.split(" ")?.firstOrNull() ?: countryCode
            val message = buildString {
                append("Se detectaron ${analysis.duplicates.size} duplicados.\n\n")
                append("${analysis.candidates.size} números con prefijo $countryCode ($countryName)")
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Importar ${analysis.candidates.size} contactos")
                .setMessage(message)
                .setPositiveButton("Confirmar") { _, _ -> finishImportNew(uri, fileName, countryCode, pIdx, bIdx, wIdx, rIdx, rcIdx, analysis.candidates.size) }
                .setNegativeButton("Atrás", null)
                .show()
        }
    }

    private fun finishImportNew(uri: Uri, fileName: String, countryCode: String, pIdx: Int, bIdx: Int, wIdx: Int, rIdx: Int, rcIdx: Int, count: Int) {
        findViewById<View>(R.id.analyzingOverlay).visibility = View.VISIBLE
        lifecycleScope.launch {
            val batchId = viewModel.commitImport(fileName, uri, pIdx, bIdx, wIdx, rIdx, rcIdx, countryCode)
            findViewById<View>(R.id.analyzingOverlay).visibility = View.GONE
            Toast.makeText(this@MainActivity, "Importación completada", Toast.LENGTH_LONG).show()
            currentBatchIdState.value = batchId
        }
    }

    private fun checkPermissionsAndStart() {
        val missing = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            proceedToSimSelection()
        }
    }

    private fun showDialerRoleExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Marcador Predeterminado")
            .setMessage("Para que la automatización (pantalla de llamadas en la app, detección de respuesta y colgado automático) funcione correctamente, la app debe estar seleccionada como marcador predeterminado.\n\n¿Deseas intentarlo de nuevo o continuar?")
            .setPositiveButton("Seleccionar Marcador") { _, _ ->
                launchDialerRolePicker()
            }
            .setNegativeButton("Continuar de todos modos") { _, _ ->
                checkPermissionsAndStart()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun startDialingFlow() {
        val currentFolderId = currentBatchIdState.value ?: return
        
        // RF-1.3: Sesión única global
        val activeSession = com.tuempresa.autodialer.dialer.DialerEvents.session.value
        if (activeSession != null && activeSession.folderId != currentFolderId) {
            AlertDialog.Builder(this)
                .setTitle("Automatización activa")
                .setMessage("Ya hay una automatización activa en otra carpeta. Debes detenerla antes de iniciar una nueva.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                launchDialerRolePicker()
                return
            }
        }
        
        checkPermissionsAndStart()
    }

    private fun proceedToSimSelection() {
        val settings = viewModel.settingsState.value
        if (settings.simSelectionMode == "FIXED") {
            val handle = SimSelector.getHandleFromStrings(settings.workPhoneAccountComponent, settings.workPhoneAccountId)
            if (handle != null && SimSelector.isHandleValid(this, handle)) {
                actuallyStartService(handle)
                return
            }
        }

        val sims = SimSelector.listAvailableSims(this)
        if (sims.size <= 1) { actuallyStartService(sims.firstOrNull()?.handle); return }
        val labels = sims.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar SIM")
            .setItems(labels) { _, which -> actuallyStartService(sims[which].handle) }
            .show()
    }

    private fun actuallyStartService(phoneAccount: PhoneAccountHandle?) {
        if (phoneAccount != null) {
            viewModel.updateLastPhoneAccount(phoneAccount.componentName.flattenToString(), phoneAccount.id)
        }
        val intent = Intent(this, DialerService::class.java).apply {
            action = DialerService.ACTION_START
            putExtra(DialerService.EXTRA_BATCH_ID, currentBatchIdState.value ?: -1L)
            if (phoneAccount != null) putExtra(DialerService.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount)
        }
        ContextCompat.startForegroundService(this, intent)
        isDialingState.value = true
    }

    private fun stopDialing() {
        startService(Intent(this, DialerService::class.java).apply { action = DialerService.ACTION_STOP })
        isDialingState.value = false
        isPausedState.value = false
    }

    private fun togglePause() {
        if (isPausedState.value) {
            startService(Intent(this, DialerService::class.java).apply { action = DialerService.ACTION_RESUME })
            isPausedState.value = false
        } else {
            startService(Intent(this, DialerService::class.java).apply { action = DialerService.ACTION_PAUSE })
            isPausedState.value = true
        }
    }

    private fun skipCurrentContact() {
        startService(Intent(this, DialerService::class.java).apply { action = DialerService.ACTION_SKIP })
    }

    private fun observeDialerEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DialerEvents.events.collect { event ->
                    when (event) {
                        is DialerEvent.NeedsRetryTimeChoice -> showRetryTimePicker(event.contactId, event.phoneNumber)
                        is DialerEvent.ShowNoAnswerNotice -> Toast.makeText(this@MainActivity, event.notice, Toast.LENGTH_LONG).show()
                        is DialerEvent.QueueFinished -> {
                            isDialingState.value = false
                            isPausedState.value = false
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun showRetryTimePicker(contactId: Long, phoneNumber: String) {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            viewModel.scheduleRetryForTomorrow(contactId, h, m)
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun showAgendaDetail(item: AgendaWithContact) {
        AlertDialog.Builder(this)
            .setTitle(item.contact?.businessName ?: "Recordatorio")
            .setMessage(item.agendaItem.reason)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showGlobalRetryTimePicker() {
        val settings = viewModel.settingsState.value
        TimePickerDialog(this, { _, h, m ->
            viewModel.updateRetryTime(h, m)
        }, settings.retryHour, settings.retryMinute, true).show()
    }

    private fun onGoogleButtonClicked() {
        lifecycleScope.launch {
            val result = authManager.signIn(this@MainActivity)
            result.onSuccess { viewModel.triggerSyncNow() }
        }
    }

    private fun showSyncLogDialog() {
        val logs = viewModel.lastSyncTime.value
        if (logs.isNullOrEmpty()) {
            Toast.makeText(this, "No hay registros de sincronización", Toast.LENGTH_SHORT).show()
            return
        }
        val text = logs.joinToString("\n") { "${if(it.success) "✓" else "✗"} ${it.description}" }
        AlertDialog.Builder(this).setTitle("Historial de Sync").setMessage(text).show()
    }

    private fun showContactDetail(contact: ContactEntity) {
        AlertDialog.Builder(this)
            .setTitle(contact.businessName)
            .setMessage("Teléfono: ${contact.phoneNumber}")
            .show()
    }

    private fun showVacationActivationDialog() {
        if (viewModel.settings.isVacationModeActive) {
            vacationManager.deactivate()
            return
        }
        
        val options = arrayOf(
            "Pausa inmediata (Indefinida)",
            "Pausa inmediata (Con reanudación)",
            "Programar inicio y fin a futuro"
        )
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Activar Modo Vacaciones")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> vacationManager.activate()
                    1 -> showVacationResumeDatePicker()
                    2 -> showVacationFullSchedulePicker()
                }
            }
            .show()
    }

    private fun showVacationResumeDatePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val chosen = Calendar.getInstance().apply { set(year, month, day) }
            TimePickerDialog(this, { _, hour, minute ->
                chosen.set(Calendar.HOUR_OF_DAY, hour)
                chosen.set(Calendar.MINUTE, minute)
                vacationManager.activate(0L, chosen.timeInMillis)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showVacationFullSchedulePicker() {
        Toast.makeText(this, "Función simplificada", Toast.LENGTH_SHORT).show()
    }
}
