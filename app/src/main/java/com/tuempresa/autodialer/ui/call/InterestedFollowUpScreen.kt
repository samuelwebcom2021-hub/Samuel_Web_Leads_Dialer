package com.tuempresa.autodialer.ui.call

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.ui.components.CrmActionButton
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark
import java.util.Calendar

@Composable
fun InterestedFollowUpScreen(
    session: CallSessionEntity,
    onFollowUpScheduled: (Calendar, String) -> Unit,
    onCallNow: () -> Unit,
    onSaveOnly: () -> Unit
) {
    var showScheduler by remember { mutableStateOf(false) }

    // Determinar el número a mostrar en el cuadro de seguimiento (Prioridad: WhatsApp -> Número Alternativo -> Número Marcado)
    val capturedNumber = remember(session) {
        session.whatsappNumber?.takeIf { it.isNotBlank() }
            ?: session.alternateNumber?.takeIf { it.isNotBlank() }
            ?: session.dialedNumber
    }

    // Tipo predeterminado según el dato ingresado durante la llamada
    val defaultType = remember(session) {
        if (!session.whatsappNumber.isNullOrBlank()) "WhatsApp"
        else if (!session.alternateNumber.isNullOrBlank()) "Llamada"
        else "WhatsApp"
    }

    if (showScheduler) {
        FollowUpScheduler(
            initialType = defaultType,
            onSave = onFollowUpScheduled,
            onCancel = { showScheduler = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "¡Contacto Interesado!",
                style = MaterialTheme.typography.headlineSmall,
                color = GoldAccent,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Cuadro oscuro mostrando el número capturado en vivo (Imagen 4)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = SurfaceVariantDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = capturedNumber,
                        color = GoldAccent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            CrmActionButton(
                text = "PROGRAMAR SEGUIMIENTO",
                onClick = { showScheduler = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSaveOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("SOLO GUARDAR", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun FollowUpScheduler(
    initialType: String = "WhatsApp",
    onSave: (Calendar, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    
    var dateText by remember { mutableStateOf("Seleccionar fecha") }
    var timeText by remember { mutableStateOf("Seleccionar hora") }
    var followUpType by remember { mutableStateOf(initialType) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Programar Seguimiento",
            color = GoldAccent,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    calendar.set(Calendar.YEAR, y)
                    calendar.set(Calendar.MONTH, m)
                    calendar.set(Calendar.DAY_OF_MONTH, d)
                    dateText = "$d/${m+1}/$y"
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(dateText, color = GoldAccent, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                TimePickerDialog(context, { _, h, m ->
                    calendar.set(Calendar.HOUR_OF_DAY, h)
                    calendar.set(Calendar.MINUTE, m)
                    timeText = "%02d:%02d".format(h, m)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(timeText, color = GoldAccent, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Tipo de seguimiento",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("WhatsApp", "Llamada").forEach { type ->
                    val selected = followUpType == type
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { followUpType = type },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Color(0xFF38354A) else SurfaceVariantDark,
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = type,
                                color = if (selected) Color.White else Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
            CrmActionButton(
                text = "GUARDAR",
                onClick = { onSave(calendar, followUpType) },
                modifier = Modifier.weight(1f).height(48.dp)
            )
        }
    }
}
