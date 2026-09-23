package com.tuempresa.autodialer.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.data.CallResult
import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.data.CallState
import com.tuempresa.autodialer.dialer.DialerEvents
import com.tuempresa.autodialer.domain.AutomationState
import com.tuempresa.autodialer.ui.call.components.CallControls
import com.tuempresa.autodialer.ui.call.components.CallDataPanel
import com.tuempresa.autodialer.ui.call.components.DtmfKeypad
import com.tuempresa.autodialer.ui.components.ButtonState
import com.tuempresa.autodialer.ui.components.CrmActionButton
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.annotation.RequiresApi
import android.os.Build

import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CallScreen(viewModel: CallViewModel = viewModel()) {
    val session by viewModel.session.collectAsState()
    val contact by viewModel.activeContact.collectAsState()
    val automationState by viewModel.automationState.collectAsState()
    val folderName by viewModel.folderName.collectAsState()
    val progress by viewModel.progress.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var showDtmf by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler {
        if (showDtmf) {
            showDtmf = false
        } else {
            viewModel.finishSession()
        }
    }

    if (session == null) {
        LaunchedEffect(Unit) {
            (context as? CallActivity)?.finishCallActivity() ?: (context as? android.app.Activity)?.finish()
        }
        Box(Modifier.fillMaxSize())
        return
    }

    val currentSession = session!!
    val isPip = (context as? android.app.Activity)?.isInPictureInPictureMode == true

    if (isPip) {
        val service by com.tuempresa.autodialer.dialer.AutoDialerInCallService.activeInCallService.collectAsState()
        PipCallScreen(
            contactName = contact?.businessName ?: currentSession.dialedNumber,
            number = currentSession.dialedNumber,
            callState = currentSession.state,
            startTime = currentSession.startTime,
            onHangupClick = {
                viewModel.flushCapturedData()
                service?.hangup()
            }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent // RF-9
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ZONA A: IDENTIDAD Y CONTEXTO
                ZoneIdentity(
                    folderName = folderName,
                    contactName = contact?.businessName ?: currentSession.dialedNumber,
                    number = currentSession.dialedNumber,
                    automationState = automationState,
                    progress = progress,
                    callState = currentSession.state,
                    startTime = currentSession.startTime
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showDtmf) {
                    DtmfKeypad(
                        onDigitClick = { /* DTMF tones */ },
                        modifier = Modifier.semantics { contentDescription = "Teclado de marcación" }
                    )
                    TextButton(
                        onClick = { showDtmf = false },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Ocultar teclado", color = GoldAccent)
                    }
                } else {
                    val postState by viewModel.postCallState.collectAsState()

                    when (currentSession.state) {
                        CallState.DISCONNECTED, CallState.FAILED -> {
                            // RF-6: Regla de Interesado Automático (Dueño o WhatsApp capturados válidos)
                            val hasValidWhatsapp = !currentSession.whatsappNumber.isNullOrBlank() && currentSession.whatsappNumber.trim().length >= 4
                            val hasValidAlt = !currentSession.alternateNumber.isNullOrBlank() && currentSession.alternateNumber.trim().length >= 4
                            val hasCapturedData = hasValidWhatsapp || hasValidAlt

                            if (postState == CallViewModel.PostCallState.FOLLOW_UP) {
                                InterestedFollowUpScreen(
                                    session = currentSession,
                                    onFollowUpScheduled = { cal, type -> viewModel.scheduleFollowUp(cal, type) },
                                    onCallNow = { viewModel.callOwnerNow() },
                                    onSaveOnly = { viewModel.finishSession() }
                                )
                            } else if (hasCapturedData && currentSession.state == CallState.DISCONNECTED) {
                                LaunchedEffect(currentSession.callId) {
                                    viewModel.resolveOutcome(CallResult.INTERESTED)
                                }
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = GoldAccent)
                                        Spacer(Modifier.height(16.dp))
                                        val text = if (!currentSession.whatsappNumber.isNullOrBlank()) "Interesado (WhatsApp detectado)..." else "Interesado (Número de dueño detectado)..."
                                        Text(text, color = Color.White)
                                    }
                                }
                            } else {
                                PostCallScreen(
                                    onResultSelected = { result ->
                                        viewModel.resolveOutcome(result)
                                    }
                                )
                            }
                        }
                        else -> {
                            val service by com.tuempresa.autodialer.dialer.AutoDialerInCallService.activeInCallService.collectAsState()

                            // ZONA B: CONTROLES
                            CallControls(
                                onKeypadClick = { showDtmf = true },
                                onHangupClick = {
                                    viewModel.flushCapturedData()
                                    service?.hangup()
                                }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // ZONA C: INFORMACIÓN COMERCIAL
                            val altNum by viewModel.alternateNumber.collectAsState()
                            val waNum by viewModel.whatsappNumber.collectAsState()
                            val noteText by viewModel.notes.collectAsState()

                            CallDataPanel(
                                session = currentSession,
                                contact = contact,
                                alternateNumber = altNum,
                                whatsappNumber = waNum,
                                notes = noteText,
                                onDataChange = { a, w, n -> viewModel.updateCapturedData(a, w, n) },
                                onSaveClick = { viewModel.flushCapturedData() },
                                modifier = Modifier.semantics { contentDescription = "Panel de datos comerciales" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ZoneIdentity(
    folderName: String,
    contactName: String,
    number: String,
    automationState: AutomationState,
    progress: com.tuempresa.autodialer.dialer.DialerEvent.Progress?,
    callState: CallState,
    startTime: Long
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Información del contacto y estado de la llamada" }
    ) {
        // Contexto de campaña
        Text(
            text = folderName.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = GoldAccent.copy(alpha = 0.8f),
            letterSpacing = 1.5.sp
        )
        if (progress != null) {
            Text(
                text = "Contacto ${progress.remaining} restantes",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estado de automatización
        AutomationStatus(state = automationState)

        Spacer(modifier = Modifier.height(16.dp))

        // Identidad del contacto
        Text(
            text = contactName,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge,
            color = GoldAccent,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Estado de la llamada y temporizador
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = "Estado: ${callState.name}" }
        ) {
            val statusText = when (callState) {
                CallState.ACTIVE -> "EN LLAMADA"
                CallState.RINGING -> "SONANDO"
                CallState.DIALING -> "CONECTANDO"
                CallState.DISCONNECTING -> "COLGANDO..."
                CallState.DISCONNECTED -> "FINALIZADA"
                CallState.FAILED -> "ERROR"
                else -> "PROCESANDO"
            }
            val statusColor = if (callState == CallState.ACTIVE) Color(0xFF4CAF50) else Color(0xFFFFD54F)
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (callState == CallState.ACTIVE) {
                Spacer(modifier = Modifier.width(16.dp))
                CallTimer(startTime = startTime)
            }
        }
    }
}

@Composable
fun AutomationStatus(state: AutomationState) {
    val isActive = state != AutomationState.PAUSED && state != AutomationState.IDLE
    val text = if (isActive) "AUTOMATIZACIÓN ACTIVA" else "AUTOMATIZACIÓN PAUSADA"
    val color = if (isActive) Color(0xFF4CAF50) else Color.Gray

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.semantics { contentDescription = "Estado del motor: $text" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun CallTimer(startTime: Long) {
    var ticks by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            ticks = (System.currentTimeMillis() - startTime) / 1000
            delay(1000)
        }
    }

    val minutes = ticks / 60
    val seconds = ticks % 60
    val timeString = "%02d:%02d".format(minutes, seconds)

    Text(
        text = timeString,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.semantics { contentDescription = "Duración de llamada: $minutes minutos $seconds segundos" }
    )
}

@Composable
fun PostCallScreen(onResultSelected: (CallResult) -> Unit) {
    var selectedResult by remember { mutableStateOf<CallResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Llamada Finalizada",
            style = MaterialTheme.typography.headlineSmall,
            color = GoldAccent,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "¿Cuál fue el resultado de la prospección?",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        val results = listOf(
            CallResult.INTERESTED to "👍 Interesado",
            CallResult.NOT_INTERESTED to "👎 No Interesado",
            CallResult.RETRY_LATER to "⏳ Reintentar luego",
            CallResult.WRONG_NUMBER to "❌ Número equivocado"
        )

        results.forEach { (result, label) ->
            CrmActionButton(
                text = label,
                onClick = { 
                    selectedResult = result
                    scope.launch {
                        delay(300) // Feedback visual
                        onResultSelected(result)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(56.dp),
                state = if (selectedResult == result) ButtonState.LOADING else ButtonState.NORMAL,
                containerColor = SurfaceVariantDark,
                contentColor = Color.White,
                enabled = selectedResult == null,
                contentDescription = "Marcar como $label"
            )
        }
    }
}

@Composable
fun PipCallScreen(
    contactName: String,
    number: String,
    callState: CallState,
    startTime: Long,
    onHangupClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = contactName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = number,
                color = GoldAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = if (callState == CallState.ACTIVE) Color(0xFF4CAF50) else Color(0xFFFFD54F)
                Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                if (callState == CallState.ACTIVE) {
                    CallTimer(startTime = startTime)
                } else {
                    Text("CONECTANDO", color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onHangupClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(26.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("FINALIZAR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
