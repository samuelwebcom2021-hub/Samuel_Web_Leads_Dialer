package com.tuempresa.autodialer.ui.settings

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.tuempresa.autodialer.data.repository.DialerSettingsData
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark

@Composable
fun SettingsScreen(
    settings: DialerSettingsData,
    isDialerRoleHeld: Boolean,
    currentUser: FirebaseUser?,
    onRequestDialerRole: () -> Unit,
    onSelectSim: () -> Unit,
    workSimLabel: String,
    onVacationClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onSyncLogClick: () -> Unit,
    syncStatus: String?,
    lastSyncTime: String,
    onSyncNow: () -> Unit,
    onUpdateMaxAttempts: (Int) -> Unit,
    onUpdateMaxRetryDays: (Int) -> Unit,
    onUpdateAutoHangup: (Int) -> Unit,
    onUpdateRedialDelay: (Int, Int) -> Unit,
    onUpdateTransitionDelay: (Int, Int) -> Unit,
    onUpdateSoundAlert: (Boolean) -> Unit,
    onRetryTimeClick: () -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item { SettingsHeader("Configuración") }

            // SECCIÓN: CUENTA (RF-9 Rediseño)
            item {
                SettingsSection("CUENTA", Icons.Default.AccountCircle) {
                    val isConnected = currentUser != null
                    
                    SettingsItem(
                        title = if (isConnected) "Cuenta conectada" else "☁ Conectar Google",
                        subtitle = currentUser?.email ?: "Respaldo y sincronización en la nube",
                        icon = if (isConnected) Icons.Default.CloudDone else Icons.Default.Cloud,
                        onClick = onGoogleClick,
                        highlight = isConnected,
                        contentDescription = if (isConnected) "Gestionar cuenta conectada" else "Vincular cuenta de Google"
                    )

                    if (isConnected) {
                        Spacer(Modifier.height(8.dp).padding(horizontal = 16.dp).background(OnSurfaceMuted.copy(alpha = 0.1f)).fillMaxWidth().height(1.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Respaldo en la nube", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(syncStatus ?: "Sincronizado", color = GoldAccent, fontSize = 12.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("·", color = OnSurfaceMuted)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Última: $lastSyncTime", color = OnSurfaceMuted, fontSize = 12.sp)
                                }
                            }
                            IconButton(
                                onClick = onSyncNow,
                                modifier = Modifier.semantics { contentDescription = "Sincronizar ahora" }
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = GoldAccent)
                            }
                        }
                    }
                }
            }

            // LLAMADAS
            item {
                SettingsSection("LLAMADAS", Icons.Default.Call) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        SettingsItem(
                            title = if (isDialerRoleHeld) "● App de llamadas activa" else "App de llamadas no configurada",
                            subtitle = if (isDialerRoleHeld) "Samuel Dialer es el marcador predeterminado. [ Administrar ]" 
                                      else "Samuel Dialer no es el marcador predeterminado. [ Activar ]",
                            icon = Icons.Default.SettingsPhone,
                            onClick = onRequestDialerRole,
                            highlight = isDialerRoleHeld,
                            contentDescription = "Configurar aplicación telefónica predeterminada"
                        )
                    }
                    SettingsItem(
                        title = "SIM para llamadas",
                        subtitle = workSimLabel,
                        icon = Icons.Default.SimCard,
                        onClick = onSelectSim,
                        contentDescription = "Seleccionar SIM para marcación"
                    )
                }
            }

            // AUTOMATIZACIÓN (RF-5)
            item {
                SettingsSection("AUTOMATIZACIÓN", Icons.Default.AutoMode) {
                    
                    SettingsSubHeader("RESPUESTA")
                    EditableSettingItem(
                        title = "Tiempo máximo para contestar (seg)",
                        value = settings.autoHangupSeconds.toString(),
                        subtitle = "Si no responde: Colgar automáticamente",
                        onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateAutoHangup(v) },
                        contentDescription = "Configurar tiempo de espera de respuesta"
                    )

                    SettingsSubHeader("REINTENTOS")
                    EditableSettingItem(
                        title = "Intentos por día",
                        value = settings.maxAttemptsPerContact.toString(),
                        onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateMaxAttempts(v) },
                        contentDescription = "Límite de llamadas por día por contacto"
                    )
                    EditableSettingItem(
                        title = "Días máximos de reintento",
                        value = settings.maxRetryDays.toString(),
                        onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateMaxRetryDays(v) },
                        contentDescription = "Días totales de seguimiento"
                    )

                    SettingsSubHeader("ENTRE REINTENTOS (Mismo contacto)")
                    Row(Modifier.fillMaxWidth()) {
                        EditableSettingItem(
                            title = "Mínimo (s)",
                            value = settings.redialDelayMin.toString(),
                            onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateRedialDelay(v, settings.redialDelayMax) },
                            modifier = Modifier.weight(1f),
                            contentDescription = "Pausa mínima entre reintentos"
                        )
                        EditableSettingItem(
                            title = "Máximo (s)",
                            value = settings.redialDelayMax.toString(),
                            onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateRedialDelay(settings.redialDelayMin, v) },
                            modifier = Modifier.weight(1f),
                            contentDescription = "Pausa máxima entre reintentos"
                        )
                    }

                    SettingsSubHeader("ENTRE CONTACTOS (Distintos)")
                    Row(Modifier.fillMaxWidth()) {
                        EditableSettingItem(
                            title = "Mínimo (s)",
                            value = settings.transitionDelayMin.toString(),
                            onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateTransitionDelay(v, settings.transitionDelayMax) },
                            modifier = Modifier.weight(1f),
                            contentDescription = "Pausa mínima entre contactos distintos"
                        )
                        EditableSettingItem(
                            title = "Máximo (s)",
                            value = settings.transitionDelayMax.toString(),
                            onValueChange = { val v = it.toIntOrNull(); if (v != null) onUpdateTransitionDelay(settings.transitionDelayMin, v) },
                            modifier = Modifier.weight(1f),
                            contentDescription = "Pausa máxima entre contactos distintos"
                        )
                    }

                    SettingsSubHeader("PROGRAMACIÓN")
                    val timeStr = "%02d:%02d".format(settings.retryHour, settings.retryMinute)
                    SettingsItem(
                        title = "Hora de reintentos del día siguiente",
                        subtitle = timeStr,
                        icon = Icons.Default.Schedule,
                        onClick = onRetryTimeClick,
                        contentDescription = "Cambiar hora de reintentos automáticos"
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggle(
                        title = "Modo Vacaciones",
                        subtitle = "Pausa global de automatización",
                        checked = settings.isVacationModeActive,
                        onCheckedChange = { onVacationClick() },
                        contentDescription = "Alternar pausa global del sistema"
                    )
                }
            }

            // INTERFAZ
            item {
                SettingsSection("INTERFAZ", Icons.Default.Palette) {
                    SettingsToggle(
                        title = "Alerta sonora al contestar",
                        checked = settings.soundAlertOnAnswer,
                        onCheckedChange = onUpdateSoundAlert,
                        contentDescription = "Sonido de confirmación al conectar llamada"
                    )
                    // RF-9: Se elimina Tarjeta Flotante como item independiente
                }
            }

            // SISTEMA
            item {
                SettingsSection("SISTEMA", Icons.Default.Dns) {
                    SettingsItem(
                        title = "Historial de sincronización",
                        subtitle = "Ver logs de Firebase",
                        icon = Icons.Default.History,
                        onClick = onSyncLogClick,
                        contentDescription = "Registro de actividades en la nube"
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        color = GoldAccent,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
fun SettingsSubHeader(text: String) {
    Text(
        text = text,
        color = GoldAccent.copy(alpha = 0.8f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantDark
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    highlight: Boolean = false,
    contentDescription: String? = null
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = if (highlight) GoldAccent else Color.White
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = if (highlight) GoldAccent else Color.White, fontWeight = FontWeight.Medium)
                subtitle?.let {
                    Text(it, color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceMuted)
        }
    }
}

@Composable
fun EditableSettingItem(
    title: String,
    value: String,
    subtitle: String? = null,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Column(
        modifier
            .padding(16.dp)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
    ) {
        Text(title, color = Color.White, fontSize = 13.sp)
        if (subtitle != null) {
            Text(subtitle, color = OnSurfaceMuted, fontSize = 11.sp)
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = GoldAccent
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldAccent,
                checkedTrackColor = GoldAccent.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
