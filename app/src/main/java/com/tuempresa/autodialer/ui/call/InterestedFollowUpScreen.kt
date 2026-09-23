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
    val context = LocalContext.current
    var showScheduler by remember { mutableStateOf(false) }

    if (showScheduler) {
        FollowUpScheduler(
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
            
            // Cuadro oscuro (Image 10)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = SurfaceVariantDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                // Espacio para mostrar nombre si se desea, o simplemente decorativo como en la imagen
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = session.dialedNumber,
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
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
    onSave: (Calendar, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    
    var dateText by remember { mutableStateOf("Seleccionar fecha") }
    var timeText by remember { mutableStateOf("Seleccionar hora") }
    var followUpType by remember { mutableStateOf("WhatsApp") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Programar Seguimiento", color = GoldAccent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GoldAccent)
            Spacer(Modifier.width(12.dp))
            Text(dateText, color = Color.White)
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldAccent)
            Spacer(Modifier.width(12.dp))
            Text(timeText, color = Color.White)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Tipo de seguimiento", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("WhatsApp", "Llamada").forEach { type ->
                val selected = followUpType == type
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp).clickable { followUpType = type },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) GoldAccent.copy(alpha = 0.2f) else SurfaceVariantDark,
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, GoldAccent) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(type, color = if (selected) GoldAccent else Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("CANCELAR", color = Color.Gray)
            }
            CrmActionButton(
                text = "GUARDAR",
                onClick = { onSave(calendar, followUpType) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
